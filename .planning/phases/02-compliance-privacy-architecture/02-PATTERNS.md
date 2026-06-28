# Phase 2: Compliance & Privacy Architecture - Pattern Map

**Mapped:** 2026-06-26
**Files analyzed:** 8 new/modified files
**Analogs found:** 7 / 8

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `backend/db/migrations/00002_schema.sql` | migration | batch | `backend/db/migrations/00001_init.sql` | role-match (Phase 1 is empty; pattern is structural) |
| `backend/internal/api/server.go` | service + controller | request-response | `backend/internal/api/server.go` (existing) | exact (modification, not new file) |
| `backend/cmd/server/main.go` | config | request-response | `backend/cmd/migrate/main.go` | role-match (DB init pattern) |
| `api/openapi.yaml` | config | request-response | `api/openapi.yaml` (existing) | exact (modification) |
| `backend/internal/api/generated.go` | generated | request-response | `backend/internal/api/generated.go` (existing) | exact (regenerated via `make generate` — do not edit) |
| `shared/src/commonMain/sqldelight/migrations/2.sqm` | migration | batch | `shared/src/commonMain/sqldelight/migrations/1.sqm` | role-match (Phase 1 is comment-only placeholder) |
| `backend/internal/api/children_handler_test.go` | test | request-response | `backend/internal/api/server_test.go` | exact |
| `backend/internal/api/account_handler_test.go` | test | request-response | `backend/internal/api/server_test.go` | exact |

---

## Pattern Assignments

### `backend/db/migrations/00002_schema.sql` (migration, batch)

**Analog:** `backend/db/migrations/00001_init.sql`

**Goose marker pattern** (lines 1-3 of 00001_init.sql):
```sql
-- +goose Up
-- Phase 1: empty init migration — schema tables added in Phase 2

-- +goose Down
-- no-op
```

**Key rules extracted from analog + goose conventions:**
- File must begin with `-- +goose Up` (no blank line before it)
- `-- +goose Down` section must exist; drop all objects in reverse FK-dependency order
- File naming: zero-padded 5-digit prefix → `00002_schema.sql`
- All DDL inside Up section; goose wraps in a transaction automatically for SQL migrations

**Full Up section order (FK-dependency order — consent_events before children, children before potty_events):**
```sql
-- +goose Up

CREATE TYPE event_type AS ENUM ('pee', 'poo', 'both', 'accident', 'tried');
CREATE TYPE device_platform AS ENUM ('android', 'ios');

CREATE TABLE consent_events ( ... );          -- no FK deps; first
CREATE TABLE children ( ... consent_event_id UUID NOT NULL REFERENCES consent_events(id) ... );
CREATE TABLE potty_events ( ... child_id UUID NOT NULL REFERENCES children(id) ... );
CREATE TABLE device_tokens ( ... );           -- no FK to children
CREATE TABLE notification_preferences ( ... child_id UUID NOT NULL REFERENCES children(id) ... );
CREATE TABLE erasure_audit ( ... );           -- no FK to any other table

-- +goose Down

DROP TABLE IF EXISTS erasure_audit;
DROP TABLE IF EXISTS notification_preferences;
DROP TABLE IF EXISTS device_tokens;
DROP TABLE IF EXISTS potty_events;
DROP TABLE IF EXISTS children;
DROP TABLE IF EXISTS consent_events;
DROP TYPE IF EXISTS device_platform;
DROP TYPE IF EXISTS event_type;
```

---

### `backend/internal/api/server.go` (service + controller, request-response)

**Analog:** `backend/internal/api/server.go` (existing file — this is a modification)

**Current file** (full content, lines 1-12):
```go
package api

import "net/http"

// Server implements the generated ServerInterface for all API handlers.
type Server struct{}

// GetHealthz satisfies the oapi-codegen ServerInterface for GET /healthz.
// Returns 200 OK with no body and requires no authentication (REQ-001).
func (s *Server) GetHealthz(w http.ResponseWriter, r *http.Request) {
    w.WriteHeader(http.StatusOK)
}
```

