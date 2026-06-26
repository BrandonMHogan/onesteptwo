---
phase: 02-compliance-privacy-architecture
reviewed: 2026-06-26T00:00:00Z
depth: standard
files_reviewed: 16
files_reviewed_list:
  - api/openapi.yaml
  - backend/cmd/server/main.go
  - backend/db/migrations/00002_schema.sql
  - backend/go.mod
  - backend/go.sum
  - backend/internal/api/children_handler_test.go
  - backend/internal/api/erasure_handler_test.go
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
  critical: 2
  warning: 5
  info: 3
  total: 10
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-06-26
**Depth:** standard
**Files Reviewed:** 16
**Status:** issues_found

## Summary

This phase delivers the consent-gate schema, atomic child-creation and erasure handlers, and the client-side SQLDelight schema. The overall architecture is sound: the FK direction on `consent_event_id`, the hard-delete cascade order, and the audit trail are all correctly reasoned. Two BLOCKERS must be fixed before this code is considered shippable, and five WARNINGS represent compliance gaps and quality failures that will compound in later phases.

---

## Critical Issues

### CR-01: IDOR in `DeleteV1ChildrenId` — any user can delete any child

**File:** `backend/internal/api/server.go:146-241`

**Issue:** The handler only checks that `X-Clerk-User-Id` is a non-empty string. It never verifies that the child being deleted belongs to the requester's organization. The SELECT at line 173 is `WHERE id = $1` with no `clerk_org_id` filter. A caller with any non-empty user-ID header can delete any child profile in any org by supplying its UUID. The code acknowledges this at line 184 ("IDOR gap T-2-02") but ships without the guard.

Even under Phase 2 placeholder auth, the other mutating endpoints (`PostV1Children`, `DeleteV1Account`) require `X-Clerk-Org-Id` as well, so this endpoint is inconsistently less protected than its siblings. The gap is not bounded to a future phase; it is live in the current handler.

**Fix:** Add an org ownership check inside the transaction, immediately after the consent_event_id SELECT. If the child's `clerk_org_id` does not match the requester's `X-Clerk-Org-Id` header, return 404 (not 403 — do not confirm existence to unauthorized callers):

```go
// Existing SELECT: capture consent_event_id AND clerk_org_id in one query
var consentEventID, childOrgID string
err = tx.QueryRowContext(ctx,
    `SELECT consent_event_id, clerk_org_id FROM children WHERE id = $1`,
    childID,
).Scan(&consentEventID, &childOrgID)
if err == sql.ErrNoRows {
    WriteProblem(w, http.StatusNotFound, "about:blank", "Not Found", "child not found")
    return
}
if err != nil {
    WriteProblem(w, http.StatusInternalServerError, "about:blank", "Internal Server Error", "could not look up child")
    return
}

// Extract org from header (already required for PostV1Children / DeleteV1Account)
clerkOrgID := r.Header.Get("X-Clerk-Org-Id")
if clerkOrgID == "" || childOrgID != clerkOrgID {
    WriteProblem(w, http.StatusNotFound, "about:blank", "Not Found", "child not found")
    return
}
```

---

### CR-02: Default error handler returns `text/plain` with raw Go error text, violating REQ-NF-001

**File:** `backend/internal/api/generated.go:259-263`

**Issue:** `HandlerFromMux` in `main.go` does not supply a custom `ErrorHandlerFunc`. The generated fallback is:

```go
options.ErrorHandlerFunc = func(w http.ResponseWriter, r *http.Request, err error) {
    http.Error(w, err.Error(), http.StatusBadRequest)
}
```

`http.Error` sets `Content-Type: text/plain; charset=utf-8` and writes the raw Go error string to the response body. This fires whenever UUID path-parameter binding fails in `DeleteV1ChildrenId` (line 134-138, `InvalidParamFormatError`). The result is a `400` response with `Content-Type: text/plain` and a message such as `Invalid format for parameter id: ...`, exposing internal detail and violating the stated API contract (REQ-NF-001: "All Go handlers must use WriteProblem for error responses").

