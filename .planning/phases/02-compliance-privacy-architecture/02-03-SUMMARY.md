---
phase: 02-compliance-privacy-architecture
plan: 03
subsystem: api
tags: [go, erasure, gdpr, coppa, cascade, audit-log, transaction, postgresql]

requires:
  - phase: 02-01
    provides: "erasure_audit, children, consent_events, potty_events, notification_preferences tables"
  - phase: 02-02
    provides: "DeleteV1ChildrenId and DeleteV1Account stub bodies to replace; DB wired to Server struct"

provides:
  - "DELETE /v1/children/{id} — FK-safe child erasure cascade with D-10 response and in-transaction audit"
  - "DELETE /v1/account — org-wide child cascade deletion with account-level audit row"
  - "D-12 sweep-on-request: erasure_audit rows older than 90 days purged inside every erasure tx"

affects: ["03-auth-jwt", "phase-3-clerk", "staging-smoke-test"]

tech-stack:
  added: []
  patterns:
    - "FK-safe deletion order: potty_events → notification_preferences → children → consent_events"
    - "Sweep-on-request audit purge (D-12): DELETE erasure_audit >90d at start of every erasure tx"
    - "D-10 structured erasure confirmation: 200 JSON with deleted_* counts, not 204"
    - "commit-before-external-call (Pitfall 2): tx.Commit before any potential Clerk API call"

key-files:
  created:
    - backend/internal/api/erasure_handler_test.go
  modified:
    - backend/internal/api/server.go

key-decisions:
  - "FK-safe order (children before consent_events) — D-05 FK prevents reversing this without violation"
  - "D-12 purge runs at start of every erasure tx (not background job) — sweep-on-request per REQ-C-008"
  - "target_id for account_deletion uses gen_random_uuid() — clerk_org_id is not a UUID in Phase 2; Phase 3 TODO"
  - "device_tokens: only requesting user's own tokens deleted in Phase 2; org-wide deletion deferred to Phase 3 (requires Clerk org member list)"
  - "No Clerk API call in Phase 2 — org/user deletion deferred; DB commit happens before any future external call (Pitfall 2)"

patterns-established:
  - "Erasure cascade pattern: SELECT consent_event_id first, then delete dependents, then child row, then consent row"
  - "Every erasure handler: D-12 purge → cascade → audit INSERT → tx.Commit → D-10 response"

requirements-completed: [REQ-011, REQ-012, REQ-013, REQ-014, REQ-C-002, REQ-C-003, REQ-C-004, REQ-C-008]

duration: 18min
completed: 2026-06-26
---

# Phase 02-03: Erasure Endpoints Summary

**GDPR/COPPA erasure cascade: FK-safe child and account hard-delete with in-transaction audit, D-12 purge, and D-10 structured confirmation**

## Performance

- **Duration:** ~18 min
- **Completed:** 2026-06-26
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- `DELETE /v1/children/{id}` hard-deletes in FK-safe order (potty_events → notification_preferences → children → consent_events), records audit row, returns D-10 JSON
- `DELETE /v1/account` cascades deletion across all children in the requesting org, deletes user's own device tokens, writes account-level audit row, returns D-10 JSON
- Every erasure tx opens with D-12 sweep: `DELETE FROM erasure_audit WHERE deleted_at < NOW() - INTERVAL '90 days'`
- Both handlers are unit-tested at auth path (nil-DB safe, 401 with `application/problem+json`)

## Task Commits

1. **Task 1: Child erasure cascade** — `c327421` (test, TDD RED) + `25503b1` (feat)
2. **Task 2: Account erasure cascade** — `e60328b` (feat, includes test)

**Plan metadata:** `docs(02-03)` pending

## Files Created/Modified
- `backend/internal/api/server.go` — `DeleteV1ChildrenId` (full implementation replacing 02-02 stub) + `DeleteV1Account` (full account cascade)
- `backend/internal/api/erasure_handler_test.go` — `TestDeleteV1ChildrenId_MissingAuthHeader` + `TestDeleteV1Account_MissingAuthHeader`

## Decisions Made
- FK-safe deletion order enforced: `SELECT consent_event_id` captured before any delete; `children` deleted before `consent_events` (D-05 FK would reject the reverse)
- `target_id` in `erasure_audit` uses `gen_random_uuid()` for account deletions in Phase 2 — Clerk org IDs are non-UUID strings; Phase 3 will supply a real UUID org identifier
- Phase 3 Clerk API call (org member token sweep + Organization deletion) explicitly deferred with TODO comments; tx.Commit comes before any future external call per Pitfall 2

## Deviations from Plan
None — plan executed exactly as written. `DeleteV1Account` was a stub from 02-02 that was replaced as planned.

## Issues Encountered
Agent connection dropped mid-execution; `DeleteV1Account` and SUMMARY.md were completed inline by the orchestrator after the dropout. All tests pass.

## Next Phase Readiness
- All three compliance endpoints (POST /v1/children, DELETE /v1/children/{id}, DELETE /v1/account) are fully implemented and tested at auth/validation paths
- Staging smoke test (manual, 02-VALIDATION.md) needed: create child → delete child → verify erasure_audit row written and child rows gone
- Phase 3: close IDOR gap (T-2-02) via JWT org check in DeleteV1ChildrenId, org-wide device_token sweep, Clerk Organization deletion

---
*Phase: 02-compliance-privacy-architecture*
*Completed: 2026-06-26*
