---
phase: 02-compliance-privacy-architecture
plan: 02
subsystem: api
tags: [go, openapi, oapi-codegen, consent-gate, transaction, database-sql, tdd]
dependency_graph:
  requires:
    - 02-01 (backend/db/migrations/00002_schema.sql — consent_events + children tables)
  provides:
    - api/openapi.yaml (three new endpoints + four schemas)
    - backend/internal/api/generated.go (regenerated ServerInterface with three new methods)
    - backend/internal/api/server.go (DB-backed Server + PostV1Children handler)
    - backend/cmd/server/main.go (postgres connection with pool limits)
    - backend/internal/api/children_handler_test.go (httptest coverage for consent handler)
  affects:
    - Plan 02-03 (DeleteV1ChildrenId and DeleteV1Account stub methods to be replaced)
    - Phase 03 (JWT auth replaces placeholder header check — TODO Phase 3 marker in handler)
tech_stack:
  added:
    - github.com/oapi-codegen/runtime v1.4.2 (required for UUID path-param binding in generated code)
  patterns:
    - oapi-codegen v2 std-http-server code generation from OpenAPI spec
    - database/sql + lib/pq connection pool (max 25 open, 5 idle)
    - atomic transaction (BeginTx / defer Rollback / Commit) for consent-then-child inserts
    - TDD RED/GREEN/REFACTOR cycle for handler unit tests
    - httptest request-response pattern with nil DB for auth/validation unit tests
key_files:
  created:
    - backend/internal/api/children_handler_test.go
  modified:
    - api/openapi.yaml
    - backend/internal/api/generated.go
    - backend/internal/api/server.go
    - backend/cmd/server/main.go
    - backend/internal/api/problem.go
    - backend/go.mod
    - backend/go.sum
decisions:
  - "Removed ProblemDetail struct from problem.go (naming conflict with generated.go ProblemDetail): updated WriteProblem to use pointer types from the generated struct. JSON output is functionally equivalent; existing tests pass unchanged."
  - "Added oapi-codegen/runtime v1.4.2 (not v1.1.1) — v1.1.1 missing Type/Format fields in BindStyledParameterOptions required by oapi-codegen v2.7.1 generated code."
  - "DeleteV1ChildrenId and DeleteV1Account carry TODO 02-03 stubs returning 501; full erasure cascade implemented in plan 02-03."
  - "PostV1Children TDD: nil DB is safe for unit tests because auth/validation gates return before any DB call — documented in handler comment."
metrics:
  duration: ~6 min
  completed: 2026-06-26
  tasks_completed: 2
  files_created: 1
  files_modified: 7
---

# Phase 02 Plan 02: API Contract, DB Wiring, and Consent-Gate Handler Summary

**One-liner:** OpenAPI spec extended with three compliance endpoints; generated.go regenerated with PostV1Children/DeleteV1ChildrenId/DeleteV1Account on ServerInterface; Server carries *sql.DB with pool limits; PostV1Children atomically inserts consent_events then children in a single BeginTx transaction with no IP captured and TDD unit-test green.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Declare API contract, wire DB, regenerate interface | b8f6caf | api/openapi.yaml, generated.go, server.go, main.go, problem.go, go.mod, go.sum |
| TDD-RED | Failing tests for PostV1Children | eb7661d | children_handler_test.go |
| 2 | Implement PostV1Children consent-gate handler | 13992ac | server.go |

## What Was Built

**Task 1 — API contract + DB wiring:**

1. `api/openapi.yaml` extended with three paths:
   - `POST /v1/children` (operationId: postV1Children, requestBody: CreateChildRequest, 201/400/401)
   - `DELETE /v1/children/{id}` (operationId: deleteV1ChildrenId, path param id: uuid, 200/401/404)
   - `DELETE /v1/account` (operationId: deleteV1Account, 200/401)
   And four component schemas: `CreateChildRequest`, `ChildResponse`, `ErasureConfirmation`, `ProblemDetail`.

