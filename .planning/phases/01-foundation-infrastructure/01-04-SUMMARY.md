---
phase: 01-foundation-infrastructure
plan: "04"
subsystem: infra
tags: [railway, railpack, postgresql, clerk, firebase, google-services, healthcheck, custom-domain]

dependency_graph:
  requires:
    - phase: 01-01
      provides: "Go binary with /healthz endpoint and backend/railway.json deploy config"
    - phase: 01-02
      provides: "androidApp/src/debug/google-services.json placeholder; src/release/ gitignore"
    - phase: 01-03
      provides: "origin/production branch for Railway production service to watch"
  provides:
    - "Railway staging service (onesteptwo-staging) auto-deploying from main branch"
    - "Railway production service (onesteptwo-production) auto-deploying from production branch"
    - "Staging PostgreSQL instance (postgres-staging) on private network with DATABASE_URL injected"
    - "Production PostgreSQL instance (postgres-production) on private network with DATABASE_URL injected"
    - "Live /healthz returning HTTP 200: staging https://onesteptwo-staging.up.railway.app, production https://api.onesteptwo.com"
    - "Clerk staging application (pk_test_* / sk_test_*) with Organizations enabled"
    - "Clerk production application (pk_live_* / sk_live_*) with Organizations enabled"
    - "Firebase staging project (onesteptwo-staging, project_number 162166871959)"
    - "Firebase production project (separate project) with google-services.json as GitHub Secret"
    - "Real staging androidApp/src/debug/google-services.json committed (replaces Plan 01-02 placeholder)"
  affects:
    - phase-03 (Auth: Clerk SDK keys become buildConfigField values; Clerk org model lands here)
    - phase-08 (Push: Firebase FCM integration uses the two-project setup established here)
    - all-phases (every push to main auto-deploys to staging; production releases via PR to production branch)

tech-stack:
  added:
    - "Railway Railpack builder (replaces planned NIXPACKS — see Deviations)"
    - "Clerk Organizations (staging + production apps, each with Organizations enabled)"
    - "Firebase Android SDK config (real google-services.json for staging; production as GitHub Secret)"
  patterns:
    - "Railpack buildCommand: go build -o /app/server ./cmd/server; startCommand: /app/server"
    - "Railway private network DATABASE_URL injection — value never written to any file"
    - "Two-environment Firebase separation: debug → staging project, release → production project (gitignored)"
    - "Custom domain on production (api.onesteptwo.com via Namecheap CNAME → Railway)"

key-files:
  created: []
  modified:
    - "backend/railway.json — switched builder from NIXPACKS to Railpack buildCommand/startCommand"
    - "androidApp/src/debug/google-services.json — replaced placeholder with real staging Firebase config"
    - ".planning/ROADMAP.md — added R2 backup cron to Post-v1 Backlog (deferred from Task 2)"

key-decisions:
  - "Railpack replaces NIXPACKS: Railway migrated to Railpack as default builder; explicit buildCommand/startCommand required for Go monorepo with backend/ root"
  - "Custom production domain api.onesteptwo.com via Namecheap CNAME → Railway (not the generated up.railway.app URL)"
  - "R2 backup cron deferred to post-v1 backlog: requires Cloudflare payment method; PostgreSQL instances exist and are healthy; backup is operational risk, not a v1 launch blocker"

patterns-established:
  - "Branch-based Railway deploy: main → staging auto-deploy; production → production auto-deploy (D-04/D-05)"
  - "Secrets in Railway env only: DATABASE_URL, Clerk sk_*, FCM service account never committed"
  - "Firebase two-project isolation: src/debug/ committed staging config; src/release/ gitignored production config"

requirements-completed: [REQ-001]

duration: "~4h (human provisioning)"
completed: "2026-06-26"
---

# Phase 01 Plan 04: External Service Provisioning Summary

**Two Railway services with PostgreSQL live at /healthz (staging + production), Clerk organizations and Firebase projects provisioned for both environments, real staging google-services.json committed — R2 backup cron deferred to post-v1.**

## Performance

- **Duration:** ~4h (human provisioning across three checkpoint tasks)
- **Started:** 2026-06-26T14:30:00Z (approx — checkpoint presented to user)
- **Completed:** 2026-06-26T18:12:29Z
- **Tasks:** 3 (all checkpoint:human-action)
- **Files modified:** 2 code files + 1 planning file

