# Decisions

All decisions below are LOCKED, sourced from the confirmed ADR (docs/02-stack.md, precedence 0)
and confirmed consistent with SPECs (precedence 1). No SPEC contradicts any ADR decision.

---

## Mobile UI — Android

Source: docs/02-stack.md (type: ADR)
Status: LOCKED
Decision: Jetpack Compose (native Android)
Rationale: Full platform capability with no cross-platform UI compromise.

---

## Mobile UI — iOS

Source: docs/02-stack.md (type: ADR)
Status: LOCKED
Decision: SwiftUI (native iOS)
Rationale: Native performance and platform feel; mirrors the Android approach.

---

## Shared Business Logic

Source: docs/02-stack.md (type: ADR)
Status: LOCKED
Decision: Kotlin Multiplatform (KMP), module named :shared
Rationale: Single implementation of sync, data models, and networking shared across Android and iOS.

---

## On-Device Local Storage

Source: docs/02-stack.md (type: ADR)
Status: LOCKED
Decision: SQLDelight
Rationale: Type-safe SQL, KMP-compatible; generates Kotlin models from schema; uses built-in .sqm migration files for on-device schema versioning.

---

## HTTP Networking Client

Source: docs/02-stack.md (type: ADR)
Status: LOCKED
Decision: Ktor Client (in commonMain of :shared)
Rationale: KMP-native HTTP client; works in commonMain without platform-specific code.

---

## Swift Interop

Source: docs/02-stack.md (type: ADR)
Status: LOCKED
Decision: SKIE (Touchlab). Do NOT mix with KMP-NativeCoroutines.
Rationale: Generates idiomatic Swift API from Kotlin — async/await, sealed class enums, default params. Actively maintained; tracks Kotlin releases within days.

---

## Backend Language

Source: docs/02-stack.md (type: ADR)
Status: LOCKED
Decision: Go (single static binary)
Rationale: Simple deployment, fast compile, low memory footprint on Railway, easy contributor onboarding. Note: docs/01-architecture-evaluation.md originally recommended Ktor Server; ADR overrides — see INGEST-CONFLICTS.md INFO-01.

---

## API Contract

Source: docs/02-stack.md (type: ADR), confirmed by docs/03-system-architecture.md (SPEC)
Status: LOCKED
Decision: OpenAPI spec at /api/openapi.yaml is the source of truth for all request/response shapes.
Rationale: Single authoritative contract; generated Go stubs (oapi-codegen) and enables client generation.

---

## Backend Code Generation

Source: docs/02-stack.md (type: ADR)
Status: LOCKED
Decision: oapi-codegen generates /backend/internal/api/generated.go from the OpenAPI spec. Run via `make generate`. Generated file is committed.
Rationale: Eliminates hand-written handler signatures; keeps Go types in sync with the spec.

---

## Backend Deployment Platform

Source: docs/02-stack.md (type: ADR), confirmed by docs/03-system-architecture.md (SPEC)
Status: LOCKED
Decision: Railway (always-on, private network to Postgres). Go API is production-only on Railway; staging runs locally via `go run ./cmd/api`.
Rationale: Simple container hosting, private networking, one dashboard.

---

## Database

Source: docs/02-stack.md (type: ADR), confirmed by docs/03-system-architecture.md (SPEC)
Status: LOCKED
Decision: PostgreSQL hosted on Railway (two separate instances — one for staging, one for production).
Rationale: Private network to Go backend (~1ms latency), zero extra service, standard Postgres.

---

## Database Migration Tooling

Source: docs/02-stack.md (type: ADR)
Status: LOCKED
Decision: goose. Plain SQL migrations with `-- +goose Up` / `-- +goose Down` markers. Run via `go run cmd/migrate/main.go`. No external binary.
Rationale: Lightweight, SQL-native, no external dependency.

---

## Database Backups

Source: docs/02-stack.md (type: ADR)
Status: LOCKED
Decision: Daily pg_dump → Cloudflare R2 (via Railway cron job). Must be configured on day one — Railway Postgres has no built-in PITR.
Rationale: S3-compatible, zero egress fees. Railway's managed Postgres lacks point-in-time recovery.

---

## Auth Provider

Source: docs/02-stack.md (type: ADR), confirmed by docs/06-auth.md (SPEC)
Status: LOCKED
Decision: Clerk. Headless native SDKs on Android (clerk-android-api) and iOS (ClerkKit). Organizations used for family grouping. 100 free orgs.
Rationale: Headless JWT management, built-in invitation flow, 100 free orgs (Kinde cap of 5 is a dealbreaker for this architecture).

---

## Auth — Android SDK

Source: docs/02-stack.md (type: ADR), confirmed by docs/06-auth.md (SPEC)
Status: LOCKED
Decision: clerk-android-api (no UI artifact). Headless JWT management; integrates with KMP Ktor networking layer.
Rationale: No browser redirect required; clean integration with KMP shared module.

---

## Auth — iOS SDK

