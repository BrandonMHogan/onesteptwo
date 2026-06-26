---
phase: 01-foundation-infrastructure
plan: "03"
subsystem: infra
tags: [github-actions, ci, gradle, go, android, goose, production-branch]

dependency_graph:
  requires:
    - phase: 01-01
      provides: "Go module with backend/go.mod and backend/go.sum for setup-go"
    - phase: 01-02
      provides: "Gradle wrapper, androidApp module, debug google-services.json for assembleDebug"
  provides:
    - ".github/workflows/ci.yml — single Linux CI job validating Go and Android tiers"
    - "origin/production branch mirroring main for Railway branch-based deploy"
  affects:
    - phase-01-04 (Firebase provisioning — CI will run assembleDebug once google-services.json is real)
    - all-future-phases (every push/PR validates Go + Android automatically)

tech-stack:
  added:
    - "GitHub Actions (ubuntu-latest runner)"
    - "actions/checkout@v4"
    - "actions/setup-go@v5 (go-version-file: backend/go.mod)"
    - "actions/setup-java@v4 (java-version: 21, distribution: temurin)"
    - "gradle/actions/setup-gradle@v3"
  patterns:
    - "CI validates both tiers in a single Linux job — no macOS runner (D-01)"
    - "generated.go compiled by CI, never regenerated in CI (D-03)"
    - "GRADLE_OPTS with -Dorg.gradle.jvmargs=-Xmx4096M -Dorg.gradle.daemon=false -Dorg.gradle.parallel=true"
    - "local.properties written at CI runtime via ANDROID_SDK_ROOT (ubuntu-latest ships Android SDK)"
    - "production branch = Railway production service watchpoint (D-04/D-05)"

key-files:
  created:
    - ".github/workflows/ci.yml"
  modified: []

key-decisions:
  - "CI uses a single ubuntu-latest job for both Go and Android tiers (D-01 — iOS CI skipped, no macOS runner)"
  - "goose validate runs via go run at pinned version v3.27.1, matching go.mod, no separate install step"
  - "production branch created from main tip (62632cb) — starts as exact mirror per D-05"

patterns-established:
  - "CI workflow: all third-party actions use official namespaces (actions/*, gradle/actions/*) pinned to major versions"
  - "Android CI: sdk.dir set at runtime via ANDROID_SDK_ROOT, not committed to local.properties"

requirements-completed: [REQ-001, REQ-002, REQ-025, REQ-NF-001]

duration: 2min
completed: 2026-06-26
---

# Phase 01 Plan 03: CI Pipeline and Production Branch Summary

**GitHub Actions single-job Linux CI pipeline (Go build + test + goose validate + assembleDebug) and origin/production branch for Railway branch-based deploys.**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-06-26T13:50:02Z
- **Completed:** 2026-06-26T13:51:17Z
- **Tasks:** 2
- **Files modified:** 1 created, 0 modified

## Accomplishments

- GitHub Actions CI workflow validates Go (`go build`, `go test`, `goose validate`) and Android (`assembleDebug`) on every push and PR to `main` and `production`
- Single `ubuntu-latest` job — no macOS runner, no iOS build attempted (D-01)
- No codegen step in CI — `generated.go` is already committed and only compiled (D-03)
- `origin/production` branch created and pushed, mirroring `main` at commit `62632cb`, ready for Railway production service to watch (D-04/D-05)

## Task Commits

| Task | Name | Commit | Type |
|------|------|--------|------|
| 1 | GitHub Actions CI workflow (Go + Android, Linux-only) | 62632cb | feat |
| 2 | Create and push production git branch | (git topology — no file commit) | — |

**Plan metadata:** (docs commit below)

## Files Created/Modified

- `.github/workflows/ci.yml` — Single Linux CI job: Go build/test/goose-validate + Android assembleDebug; triggers on push/PR to main and production

## Decisions Made

- Used `go run github.com/pressly/goose/v3/cmd/goose@v3.27.1` for the validate step rather than a separate install step — consistent with the pinned version in `go.mod` and the zero-install pattern used elsewhere
- `production` branch created from `main` with no additional commits — it is an exact mirror at creation time per D-05 (release = manual PR from main → production)

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required for this plan. Railway watching of the `production` branch is a dashboard action deferred to Plan 01-04 provisioning.

## Verification Results

| Check | Result |
|-------|--------|
| `python3 -c 'import yaml; yaml.safe_load(open(".github/workflows/ci.yml"))'` | PASS (valid YAML) |
| `grep 'runs-on: ubuntu-latest'` | PASS |
| `grep 'go-version-file: backend/go.mod'` | PASS |
| `grep 'assembleDebug'` | PASS |
| `grep 'java-version: "21"'` | PASS |
| `! grep 'oapi-codegen'` | PASS (no codegen in CI) |
| `! grep 'macos'` | PASS (no macOS runner) |
| `git ls-remote --heads origin production` | PASS (refs/heads/production exists) |
| Production tip matches main | PASS (both 62632cb2047c28e5...) |
| Local checkout back on main | PASS |

## Known Stubs

None — the CI workflow is complete and functional. The `google-services.json` at `androidApp/src/debug/` is a placeholder (documented in Plan 01-02), which is acceptable because `assembleDebug` does not validate the Firebase config contents.

## Threat Flags

All T-03-* threats from the plan's threat model are mitigated:

| Flag | File | Description |
|------|------|-------------|
| T-03-01 mitigated | .github/workflows/ci.yml | No oapi-codegen step — only compiles committed generated.go |
| T-03-02 mitigated | .github/workflows/ci.yml | No deploy step, no repository secrets consumed — fork PRs cannot exfiltrate credentials |
| T-03-03 accepted | .github/workflows/ci.yml | Debug google-services.json is non-sensitive placeholder; nothing secret in CI logs |
| T-03-SC mitigated | .github/workflows/ci.yml | All actions are official (actions/*, gradle/actions/*) pinned to major versions |

## Next Phase Readiness

- CI is active: the next push to `main` will trigger the workflow and validate the full stack
- `origin/production` exists and is ready for Railway's production service to watch
- Plan 01-04 (Firebase + Railway + Clerk provisioning) can now rely on CI green for every merge to main

## Self-Check: PASSED

Files created:
- .github/workflows/ci.yml: FOUND

Commits:
- 62632cb: FOUND (feat(01-03): add GitHub Actions CI workflow)

Git remote state:
- origin/production: FOUND (refs/heads/production, tip 62632cb)

---
*Phase: 01-foundation-infrastructure*
*Completed: 2026-06-26*
