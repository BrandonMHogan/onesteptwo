---
phase: 2
slug: compliance-privacy-architecture
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-26
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | go test |
| **Config file** | none — standard Go test runner |
| **Quick run command** | `cd backend && go test ./internal/api/... -run TestConsent -v` |
| **Full suite command** | `cd backend && go test ./... -v` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && go test ./internal/api/... -v`
- **After every plan wave:** Run `cd backend && go test ./... -v`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 2-01-01 | 01 | 1 | REQ-008 | — | children table has only 7 columns | unit | `psql -c "\d children"` | ❌ W0 | ⬜ pending |
| 2-01-02 | 01 | 1 | REQ-C-001 | — | consent_events has no IP column | unit | `psql -c "\d consent_events"` | ❌ W0 | ⬜ pending |
| 2-02-01 | 02 | 1 | REQ-010 | T-2-01 | POST /v1/children rejects when no consent_events row | unit | `go test ./internal/api/... -run TestCreateChild_NoConsent` | ❌ W0 | ⬜ pending |
| 2-02-02 | 02 | 1 | REQ-011 | T-2-02 | DELETE /v1/children/{id} cascades in FK-safe order | unit | `go test ./internal/api/... -run TestDeleteChild_Cascade` | ❌ W0 | ⬜ pending |
| 2-02-03 | 02 | 2 | REQ-012 | T-2-03 | DELETE /v1/account cascades all family data | unit | `go test ./internal/api/... -run TestDeleteAccount_Cascade` | ❌ W0 | ⬜ pending |
| 2-03-01 | 03 | 2 | REQ-014 | — | erasure_audit row inserted on every deletion | unit | `go test ./internal/api/... -run TestErasureAudit` | ❌ W0 | ⬜ pending |
| 2-04-01 | 04 | 3 | REQ-C-005 | — | 2.sqm SQLite types valid; verifyMigrations passes | build | `./gradlew :shared:compileKotlinIosArm64` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/internal/api/children_test.go` — stubs for REQ-010, REQ-011
- [ ] `backend/internal/api/account_test.go` — stubs for REQ-012, REQ-014
- [ ] Test DB setup (test container or migration-up helper) — shared fixtures

*If test infrastructure already exists, adjust file paths accordingly.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 90-day audit purge fires correctly | REQ-014 | Time-dependent behavior | Insert erasure_audit row with `deleted_at = NOW() - INTERVAL '91 days'`, call the purge trigger/function, verify row is gone |
| Goose migration applies cleanly to staging Railway DB | REQ-NF-003 | Production-like environment required | Run `cmd/migrate/main.go` against Railway staging, verify all 6 tables created |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
