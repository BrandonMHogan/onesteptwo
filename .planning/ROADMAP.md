# Roadmap: OneStepTwo

## Overview

Nine phases deliver a fully store-published, privacy-compliant, offline-first potty tracking app. Phase 1 establishes the technical skeleton end-to-end. Phase 2 locks in all compliance architecture before any child data can flow. Phase 3 completes authentication and the family model. Phase 4 produces the complete UI/UX specification before any production UI code is written. Phases 5 through 7 build core event logging, offline sync, and progress/milestones — the three pillars of the user experience. Phase 8 adds push notifications. Phase 9 prepares and submits both apps to their respective stores with all legal deliverables in place.

## Phases

- [x] **Phase 1: Foundation & Infrastructure** - Monorepo scaffold, CI/CD, Railway deploy, PostgreSQL + R2 backup, Clerk + Firebase provisioning, Go healthcheck, SQLDelight and goose migrations initialized
- [ ] **Phase 2: Compliance & Privacy Architecture** - Consent gate, erasure cascade, audit log, data minimisation constraints, and all COPPA/GDPR/PIPEDA/POPIA obligations enforced in schema and API before any child data flows
- [ ] **Phase 3: Authentication & Family Model** - Sign up/in on Android + iOS, Clerk org enforcement, role-gated access, invitation flow, multi-org picker, JWT validation with azp and org context checks
- [ ] **Phase 4: UI/UX Design** - Screen flows, lo-fi wireframes, design tokens (color, type, spacing), component specs, and navigation/animation patterns for all screens on both platforms — spec complete before implementation begins
- [ ] **Phase 5: Core Event Logging** - Onboarding wizard (family setup + consent + child profile), 4-tab navigation, one-tap logging with details-later flow, event types, pending-details banner, heatmap history tab, child switcher, Settings screens
- [ ] **Phase 6: Offline-First Sync** - Pending queue in SQLDelight, connectivity-triggered retry, pending count indicator, pull-to-refresh, last-write-wins conflict resolution
- [ ] **Phase 7: Progress & Milestones** - Progress tab with streak (current + best), total event counts, and milestone badges; heatmap history reflects synced data
- [ ] **Phase 8: Push Notifications** - FCM integration on Android + iOS, device token lifecycle, per-child notification preferences, Go dispatch logic
- [ ] **Phase 9: Launch Preparation & Store Submission** - SvelteKit marketing site live, privacy policy + ToS finalized, data map produced, both apps submitted to Play Store and App Store

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
  5. CI pipeline runs on every commit — Go build + test passes, Android and iOS builds succeed, and 1.sqm migration applies cleanly**Plans**: 4 plans

**Wave 1**

  - [x] 01-01-PLAN.md — Go backend skeleton: /healthz, RFC 7807 helper, server + migrate entrypoints, first goose migration, railway.json (REQ-001, REQ-002, REQ-NF-001)
  - [x] 01-02-PLAN.md — Gradle/KMP monorepo + toolchain: JDK 21 + Android SDK + Gradle 8.10.2, version catalog, :shared + :androidApp, SQLDelight 1.sqm (REQ-025)

**Wave 2** *(blocked on Wave 1 completion)*

  - [x] 01-03-PLAN.md — GitHub Actions CI (Go + Android, Linux-only) and production branch creation (REQ-001, REQ-002, REQ-025, REQ-NF-001)

**Wave 3** *(blocked on Wave 2 completion)*

  - [x] 01-04-PLAN.md — External provisioning: Railway services + Postgres, Clerk apps, Firebase projects, /healthz live (REQ-001) — R2 backup cron deferred to post-v1

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

### Phase 4: UI/UX Design

**Goal**: The complete UI/UX specification for OneStepTwo is documented before any production UI code is written — covering all screen flows, lo-fi wireframes, design tokens, component specs, and navigation/animation patterns for both Jetpack Compose and SwiftUI. Engineers implement from this spec; design decisions do not happen during Phase 5+.
**Depends on**: Phase 3
**Requirements**: REQ-035
**Success Criteria** (what must be TRUE):

  1. A screen flow diagram covers every user path: admin onboarding wizard, caregiver first login, main app (4-tab navigation), child switcher, one-tap log → "add details later" toast, history heatmap drill-down, progress tab, settings screens (admin vs caregiver role differences), invite caregiver flow, empty states, error states, and offline state indicators
  2. Lo-fi wireframes or annotated mockups exist for every distinct screen and state — with sufficient layout and content detail that an engineer can implement without making design guesses
  3. Design tokens are documented for both platforms: color palette (primary, secondary, background, surface, error, success — with dark mode variants), typography scale (Compose TextStyle and SwiftUI Font equivalents), spacing grid, corner radii, and elevation/shadow
  4. Component specs cover: bottom tab bar (4 tabs, active/inactive states), bottom-anchored log button (placement, size, tap area, haptic feedback spec), event card (complete variant and pending-details variant), heatmap calendar cell (empty/low/medium/high intensity), milestone badge (locked and unlocked states), toast/snackbar, "add details" bottom sheet, pending details banner, child switcher, empty state, error state, and loading state
  5. Navigation and animation patterns are documented: tab switch transitions, push/pop navigation for drill-down screens, log button tap feedback (haptic + visual confirmation), toast appear/dismiss timing, bottom sheet open/close, and any platform-specific motion guidance for Compose vs SwiftUI

**Plans**: TBD

### Phase 5: Core Event Logging

