# Requirements

All requirements extracted from SPECs (precedence 1) and confirmed consistent with ADR decisions.
Source documents: docs/03-system-architecture.md, docs/04-data-model.md, docs/05-privacy.md,
docs/06-auth.md, docs/07-sync-and-notifications.md.

---

## Functional

- [REQ-001] The Go API must expose GET /healthz returning 200 OK with no body. It must not require authentication and must respond before any other endpoint is registered. Railway uses this for container readiness and liveness. (source: docs/03-system-architecture.md)

- [REQ-002] All API endpoints must be prefixed with /v1/. Breaking changes that cannot be backward-compatible must introduce a /v2/ prefix. Old app versions on the stores must continue working against /v1/ until adoption drops to negligible levels. (source: docs/03-system-architecture.md)

- [REQ-003] Events are append-only inserts. Two caregivers logging simultaneously produce two rows — no merge conflict. Events can be edited after creation (e.g. adding event_type detail to a quick-tap). (source: docs/04-data-model.md, docs/07-sync-and-notifications.md)

- [REQ-004] The mobile app must write events to local SQLDelight immediately (offline-first). The event record gets sync_status = 'pending'. The Ktor Client then attempts a POST to the Go API. On success the record is marked sync_status = 'synced'. On failure the record stays pending and retries on next reconnect. (source: docs/04-data-model.md, docs/07-sync-and-notifications.md)

- [REQ-005] On reconnect (network available), the KMP sync layer must immediately retry all pending rows in created_at order. No exponential backoff in v1. The UI must show a small indicator when pending rows exist (e.g. "X events pending sync"). (source: docs/07-sync-and-notifications.md)

- [REQ-006] Client-generated UUID: the mobile client generates the event UUID before any network request. The server uses INSERT ... ON CONFLICT (id) DO NOTHING, making sync retries safe no-ops. (source: docs/04-data-model.md)

- [REQ-007] Soft delete for caregiver-initiated event cleanup: set deleted_at and deleted_by. Soft-deleted rows are excluded from normal queries but remain in the database. Hard delete is reserved for erasure requests only. (source: docs/04-data-model.md)

- [REQ-008] The child profile must store only: id, clerk_org_id, nickname, birth_month, birth_year, created_at, updated_at. No legal name, no full DOB, no gender, no photo, no medical information. Adding any new field requires written justification in docs/04-data-model.md before the migration is written. (source: docs/04-data-model.md, docs/05-privacy.md)

- [REQ-009] Parental consent must be recorded before a child profile is created. The consent screen must include: (a) a self-attestation checkbox ("I confirm I am the parent or legal guardian of this child and am 18 years of age or older"), and (b) a plain-language explanation of what data is collected and why. (source: docs/05-privacy.md)

- [REQ-010] A consent_events row must be inserted before the children row. Fields required: clerk_user_id, child_id, consented_at, app_version. No IP address stored. (source: docs/04-data-model.md, docs/05-privacy.md)

- [REQ-011] Deletion cascade — child profile deleted: hard delete all potty_events, notification_preferences, and consent_events for that child_id, then hard delete the children row. (source: docs/04-data-model.md)

- [REQ-012] Deletion cascade — family account closed: trigger child-profile deletion for all children in the org, hard delete all device_tokens for all clerk_user_ids in the org, delete the Clerk Organization. Result: zero rows in the database reference that family. (source: docs/04-data-model.md)

- [REQ-013] The Go API must expose verified deletion endpoints for both child-profile deletion and full-account deletion. These are not optional features. (source: docs/04-data-model.md, docs/05-privacy.md)

- [REQ-014] Right to erasure: the delete flow must be clearly labelled and easy to find (not buried in settings). Deletion endpoints must return a confirmation of what was deleted. An erasure audit event must be logged (who requested it, when, what was removed) in an audit table that is itself purged after 90 days. (source: docs/05-privacy.md)

- [REQ-015] Every Clerk Organization maps 1:1 to a family unit. Every user who can view or log events for a child must be a member of that org. Access is enforced by checking claims.ActiveOrganizationID against the child's clerk_org_id in every Go handler. (source: docs/06-auth.md)

- [REQ-016] Two roles: admin (Clerk token value: org:admin) — full access including member management and child profile creation/deletion; caregiver (org:caregiver) — log events and view history only. Always use the org: prefix in role checks. (source: docs/06-auth.md)