**Modifications required — Step 1: add DB field to struct:**
```go
import (
    "database/sql"
    "encoding/json"
    "net/http"
    "time"
)

type Server struct {
    DB *sql.DB
}
```

**Handler method signature pattern** (must match oapi-codegen generated interface — see generated.go line 17):
```go
// GetHealthz(w http.ResponseWriter, r *http.Request)              — no path params
// PostV1Children(w http.ResponseWriter, r *http.Request)          — no path params
// DeleteV1ChildrenId(w http.ResponseWriter, r *http.Request, id openapi_types.UUID) — path param
// DeleteV1Account(w http.ResponseWriter, r *http.Request)         — no path params
```

**Error response pattern** — uses `WriteProblem` from `backend/internal/api/problem.go` (line 20):
```go
WriteProblem(w, 401, "about:blank", "Unauthorized", "missing identity headers")
WriteProblem(w, 400, "about:blank", "Bad Request", "invalid JSON body")
WriteProblem(w, 404, "about:blank", "Not Found", "child not found")
WriteProblem(w, 500, "about:blank", "Internal Server Error", "could not begin transaction")
```

**Auth placeholder pattern** (Phase 2 only — replaced by JWT claims in Phase 3):
```go
// TODO Phase 3: replace with JWT claim extraction
clerkUserID := r.Header.Get("X-Clerk-User-Id")
clerkOrgID  := r.Header.Get("X-Clerk-Org-Id")
if clerkUserID == "" || clerkOrgID == "" {
    WriteProblem(w, 401, "about:blank", "Unauthorized", "missing identity headers")
    return
}
```

**Transaction pattern** (database/sql — standard for all three new handlers):
```go
tx, err := s.DB.BeginTx(r.Context(), nil)
if err != nil {
    WriteProblem(w, 500, "about:blank", "Internal Server Error", "could not begin transaction")
    return
}
defer tx.Rollback() // safe no-op after Commit

// ... tx.ExecContext / tx.QueryRowContext calls ...

if err = tx.Commit(); err != nil {
    WriteProblem(w, 500, "about:blank", "Internal Server Error", "could not commit transaction")
    return
}
```

**Successful JSON response pattern** (200/201):
```go
w.Header().Set("Content-Type", "application/json")
w.WriteHeader(201) // or 200
json.NewEncoder(w).Encode(map[string]any{ ... })
```

**D-10 erasure response shape** (both DELETE handlers):
```go
json.NewEncoder(w).Encode(map[string]any{
    "deleted_children":                 1,        // or N for account deletion
    "deleted_events":                   N,
    "deleted_consent_events":           N,
    "deleted_notification_preferences": N,
    "deleted_device_tokens":            N,
    "requested_by":                     clerkUserID,
    "requested_at":                     time.Now().UTC().Format(time.RFC3339),
})
```

**D-12 sweep-on-request audit purge** (inside tx, at the top of each DELETE handler):
```go
// D-12: sweep erasure_audit rows older than 90 days before writing new row
tx.ExecContext(ctx, `DELETE FROM erasure_audit WHERE deleted_at < NOW() - INTERVAL '90 days'`)
```

**FK-safe child erasure order** (CRITICAL — see RESEARCH.md Pitfall 1):
```go
// 1. Capture consent_event_id BEFORE any deletion
var consentEventID string
err = tx.QueryRowContext(ctx, `SELECT consent_event_id FROM children WHERE id = $1`, id).Scan(&consentEventID)

// 2. Delete dependents first
tx.ExecContext(ctx, `DELETE FROM potty_events WHERE child_id = $1`, id)
tx.ExecContext(ctx, `DELETE FROM notification_preferences WHERE child_id = $1`, id)

// 3. Delete children (releases FK reference)
tx.ExecContext(ctx, `DELETE FROM children WHERE id = $1`, id)

// 4. Delete consent_events (now safe — FK released)
tx.ExecContext(ctx, `DELETE FROM consent_events WHERE id = $1`, consentEventID)

// 5. Insert audit row
tx.ExecContext(ctx, `INSERT INTO erasure_audit (clerk_user_id, action, target_id, target_type, deleted_at)
    VALUES ($1, 'child_deletion', $2, 'child', NOW())`, clerkUserID, id)
```