**Fix:** Supply a custom `ErrorHandlerFunc` when calling `HandlerFromMux` in `main.go`:

```go
// In main.go, replace:
api.HandlerFromMux(srv, mux)

// With:
api.HandlerWithOptions(srv, api.StdHTTPServerOptions{
    BaseRouter: mux,
    ErrorHandlerFunc: func(w http.ResponseWriter, r *http.Request, err error) {
        api.WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request", "invalid path parameter")
    },
})
```

---

## Warnings

### WR-01: `DeleteV1Account` only purges the requesting user's device tokens — incomplete GDPR/COPPA erasure

**File:** `backend/internal/api/server.go:330`

**Issue:** The account deletion handler deletes device tokens only for `clerkUserID` (the requester):

```go
r4, err := tx.ExecContext(ctx, `DELETE FROM device_tokens WHERE clerk_user_id = $1`, clerkUserID)
```

`docs/04-data-model.md` requires "Hard delete all `device_tokens` for all `clerk_user_id`s in the org" as step 2 of account closure. Co-caregivers who are also members of the deleted org retain device token rows indefinitely. The comment defers this to Phase 3 / Phase 8 but the compliance requirement is not gated on those phases — it is a requirement of the account deletion flow itself.

**Fix:** As a minimum, scope the device_token deletion to all users who have notification preferences for any child in the deleted org, using the already-scanned `children` slice. For the full fix in Phase 3, use the Clerk org member list. At minimum, add a comment in the response body acknowledging the partial erasure so operators are not misled by the `deleted_device_tokens` count.

---

### WR-02: `erasure_audit.target_id` is a throwaway random UUID for account deletions

**File:** `backend/internal/api/server.go:339-347`

**Issue:** The audit INSERT for account deletions uses `gen_random_uuid()` as `target_id`:

```go
`INSERT INTO erasure_audit (clerk_user_id, action, target_id, target_type, deleted_at)
 VALUES ($1, 'account_deletion', gen_random_uuid(), 'family', NOW())`,
```

The `target_id` column is the only way to identify *what* was deleted — but a random UUID links to nothing. If an auditor queries `erasure_audit` to verify a family's data was erased, `target_id` will be an unrecognizable UUID that cannot be mapped to any org or family. The comment acknowledges this ("TODO Phase 3: use the Clerk org UUID") but does not block on it.

**Fix:** Use `clerkOrgID` as `target_id` cast to a UUID, or store it separately. If `clerk_org_id` is not a UUID during Phase 2 testing, at minimum store it in a `target_label TEXT` column, or store the string representation in `target_id` after agreeing on a convention. A random value in this field makes the audit log useless for account deletions.

---

### WR-03: No database connectivity check at server startup

**File:** `backend/cmd/server/main.go:20-24`

**Issue:** `sql.Open("postgres", os.Getenv("DATABASE_URL"))` only validates the driver name and DSN string format — it does not establish a TCP connection. If the database is unreachable at startup, the process starts, logs `starting server on :8080`, and then fails every request with an internal server error. There is no early-fail signal and no way to distinguish "DB misconfigured" from "DB temporarily unavailable" in the startup logs.

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

### WR-04: Erasure response bodies use `map[string]any` with `int64` values instead of the generated `ErasureConfirmation` type

**File:** `backend/internal/api/server.go:232-238`, `358-366`

**Issue:** Both `DeleteV1ChildrenId` and `DeleteV1Account` write responses as `map[string]any`, ignoring the generated `ErasureConfirmation` struct. `RowsAffected()` returns `int64` (e.g., `eventsDeleted int64`), but the generated struct has `*int` fields. If the OpenAPI schema for `ErasureConfirmation` is updated (new fields, renamed fields), the actual response body will silently diverge — there is no compiler check. Additionally, `int64` vs `int` is not a wire-level problem in JSON, but the discrepancy creates confusion about which type owns the contract.

