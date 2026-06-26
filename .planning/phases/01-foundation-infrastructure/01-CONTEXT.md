# Phase 1: Foundation & Infrastructure - Context

**Gathered:** 2026-06-25
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 1 delivers the deployable technical skeleton end-to-end: Gradle monorepo scaffolded with `:shared` (KMP) and `:androidApp`, Go backend serving `/healthz` on Railway, PostgreSQL provisioned with R2 backup configured, Clerk and Firebase projects provisioned for both environments, CI pipeline passing (Go + Android on Linux), SQLDelight initialized with `1.sqm`, and goose initialized with the first empty migration. No application logic is built in this phase — the goal is a clean, deployable skeleton that every subsequent phase builds on.

</domain>

<decisions>
## Implementation Decisions

### CI/CD Pipeline
- **D-01:** iOS CI is **skipped for Phase 1**. iOS build verification is done locally until the iOS codebase has meaningful code (expected Phase 3 or later). GitHub Actions runs on Linux runners only.
- **D-02:** GitHub Actions CI pipeline covers: **Go build + test** + **Android (KMP) build** on a single Linux runner job. Migration apply (`1.sqm`) is included as part of the Go test step.
- **D-03:** `generated.go` (oapi-codegen output) is **committed to the repo**. CI verifies it compiles — no codegen step runs in CI. Regenerate locally via `make generate`.

### Railway Deployment
- **D-04:** Branch-based Railway auto-deploy:
  - `main` branch → **staging** Railway service auto-deploys
  - `production` branch → **production** Railway service auto-deploys
- **D-05:** Production releases happen via **manual PR from `main` → `production`**. No automated promotion — merging the PR is the release action. Railway detects the push to `production` and deploys.

### OpenAPI Spec
- **D-06:** `/api/openapi.yaml` starts with **only the `/healthz` endpoint**. Each subsequent phase adds its own endpoint definitions as they are implemented. The spec is always honest — it reflects what is actually deployed.

### Gradle Build
- **D-07:** Use **`gradle/libs.versions.toml` version catalog** for centralized dependency version management across all Gradle modules.
- **D-08:** Gradle module structure: **`:shared`** (KMP — `commonMain`, `androidMain`, `iosMain` source sets) + **`:androidApp`** (Jetpack Compose app). The iOS app (`iosApp/`) is an Xcode project that consumes the `:shared` KMP XCFramework — it is not a Gradle module.

### Claude's Discretion
- Exact Gradle wrapper version to use (use latest stable at time of implementation)
- Go HTTP router choice (stdlib `net/http` vs `chi` vs `gorilla/mux` — keep it minimal for a healthcheck-only backend)
- Specific Railway `railway.json` / `Dockerfile` vs Nixpacks auto-detection
- goose migration directory location within the backend (`db/migrations/` is conventional)
- GitHub Actions job naming and structure details

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope & Requirements
- `.planning/ROADMAP.md` §Phase 1 — Phase goal, success criteria (5 items), and requirement list (REQ-001, REQ-002, REQ-025, REQ-NF-001)
- `.planning/REQUIREMENTS.md` — REQ-001 (`/healthz` spec), REQ-002 (`/v1/` prefix), REQ-025 (SQLDelight `.sqm` migrations from day one with no gaps), REQ-NF-001 (RFC 7807 error format)

### Stack & Architecture Decisions (all LOCKED)
- `.planning/PROJECT.md` — Complete locked decisions: Go module path (`github.com/BrandonMHogan/onesteptwo/backend`), Railway always-on, two PostgreSQL instances (staging + production), goose migration tooling, oapi-codegen workflow, two Firebase projects, Clerk SDK selections, Kotlin 2.0.21 pinned, Java toolchain 21, minSdk 29, SKIE interop

### API Contract
- `docs/03-system-architecture.md` — RFC 7807 Problem Details error format, `/v1/` prefix convention, Go binary deployment model
- `/api/openapi.yaml` — OpenAPI spec (to be created in this phase; starts with `/healthz` only)

### Mobile & KMP
- `.planning/PROJECT.md` §Stack — KMP `:shared` module constraints: Kotlin 2.0.21 pinned, SKIE only (no KMP-NativeCoroutines), concrete typed classes at KMP/Swift boundary, SQLDelight `.sqm` migrations

### Compliance Context (carry-forward awareness)
- `.planning/REQUIREMENTS.md` §Constraints — Hard regulatory gates relevant to schema setup (consent_events ordering, no IP in consent_events) — Phase 1 does not implement these but the schema skeleton must not accidentally foreclose them

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- None — all module directories (`androidApp/`, `backend/`, `docs/`, `frontend/`, `iosApp/`, `shared/`) exist with only `.gitkeep` placeholder files. Phase 1 writes all build configuration, Go source, and CI config from scratch.

### Established Patterns
- The directory structure is already laid out to match the intended monorepo layout — the planner should preserve this structure and not rename or reorganize top-level directories.
- `iosApp/` holds the Xcode project. It is not a Gradle module. The KMP `:shared` module produces an XCFramework that Xcode consumes directly.

### Integration Points
- The Go backend and the Android/KMP build are independent in Phase 1 (no API calls yet). CI verifies both compile and pass their respective test suites, but they do not call each other.
- The `1.sqm` SQLDelight migration and the first goose migration file should both be empty schemas (no tables yet) — real schema creation happens in Phase 2.

</code_context>

<specifics>
## Specific Ideas

- The home screen non-scrollable constraint from Phase 4 is not relevant to Phase 1 — that context is for later phases.
- The `production` git branch does not exist yet — Phase 1 plan should include creating it from `main` as part of the Railway wiring step.
- Railway staging watches `main`; this means every merged PR to main triggers a staging deploy. The team should be aware that `main` is always "live to staging."

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 1-Foundation & Infrastructure*
*Context gathered: 2026-06-25*