---

### `backend/cmd/server/main.go` (config, request-response)

**Analog:** `backend/cmd/migrate/main.go` (lines 1-31) — this is the only existing file showing `sql.Open` pattern.

**Current server main.go** (full content, lines 1-25):
```go
package main

import (
    "log"
    "net/http"
    "os"

    "github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)

func main() {
    port := os.Getenv("PORT")
    if port == "" {
        port = "8080"
    }

    srv := &api.Server{}
    mux := http.NewServeMux()
    api.HandlerFromMux(srv, mux)

    log.Printf("starting server on :%s", port)
    if err := http.ListenAndServe(":"+port, mux); err != nil {
        log.Fatal(err)
    }
}
```

**DB init pattern from migrate/main.go** (lines 4-20):
```go
import (
    "database/sql"
    _ "github.com/lib/pq"
)

db, err := sql.Open("postgres", os.Getenv("DATABASE_URL"))
if err != nil {
    log.Fatal(err)
}
defer db.Close()
```

**Additions to server main.go** (insert before `srv := &api.Server{}`):
```go
import (
    "database/sql"
    _ "github.com/lib/pq"
)

db, err := sql.Open("postgres", os.Getenv("DATABASE_URL"))
if err != nil {
    log.Fatal(err)
}
defer db.Close()
db.SetMaxOpenConns(25)
db.SetMaxIdleConns(5)

srv := &api.Server{DB: db}   // pass DB to server
```

---

### `api/openapi.yaml` (config, request-response)

**Analog:** `api/openapi.yaml` (existing file — modification)

**Current structure** (full content, lines 1-13):
```yaml
openapi: "3.0.3"
info:
  title: OneStepTwo API
  version: "0.1.0"
paths:
  /healthz:
    get:
      operationId: getHealthz
      summary: Health check
      responses:
        "200":
          description: Service is healthy
```

**operationId → Go method name mapping** (oapi-codegen camelCase rule):
- `getHealthz` → `GetHealthz`
- `postV1Children` → `PostV1Children`
- `deleteV1ChildrenId` → `DeleteV1ChildrenId`
- `deleteV1Account` → `DeleteV1Account`

**New paths to add** (must be added BEFORE running `make generate`):
- `POST /v1/children` with `operationId: postV1Children`
- `DELETE /v1/children/{id}` with `operationId: deleteV1ChildrenId` and path param `id` (uuid format)
- `DELETE /v1/account` with `operationId: deleteV1Account`

**Component schemas to add** (referenced by new paths):
- `CreateChildRequest` — `{nickname, birth_month, birth_year, consent: {app_version, consent_text_version}}`
- `ChildResponse` — `{id, clerk_org_id, nickname, birth_month, birth_year}`
- `ErasureConfirmation` — `{deleted_children, deleted_events, deleted_consent_events, deleted_notification_preferences, deleted_device_tokens, requested_by, requested_at}`
- `ProblemDetail` — `{type, title, status, detail}` (matches `problem.go` ProblemDetail struct)

---

### `shared/src/commonMain/sqldelight/migrations/2.sqm` (migration, batch)

**Analog:** `shared/src/commonMain/sqldelight/migrations/1.sqm` (lines 1-7)