2. `make generate` regenerated `backend/internal/api/generated.go` — `ServerInterface` now declares:
   - `PostV1Children(w http.ResponseWriter, r *http.Request)`
   - `DeleteV1ChildrenId(w http.ResponseWriter, r *http.Request, id openapi_types.UUID)`
   - `DeleteV1Account(w http.ResponseWriter, r *http.Request)`

3. `backend/internal/api/server.go` — `Server` struct now carries `DB *sql.DB`. Stubs added for all three new interface methods. `DeleteV1ChildrenId` and `DeleteV1Account` return 501 with `// TODO 02-03` comment. `PostV1Children` had an initial 501 stub replaced in Task 2.

4. `backend/cmd/server/main.go` — Opens postgres connection before constructing the Server:
   `sql.Open("postgres", os.Getenv("DATABASE_URL"))` with `SetMaxOpenConns(25)` and `SetMaxIdleConns(5)`. Constructs `&api.Server{DB: db}`.

5. `backend/internal/api/problem.go` — Removed duplicate `ProblemDetail` struct (now generated); updated `WriteProblem` to use pointer fields from the generated type. JSON output is functionally identical.

**Task 2 (TDD) — PostV1Children consent-gate handler:**

Handler in `server.go` implements the full consent-gate protocol (D-07, REQ-009, REQ-010, REQ-C-001):

1. **Auth gate** (placeholder, Phase 3 replaces with JWT): reads `X-Clerk-User-Id` and `X-Clerk-Org-Id` headers; 401 if either is empty. Marked `// TODO Phase 3: replace with JWT claim extraction`.
2. **JSON decode**: 400 on missing/malformed body.
3. **Input validation** (Security V5): nickname non-empty and ≤100 chars; birth_month 1-12; birth_year 2000..current_year+1; consent.app_version and consent.consent_text_version non-empty.
4. **Atomic transaction** (D-07):
   - `s.DB.BeginTx(r.Context(), nil)` → `defer tx.Rollback()`
   - `INSERT INTO consent_events (clerk_user_id, consented_at, app_version, consent_text_version) VALUES ($1, NOW(), $2, $3) RETURNING id` — exactly the D-06 four fields, no IP column
   - `INSERT INTO children (clerk_org_id, nickname, birth_month, birth_year, consent_event_id) VALUES ($1,$2,$3,$4,$5) RETURNING id`
   - `tx.Commit()`
5. **201 Created** with ChildResponse `{id, clerk_org_id, nickname, birth_month, birth_year}`.
6. **No IP**: handler never reads `r.RemoteAddr` or any forwarded-for header (REQ-C-009, T-2-03). Verified by grep.
7. **All SQL parameterized**: `$1`-style placeholders throughout; no fmt.Sprintf SQL (T-2-05).

**Unit tests** in `children_handler_test.go` (`package api_test`):
- `TestPostV1Children_MissingBody` → 400 + application/problem+json
- `TestPostV1Children_MalformedBody` → 400 + application/problem+json
- `TestPostV1Children_MissingAuthHeaders` → 401
- `TestPostV1Children_MissingClerkUserId` → 401
- `TestPostV1Children_InvalidInput` (5 subtests) → 400 for each validation failure

All tests use `&api.Server{}` with nil DB — safe because auth/validation gates return before any DB access.

## Verification Results

All acceptance criteria passed before commit:

**Task 1:**
- operationId: postV1Children, deleteV1ChildrenId, deleteV1Account in openapi.yaml: PASS
- Schemas CreateChildRequest, ChildResponse, ErasureConfirmation, ProblemDetail in openapi.yaml: PASS
- generated.go ServerInterface lists all three methods: PASS
- DB *sql.DB on Server struct: PASS
- api.Server{DB: db} in main.go with pool limits: PASS
- go build ./... exits 0: PASS
- TODO 02-03 comment in stub bodies: PASS

