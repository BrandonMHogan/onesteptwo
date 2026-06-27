---
phase: 02-compliance-privacy-architecture
plan: "06"
subsystem: api
tags: [go, erasure, gdpr, coppa, clerk, device-tokens, audit-log, traceability, uuid]

requires:
  - phase: 02-compliance-privacy-architecture plan 05
    provides: RFC 7807 WriteProblem helper wired into main.go error handler

provides:
  - ClerkOrgClient interface + httpClerkClient (clerk.go) for org member listing and org deletion
  - DeleteV1Account rewritten: org-wide device_tokens delete, deterministic UUIDv5 audit target_id, post-commit Clerk org deletion
  - TestDeleteV1Account_ClerkMemberFetchError: nil-DB safe test for member-fetch error path
  - REQUIREMENTS.md traceability: 7 Phase 2 erasure requirements marked Complete

affects: [phase-03-auth, phase-08-push-notifications]

tech-stack:
  added:
    - github.com/google/uuid v1.6.0 (promoted from indirect to direct — uuid.NewSHA1 for deterministic UUIDv5)
  patterns:
    - Clerk network call happens BEFORE BeginTx; error returns before any data modification
    - post-commit external call (Pitfall 2): Clerk org deletion after tx.Commit, logged on failure, never fails request
    - deterministic UUIDv5 of "onesteptwo:org:<clerkOrgID>" as erasure_audit.target_id (recomputable for traceability)
    - pq.Array($1) for org-wide multi-user DELETE with ANY operator

key-files:
  created:
    - backend/internal/api/clerk.go
  modified:
    - backend/internal/api/server.go
    - backend/internal/api/erasure_handler_test.go
    - backend/go.mod
    - backend/go.sum
    - .planning/REQUIREMENTS.md

key-decisions:
  - "Clerk member list is fetched BEFORE BeginTx so a network failure never leaves a partial transaction open"
  - "Clerk org deletion is invoked AFTER tx.Commit (Pitfall 2); a Clerk failure is logged and does not roll back the already-committed DB erasure"
  - "erasure_audit.target_id is uuid.NewSHA1(uuid.NameSpaceURL, []byte('onesteptwo:org:'+clerkOrgID)) — same org always yields same UUID, enabling audit lookup by org id without storing the raw Clerk org string (REQ-C-008)"
  - "pq.Array(memberIDs) with ANY($1) deletes all org members' device_tokens in one statement; requester is appended defensively to the list fetched from Clerk"

patterns-established:
  - "External call before transaction: fetch Clerk data pre-tx to avoid holding DB connections across network round-trips"
  - "Pitfall 2 ordering: all post-commit side effects (Clerk API, FCM, etc.) happen after tx.Commit and are logged on error without failing the response"
  - "Deterministic audit UUID: uuid.NewSHA1(uuid.NameSpaceURL, []byte('onesteptwo:<type>:'+externalID)) converts non-UUID external identifiers to traceable, recomputable UUIDs for audit columns typed UUID NOT NULL"

requirements-completed: [REQ-011, REQ-012, REQ-014, REQ-C-002, REQ-C-003, REQ-C-004, REQ-C-008]

duration: 25min
completed: 2026-06-26
---

# Phase 02 Plan 06: Account Erasure Completion Summary

**Org-wide device_tokens deletion via Clerk member list, deterministic UUIDv5 audit row, and post-commit Clerk Organization deletion closing REQ-012 and 6 related Phase 2 erasure requirements**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-06-26T23:50:00Z
- **Completed:** 2026-06-26T24:15:00Z
- **Tasks:** 3 (Task 1 in prior session; Tasks 2-3 this session)
- **Files modified:** 6

## Accomplishments

- Rewrote `DeleteV1Account` to fetch all org member IDs from Clerk before `BeginTx`, replacing the single-user `device_tokens` delete with an org-wide `DELETE FROM device_tokens WHERE clerk_user_id = ANY($1)` bound via `pq.Array`
- Replaced the `gen_random_uuid()` audit target_id with a deterministic UUIDv5 derived from the Clerk org id, making the `erasure_audit` row recomputable/traceable for any clerk_org_id (REQ-C-008)
- Added post-commit `s.Clerk.DeleteOrganization` with log-on-failure pattern (Pitfall 2 ordering), ensuring the Clerk Organization is deleted without risking a failed network call rolling back committed DB erasure
- Added `TestDeleteV1Account_ClerkMemberFetchError` with a nil-DB stub verifying the 500 path returns before `BeginTx`
- Marked 7 Phase 2 erasure requirements Complete in REQUIREMENTS.md traceability

## Task Commits

1. **Task 1: Minimal Clerk REST client + Server.Clerk field + main.go wiring** - `36442e6` (feat)
2. **Task 2: Rewrite DeleteV1Account for org-wide erasure + Clerk org deletion** - `a780aed` (feat)
3. **Task 3: Update REQUIREMENTS.md traceability** - `4f696fe` (docs)

## Files Created/Modified

- `backend/internal/api/clerk.go` - ClerkOrgClient interface + httpClerkClient with ListOrgMemberUserIDs and DeleteOrganization (Task 1)
- `backend/internal/api/server.go` - DeleteV1Account rewritten: pre-tx Clerk fetch, org-wide token delete, deterministic UUID audit, post-commit org deletion
- `backend/internal/api/erasure_handler_test.go` - stubClerk test double + TestDeleteV1Account_ClerkMemberFetchError
- `backend/go.mod` - github.com/google/uuid promoted from indirect to direct
- `backend/go.sum` - updated checksums
- `.planning/REQUIREMENTS.md` - 7 Phase 2 rows changed from Pending to Complete

## Decisions Made

- Clerk member list is fetched BEFORE `BeginTx` so a network failure never leaves a partial transaction or open cursor; the 500 returns before any DB state changes
- Clerk org deletion uses Pitfall 2 ordering (post-commit) and is intentionally fire-and-forget on error — DB data is already erased, a lingering Clerk org holds only PII that Clerk owns
- `uuid.NewSHA1(uuid.NameSpaceURL, []byte("onesteptwo:org:"+clerkOrgID))` chosen for determinism: the same org always yields the same UUIDv5, enabling re-derivation from any clerk_org_id to locate its audit rows without storing the raw Clerk string in a UUID-typed column
- Requester's `clerkUserID` appended to the Clerk-fetched `memberIDs` slice defensively; SQL `ANY($1)` handles duplicates via set semantics

## Deviations from Plan

None - plan executed exactly as written. The test file had uncommitted working-tree changes from a prior session that were included in the Task 2 commit as intended by the plan.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required beyond the `CLERK_SECRET_KEY` environment variable already required by Task 1's main.go wiring.

## Next Phase Readiness

- Account erasure is now complete for REQ-012: all org member device_tokens deleted, Clerk Organization deleted post-commit, audit row traceable via deterministic UUID
- Phase 3 (Authentication & Family Model) can build on the `ClerkOrgClient` interface for any additional Clerk API interactions
- The `pq.Array` + `ANY($1)` pattern established here is available for future bulk-delete or bulk-query operations in Phase 8 (push notifications)
- T-2-02 (no JWT org ownership check) remains open and accepted for Phase 2; Phase 3 must add the JWT claim check before endpoints ship to production

---
*Phase: 02-compliance-privacy-architecture*
*Completed: 2026-06-26*