- [REQ-017] Invitation flow: admin taps "Invite caregiver" → app calls Clerk invitation API with email + org:caregiver role → Clerk sends invitation email with magic link → caregiver accepts, installs app, and is automatically added to the org. No custom invitation backend logic. (source: docs/06-auth.md)

- [REQ-018] Multi-org users: after login, the app must check if the user belongs to multiple orgs. If so, show an org-picker screen before entering the main app. The selected org is activated in the Clerk session; all subsequent JWTs carry that org_id. Switching families requires re-activating a different org via the Clerk SDK. (source: docs/06-auth.md)

- [REQ-019] The Ktor Client in KMP must use the built-in Auth plugin with bearerTokens { } and nonCancellableRefresh = true. This prevents a cancelled caller from rolling back a completed token refresh (KTOR-7852). Concurrent 401s are safe via Ktor's AuthTokenHolder Mutex. (source: docs/06-auth.md)

- [REQ-020] Push notification fires when any caregiver logs a new potty event. The Go API must: query notification_preferences for all members of the child's org, filter to enabled=true, exclude the logging user, query device_tokens for remaining users, call FCM. (source: docs/07-sync-and-notifications.md)

- [REQ-021] FCM device token lifecycle: register on sign-in, app sends new token to Go API when FCM SDK refreshes it, Go API deletes device_token row immediately when FCM returns "invalid registration token" error. A user may have tokens on multiple devices simultaneously. (source: docs/07-sync-and-notifications.md, docs/04-data-model.md)

- [REQ-022] notification_preferences must have a UNIQUE(clerk_user_id, child_id) constraint. The Go API must use an upsert (ON CONFLICT (clerk_user_id, child_id) DO UPDATE) when writing preferences. (source: docs/04-data-model.md)

- [REQ-023] Notification default: opt-in (enabled=true). Families can turn notifications off per child in settings. (source: docs/07-sync-and-notifications.md)

- [REQ-024] Notification content in v1: Title = child nickname; Body = "[Caregiver first name] logged a potty trip". No event type detail in the notification body — tap to open app. (source: docs/07-sync-and-notifications.md)

- [REQ-025] SQLDelight on-device migrations: use built-in .sqm migration files from day one. Migration files live at /shared/src/commonMain/sqldelight/migrations/. Version 1.sqm must exist from the moment the first schema is written. No gaps in numbering. (source: docs/07-sync-and-notifications.md)

- [REQ-026] The Go API must validate CLERK_AUTHORIZED_PARTY (azp claim) on every request by setting AuthorizedPartyMatches in the Clerk middleware. This is not validated by default and must be set explicitly. (source: docs/06-auth.md)

- [REQ-027] Org context enforcement: the Go middleware does not reject tokens with no active org. Each handler must explicitly check claims.ActiveOrganizationID and return 403 if empty or mismatched with the target child's clerk_org_id. (source: docs/06-auth.md)

- [REQ-028] No real-time sync. Two caregivers viewing the same child's log simultaneously do not see each other's updates live. Data freshness is triggered by pull-to-refresh or app open. (source: docs/07-sync-and-notifications.md)

- [REQ-029] Data portability (GDPR Article 20): GET /v1/account/export endpoint returns a JSON bundle of all children, events, preferences, and consent records for the requesting user's org. **Deferred to v2 — must not be forgotten.** (source: docs/05-privacy.md)

- [REQ-030] Event type field: the potty_events table must include a nullable event_type column. Valid values: pee, poo, both, accident_pee, accident_poo, tried. NULL is valid and represents a "count only" quick-tap where the caregiver chose not to specify a type. event_type may be set at creation or updated after the fact via a PATCH request.

- [REQ-031] Child switcher: the home screen must display the active child's name and a visible switcher control when the family's Clerk org has more than one child profile. Selecting a different child updates the active child context across all tabs (Home, History, Progress). A family with one child shows no switcher.

- [REQ-032] Pending details banner: the home screen must display a persistent banner showing the count of potty events logged today that have event_type = NULL. The banner is hidden when no such events exist. Tapping the banner navigates to or opens the first incomplete event for editing.

- [REQ-033] History tab heatmap: the History tab must display a rolling heatmap calendar in a GitHub-contribution-graph style. Each day cell reflects the event count for that day using intensity levels (empty / low / medium / high). The rolling window focuses on the most recent days and weeks rather than a fixed-month calendar. Tapping a day cell opens a drill-down view listing that day's events with event_type and notes.

