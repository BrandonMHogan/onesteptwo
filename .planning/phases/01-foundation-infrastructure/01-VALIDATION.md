---
phase: 1
slug: foundation-infrastructure
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-26
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | go test (backend), Gradle test (Android/KMP) |
| **Config file** | `backend/Makefile` / `build.gradle.kts` — Wave 0 installs |
| **Quick run command** | `cd backend && go build ./... && go test ./...` |
| **Full suite command** | `./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest && cd backend && go test ./...` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && go build ./...`
- **After every plan wave:** Run `cd backend && go test ./... && ./gradlew :shared:testDebugUnitTest`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 1-01-01 | 01 | 1 | REQ-001 | — | /healthz returns 200 only, no auth required | integration | `curl -f http://localhost:8080/healthz` | ❌ W0 | ⬜ pending |
| 1-01-02 | 01 | 1 | REQ-002 | — | /v1/ prefix applies to all non-healthz routes | unit | `go test ./...` | ❌ W0 | ⬜ pending |
| 1-01-03 | 01 | 1 | REQ-025 | — | 1.sqm migration applies cleanly | integration | `go run ./cmd/migrate up` | ❌ W0 | ⬜ pending |
| 1-01-04 | 01 | 1 | REQ-NF-001 | — | Error responses use RFC 7807 Problem Details format | unit | `go test ./...` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/internal/api/healthz_test.go` — stubs for REQ-001 healthcheck
- [ ] `backend/internal/api/error_test.go` — RFC 7807 format stubs for REQ-NF-001
- [ ] `backend/db/migrations/` — goose migration directory with empty `00001_init.sql`
- [ ] SQLDelight `1.sqm` empty migration in `:shared` module

*If none: "Existing infrastructure covers all phase requirements."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Railway staging deploys on push to main | REQ-001 | External cloud service, no local simulation | Push to main, verify Railway dashboard shows healthy deployment |
| Railway production deploys on push to production | REQ-001 | External cloud service, no local simulation | Push to production branch, verify Railway dashboard shows healthy deployment |
| PostgreSQL R2 backup runs and test dump succeeds | REQ-NF-001 | External cloud service scheduling | Trigger manual backup in Railway dashboard, verify dump object appears in R2 bucket |
| Clerk dev org provisioned with headless SDK keys | REQ-002 | External provisioning, no local simulation | Log into Clerk dashboard, verify org exists, copy SDK keys to .env |
| Firebase projects exist with FCM config confirmed | REQ-002 | External provisioning, no local simulation | Log into Firebase console, verify two projects, download google-services.json |
| CI pipeline passes on fresh PR | REQ-NF-001 | GitHub Actions environment | Open a PR, verify all checks pass in GitHub Actions tab |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
