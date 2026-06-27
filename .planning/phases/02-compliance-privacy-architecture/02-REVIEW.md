---
phase: 02-compliance-privacy-architecture
reviewed: 2026-06-26T00:00:00Z
depth: standard
files_reviewed: 17
files_reviewed_list:
  - api/openapi.yaml
  - backend/cmd/server/main.go
  - backend/db/migrations/00002_schema.sql
  - backend/internal/api/children_handler_test.go
  - backend/internal/api/clerk.go
  - backend/internal/api/erasure_handler_test.go
  - backend/internal/api/error_handler.go
  - backend/internal/api/error_handler_test.go
  - backend/internal/api/generated.go
  - backend/internal/api/problem.go
  - backend/internal/api/server.go
  - docs/04-data-model.md
  - shared/build.gradle.kts
  - shared/src/commonMain/sqldelight/com/onesteptwo/db/Children.sq
  - shared/src/commonMain/sqldelight/com/onesteptwo/db/ConsentEvents.sq
  - shared/src/commonMain/sqldelight/com/onesteptwo/db/PottyEvents.sq
  - shared/src/commonMain/sqldelight/migrations/2.sqm
findings:
  critical: 3
  warning: 11
  info: 5
  total: 19
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-06-26
**Depth:** standard
**Files Reviewed:** 17
**Status:** issues_found

## Summary

This phase delivered the consent-gate schema, atomic child-creation handler, child and account erasure endpoints, an RFC 7807 error layer, a Clerk API client, and a parallel SQLDelight client-side schema. Several issues flagged in the prior REVIEW.md have been resolved by subsequent gap-closure commits (device-token org-wide sweep now uses `pq.Array(memberIDs)`; the account-deletion audit `target_id` now uses a deterministic UUIDv5 of the org ID). This review reflects the current code state.

Three blockers remain: an IDOR in the child-deletion endpoint, placeholder authentication with no cryptographic verification, and a silent incomplete-erasure path that returns 200 OK when Clerk PII is not deleted. Eleven warnings and five informational items are documented below.

---

## Structural Findings (fallow)

No structural pre-pass (`<structural_findings>`) was provided for this phase.

---

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: IDOR — `DeleteV1ChildrenId` verifies identity but not ownership

**File:** `backend/internal/api/server.go:150-188`

**Issue:** The handler checks only that `X-Clerk-User-Id` is non-empty. It never reads `X-Clerk-Org-Id` from the request and never verifies that the target child's `clerk_org_id` matches the requester's organization. The SELECT at line 176 is `WHERE id = $1` with no org filter. Any authenticated caller who knows a child UUID can delete that child's entire data set — potty events, consent record, and notification preferences — belonging to an unrelated family.

The gap is compounded by the header-extraction omission: `clerkOrgID` is not declared anywhere in `DeleteV1ChildrenId`'s scope. Even the Phase 3 TODO at line 188 ("IDOR gap T-2-02") cannot be implemented without also adding the header extraction — it is not a one-line change.

This endpoint is also inconsistently protected relative to its siblings: `PostV1Children` and `DeleteV1Account` both require `X-Clerk-Org-Id` in addition to `X-Clerk-User-Id`.

**Fix:** Extract the org header and add an ownership check inside the transaction, immediately after the consent_event_id lookup:

```go
clerkUserID := r.Header.Get("X-Clerk-User-Id")
clerkOrgID  := r.Header.Get("X-Clerk-Org-Id")
if clerkUserID == "" || clerkOrgID == "" {
    WriteProblem(w, http.StatusUnauthorized, "about:blank", "Unauthorized", "missing identity headers")
    return
}

// ...inside tx, fetch both consent_event_id and clerk_org_id in one round-trip:
var consentEventID, owningOrgID string
err = tx.QueryRowContext(ctx,
    `SELECT consent_event_id, clerk_org_id FROM children WHERE id = $1`,
    childID,
).Scan(&consentEventID, &owningOrgID)
if err == sql.ErrNoRows {
    WriteProblem(w, http.StatusNotFound, "about:blank", "Not Found", "child not found")
    return
}
if err != nil {
    WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not look up child")
    return
}
// Treat ownership failure as 404 — do not confirm existence to unauthorized callers.
if owningOrgID != clerkOrgID {
    WriteProblem(w, http.StatusNotFound, "about:blank", "Not Found", "child not found")
    return
}
```