**Current 1.sqm** (full content):
```sql
-- shared/src/commonMain/sqldelight/migrations/1.sqm
-- Phase 1: placeholder migration. No schema tables yet.
-- Real schema (children, potty_events, etc.) is created in Phase 2.
-- This file must exist from day one per REQ-025.

-- SQLDelight .sqm files are plain SQL with no transaction wrappers.
-- Do NOT wrap in BEGIN/END TRANSACTION.
```

**Critical SQLite vs PostgreSQL type mapping:**
| PostgreSQL | SQLite (.sqm) |
|------------|---------------|
| `UUID` | `TEXT NOT NULL` |
| `TIMESTAMPTZ` | `TEXT NOT NULL` |
| `event_type` (ENUM) | `TEXT` (nullable) |
| `BOOLEAN` | `INTEGER` |
| `gen_random_uuid()` | no default — client provides UUID |

**2.sqm must NOT contain:**
- `BEGIN` / `END TRANSACTION` (crashes SQLite drivers — per 1.sqm comment)
- `CREATE TYPE` (no ENUM types in SQLite)
- `TIMESTAMPTZ`, `UUID`, `gen_random_uuid()`
- `REFERENCES` FK constraints (SQLite allows them but SQLDelight does not require them for the client schema)

**D-14 client-only column** (add to potty_events, absent from server schema):
```sql
sync_status TEXT NOT NULL DEFAULT 'pending'
```

**D-15 soft-delete columns** (mirror server schema for sync-down visibility):
```sql
deleted_at  TEXT,
deleted_by  TEXT
```

---

### `backend/internal/api/children_handler_test.go` (test, request-response)

**Analog:** `backend/internal/api/server_test.go` (lines 1-62)

**Test package and imports pattern** (lines 1-10):
```go
package api_test

import (
    "encoding/json"
    "net/http"
    "net/http/httptest"
    "testing"

    "github.com/BrandonMHogan/onesteptwo/backend/internal/api"
)
```

**httptest request-response pattern** (lines 12-27):
```go
srv := &api.Server{}         // NOTE: Phase 2 tests use nil DB or mock DB
mux := http.NewServeMux()
api.HandlerFromMux(srv, mux)

req := httptest.NewRequest(http.MethodPost, "/v1/children", body)
req.Header.Set("Content-Type", "application/json")
rec := httptest.NewRecorder()
mux.ServeHTTP(rec, req)

if rec.Code != http.StatusCreated {
    t.Errorf("expected 201, got %d", rec.Code)
}
```

**ProblemDetail decoding pattern** (lines 40-61):
```go
var detail struct {
    Type   string `json:"type"`
    Title  string `json:"title"`
    Status int    `json:"status"`
    Detail string `json:"detail"`
}
if err := json.NewDecoder(rec.Body).Decode(&detail); err != nil {
    t.Fatalf("failed to decode response body: %v", err)
}
```

**Test cases to cover** (per RESEARCH.md Validation Architecture):
- `TestPostV1Children_MissingBody` → expect 400 with `application/problem+json`
- `TestPostV1Children_MissingConsentFields` → expect 400
- `TestPostV1Children_MissingAuthHeaders` → expect 401
- `TestDeleteV1ChildrenId_MissingAuthHeader` → expect 401
- `TestDeleteV1Account_MissingAuthHeader` → expect 401

**Note on DB in tests:** Phase 2 unit tests use `httptest` only. Tests that exercise handler routing / auth header validation do not need a real DB. Tests can initialize `&api.Server{}` with a nil DB for cases where the handler returns before touching the DB (auth and input validation paths). Do not set up a real PostgreSQL connection in unit tests.

---

### `backend/internal/api/account_handler_test.go` (test, request-response)

**Analog:** `backend/internal/api/server_test.go` — same pattern as children_handler_test.go above.

Same package (`api_test`), same import block, same httptest pattern. Test method: `DELETE /v1/account`.

---

## Shared Patterns