**Fix:** Use the generated type to ensure schema alignment:

```go
n1 := int(eventsDeleted)
n2 := int(prefsDeleted)
n3 := int(consentDeleted)
nChildren := 1
nTokens := 0
w.Header().Set("Content-Type", "application/json")
w.WriteHeader(http.StatusOK)
_ = json.NewEncoder(w).Encode(ErasureConfirmation{
    DeletedChildren:                &nChildren,
    DeletedEvents:                  &n1,
    DeletedConsentEvents:           &n3,
    DeletedNotificationPreferences: &n2,
    DeletedDeviceTokens:            &nTokens,
    RequestedBy:                    &clerkUserID,
    RequestedAt:                    func() *string { s := time.Now().UTC().Format(time.RFC3339); return &s }(),
})
```

---

### WR-05: `sync_status` has no CHECK constraint — any string value is accepted

**File:** `shared/src/commonMain/sqldelight/com/onesteptwo/db/PottyEvents.sq:18`  
**Also:** `shared/src/commonMain/sqldelight/migrations/2.sqm:45`

**Issue:** `sync_status TEXT NOT NULL DEFAULT 'pending'` without a CHECK constraint permits any string value to be stored (e.g., `'in_flight'`, `'error'`, `''`). SQLite supports CHECK constraints in this context. The offline sync layer will query `WHERE sync_status = 'pending'`, so any row written with a typo or unrecognized status will silently remain unsynchronized forever — no error, no feedback.

**Fix:** Add a CHECK constraint in both files:

```sql
sync_status TEXT NOT NULL DEFAULT 'pending' CHECK (sync_status IN ('pending', 'synced'))
```

The migration file (`2.sqm`) and the canonical `.sq` file must both be updated so `verifyMigrations` in `build.gradle.kts` keeps them in sync.

---

## Info

### IN-01: `birth_year` minimum bound is a hardcoded magic number

**File:** `backend/internal/api/server.go:70`

**Issue:** `req.BirthYear < 2000` uses a bare integer literal with no named constant or comment explaining the rationale. If the valid range ever changes, the value must be found by grep rather than by looking up a constant.

**Fix:**
```go
const minBirthYear = 2000 // app targets children born 2000 or later (toilet training context)
case req.BirthYear < minBirthYear || req.BirthYear > time.Now().Year()+1:
```

---

### IN-02: `requested_at` in response and `deleted_at` in audit are from different clocks

**File:** `backend/internal/api/server.go:211,239` (child deletion) and `341,365` (account deletion)

**Issue:** The audit row writes `deleted_at` using PostgreSQL's `NOW()` (evaluated when the INSERT executes inside the transaction), while the HTTP response uses Go's `time.Now()` (evaluated after `tx.Commit()` returns). The two timestamps will consistently differ by the round-trip time of the final commit call. If a client captures `requested_at` from the response and then queries the audit log for that timestamp, the values will not match.

**Fix:** Capture the commit time in Go before encoding the response, or `RETURNING deleted_at` from the audit INSERT and use that value in both the audit record and the response.

---

### IN-03: Client-side `children` table has no FK enforcement for `consent_event_id`

**File:** `shared/src/commonMain/sqldelight/com/onesteptwo/db/Children.sq:11`

**Issue:** `consent_event_id TEXT NOT NULL` has no `REFERENCES consent_events(id)` clause. SQLite does not enforce referential integrity by default, and even with `PRAGMA foreign_keys = ON`, the declaration is absent here. The client-side consent gate is application-layer only. An app bug or a direct SQLite write could create a `children` row with an invalid or missing `consent_event_id`. This differs from the server schema where the FK is the DB-level enforcement mechanism.

**Fix:** Add the REFERENCES clause and enable `PRAGMA foreign_keys = ON` in the SQLDelight driver setup:

```sql
consent_event_id TEXT NOT NULL REFERENCES consent_events(id),
```

Note this is a schema change requiring a new migration file (`3.sqm`).

---

_Reviewed: 2026-06-26_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
