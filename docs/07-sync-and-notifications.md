# Sync Strategy & Push Notifications

> Last updated: 2026-06-25

## Sync Model

The app is **offline-first**. SQLDelight on-device is the source of truth for the mobile app. Network sync is a background concern, not a blocking one.

**No real-time sync.** Two caregivers viewing the same child's log simultaneously do not see each other's updates live. To see the latest data, a user pulls to refresh or reopens the app. This is intentional — it avoids WebSockets, SSE, and the complexity they bring. A potty tracker does not need sub-second data freshness.

### Write Path

1. User logs an event → written to SQLDelight immediately with `sync_status = 'pending'` (UI responds instantly)
2. Ktor Client sends `POST /children/{id}/events` to Go API
3. Go API inserts into PostgreSQL via `ON CONFLICT (id) DO NOTHING`, triggers push notification
4. On success, local SQLDelight record is updated to `sync_status = 'synced'`
5. On failure (offline, timeout), record stays `pending` — on next reconnect the sync layer retries all `pending` rows ordered by `created_at`

### Read Path

1. App opens or user pulls to refresh
2. Ktor Client fetches recent events from Go API
3. KMP sync layer merges server response into SQLDelight
4. UI re-renders from SQLDelight

### Retry Strategy

On reconnect (network available event), the KMP sync layer immediately attempts all `pending` rows in `created_at` order. If the batch fails, rows stay `pending` and retry on the next reconnect. No timed polling, no exponential backoff in v1.

The UI shows a small "X events pending sync" indicator whenever `pending` rows exist.

> **Revisit before scaling:** Connectivity-triggered retry is fine for a small user base but could spike backend load if many devices reconnect simultaneously (e.g. after a Railway outage). Consider adding exponential backoff with jitter before launch or early in growth.

Background sync while the app is closed (WorkManager / BGTaskScheduler) is deferred to v2 — sync requires the app to be open or foregrounded.

### Conflict Handling

Events are append-only inserts — there are no insert conflicts. Two caregivers logging at the same time produces two rows. If they are clearly duplicates (same time window), the app surfaces a UI prompt for cleanup. Either caregiver can soft-delete one or leave both.

For edits: if two caregivers edit the same event's details while offline, last-write-wins on `updated_at` when both sync. Acceptable for supplementary fields like event type or notes.

## On-Device Schema Migrations (SQLDelight)

SQLDelight's built-in `.sqm` migration system is used from day one. Migration files live in:

```
/shared/src/commonMain/sqldelight/migrations/
  1.sqm   ← initial schema (created with the first .sq file, even if minimal)
  2.sqm   ← first schema change, and so on
```

The generated `Database` object applies all pending migrations automatically on open — no runtime migration code required. **Version `1.sqm` must exist from the moment the first schema is written.** Starting at 1 from day one gives every future change a clean numbered sequence with no gaps to reconstruct.

## Push Notifications

### Infrastructure

**FCM (Firebase Cloud Messaging)** is the unified delivery layer. One API call from the Go backend delivers to both Android (directly via FCM) and iOS (via FCM → APNs bridge). No need to call APNs directly.

### Firebase Project Setup

Two Firebase projects — one per environment (staging, production). Credentials are managed as follows:

| Credential | Location | Notes |
|---|---|---|
| Firebase service account JSON | Railway env var `FCM_SERVICE_ACCOUNT_JSON` | Full JSON as a string — never committed to the repo |
| APNs auth key (`.p8`) | Uploaded to Firebase console | Firebase handles the APNs bridge — not needed in Go backend |
| `google-services.json` | `/android/app/google-services.json` | Committed — contains project identifiers only, no secrets |
| `GoogleService-Info.plist` | `/ios/OneStepTwo/GoogleService-Info.plist` | Committed — same, no secrets |

The Go backend reads `FCM_SERVICE_ACCOUNT_JSON` at startup and initialises the Firebase Admin SDK. Staging and production use separate Firebase projects with separate env vars set in Railway per environment.

### When Notifications Fire

A notification is sent when any caregiver logs a new potty event. The Go API:
1. Inserts the event
2. Queries `notification_preferences` for all members of the child's org
3. Filters to members with `enabled = true` and excludes the user who just logged (no self-notify)
4. Queries `device_tokens` for those users
5. Calls FCM with those tokens

### Device Token Lifecycle

- Token is registered when a user signs in on a device
- Token is refreshed by the FCM SDK automatically; the app sends the new token to the Go API
- When FCM returns an "invalid registration token" error, the Go backend deletes that token row immediately
- A user can have tokens on multiple devices simultaneously

### Notification Preferences

Opt-in per family member, per child. Default is opt-in (notifications on) — families can turn them off individually in settings. Stored in the `notification_preferences` table (see `04-data-model.md`).

Future granularity (per event type, quiet hours) can be added as additional columns without restructuring.

### What the Notification Contains

Keep it minimal:

```
Title: [Child nickname]
Body:  "[Caregiver first name] logged a potty trip"
```

No event type detail in the notification body in v1 — tap the notification to open the app and see the full entry. This sidesteps any concern about sensitive details appearing on a lock screen.