### Error Responses
**Source:** `backend/internal/api/problem.go` lines 20-29
**Apply to:** All three new handler methods (`PostV1Children`, `DeleteV1ChildrenId`, `DeleteV1Account`)
```go
// Always call WriteProblem — never write error JSON manually
WriteProblem(w, status, "about:blank", "Human Title", "developer detail — never pass err.Error()")
```

### Auth Header Extraction (Phase 2 Placeholder)
**Source:** Derived pattern — no existing analog (Phase 3 adds Clerk middleware)
**Apply to:** All three new handler methods
```go
// TODO Phase 3: replace with JWT claim extraction
clerkUserID := r.Header.Get("X-Clerk-User-Id")
clerkOrgID  := r.Header.Get("X-Clerk-Org-Id")
```
Header name uses canonical Go form (`X-Clerk-User-Id`). Go's `http.Header.Get` canonicalizes automatically, but use canonical form for clarity.

### Database/SQL Transaction Wrapper
**Source:** `backend/cmd/migrate/main.go` lines 18-20 (sql.Open pattern) + Go stdlib database/sql docs
**Apply to:** All three new handler methods
```go
tx, err := s.DB.BeginTx(r.Context(), nil)
if err != nil { WriteProblem(...); return }
defer tx.Rollback()
// ... work ...
if err = tx.Commit(); err != nil { WriteProblem(...); return }
```
Never mix `db.ExecContext()` inside a live `tx` — use `tx.ExecContext()` throughout.

### No IP Address in Consent Records
**Source:** D-06, REQ-C-009
**Apply to:** `PostV1Children` handler
The handler must NOT read `r.RemoteAddr`, `r.Header.Get("X-Forwarded-For")`, or any IP field and write it to `consent_events`. This is a hard GDPR/COPPA constraint. No analog for a pattern to copy — it is a negative constraint: omit.

### Parameterized SQL Queries
**Source:** All new handlers
**Apply to:** Every SQL statement in every new handler
```go
// CORRECT — parameterized
tx.ExecContext(ctx, `DELETE FROM potty_events WHERE child_id = $1`, id)

// NEVER DO — string interpolation
tx.ExecContext(ctx, fmt.Sprintf(`DELETE FROM potty_events WHERE child_id = '%s'`, id))
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `docs/04-data-model.md` (update) | documentation | — | No doc update analog exists; update is a content edit not a code pattern |

---

## Critical Implementation Notes for Planner

1. **Server struct must be modified FIRST** — before any handler is written, `type Server struct { DB *sql.DB }` must be in place or the handlers will not compile.

2. **openapi.yaml must be updated BEFORE `make generate`** — new handlers only exist after regeneration of `generated.go`. The `ServerInterface` will gain `PostV1Children`, `DeleteV1ChildrenId`, `DeleteV1Account` methods only after regeneration.

3. **FK-safe deletion order is non-negotiable** — RESEARCH.md Pitfall 1 documents that D-08 in CONTEXT.md lists the wrong order. The correct order (children deleted BEFORE consent_events) is captured in the Pattern Assignments above.

4. **Account deletion and Clerk API** — RESEARCH.md Open Question 3 notes that deleting `device_tokens` for all org members requires knowing which `clerk_user_id`s are in the org. The `device_tokens` table has no `clerk_org_id` column. The planner must decide: (a) call Clerk API to get org member list inside handler (requires Clerk SDK in Phase 2), or (b) note in plan that `device_token` deletion is a Phase 3 concern. Flag this as an open item in the plan.

5. **`2.sqm` numbering** — SQLDelight `verifyMigrations = true` means gaps in numbering break the build. `1.sqm` exists; `2.sqm` must be the next file. Do not create `3.sqm` or skip to `2.sqm` with a gap.

---

## Metadata

**Analog search scope:** `backend/internal/api/`, `backend/cmd/`, `backend/db/migrations/`, `api/`, `shared/src/commonMain/sqldelight/migrations/`
**Files scanned:** 9
**Pattern extraction date:** 2026-06-26
