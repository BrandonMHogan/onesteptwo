# Roadmap: OneStepTwo

## Overview

Seven phases deliver a fully store-published, privacy-compliant, offline-first potty tracking app. Phase 1 establishes the technical skeleton end-to-end. Phase 2 locks in all compliance architecture before any child data can flow. Phase 3 completes authentication and the family model. Phases 4 through 6 build core event logging, offline sync, and push notifications — the three pillars of the v1 value proposition. Phase 7 prepares and submits both apps to their respective stores with all legal deliverables in place.

## Phases

- [ ] **Phase 1: Foundation & Infrastructure** - Monorepo scaffold, CI/CD, Railway deploy, PostgreSQL + R2 backup, Clerk + Firebase provisioning, Go healthcheck, SQLDelight and goose migrations initialized
- [ ] **Phase 2: Compliance & Privacy Architecture** - Consent gate, erasure cascade, audit log, data minimisation constraints, and all COPPA/GDPR/PIPEDA/POPIA obligations enforced in schema and API before any child data flows
- [ ] **Phase 3: Authentication & Family Model** - Sign up/in on Android + iOS, Clerk org enforcement, role-gated access, invitation flow, multi-org picker, JWT validation with azp and org context checks
- [ ] **Phase 4: Core Event Logging** - Child profile creation with consent gate, one-tap potty event logging on both platforms, event editing, soft delete, client-generated UUIDs
- [ ] **Phase 5: Offline-First Sync** - Pending queue in SQLDelight, connectivity-triggered retry, pending count indicator, pull-to-refresh, last-write-wins conflict resolution
- [ ] **Phase 6: Push Notifications** - FCM integration on Android + iOS, device token lifecycle, per-child notification preferences, Go dispatch logic
- [ ] **Phase 7: Launch Preparation & Store Submission** - SvelteKit marketing site live, privacy policy + ToS finalized, data map produced, both apps submitted to Play Store and App Store

## Phase Details

### Phase 1: Foundation & Infrastructure
**Goal**: The project skeleton is deployable end-to-end — Railway serves a live healthcheck, PostgreSQL is provisioned with backup configured, Clerk and Firebase projects exist for both environments, monorepo builds clean on CI, and migrations are initialized at version 1 on day one.
**Depends on**: Nothing (first phase)
**Requirements**: REQ-001, REQ-002, REQ-025, REQ-NF-001
**Success Criteria** (what must be TRUE):
  1. GET /healthz returns 200 OK on both staging and production Railway deployments without authentication
  2. PostgreSQL instances exist on Railway for staging and production; daily pg_dump to Cloudflare R2 is configured and a test dump succeeds
  3. Clerk dev and production organizations are provisioned; headless SDK keys are in place for Android and iOS
  4. Staging and production Firebase projects exist; two-project FCM configuration is confirmed
  5. CI pipeline runs on every commit — Go build + test passes, Android and iOS builds succeed, and 1.sqm migration applies cleanly
**Plans**: TBD

### Phase 2: Compliance & Privacy Architecture
**Goal**: All legal obligations are enforced in the database schema and Go API before any child profile can be created — consent gate, erasure cascade, audit log, and data minimisation constraints are provably correct.
**Depends on**: Phase 1
**Requirements**: REQ-008, REQ-009, REQ-010, REQ-011, REQ-012, REQ-013, REQ-014, REQ-NF-003, REQ-C-001, REQ-C-002, REQ-C-003, REQ-C-004, REQ-C-005, REQ-C-008, REQ-C-009
**Success Criteria** (what must be TRUE):
  1. The backend rejects any attempt to create a children row when no consent_events row exists for that user — enforced at both the DB constraint level and the Go handler
  2. A GDPR/COPPA erasure request to the deletion endpoint cascades hard DELETEs across potty_events, notification_preferences, consent_events, and children for the target child_id, then returns a structured confirmation payload
  3. The children table migration contains only id, clerk_org_id, nickname, birth_month, birth_year, created_at, updated_at — no additional columns
  4. The consent_events table has no IP address column; the Go handler does not write one
  5. An erasure audit row is inserted on every deletion; a scheduled process (or DB rule) purges audit rows older than 90 days
**Plans**: TBD

### Phase 3: Authentication & Family Model
**Goal**: Users can sign up, sign in, create a family organization, and have their JWT validated with org context enforced on every API request; caregivers can join via invitation and role-gated access is verified.
**Depends on**: Phase 2
**Requirements**: REQ-015, REQ-016, REQ-017, REQ-018, REQ-019, REQ-026, REQ-027, REQ-NF-006, REQ-NF-007, REQ-NF-010
**Success Criteria** (what must be TRUE):
  1. A new user can sign up and sign in on both Android and iOS using Clerk headless SDK; the resulting JWT is injected into every Ktor request via the bearerTokens plugin with nonCancellableRefresh = true
  2. The Go middleware validates CLERK_AUTHORIZED_PARTY (azp) on every request; a request bearing a valid Clerk token from a different frontend is rejected
  3. Every Go handler returns 403 when claims.ActiveOrganizationID is absent or does not match the target child's clerk_org_id
  4. A user who belongs to multiple Clerk orgs sees an org-picker screen after login and can activate a different org to switch families
  5. An admin invites a caregiver by email; the caregiver receives the Clerk email, accepts, logs in, and can log events but cannot create or delete child profiles
