# Data Model Decisions

> Last updated: 2026-06-26
> Full SQL migrations live in `/backend/db/migrations/` — this doc captures the *decisions*, not the schema syntax.

## Core Principles

- **Append-only event inserts** — potty events are never overwritten on creation. Two caregivers logging simultaneously produces two rows, not a conflict.
- **Client-generated UUIDs** — the mobile client generates the `id` UUID when the event is first written to SQLDelight (before any network request). The same UUID is sent in the POST body. The Go API uses `INSERT ... ON CONFLICT (id) DO NOTHING`, making retries from offline sync a safe no-op.
- **Sync status on events** — `potty_events` carries a local-only `sync_status` column (`pending` | `synced`). On every reconnect, the KMP sync layer uploads all `pending` rows (ordered by `created_at`) and marks them `synced` on success. Failures leave the row `pending` — it retries on the next reconnect. No dead-letter state.
- **Editable rows** — events can be updated after creation (e.g., adding pee/poo detail to a quick-tap log). Last-write-wins on `updated_at` is acceptable for supplementary fields.
- **Soft deletes for event cleanup** — caregivers can delete a duplicate or incorrect event. The row stays in the database with a `deleted_at` timestamp; it is excluded from normal queries. History is never truly destroyed at the event level.
- **Hard deletes for erasure requests** — when a family deletes a child profile or closes their account, all associated rows are permanently removed. No soft delete. See `05-privacy.md`.

## Child Profile

The absolute minimum required to make the app useful. Nothing else.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `clerk_org_id` | string | Links to the Clerk Organization (family) |
| `nickname` | string | First name or whatever the family calls the child. Not a legal name. |
| `birth_month` | integer | 1–12. Not a full date of birth. |
| `birth_year` | integer | Four-digit year. |
| `consent_event_id` | UUID | Required compliance FK — references the `consent_events` row captured at profile creation. A children row physically cannot exist without a pre-existing consent record (D-05 consent gate). This is not a PII field. |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

No gender, no photo, no last name, no legal name, no full date of birth, no medical information. If a future feature requires an additional field, it must be documented in this file with its justification before the migration is written.

**Note on `consent_event_id`:** REQ-008 originally listed 7 columns for `children`. D-05 (Phase 2 locked decision) adds `consent_event_id` as a required NOT NULL foreign key — it is the database-level enforcement of the consent gate. The prohibition on "additional columns" applies to PII columns (gender, photo, legal name), not to required compliance FKs. The migration `00002_schema.sql` defines 8 columns including `consent_event_id`.

All parent and caregiver PII (name, email, phone) lives exclusively in Clerk. The database stores only `clerk_user_id` and `clerk_org_id` as opaque identifiers (REQ-C-005).

## Potty Events

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key — client-generated (no server-side DEFAULT) |
| `child_id` | UUID | FK → children |
| `logged_by` | string | Clerk user ID of the caregiver who logged it |
| `occurred_at` | timestamp | When the trip happened (may differ from `created_at` if logged retroactively) |
| `created_at` | timestamp | When the row was inserted |
| `updated_at` | timestamp | Last edit |
| `updated_by` | string | Clerk user ID of last editor (nullable) |
| `event_type` | enum (nullable) | `pee`, `poo`, `both`, `accident`, `tried` — PostgreSQL native ENUM type. Column is nullable; `NULL` means quick-tap (no type selected yet). Adding new values later requires `ALTER TYPE ... ADD VALUE`. |
| `notes` | text | Optional free-text, nullable |
| `deleted_at` | timestamp | Soft delete. Null = visible. Non-null = hidden from normal queries. |
| `deleted_by` | string | Clerk user ID who deleted it (nullable) |

**`event_type` canonical values (D-01, D-02):** `pee, poo, both, accident, tried`. An uneventful day is implicit from the absence of log entries, so no extra value is needed. Accidents are tracked as a single category. `tried` represents an attempt with no output.

## Device Tokens (Push Notifications)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `clerk_user_id` | string | Owning user |
| `token` | string | FCM registration token |
| `platform` | enum | `android`, `ios` |
| `created_at` | timestamp | |
| `last_seen_at` | timestamp | Updated on each successful delivery |

Tokens are deleted immediately when FCM returns an "invalid token" error. A user can have multiple tokens (multiple devices).

## Notification Preferences

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `clerk_user_id` | string | |
| `child_id` | UUID | FK → children — preferences are per child, per user |
| `enabled` | boolean | Master on/off |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

A `UNIQUE(clerk_user_id, child_id)` constraint is required — duplicate rows would cause a user to receive duplicate notifications. The Go API uses an upsert (`ON CONFLICT (clerk_user_id, child_id) DO UPDATE`) when writing preferences.

Simple for v1. Additional granularity (per event type, quiet hours, etc.) can be added later.

## Consent Events

Legal requirement under COPPA and GDPR — must be recorded before a child profile is created.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `clerk_user_id` | string | The parent/guardian who gave consent |
| `consented_at` | timestamp | When consent was recorded |
| `app_version` | string | App version at consent time — proves which consent copy the user saw |
| `consent_text_version` | string | Version identifier of the consent copy text (e.g. `'v1'`) — stored so future audits can retrieve exactly what the parent agreed to |

**FK direction (D-05):** `consent_events` has **no `child_id` column**. Instead, `children.consent_event_id` is a `UUID NOT NULL REFERENCES consent_events(id)`. This means the database physically cannot insert a children row without a pre-existing consent_events row — the consent gate is enforced at the schema level, not just at the application layer.

**No IP address stored** — `consent_events` contains no `ip`, `ip_address`, or `remote_addr` column. Storing an IP address would itself require justification under data minimisation and is prohibited (REQ-C-009).

**No parent/caregiver PII** — name, email, phone, and other PII live exclusively in Clerk. This table stores only `clerk_user_id` as an opaque identifier (REQ-C-005).

The `consent_text_version` field (e.g., `'v1'`, `'v1.1'`) identifies which version of the consent copy the parent saw. The full consent text lives in the app's strings file, versioned in git.

## Deletion Cascade (Account / Child Removal)

When a child profile is deleted, the FK-safe order is (D-08 corrected for D-05 FK direction):

1. Save `children.consent_event_id` before any deletion
2. Hard delete all `potty_events` for that `child_id`
3. Hard delete all `notification_preferences` for that `child_id`
4. Hard delete the `children` row (releases the FK reference to consent_events)
5. Hard delete the `consent_events` row (now safe — no children row references it)

**Important:** Step 5 must occur after step 4. Because `children.consent_event_id REFERENCES consent_events(id)`, PostgreSQL will reject deletion of a `consent_events` row that is still referenced by a `children` row. The Go handler captures `consent_event_id` before any deletion and deletes `children` first to release the reference.

When a family account is closed:
1. Trigger child profile deletion (above) for all children in the org
2. Hard delete all `device_tokens` for all `clerk_user_id`s in the org
3. Delete the Clerk Organization (Clerk's responsibility for PII)
4. Result: zero rows in the database reference that family

The Go API must expose verified deletion endpoints for both of these flows. They are not optional features. See `05-privacy.md`.