Source: docs/02-stack.md (type: ADR), confirmed by docs/06-auth.md (SPEC)
Status: LOCKED
Decision: ClerkKit (no UI artifact). Same headless pattern as Android.
Rationale: No browser redirect required.

---

## Auth — Web

Source: docs/02-stack.md (type: ADR), confirmed by docs/06-auth.md (SPEC)
Status: LOCKED
Decision: clerk-sveltekit community adapter (markjaquith). Handles SSR session, injects into SvelteKit locals. Note: marketing site v1 has no auth; this applies when web dashboard is added in v2.
Rationale: Functional in production; handles locals injection and supports headless bundle import.

---

## Auth — Go SDK

Source: docs/06-auth.md (type: SPEC)
Status: LOCKED
Decision: Use github.com/clerk/clerk-sdk-go/v2 (v2.7.0+). Do NOT use deprecated github.com/clerkinc/clerk-sdk-go (v1, EOL April 2025) or roll custom JWKS validation.
Rationale: Official maintained SDK; v1 is EOL.

---

## Web Frontend Framework

Source: docs/02-stack.md (type: ADR)
Status: LOCKED
Decision: SvelteKit. V1 scope: marketing landing page only. Web dashboard deferred to v2.
Rationale: Lightweight, fast, good SSR support.

---

## Web Frontend Deployment

Source: docs/02-stack.md (type: ADR)
Status: LOCKED
Decision: Cloudflare Pages (static SvelteKit output).
Rationale: Free, fast global CDN, zero config for SvelteKit static output.

---

## Push Notifications Infrastructure

Source: docs/02-stack.md (type: ADR), confirmed by docs/07-sync-and-notifications.md (SPEC)
Status: LOCKED
Decision: FCM (Firebase Cloud Messaging) as unified delivery layer. One API call from Go delivers to both Android and iOS (via FCM → APNs bridge). No direct APNs calls.
Rationale: Avoids maintaining separate APNs integration; single unified API.

---

## File / Media Storage

Source: docs/02-stack.md (type: ADR)
Status: LOCKED
Decision: No file storage in v1. Photos deferred — privacy and security implications for child media require dedicated discussion before v2.
Rationale: Risk surface for child media is high; must not be rushed.

---

## Sync Model

Source: docs/07-sync-and-notifications.md (type: SPEC), consistent with docs/03-system-architecture.md (SPEC)
Status: LOCKED
Decision: Offline-first. No real-time sync. SQLDelight on-device is source of truth. Pull-to-refresh or app-open triggers server fetch. Append-only event inserts eliminate insert conflicts. Last-write-wins on updated_at for edits.
Rationale: A potty tracker does not need sub-second freshness; avoids WebSocket/SSE complexity.

---

## API Error Format

Source: docs/03-system-architecture.md (type: SPEC)
Status: LOCKED
Decision: RFC 7807 Problem Details (application/problem+json) for all Go API errors. Fields: type (URI), title, status, detail. A reusable ProblemDetails schema is defined in /api/openapi.yaml.
Rationale: Machine-readable stable error identifiers; standard format.

---

## API Versioning

Source: docs/03-system-architecture.md (type: SPEC)
Status: LOCKED
Decision: All endpoints prefixed /v1/. Breaking changes introduce /v2/. Old versions on app stores continue against /v1/ until adoption drops.
Rationale: Allows parallel support of old and new app versions during staged rollouts.

---

## Family Model

Source: docs/06-auth.md (type: SPEC)
Status: LOCKED
Decision: Clerk Organization maps 1:1 to a family unit. Two roles: admin (org:admin) and caregiver (org:caregiver). Always use org: prefix — not bare role strings.
Rationale: Clerk Organizations provide built-in membership, invitation, and role management without custom backend logic.

---

## Event ID Strategy

Source: docs/04-data-model.md (type: SPEC)
Status: LOCKED
Decision: Client-generated UUIDs. Mobile generates the UUID on local write. Server uses INSERT ... ON CONFLICT (id) DO NOTHING. Retries from offline sync are safe no-ops.
Rationale: Enables offline-first write without a server round-trip for ID assignment; retry-safe.

---

## Event Deletion Strategy

Source: docs/04-data-model.md (type: SPEC), confirmed by docs/05-privacy.md (SPEC)
Status: LOCKED
Decision: Soft delete (deleted_at) for caregiver-initiated cleanup. Hard delete (real DELETE) for GDPR/COPPA erasure requests. No exceptions.
Rationale: Soft delete preserves history for corrections; hard delete satisfies legal erasure obligations.

---

## Child Profile Data Minimisation

Source: docs/04-data-model.md (type: SPEC), confirmed by docs/05-privacy.md (SPEC)
Status: LOCKED
Decision: Child profile stores only: id, clerk_org_id, nickname, birth_month, birth_year. No legal name, no full DOB, no gender, no photo, no medical information. Any new field requires written justification in 04-data-model.md before migration.
Rationale: COPPA/GDPR data minimisation; reduces risk surface for child PII.