**Plans**: TBD
**UI hint**: yes

### Phase 4: Core Event Logging
**Goal**: A parent can create a child profile (gated behind the consent screen), and any caregiver can log, edit, and soft-delete potty events on both Android and iOS with immediate local feedback.
**Depends on**: Phase 3
**Requirements**: REQ-003, REQ-006, REQ-007
**Success Criteria** (what must be TRUE):
  1. An admin completes the consent screen (self-attestation checkbox + plain-language data explanation); a consent_events row is inserted; then the children row is created — enforced in that order with no path to skip the consent step
  2. A caregiver taps once to log a potty event; the event appears immediately in local event history before any network round-trip
  3. A logged event can be edited to add or change event_type detail after the initial quick-tap
  4. A caregiver soft-deletes an event; the deleted event no longer appears in normal history; the row remains in the database with deleted_at set
  5. Submitting the same event UUID twice to the server (e.g. after a sync retry) is a safe no-op — the second INSERT is silently discarded via ON CONFLICT DO NOTHING
**Plans**: TBD
**UI hint**: yes

### Phase 5: Offline-First Sync
**Goal**: Events logged without connectivity are queued locally and automatically synced to the server when the network is restored; the UI clearly communicates pending state and pull-to-refresh merges server data.
**Depends on**: Phase 4
**Requirements**: REQ-004, REQ-005, REQ-028, REQ-NF-002, REQ-NF-005, REQ-NF-008, REQ-NF-009
**Success Criteria** (what must be TRUE):
  1. With no network connection, a caregiver logs events and sees them immediately in local history; each event is stored with sync_status = 'pending' in SQLDelight
  2. When connectivity is restored (without a manual action), all pending events are retried in created_at order and marked 'synced' on server acknowledgment
  3. The app displays a visible indicator (e.g. "3 events pending sync") whenever pending events exist; the indicator clears when all events are synced
  4. Two caregivers who edit the same event's details offline and then sync both do not cause a crash or silent data loss — last-write-wins on updated_at is applied
  5. Pull-to-refresh fetches the latest events from the server and merges them into SQLDelight without duplicating locally-created records
**Plans**: TBD
**UI hint**: yes

### Phase 6: Push Notifications
**Goal**: Every opted-in family member receives an FCM push notification on both Android and iOS when any caregiver logs a new potty event; device tokens are managed correctly through their full lifecycle.
**Depends on**: Phase 5
**Requirements**: REQ-020, REQ-021, REQ-022, REQ-023, REQ-024
**Success Criteria** (what must be TRUE):
  1. When a caregiver logs an event, all other family members with notifications enabled receive an FCM push notification on their registered Android and iOS devices within a few seconds
  2. The notification title is the child's nickname; the body is "[Caregiver first name] logged a potty trip" — no event type detail
  3. A device token is registered with the Go API at sign-in; when FCM refreshes the token, the app sends the updated token to the API; when FCM returns an invalid token error, the Go API immediately deletes that device_token row
  4. A user can toggle per-child notification preferences in app settings; toggling off stops push notifications for that child on all the user's devices
  5. The notification_preferences table enforces UNIQUE(clerk_user_id, child_id); writing preferences uses an upsert and never creates duplicate rows
**Plans**: TBD
**UI hint**: yes

### Phase 7: Launch Preparation & Store Submission
**Goal**: Both Android and iOS apps are submitted to their respective app stores with all compliance deliverables complete, the SvelteKit marketing site is live, and the v1 success metric is achieved.
**Depends on**: Phase 6
**Requirements**: REQ-NF-004, REQ-C-006, REQ-C-007
**Success Criteria** (what must be TRUE):
  1. The SvelteKit marketing site is live at the production URL on Cloudflare Pages
  2. A privacy policy and terms of service (reviewed with legal counsel) are publicly linked from both the app and the marketing site before store submission
  3. docs/06-data-map.md exists and contains one row per database table listing: data stored, legal basis, and deletion path
  4. The in-app deletion/offboarding flow is reachable without deep navigation and produces a human-readable confirmation of what was deleted
  5. The Android app is submitted to Google Play Store and the iOS app is submitted to Apple App Store; both pass review including child-safety and privacy requirements; both are live and downloadable
**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation & Infrastructure | 0/TBD | Not started | - |
| 2. Compliance & Privacy Architecture | 0/TBD | Not started | - |
| 3. Authentication & Family Model | 0/TBD | Not started | - |
| 4. Core Event Logging | 0/TBD | Not started | - |
| 5. Offline-First Sync | 0/TBD | Not started | - |
| 6. Push Notifications | 0/TBD | Not started | - |
| 7. Launch Preparation & Store Submission | 0/TBD | Not started | - |