---

### CR-02: Header-based authentication is trivially forgeable; no JWT verification

**File:** `backend/internal/api/server.go:49, 152, 257`

**Issue:** All three write handlers extract caller identity exclusively from `X-Clerk-User-Id` and `X-Clerk-Org-Id` HTTP headers that any HTTP client can set to arbitrary values. There is no cryptographic proof of identity: no JWT signature verification, no Clerk webhook signature check, no mTLS. If the server TCP port is reachable by any caller other than a Clerk-controlled reverse proxy that injects these headers after verifying a session token, all authorization is bypassable with a single `curl -H "X-Clerk-User-Id: victim_user"`.

The TODO comments ("Phase 3: replace with JWT claim extraction") acknowledge this is a placeholder, but as shipped the code has no auth gate beyond header presence. Combined with CR-01, an unauthenticated attacker who can reach the server port can delete any data in the system.

**Fix:** For the current phase, document the required network topology as a deployment invariant (server port must be unreachable except through the authenticated Clerk proxy) and add a startup assertion or deny-by-default middleware. For Phase 3, verify Clerk session JWTs using Clerk's Go SDK or by fetching Clerk's JWKS endpoint and validating the `sub` and `org_id` claims from the token.

---

### CR-03: Clerk org deletion failure silently returns 200 OK while parent PII remains

**File:** `backend/internal/api/server.go:382-384`

**Issue:** After the database transaction commits, `s.Clerk.DeleteOrganization` failure is only logged server-side. The API response is still `200 OK` with a fully-populated `ErasureConfirmation` payload:

```go
if err := s.Clerk.DeleteOrganization(ctx, clerkOrgID); err != nil {
    log.Printf("clerk org deletion failed for %s: %v", clerkOrgID, err)
}
// ... response written with 200 OK
```

The Clerk organization is the only location storing parent and caregiver PII (name, email, phone). If this call fails:

1. The user receives a confirmation that erasure is complete — it is not.
2. The `erasure_audit` row (already committed) records the deletion as having occurred with no failure flag.
3. There is no retry mechanism, no user notification, and no way for a compliance auditor to detect the gap.

Under GDPR Article 17 (and analogous COPPA obligations), a right-to-erasure response that claims success while PII persists in a third-party system is a compliance failure. The response and audit trail actively mislead any subsequent audit.

**Fix:** At minimum, add a `clerk_org_deleted` boolean to the response and write a second audit row when Clerk deletion fails. A robust fix enqueues a retry and does not return the success response until the Clerk call either succeeds or the failure is recorded in a durable dead-letter store:

```go
clerkDeleted := true
if err := s.Clerk.DeleteOrganization(ctx, clerkOrgID); err != nil {
    log.Printf("clerk org deletion failed for %s: %v", clerkOrgID, err)
    clerkDeleted = false
    // TODO: insert failure row into a pending_clerk_deletions table for retry
}
// Pass clerkDeleted through to the ErasureConfirmation response body
```

---

## Warnings

### WR-01: Generated fallback `ErrorHandlerFunc` writes `err.Error()` as `text/plain`, violating REQ-NF-001

**File:** `backend/internal/api/generated.go:259-262`

**Issue:** `HandlerWithOptions` installs a fallback when `ErrorHandlerFunc` is nil:

```go
options.ErrorHandlerFunc = func(w http.ResponseWriter, r *http.Request, err error) {
    http.Error(w, err.Error(), http.StatusBadRequest)
}
```

`http.Error` sets `Content-Type: text/plain; charset=utf-8` and writes the raw Go error string. This violates REQ-NF-001 ("All Go handlers must use WriteProblem for error responses") and the T-2-12 information-disclosure control. `main.go` correctly avoids the fallback by using `HandlerWithOptions` with `api.ProblemErrorHandler` (line 31). However, all tests in `children_handler_test.go` and `erasure_handler_test.go` use `HandlerFromMux` without a custom `ErrorHandlerFunc`. If a path-binding error is ever triggered through those test entry points (e.g., a test added for `DeleteV1ChildrenId` with a malformed ID), the fallback fires silently and the test would see `text/plain` instead of `application/problem+json`, masking a REQ-NF-001 violation.

**Fix:** Update all test call sites that use `HandlerFromMux` to `HandlerWithOptions` with `api.ProblemErrorHandler`, matching the production setup:

```go
api.HandlerWithOptions(srv, api.StdHTTPServerOptions{
    BaseRouter:       mux,
    ErrorHandlerFunc: api.ProblemErrorHandler,
})
```

---

### WR-02: DB connection not verified at startup; server starts with unreachable database

**File:** `backend/cmd/server/main.go:20-21`

**Issue:** `sql.Open("postgres", ...)` validates the driver name and DSN format only — it does not establish a TCP connection. No `db.Ping()` follows. The process starts, logs `"starting server on :8080"`, and accepts requests even when the database is completely unreachable. The first erasure or child-creation request fails with a 500 that would have been caught at startup.

**Fix:**
```go
db, err := sql.Open("postgres", os.Getenv("DATABASE_URL"))
if err != nil {
    log.Fatal(err)
}
if err := db.PingContext(context.Background()); err != nil {
    log.Fatalf("database unreachable: %v", err)
}
```

---

### WR-03: No graceful shutdown; in-flight erasure transactions aborted on SIGTERM

**File:** `backend/cmd/server/main.go:34`

**Issue:** `http.ListenAndServe` does not support context-based cancellation or request draining. SIGTERM aborts the process immediately, rolling back any open database transactions. The erasure handlers span multiple SQL operations plus an external Clerk API call. An abrupt shutdown mid-erasure can leave the `erasure_audit` row unwritten with no record of the attempted deletion, or leave the Clerk call (which happens post-commit) in an unknown state.

**Fix:** Use `http.Server` with `Shutdown` and signal handling:

```go
httpSrv := &http.Server{Addr: ":" + port, Handler: mux}
go func() {
    sigCh := make(chan os.Signal, 1)
    signal.Notify(sigCh, syscall.SIGTERM, syscall.SIGINT)
    <-sigCh
    ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
    defer cancel()
    _ = httpSrv.Shutdown(ctx)
}()
if err := httpSrv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
    log.Fatal(err)
}
```

---

### WR-04: Erasure-audit purge errors silently discarded; old audit rows can accumulate past 90 days

**File:** `backend/internal/api/server.go:169, 285`

**Issue:** Both handlers use:

```go
_, _ = tx.ExecContext(ctx, `DELETE FROM erasure_audit WHERE deleted_at < NOW() - INTERVAL '90 days'`)
```

Both the `sql.Result` and the error are discarded. If the purge fails for any reason (lock contention, permission regression, transient error), the failure goes undetected: no log, no metric, no counter. REQ-C-008 requires that audit data not persist beyond 90 days. A silent purge failure is a self-concealing compliance regression.

**Fix:** Log the error at minimum:

```go
if _, err := tx.ExecContext(ctx, `DELETE FROM erasure_audit WHERE deleted_at < NOW() - INTERVAL '90 days'`); err != nil {
    log.Printf("erasure_audit purge failed: %v", err)
}
```

---

### WR-05: Clerk member list hard-capped at 100 with no truncation detection

**File:** `backend/internal/api/clerk.go:58`

**Issue:** The memberships endpoint URL is built with `?limit=100` and there is no check on whether the response contained exactly 100 results (a signal of truncation). The comment acknowledges pagination may be required. If an org ever exceeds 100 members, the device-token DELETE in `DeleteV1Account` will miss members beyond the first page, leaving their tokens in the database after an erasure request. For a potty-training family app this threshold is very high, but the silent truncation is still a data-retention risk with no runtime warning.

**Fix:** After decoding, detect and reject truncated responses until pagination is implemented:

```go
if len(body.Data) == 100 {
    return nil, fmt.Errorf("clerk: org %q has 100+ members; pagination required for complete erasure", orgID)
}
```

---

### WR-06: No length validation on `consent.app_version` or `consent.consent_text_version`

**File:** `backend/internal/api/server.go:77-83`

**Issue:** Both consent fields are validated to be non-empty but have no upper-bound length check before insertion into the `consent_events` table (`TEXT` columns with no `VARCHAR(n)` limit in the schema). An attacker with any valid auth session can submit arbitrarily large strings for these fields, bloating the database. The OpenAPI spec declares them as plain `string` with no `maxLength` constraint, so generated clients receive no guidance either.

**Fix:** Add length guards and add `maxLength` to `openapi.yaml`:

```go
case len(req.Consent.AppVersion) > 50:
    WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "consent.app_version exceeds maximum length")
    return
case len(req.Consent.ConsentTextVersion) > 50:
    WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "consent.consent_text_version exceeds maximum length")
    return
```

---

### WR-07: `createChildRequest` in `server.go` duplicates the generated `CreateChildRequest` struct

**File:** `backend/internal/api/server.go:30-38`

**Issue:** `server.go` defines a private `createChildRequest` struct (lines 30-38) that is field-for-field identical to the generated `CreateChildRequest` in `generated.go` (lines 26-34). The handler decodes into the private copy rather than the generated type. Additionally, the 201 response at line 136-143 uses `map[string]any` instead of the generated `ChildResponse`. If the OpenAPI schema changes, `oapi-codegen` updates the generated types automatically, but the handwritten struct and map diverge silently — no compile error, no test failure.

**Fix:** Remove `createChildRequest` from `server.go`. Decode directly into `*PostV1ChildrenJSONRequestBody` (the generated alias for `CreateChildRequest`). Build the 201 body using the generated `ChildResponse` struct.

---

### WR-08: OpenAPI response schemas have no `required` arrays; all fields are implicitly optional

**File:** `api/openapi.yaml:99-130`

**Issue:** `ChildResponse` (lines 99-111) and `ErasureConfirmation` (lines 113-130) list properties but include no `required` array. Per the JSON Schema specification, all undeclared properties default to optional. KMP mobile clients cannot assume that `id`, `deleted_children`, `requested_at`, or any other field will be present in the response — all fields must be defensively null-checked. This is a contract bug that leaks internal implementation details (whether the server omits a field vs returns null) into the client.

**Fix:** Add `required` arrays matching the fields the server always populates:

```yaml
ChildResponse:
  type: object
  required: [id, clerk_org_id, nickname, birth_month, birth_year]
  properties: ...

ErasureConfirmation:
  type: object
  required:
    - deleted_children
    - deleted_events
    - deleted_consent_events
    - deleted_notification_preferences
    - deleted_device_tokens
    - requested_by
    - requested_at
  properties: ...
```

---

### WR-09: Magic number `2000` as `birth_year` lower bound with no documented policy

**File:** `backend/internal/api/server.go:74`

**Issue:** `req.BirthYear < 2000` uses a bare literal with no named constant or comment linking the value to any product or compliance policy. The number silently rejects any child born before 2000 (who would be 25+ in 2025 — unlikely for potty training, but possible for children with special needs). If the rationale is a maximum age window, the bound should be derived from `time.Now().Year() - maxAgeYears` so the window advances automatically each calendar year rather than hardcoding a fixed year that grows staler over time.

**Fix:**
```go
const maxChildAgeYears = 10 // potty training context; adjust if app scope expands
minBirthYear := time.Now().Year() - maxChildAgeYears
if req.BirthYear < minBirthYear || req.BirthYear > time.Now().Year()+1 {
    WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "birth_year is out of valid range")
    return
}
```

---

### WR-10: `CLERK_SECRET_KEY` not validated at startup; server starts silently without Clerk access

**File:** `backend/cmd/server/main.go:29`

**Issue:** `api.NewClerkClient(os.Getenv("CLERK_SECRET_KEY"))` accepts an empty string without error. Both Clerk methods guard against an empty key at call time (lines 54-56 and 101-103 of `clerk.go`), but they return an error at request time rather than at startup. For `DeleteV1Account`, a missing key means every account-erasure request fails with 500 before touching the database. This misconfiguration is invisible until the first deletion is attempted in production.

**Fix:**
```go
clerkKey := os.Getenv("CLERK_SECRET_KEY")
if clerkKey == "" {
    log.Fatal("CLERK_SECRET_KEY environment variable is not set")
}
srv.Clerk = api.NewClerkClient(clerkKey)
```

---

### WR-11: `sync_status` column has no CHECK constraint; any string value is accepted

**File:** `shared/src/commonMain/sqldelight/com/onesteptwo/db/PottyEvents.sq:18`
**Also:** `shared/src/commonMain/sqldelight/migrations/2.sqm:45`