- [REQ-034] Progress tab: the Progress tab must display for the active child: (a) current streak — the number of consecutive calendar days with at least one logged event ending on the most recent active day; (b) best-ever streak — the longest streak achieved all time; (c) total events this week (Mon–Sun); (d) total events all time; (e) milestone badges for: first successful potty trip, first day with zero accident events, a 7-day streak, and a 30-day streak. All calculations are derived from the local SQLDelight database.

- [REQ-035] Four-tab navigation: the main app must use a bottom tab bar with exactly four tabs in order: Home, History, Progress, Settings. Tab icons, labels, and active/inactive visual states must be defined in the Phase 4 UI design spec. Navigation between tabs is always available from the main app; the tab bar is hidden during onboarding and full-screen detail flows.

- [REQ-036] Admin onboarding wizard: a first-time admin must complete a linear wizard before accessing the main app. Steps in order: (1) Clerk sign-up (email/password or OAuth); (2) enter a family display name to create the Clerk org; (3) enter first child's nickname and birth month/year; (4) display plain-language data explanation and self-attestation consent checkbox — user must check before proceeding; (5) a consent_events row is inserted, then a children row is created — the wizard must enforce this order with no bypass path; (6) admin lands on the Home tab with the child's name visible. A returning caregiver who accepts a Clerk invitation skips the wizard and lands on Home.

---

## Non-Functional

- [REQ-NF-001] API errors must use RFC 7807 Problem Details format (application/problem+json) with fields: type (stable URI), title (log-safe description), status (mirrored HTTP code), detail (developer explanation, never shown to end users). (source: docs/03-system-architecture.md)

- [REQ-NF-002] The mobile app must be fully functional without a network connection. SQLDelight is the on-device source of truth. Events written offline are queued and synced when connectivity is restored. (source: docs/03-system-architecture.md, docs/07-sync-and-notifications.md)

- [REQ-NF-003] No automatic data expiry. Data is retained until the user explicitly deletes it or closes their account. User-initiated graduation/offboarding is the retention mechanism. Revisit if regulators require a defined maximum retention window. (source: docs/05-privacy.md)

- [REQ-NF-004] Rate limiting: not implemented in v1. Must be revisited before scaling. A per-user rate limit on event writes (e.g. 60/minute) protects against sync retry storms. Can be added as Go middleware without changing the API contract. (source: docs/03-system-architecture.md)

- [REQ-NF-005] Background sync (WorkManager / BGTaskScheduler): deferred to v2. Sync requires the app to be open or foregrounded in v1. (source: docs/07-sync-and-notifications.md)

- [REQ-NF-006] The Go API must handle concurrent 401 refresh safely. The Ktor Auth plugin's AuthTokenHolder has an internal Mutex; Clerk's Android SDK deduplicates via a Deferred cache. No custom mutex needed. (source: docs/06-auth.md)

- [REQ-NF-007] JWK caching: the Clerk HTTP middleware caches JWKs in memory (keyed by kid, 1-hour TTL, sync.RWMutex-protected). If jwt.Verify is called directly outside the middleware (e.g. in a background worker), JWKs must be fetched and cached manually. (source: docs/06-auth.md)

- [REQ-NF-008] Retry strategy: connectivity-triggered retry is acceptable for v1 but could spike backend load if many devices reconnect simultaneously after an outage. Add exponential backoff with jitter before launch or early in growth. (source: docs/07-sync-and-notifications.md)

- [REQ-NF-009] Conflict handling for edits: if two caregivers edit the same event's details while offline, last-write-wins on updated_at when both sync. Acceptable for supplementary fields (event_type, notes). (source: docs/07-sync-and-notifications.md)

- [REQ-NF-010] The Go API must use Clerk SDK v2 (github.com/clerk/clerk-sdk-go/v2, v2.7.0+). The deprecated v1 SDK (github.com/clerkinc/clerk-sdk-go, EOL April 2025) must not be used. (source: docs/06-auth.md)

---

## Compliance

- [REQ-C-001] COPPA (US): parental consent required before collecting any data about a child. Self-attestation checkbox is the minimum acceptable standard. Verify with legal counsel whether Verified Parental Consent (VPC) is required for target markets before launch. (source: docs/05-privacy.md)

- [REQ-C-002] GDPR (EU/UK): right to erasure, data minimisation, right to data portability (Article 20, v2), and lawful basis for processing. (source: docs/05-privacy.md)

