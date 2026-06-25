# Synthesis

Entry point for gsd-roadmapper and downstream consumers.
Generated: 2026-06-25
Mode: new bootstrap

---

## Project Overview

OneStepTwo is an offline-first mobile app (Android + iOS) for potty training tracking. Families
create a shared child profile; multiple caregivers log potty events from any device. All events
are written locally to SQLDelight and synced to a Go backend on Railway when connectivity is
available. Push notifications via FCM alert family members when any caregiver logs a new event.
The architecture is privacy-first by design: the only child data stored is nickname plus birth
month and year. Parent and caregiver PII lives exclusively in Clerk, not in the app's database.

---

## Locked Decisions (26)

- Android UI: Jetpack Compose (source: docs/02-stack.md)
- iOS UI: SwiftUI (source: docs/02-stack.md)
- Shared business logic: Kotlin Multiplatform, module :shared (source: docs/02-stack.md)
- On-device storage: SQLDelight with .sqm migrations (source: docs/02-stack.md)
- HTTP client: Ktor Client in commonMain (source: docs/02-stack.md)
- Swift interop: SKIE (Touchlab) — no KMP-NativeCoroutines (source: docs/02-stack.md)
- Backend language: Go (single binary) (source: docs/02-stack.md)
- API contract: OpenAPI spec at /api/openapi.yaml (source: docs/02-stack.md)
- Backend codegen: oapi-codegen, committed generated.go (source: docs/02-stack.md)
- Backend deployment: Railway always-on (source: docs/02-stack.md)
- Database: PostgreSQL on Railway (two instances: staging, production) (source: docs/02-stack.md)
- Migrations: goose, staging-first workflow (source: docs/02-stack.md)
- Backups: daily pg_dump → Cloudflare R2 (source: docs/02-stack.md)
- Auth provider: Clerk (source: docs/02-stack.md)
- Auth Android SDK: clerk-android-api (headless) (source: docs/02-stack.md)
- Auth iOS SDK: ClerkKit (headless) (source: docs/02-stack.md)
- Auth Go SDK: clerk-sdk-go v2 only (source: docs/06-auth.md)
- Web frontend: SvelteKit on Cloudflare Pages (source: docs/02-stack.md)
- Web v1 scope: marketing landing page only (source: docs/02-stack.md)
- Push notifications: FCM unified layer (source: docs/02-stack.md)
- File storage: none in v1 (source: docs/02-stack.md)
- Sync model: offline-first, no real-time, pull-to-refresh (source: docs/07-sync-and-notifications.md)
- API error format: RFC 7807 Problem Details (source: docs/03-system-architecture.md)
- API versioning: /v1/ prefix, /v2/ for breaking changes (source: docs/03-system-architecture.md)
- Family model: Clerk Org = family; roles org:admin and org:caregiver (source: docs/06-auth.md)
- Child data minimisation: nickname + birth_month + birth_year only (source: docs/04-data-model.md)

Full decision entries with rationale: .planning/intel/decisions.md

---

## Requirements Summary (38 total)

Functional: 29 requirements (REQ-001 through REQ-029)
  - API surface: healthcheck, versioning, event CRUD, deletion cascade, invitation flow
  - Auth: org enforcement, role checks, multi-org picker, JWT refresh
  - Sync: offline write, pending queue, retry on reconnect, conflict handling
  - Push: notification trigger logic, token lifecycle, FCM delivery

Non-Functional: 9 requirements (REQ-NF-001 through REQ-NF-009)
  - RFC 7807 error format, offline capability, no rate limiting v1, no background sync v1
  - Retry strategy scalability note, edit conflict strategy, SDK version pin

Compliance: 9 requirements (REQ-C-001 through REQ-C-009)
  - COPPA, GDPR, PIPEDA, POPIA obligations
  - Consent gate, erasure cascade, audit log, no IP in consent, pre-launch deliverables
  - EU data residency evaluation trigger

Full requirements: .planning/intel/requirements.md

---

## Constraints Summary

Regulatory hard constraints:
  - Consent event must exist before children row is created (COPPA + GDPR)
  - Erasure must produce real DELETE statements — no orphaned rows
  - No IP addresses stored in consent_events

Technical hard constraints:
  - Android minSdk 29, iOS target 17.0, Java toolchain 21, Kotlin 2.0.21 (pinned)
  - SKIE only — no mixing with KMP-NativeCoroutines or Swift Export
  - Do not expose generics at KMP/Swift boundary; use concrete typed classes
  - Clerk SDK v2 (clerk-sdk-go v2) — v1 is EOL April 2025
  - CLERK_AUTHORIZED_PARTY must be set — azp not validated by default
  - FCM service account JSON never committed; Railway env var only
  - SQLDelight .sqm migrations start at 1.sqm on day one, no gaps

Operational hard constraints:
  - Railway always-on (no sleep); JVM memory flags required in env
  - Production env vars in Railway dashboard only — never in repo
  - Postgres backup (pg_dump → R2) must be configured on day one
  - Two separate Firebase projects per environment (staging, production)

Full constraints: .planning/intel/constraints.md

---

## Cross-Reference Map

docs/01-architecture-evaluation.md → docs/02-stack.md
  (historical evaluation; all recommendations superseded by ADR)

docs/02-stack.md → /api/openapi.yaml
  (ADR delegates API shape authority to OpenAPI spec)

docs/03-system-architecture.md → api/openapi.yaml, docs/06-auth.md, docs/07-sync-and-notifications.md
  (system architecture delegates auth detail to 06, sync detail to 07)

docs/04-data-model.md → docs/05-privacy.md, /backend/migrations/
  (schema cross-references privacy spec for erasure rules; migrations are the implementation)

docs/05-privacy.md → docs/04-data-model.md, docs/06-data-map.md
  (privacy spec cross-references schema for consent_events; data map is a pre-launch deliverable)

docs/06-auth.md → (no in-repo cross-references)

docs/07-sync-and-notifications.md → docs/04-data-model.md
  (sync spec references schema for sync_status, notification_preferences, device_tokens)

External references not in ingest set:
  /api/openapi.yaml — referenced by 02 and 03; not classified
  /backend/migrations/ — referenced by 04; not classified
  docs/06-data-map.md — referenced by 05; pre-launch deliverable, does not yet exist

---

## Conflict Summary

Blockers: 0
Competing variants (warnings): 0
Auto-resolved / informational: 4

All 4 INFO entries relate to the ADR overriding the historical DOC (backend language, Kotlin
version, :core module structure) and a benign bidirectional cross-reference cycle between
docs/04-data-model.md and docs/05-privacy.md.

Full report: .planning/INGEST-CONFLICTS.md

---

## Intel Files

.planning/intel/decisions.md    — 26 locked decisions with source and rationale
.planning/intel/requirements.md — 38 requirements (29 functional, 9 NFR, 9 compliance)
.planning/intel/constraints.md  — regulatory, technical, and operational constraints
.planning/intel/context.md      — project background, goals, non-goals, user types

STATUS: READY — no blockers, no competing variants. Safe to route to gsd-roadmapper.
