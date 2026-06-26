---
phase: 02-compliance-privacy-architecture
plan: "05"
subsystem: api
tags: [go, rfc7807, problem-details, error-handling, oapi-codegen, tdd]

requires:
  - phase: 02-compliance-privacy-architecture
    provides: WriteProblem helper and generated oapi-codegen types (InvalidParamFormatError, StdHTTPServerOptions)

provides:
  - ProblemErrorHandler function in backend/internal/api/error_handler.go — closes REQ-NF-001 BLOCKER for path-param binding failures
  - main.go wired to use HandlerWithOptions with ProblemErrorHandler instead of HandlerFromMux default

affects:
  - 03-authentication-family-model (all new routes will inherit RFC 7807 error handling automatically)
  - 04-core-event-logging (same — path param errors on event endpoints covered)

tech-stack:
  added: []
  patterns:
    - "ProblemErrorHandler pattern: custom ErrorHandlerFunc using errors.As to detect InvalidParamFormatError, calls WriteProblem with static param name only (never err.Error())"
    - "HandlerWithOptions replaces HandlerFromMux as the route registration call in main.go — enables custom ErrorHandlerFunc injection"

key-files:
  created:
    - backend/internal/api/error_handler.go
    - backend/internal/api/error_handler_test.go
  modified:
    - backend/cmd/server/main.go

key-decisions:
  - "Use errors.As against *InvalidParamFormatError to detect oapi-codegen binding failures; only the static ParamName is surfaced in the RFC 7807 detail to prevent Go-internals disclosure (T-2-12)"
  - "Generic fallback detail 'invalid request parameter' for non-binding errors — keeps the response safe without leaking context"

patterns-established:
  - "Pattern: ErrorHandlerFunc injection — future route expansions inherit RFC 7807 automatically via HandlerWithOptions"

requirements-completed: [REQ-NF-001, REQ-013]

duration: 8min
completed: 2026-06-26
---

# Phase 02 Plan 05: RFC 7807 ProblemErrorHandler Summary

**ProblemErrorHandler closes the REQ-NF-001 BLOCKER: oapi-codegen path-param binding failures now return 400 application/problem+json instead of text/plain, wired via HandlerWithOptions in main.go**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-06-26T23:45:00Z
- **Completed:** 2026-06-26T23:53:00Z
- **Tasks:** 2 (Task 1 TDD: 3 commits — test/feat; Task 2: 1 commit)
- **Files modified:** 3

## Accomplishments

- ProblemErrorHandler detects InvalidParamFormatError via errors.As and writes RFC 7807 400 responses via WriteProblem — surfaces only the static OpenAPI param name, never err.Error()
- Httptest coverage proves DELETE /v1/children/not-a-uuid returns 400 application/problem+json with JSON status 400 and no leaked Go error string
- main.go route registration updated from HandlerFromMux to HandlerWithOptions, injecting ProblemErrorHandler — all future routes inherit RFC 7807 binding error handling automatically

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: failing test** - `10464a8` (test)
2. **Task 1 GREEN: ProblemErrorHandler implementation** - `325f8f5` (feat)
3. **Task 2: Wire into main.go** - `db29322` (feat)

## Files Created/Modified

- `backend/internal/api/error_handler.go` - ProblemErrorHandler function: errors.As detection of InvalidParamFormatError, WriteProblem delegation with static param name only
- `backend/internal/api/error_handler_test.go` - TestProblemErrorHandler_InvalidUUIDPathParam: httptest asserting 400 status, application/problem+json Content-Type, JSON status field 400, and absence of "Invalid format for parameter"
- `backend/cmd/server/main.go` - Replaced api.HandlerFromMux(srv, mux) with api.HandlerWithOptions(srv, api.StdHTTPServerOptions{BaseRouter: mux, ErrorHandlerFunc: api.ProblemErrorHandler})

## Decisions Made

- errors.As is used against *InvalidParamFormatError (not a type assertion or string matching) — idiomatic Go error inspection that works through wrapping chains
- Only the static OpenAPI parameter name ("id") appears in the RFC 7807 detail; the Err field from InvalidParamFormatError is discarded to satisfy T-2-12 (Information Disclosure)
- Generic fallback detail "invalid request parameter" for non-InvalidParamFormatError cases keeps responses safe without conditional leakage

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- RFC 7807 error coverage is now complete for the current route set: path-param binding failures, handler-level WriteProblem calls, and the new ProblemErrorHandler all emit application/problem+json
- Phase 3 (authentication) adds new routes — HandlerWithOptions is already in place, so new routes automatically inherit ProblemErrorHandler with no additional wiring
- The BLOCKER from VERIFICATION.md Gap 1 (CR-02) is closed

---
*Phase: 02-compliance-privacy-architecture*
*Completed: 2026-06-26*
