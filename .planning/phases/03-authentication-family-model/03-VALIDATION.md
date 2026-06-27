---
phase: 03
slug: authentication-family-model
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-27
---

# Phase 03 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | go test (backend) / Android Instrumented Tests (mobile) |
| **Config file** | none — Wave 0 installs |
| **Quick run command** | `go test ./... -count=1 -short` |
| **Full suite command** | `go test ./... -count=1 -race` |
| **Estimated runtime** | ~30 seconds (backend unit/integration) |

---

## Sampling Rate

- **After every task commit:** Run `go test ./... -count=1 -short`
- **After every plan wave:** Run `go test ./... -count=1 -race`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 1 | REQ-016 | T-3-01 | JWT with expired claims rejected 401 | unit | `go test ./internal/middleware/... -run TestClerkAuth` | ❌ W0 | ⬜ pending |
| 03-01-02 | 01 | 1 | REQ-016 | T-3-02 | Token from wrong azp rejected 401 | unit | `go test ./internal/middleware/... -run TestAzpValidation` | ❌ W0 | ⬜ pending |
| 03-01-03 | 01 | 1 | REQ-017 | T-3-03 | Missing org claim → 403 | unit | `go test ./internal/middleware/... -run TestOrgContext` | ❌ W0 | ⬜ pending |
| 03-01-04 | 01 | 1 | REQ-017 | T-3-04 | Org mismatch → 403 | unit | `go test ./internal/middleware/... -run TestOrgMismatch` | ❌ W0 | ⬜ pending |
| 03-01-05 | 01 | 1 | REQ-NF-006 | — | /healthz returns 200 without auth | integration | `go test ./internal/routes/... -run TestHealthzNoAuth` | ❌ W0 | ⬜ pending |
| 03-02-01 | 02 | 2 | REQ-015 | — | Sign-up flow creates Clerk user | manual | See Manual-Only Verifications | N/A | ⬜ pending |
| 03-02-02 | 02 | 2 | REQ-018 | — | Org picker displayed for multi-org user | manual | See Manual-Only Verifications | N/A | ⬜ pending |
| 03-03-01 | 03 | 3 | REQ-019 | — | Caregiver receives invitation email | manual | See Manual-Only Verifications | N/A | ⬜ pending |
| 03-03-02 | 03 | 3 | REQ-026 | T-3-05 | Caregiver cannot delete child profile → 403 | unit | `go test ./internal/handlers/... -run TestCaregiverRBAC` | ❌ W0 | ⬜ pending |
| 03-03-03 | 03 | 3 | REQ-027 | — | Admin can delete child profile → 200 | unit | `go test ./internal/handlers/... -run TestAdminRBAC` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `internal/middleware/clerk_auth_test.go` — stubs for REQ-016, T-3-01, T-3-02
- [ ] `internal/middleware/org_context_test.go` — stubs for REQ-017, T-3-03, T-3-04
- [ ] `internal/routes/health_test.go` — stub for REQ-NF-006
- [ ] `internal/handlers/rbac_test.go` — stubs for REQ-026, REQ-027, T-3-05

*Existing go test infrastructure covers test execution; only test files need creation.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Sign-up creates Clerk user on Android | REQ-015 | Requires real device + Clerk dashboard | Install debug APK, tap Sign Up, verify user appears in Clerk dev dashboard |
| Sign-in returns JWT injected into Ktor requests | REQ-015 | Requires running backend + real device | Sign in, check Logcat for bearer token header on API call |
| Org picker shown for multi-org account | REQ-018 | Requires multi-org test account setup | Create 2 orgs in Clerk dev dashboard, sign in, confirm picker appears |
| Caregiver invitation email received | REQ-019 | Requires real email delivery (Clerk) | Admin invites caregiver email, verify email received within 60s |
| Caregiver can log events but not delete children | REQ-026/027 | Requires full app flow | Accept invitation as caregiver, attempt delete (expect blocked), log event (expect allowed) |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