## Accomplishments

- Railway staging service (`onesteptwo-staging`) deployed from `main` branch, serving `GET /healthz → HTTP 200` at `https://onesteptwo-staging.up.railway.app`
- Railway production service (`onesteptwo-production`) deployed from `production` branch, serving `GET /healthz → HTTP 200` at `https://api.onesteptwo.com` (custom domain via Namecheap CNAME → Railway)
- Two dedicated PostgreSQL instances (staging + production) provisioned on Railway's private network; `DATABASE_URL` injected automatically, never written to any file
- Clerk staging application (`pk_test_*` / `sk_test_*`) and production application (`pk_live_*` / `sk_live_*`) created with Organizations enabled (Clerk Organization = family per REQ-015)
- Firebase staging project (`onesteptwo-staging`, project_number `162166871959`) and production project provisioned; real staging `google-services.json` committed to `androidApp/src/debug/`; production `google-services.json` stored as GitHub Secret (gitignored at `androidApp/src/release/`)
- R2 daily backup cron intentionally deferred to post-v1 backlog (requires Cloudflare payment method; PostgreSQL is healthy; backup is operational risk management, not a v1 launch blocker)

## Task Commits

Tasks were user dashboard actions; code commits captured the resulting configuration changes:

1. **Task 1: Railway services + PostgreSQL** — dashboard provisioning (no repo commit) + `2c4ea64` (fix: railway.json Railpack)
2. **Task 2: Cloudflare R2 backup cron** — deferred; `a8f0e69` (docs: ROADMAP.md post-v1 backlog note)
3. **Task 3: Clerk + Firebase + google-services.json** — dashboard provisioning + `259abf9` (feat: real staging google-services.json)

**Plan metadata:** (this SUMMARY commit)

## Files Created/Modified

- `backend/railway.json` — Changed builder from `NIXPACKS` to Railpack `buildCommand`/`startCommand` pattern; healthcheck path and restart policy unchanged
- `androidApp/src/debug/google-services.json` — Replaced placeholder with real Firebase staging config (`project_id: onesteptwo-staging`, `project_number: 162166871959`, real API key)
- `androidApp/src/release/google-services.json` — Production Firebase config written by user (gitignored, not committed); confirmed present locally and confirmed gitignored by `.gitignore`

## Decisions Made

- **Railpack over NIXPACKS:** Railway migrated its default builder to Railpack. The original `{"builder": "NIXPACKS"}` in `backend/railway.json` caused deploy failures. Fixed by switching to explicit `buildCommand: "go build -o /app/server ./cmd/server"` + `startCommand: "/app/server"`. This is the Railpack pattern for Go monorepos with a non-root service directory.
- **Custom production domain:** `api.onesteptwo.com` configured via Namecheap CNAME pointing to the Railway-generated production URL. Production service uses the custom domain; staging uses the Railway-generated `up.railway.app` URL.
- **R2 backup deferred:** The Cloudflare R2 backup cron (Task 2) was deferred to the post-v1 backlog. The PostgreSQL instances are healthy and data is safe; backup is an operational concern, not a v1 launch blocker. This means Phase 1 Success Criterion 2 ("daily pg_dump → R2 configured") is **partially unmet** — PostgreSQL ✅, R2 backup ❌ (deferred).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] railway.json: NIXPACKS builder replaced with Railpack buildCommand/startCommand**
- **Found during:** Task 1 (Railway service provisioning)
- **Issue:** Railway migrated from NIXPACKS to Railpack as default builder. The `{"builder": "NIXPACKS"}` configuration in `backend/railway.json` caused the deployment to fail — Railway no longer auto-detects Go from the NIXPACKS builder in the same way.
- **Fix:** Updated `backend/railway.json` to use Railpack's explicit `buildCommand: "go build -o /app/server ./cmd/server"` and `startCommand: "/app/server"`. Healthcheck path, timeout, and restart policy were preserved.
- **Files modified:** `backend/railway.json`
- **Verification:** Both staging and production services deployed successfully; `curl .../healthz` returns HTTP 200.
- **Committed in:** `2c4ea64` (fix(01-04): switch railway.json from Nixpacks to Railpack build/start commands)

### User-decided Deferral