**Goal**: A parent completes the onboarding wizard (family creation + consent + first child profile), and any caregiver can immediately log potty events with one tap, optionally add details later, view the complete event history in a heatmap calendar, manage family and children settings, and switch between multiple children — all within the 4-tab navigation structure.
**Depends on**: Phase 4
**Requirements**: REQ-003, REQ-006, REQ-007, REQ-030, REQ-031, REQ-032, REQ-033, REQ-035, REQ-036
**Success Criteria** (what must be TRUE):

  1. A new admin completes the full onboarding wizard: sign up via Clerk → enter family name → add first child (nickname + birth month/year) → read plain-language data explanation and check self-attestation consent box → a consent_events row is inserted before the children row → admin lands on the Home tab with the child's name displayed
  2. A caregiver taps the log button and the event is registered immediately in SQLDelight without requiring event_type; a long-lived toast appears offering "Add details now or later"
  3. A logged event can have event_type (pee / poo / both / accident_pee / accident_poo / tried) and a text note appended after the initial tap; the event retains its original created_at timestamp regardless of when details are added
  4. The pending details banner on the home screen shows the count of today's events without event_type set; the banner is hidden when all events have event_type set or no events have been logged today
  5. The History tab shows a rolling heatmap calendar (GitHub-style); cell intensity reflects the event count for that day; tapping a cell opens a day-detail view listing that day's events with type and notes
  6. A family with multiple children shows a child switcher on the home screen; switching the active child updates the Home, History, and Progress tabs to show that child's data
  7. The Settings tab (tab 4) shows: family members list + invite caregiver by email (admin only), child management — add/edit/remove (admin only), notification preferences toggle per child, account info, sign out, and a clearly labelled "Delete my data" erasure request

**Plans**: TBD
**UI hint**: yes

### Phase 6: Offline-First Sync

**Goal**: Events logged without connectivity are queued locally and automatically synced to the server when the network is restored; the UI clearly communicates pending state and pull-to-refresh merges server data.
**Depends on**: Phase 5
**Requirements**: REQ-004, REQ-005, REQ-028, REQ-NF-002, REQ-NF-005, REQ-NF-008, REQ-NF-009
**Success Criteria** (what must be TRUE):

  1. With no network connection, a caregiver logs events and sees them immediately in local history; each event is stored with sync_status = 'pending' in SQLDelight
  2. When connectivity is restored (without a manual action), all pending events are retried in created_at order and marked 'synced' on server acknowledgment
  3. The app displays a visible indicator (e.g. "3 events pending sync") whenever pending events exist; the indicator clears when all events are synced
  4. Two caregivers who edit the same event's details offline and then sync both do not cause a crash or silent data loss — last-write-wins on updated_at is applied
  5. Pull-to-refresh fetches the latest events from the server and merges them into SQLDelight without duplicating locally-created records

**Plans**: TBD
**UI hint**: yes

### Phase 7: Progress & Milestones

**Goal**: The Progress tab is live, showing the active child's streak, total event counts, and earned milestone badges — all computed from the local SQLDelight database after sync.
**Depends on**: Phase 6
**Requirements**: REQ-034
**Success Criteria** (what must be TRUE):

  1. The Progress tab displays for the active child: current streak (consecutive days with at least one logged event), best-ever streak (all time), total events this week, and total events all time
  2. Milestone badges are awarded and visually displayed when earned: first successful potty trip (pee, poo, or both), first full day with zero accidents, 7-day streak, and 30-day streak
  3. All progress calculations are computed from the local SQLDelight database and reflect post-sync data from Phase 6; switching the active child via the child switcher updates the Progress tab
  4. The Progress tab shows an appropriate empty state (no streak, no milestones) when the child has no logged events

**Plans**: TBD
**UI hint**: yes

### Phase 8: Push Notifications

**Goal**: Every opted-in family member receives an FCM push notification on both Android and iOS when any caregiver logs a new potty event; device tokens are managed correctly through their full lifecycle.
**Depends on**: Phase 7
**Requirements**: REQ-020, REQ-021, REQ-022, REQ-023, REQ-024
**Success Criteria** (what must be TRUE):

  1. When a caregiver logs an event, all other family members with notifications enabled receive an FCM push notification on their registered Android and iOS devices within a few seconds
  2. The notification title is the child's nickname; the body is "[Caregiver first name] logged a potty trip" — no event type detail
  3. A device token is registered with the Go API at sign-in; when FCM refreshes the token, the app sends the updated token to the API; when FCM returns an invalid token error, the Go API immediately deletes that device_token row
  4. A user can toggle per-child notification preferences in app settings; toggling off stops push notifications for that child on all the user's devices
  5. The notification_preferences table enforces UNIQUE(clerk_user_id, child_id); writing preferences uses an upsert and never creates duplicate rows

**Plans**: TBD
**UI hint**: yes

### Phase 9: Launch Preparation & Store Submission

**Goal**: Both Android and iOS apps are submitted to their respective app stores with all compliance deliverables complete, the SvelteKit marketing site is live, and the v1 success metric is achieved.
**Depends on**: Phase 8
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
| 1. Foundation & Infrastructure | 4/4 | Complete    | 2026-06-26 |
| 2. Compliance & Privacy Architecture | 0/TBD | Not started | - |
| 3. Authentication & Family Model | 0/TBD | Not started | - |
| 4. UI/UX Design | 0/TBD | Not started | - |
| 5. Core Event Logging | 0/TBD | Not started | - |
| 6. Offline-First Sync | 0/TBD | Not started | - |
| 7. Progress & Milestones | 0/TBD | Not started | - |
| 8. Push Notifications | 0/TBD | Not started | - |
| 9. Launch Preparation & Store Submission | 0/TBD | Not started | - |

## Post-v1 Backlog

- **Cloudflare R2 backup cron** — Daily pg_dump from Railway PostgreSQL to R2. Deferred from Phase 1 (requires payment method; not needed for v1 launch). Set up after v1 ships.