- [REQ-C-003] PIPEDA / Law 25 (Canada/Quebec): consent and erasure obligations. (source: docs/05-privacy.md)

- [REQ-C-004] POPIA (South Africa): consent and erasure framework, applies if South African users are accepted. (source: docs/05-privacy.md)

- [REQ-C-005] Parent/caregiver PII — name, email, phone — must live in Clerk only. Our database stores only clerk_user_id and clerk_org_id as foreign keys. No passwords, sessions, or PII in our database. (source: docs/06-auth.md, docs/05-privacy.md)

- [REQ-C-006] Pre-launch deliverables: privacy policy (drafted with a lawyer), terms of service, in-app consent screen (plain language, not legalese), clear offboarding/deletion flow in app UI, docs/06-data-map.md (one row per table listing data stored, legal basis, deletion path). (source: docs/05-privacy.md)

- [REQ-C-007] EU data residency: Railway supports US and EU regions. When EU users onboard at scale, evaluate moving or replicating Go API and PostgreSQL to an EU Railway region. Not a day-one requirement. (source: docs/05-privacy.md)

- [REQ-C-008] Erasure audit log: each deletion event must be logged (who requested, when, what was removed). The audit table must itself be purged after 90 days. (source: docs/05-privacy.md)

- [REQ-C-009] No IP address stored in consent_events. Storing an IP would itself require justification under data minimisation. (source: docs/04-data-model.md)

---

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| REQ-001 | Phase 1 | Complete |
| REQ-002 | Phase 1 | Complete |
| REQ-003 | Phase 5 | Pending |
| REQ-004 | Phase 6 | Pending |
| REQ-005 | Phase 6 | Pending |
| REQ-006 | Phase 5 | Pending |
| REQ-007 | Phase 5 | Pending |
| REQ-008 | Phase 2 | Complete |
| REQ-009 | Phase 2 | Complete |
| REQ-010 | Phase 2 | Complete |
| REQ-011 | Phase 2 | Complete |
| REQ-012 | Phase 2 | Complete |
| REQ-013 | Phase 2 | Complete |
| REQ-014 | Phase 2 | Complete |
| REQ-015 | Phase 3 | Pending |
| REQ-016 | Phase 3 | Pending |
| REQ-017 | Phase 3 | Pending |
| REQ-018 | Phase 3 | Pending |
| REQ-019 | Phase 3 | Pending |
| REQ-020 | Phase 8 | Pending |
| REQ-021 | Phase 8 | Pending |
| REQ-022 | Phase 8 | Pending |
| REQ-023 | Phase 8 | Pending |
| REQ-024 | Phase 8 | Pending |
| REQ-025 | Phase 1 | Complete |
| REQ-026 | Phase 3 | Pending |
| REQ-027 | Phase 3 | Pending |
| REQ-028 | Phase 6 | Pending |
| REQ-029 | v2 | Deferred |
| REQ-030 | Phase 5 | Pending |
| REQ-031 | Phase 5 | Pending |
| REQ-032 | Phase 5 | Pending |
| REQ-033 | Phase 5 | Pending |
| REQ-034 | Phase 7 | Pending |
| REQ-035 | Phase 4 | Pending |
| REQ-036 | Phase 5 | Pending |
| REQ-NF-001 | Phase 1 | Complete |
| REQ-NF-002 | Phase 6 | Pending |
| REQ-NF-003 | Phase 2 | Complete |
| REQ-NF-004 | Phase 9 | Pending |
| REQ-NF-005 | Phase 6 | Pending |
| REQ-NF-006 | Phase 3 | Pending |
| REQ-NF-007 | Phase 3 | Pending |
| REQ-NF-008 | Phase 6 | Pending |
| REQ-NF-009 | Phase 6 | Pending |
| REQ-NF-010 | Phase 3 | Pending |
| REQ-C-001 | Phase 2 | Complete |
| REQ-C-002 | Phase 2 | Complete |
| REQ-C-003 | Phase 2 | Complete |
| REQ-C-004 | Phase 2 | Complete |
| REQ-C-005 | Phase 2 | Complete |
| REQ-C-006 | Phase 9 | Pending |
| REQ-C-007 | Phase 9 | Pending |
| REQ-C-008 | Phase 2 | Complete |
| REQ-C-009 | Phase 2 | Complete |

**Coverage:** 54 active v1 requirements mapped across 9 phases. REQ-029 (data portability export) explicitly deferred to v2 per requirements spec.