**Task 2: Cloudflare R2 backup cron → Post-v1 Backlog**
- **Reason:** Cloudflare R2 requires a payment method on file. This is an operational risk management item, not a v1 correctness requirement.
- **Impact on Phase 1 success criteria:** Phase 1 Success Criterion 2 is partially unmet: PostgreSQL instances exist ✅ but daily pg_dump → R2 is not configured ❌.
- **Mitigation:** Deferred item added to ROADMAP.md Post-v1 Backlog. Set up before any significant user data accumulates post-launch.
- **Committed in:** `a8f0e69` (docs: defer R2 backup cron to post-v1 backlog)

---

**Total deviations:** 1 auto-fixed (Railpack migration) + 1 user-decided deferral (R2 backup)
**Impact on plan:** Railpack fix essential for deployment. R2 deferral accepted as operational risk; PostgreSQL is live and healthy.

## Issues Encountered

None beyond the Railpack migration (documented above as a deviation). All three external service provisioning workflows completed successfully.

## Verification Results

| Check | Result |
|-------|--------|
| `curl -s -o /dev/null -w "%{http_code}" https://onesteptwo-staging.up.railway.app/healthz` | 200 |
| `curl -s -o /dev/null -w "%{http_code}" https://api.onesteptwo.com/healthz` | 200 |
| `grep -q 'com.onesteptwo.android' androidApp/src/debug/google-services.json` | PASS |
| `google-services.json project_id` is `onesteptwo-staging` (real project, not placeholder) | PASS |
| `google-services.json project_number` is `162166871959` (real project number) | PASS |
| `git check-ignore androidApp/src/release/google-services.json` | PASS (gitignored) |
| `androidApp/src/release/google-services.json` exists locally | PASS |
| Staging service watches `main` branch | PASS |
| Production service watches `production` branch | PASS |
| DATABASE_URL injected via Railway private network (not in any file) | PASS |
| Clerk staging + production apps with Organizations enabled | PASS |
| No `sk_test_*` / `sk_live_*` in git repo | PASS |

## Known Stubs

None — all committed files are real (non-placeholder) configurations. The R2 backup cron is intentionally deferred (documented above), not a stub.

## Threat Flags

All T-04-* threats from the plan's threat model:

| Flag | File/Location | Status |
|------|---------------|--------|
| T-04-01 mitigated | Railway env / GitHub Secrets | DATABASE_URL, Clerk sk_*, FCM service account, production google-services.json — all in Railway env or GitHub Secrets only; none committed |
| T-04-02 mitigated | .gitignore | `androidApp/src/release/google-services.json` gitignored; confirmed by `git check-ignore` |
| T-04-03 mitigated | Railway private network | PostgreSQL reachable only via Railway private network; no public DB port |
| T-04-04 mitigated | Two separate environments | Separate Railway services, Postgres instances, Clerk apps, Firebase projects; D-04 ties each to its own branch |
| T-04-05 accepted | /healthz | Returns 200 with empty body — no internal info leaked |

## Next Phase Readiness

Phase 2 (Compliance & Privacy Architecture) can proceed:
- Railway staging auto-deploys from `main` — every commit to main is immediately live on staging
- PostgreSQL staging instance ready to receive goose migrations (Phase 2 adds compliance schema)
- Clerk organizational model is in place; SDK keys are available for Phase 3 integration
- Firebase two-project setup is in place; FCM integration deferred to Phase 8

**Before Phase 1 is fully complete:** R2 backup cron should be set up post-v1 (deferred item). Phase 2 can begin regardless.

## Self-Check

Files modified (confirmed on disk):
- backend/railway.json: FOUND (Railpack buildCommand confirmed)
- androidApp/src/debug/google-services.json: FOUND (real project_number 162166871959 confirmed)
- androidApp/src/release/google-services.json: FOUND locally, GITIGNORED (confirmed)

Commits:
- 2c4ea64: FOUND (fix railway.json Railpack)
- a8f0e69: FOUND (docs ROADMAP.md R2 deferral)
- 259abf9: FOUND (feat real staging google-services.json)

Live endpoints:
- staging /healthz: HTTP 200 (verified)
- production /healthz: HTTP 200 (verified)

## Self-Check: PASSED

---
*Phase: 01-foundation-infrastructure*
*Completed: 2026-06-26*