**Task 2:**
- TestPostV1Children_MissingBody, MissingAuthHeaders, InvalidInput defined: PASS
- go test ./internal/api/... -run 'TestPostV1Children' -v exits 0: PASS
- func (s *Server) PostV1Children present: PASS
- INSERT INTO consent_events and INSERT INTO children present: PASS
- D-06 fields (clerk_user_id, consented_at, app_version, consent_text_version) in consent INSERT: PASS
- No r.RemoteAddr or X-Forwarded-For in server.go: PASS
- s.DB.BeginTx, defer tx.Rollback, tx.Commit all present: PASS
- No fmt.Sprintf SQL interpolation: PASS

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocker] ProblemDetail naming conflict between problem.go and generated.go**

- **Found during:** Task 1, after running `make generate`
- **Issue:** oapi-codegen v2.7.1 generates a `ProblemDetail` Go struct in `generated.go` (with pointer fields, omitempty). The existing `problem.go` also defined `ProblemDetail` (with value fields). Both are in `package api` — compile error: `ProblemDetail redeclared in this block`.
- **Fix:** Removed the `ProblemDetail` struct definition from `problem.go` (relying on the generated one). Updated `WriteProblem` to construct the generated struct using pointer fields (`&typ`, `&title`, `&status`, `&detail`). JSON output is functionally identical; existing `TestWriteProblem` passes unchanged.
- **Files modified:** `backend/internal/api/problem.go`
- **Commit:** b8f6caf

**2. [Rule 3 - Blocker] Missing github.com/oapi-codegen/runtime dependency**

- **Found during:** Task 1, after running `make generate`
- **Issue:** The regenerated `generated.go` imports `github.com/oapi-codegen/runtime` and `github.com/oapi-codegen/runtime/types` for UUID path-param binding, but neither package was in go.mod.
- **Fix:** Ran `go get github.com/oapi-codegen/runtime@v1.4.2`. Version v1.1.1 was initially fetched but lacked the `Type` and `Format` fields in `BindStyledParameterOptions` required by the generated code; v1.4.2 resolved all build errors.
- **Files modified:** `backend/go.mod`, `backend/go.sum`
- **Commit:** b8f6caf

## Known Stubs

The following stubs are intentional placeholders for Plan 02-03:

| Stub | File | Reason |
|------|------|--------|
| `DeleteV1ChildrenId` returns 501 | backend/internal/api/server.go | Full child erasure cascade implemented in plan 02-03 |
| `DeleteV1Account` returns 501 | backend/internal/api/server.go | Full account erasure cascade implemented in plan 02-03 |

These stubs satisfy the ServerInterface for compilation but are not deployed to production before plan 02-03 completes.

The `// TODO Phase 3: replace with JWT claim extraction` comment in `PostV1Children` documents a known Phase 2 limitation — the auth placeholder is intentional per plan design.

## Threat Flags

No new threat surface beyond the plan's threat model. All T-2-xx mitigations verified:

| Threat | Mitigation | Verified |
|--------|------------|---------|
| T-2-01 (consent gate bypass) | BeginTx: consent_events inserted first, children.consent_event_id references it | PASS — INSERT order enforced in handler |
| T-2-03 (IP in consent_events) | No r.RemoteAddr read; consent INSERT column list exactly D-06 four fields | PASS — grep confirms |
| T-2-05 (SQL injection) | All SQL uses $N placeholders | PASS — grep confirms no fmt.Sprintf SQL |
| T-2-06 (unauth endpoint) | 401 returned when X-Clerk-User-Id/X-Clerk-Org-Id absent | PASS — TestPostV1Children_MissingAuthHeaders |

## Self-Check: PASSED

Files created/exist:
- api/openapi.yaml: FOUND
- backend/internal/api/generated.go: FOUND
- backend/internal/api/server.go: FOUND
- backend/cmd/server/main.go: FOUND
- backend/internal/api/problem.go: FOUND
- backend/internal/api/children_handler_test.go: FOUND

Commits exist:
- b8f6caf (Task 1 — API contract + DB wiring): FOUND
- eb7661d (TDD RED — failing tests): FOUND
- 13992ac (Task 2 / TDD GREEN — handler implementation): FOUND