**Issue:** `sync_status TEXT NOT NULL DEFAULT 'pending'` has no CHECK constraint. Valid values are documented as `'pending'` and `'synced'` (D-14), but SQLite will accept any string. The offline sync layer queries `WHERE sync_status = 'pending'`; a row written with a typo (e.g., `'Pending'`, `'syncing'`) or an unrecognized status will silently never be synced — no error, no feedback, and the row stays stuck in the pending queue indefinitely.

**Fix:** Add a CHECK constraint in both the `.sq` file and the migration file so `verifyMigrations` keeps them in sync:

```sql
sync_status TEXT NOT NULL DEFAULT 'pending' CHECK (sync_status IN ('pending', 'synced'))
```

---

## Info

### IN-01: OpenAPI spec missing `403 Forbidden` for `DELETE /v1/children/{id}`

**File:** `api/openapi.yaml:38-60`

**Issue:** The spec documents 200, 401, and 404 for this endpoint. When Phase 3 adds ownership verification to close the IDOR gap (T-2-02, CR-01 above), a 403 response is needed for requests targeting another family's child. Without this response code in the spec now, code generators and mobile error-handling layers will be written without a 403 branch, requiring a second breaking spec change later.

**Fix:** Add `"403": {description: Forbidden}` to the endpoint's response list now, before client code is generated against this spec version.

---

### IN-02: `erasure_audit.action` and `target_type` use unconstrained `TEXT`

**File:** `backend/db/migrations/00002_schema.sql:75-82`

**Issue:** Valid values are documented in comments (`'child_deletion' | 'account_deletion'` and `'child' | 'family'`) but both columns are `TEXT NOT NULL` with no `CHECK` constraint. Invalid values from future code changes can be inserted silently. Compliance queries filtering `WHERE action = 'account_deletion'` will miss rows written with typos.

**Fix:**
```sql
action      TEXT NOT NULL CHECK (action IN ('child_deletion', 'account_deletion')),
target_type TEXT NOT NULL CHECK (target_type IN ('child', 'family')),
```

---

### IN-03: `device_tokens.token` has no UNIQUE constraint

**File:** `backend/db/migrations/00002_schema.sql:52-59`

**Issue:** A network retry before the server responds to a token registration can produce two rows for the same FCM token. No constraint prevents this. Both rows would be deleted on erasure, but during normal operation the user could receive duplicate push notifications (one delivery per duplicate row). A `UNIQUE(clerk_user_id, token)` constraint would make token registration idempotent on the server side, consistent with the client-generated UUID idempotency pattern used for potty events.

**Fix:** Add `UNIQUE(clerk_user_id, token)` to the table definition.

---

### IN-04: Client SQLite schema has no foreign key relationships

**File:** `shared/src/commonMain/sqldelight/com/onesteptwo/db/Children.sq:11`
**Also:** `shared/src/commonMain/sqldelight/com/onesteptwo/db/PottyEvents.sq:9`

**Issue:** `children.consent_event_id TEXT NOT NULL` and `potty_events.child_id TEXT NOT NULL` have no `REFERENCES` clauses. SQLite FK enforcement is off by default and requires both explicit FK column definitions and `PRAGMA foreign_keys = ON` at connection open time. The client can insert potty events for non-existent child IDs, or children rows with non-existent consent_event IDs, producing local state that is invalid under the server schema. This contrasts with the server schema where FKs are the primary enforcement mechanism for the consent gate (D-05).

**Fix:** Add REFERENCES clauses to both `.sq` files and the `2.sqm` migration, and ensure the SQLDelight driver wrapper enables `PRAGMA foreign_keys = ON` when opening the database connection. This requires a new `3.sqm` migration.

---

### IN-05: SQLDelight `.sq` files contain only schema — no typed query methods are generated

**File:** `shared/src/commonMain/sqldelight/com/onesteptwo/db/Children.sq`; `ConsentEvents.sq`; `PottyEvents.sq`

**Issue:** All three `.sq` files contain only `CREATE TABLE` statements. SQLDelight generates typed Kotlin extension functions from named SQL statements in `.sq` files. Without any named queries, the generated code provides no type-safe access functions for the app to read or write these tables. If the mobile app uses these tables, it must do so through raw driver calls that bypass SQLDelight's compile-time SQL verification and type safety.

**Fix:** Add named queries appropriate to Phase 2 use cases. At minimum, an insert and a select for each table. If these are intentionally schema-only for this phase, add a comment stating the queries will be added in Phase 3.

---

_Reviewed: 2026-06-26_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
