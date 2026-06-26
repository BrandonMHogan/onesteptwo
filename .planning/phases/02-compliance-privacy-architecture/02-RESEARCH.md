# Phase 2: Compliance & Privacy Architecture - Research

**Researched:** 2026-06-26
**Domain:** PostgreSQL schema migration, Go database/sql transactions, COPPA/GDPR consent gate, SQLDelight KMP migrations
**Confidence:** MEDIUM

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Event Type Enum**
- D-01: The canonical event type list is `pee, poo, both, accident, tried` (5 values). Supersedes docs/04-data-model.md and REQ-030 lists.
- D-02: `event_type` stored as PostgreSQL native ENUM `CREATE TYPE event_type AS ENUM ('pee', 'poo', 'both', 'accident', 'tried')`. Column is nullable — NULL means quick-tap. ALTER TYPE ... ADD VALUE is the safe way to add values later.

**Consent Gate**
- D-03: Consent is per child, not per account.
- D-04: Self-attestation checkbox is the legally acceptable consent mechanism for v1. Legal counsel must confirm before Phase 9 store submission.
- D-05: **FK direction flip from docs/04-data-model.md.** `consent_events` has NO `child_id`. Instead, `children` gains `consent_event_id UUID NOT NULL REFERENCES consent_events(id)`. DB cannot create a children row without referencing an existing consent_events row.
- D-06: `consent_events` schema: `{id UUID PK, clerk_user_id TEXT NOT NULL, consented_at TIMESTAMPTZ NOT NULL, app_version TEXT NOT NULL, consent_text_version TEXT NOT NULL}`. No child_id. No IP address.
- D-07: `POST /v1/children` is a single atomic transaction. Request payload: `{nickname, birth_month, birth_year, consent: {app_version, consent_text_version}}`. Handler: BEGIN TX → INSERT consent_events → INSERT children with consent_event_id → COMMIT.

**Erasure Cascade**
- D-08: Hard delete order for child-profile deletion: potty_events → notification_preferences → consent_events → children. **See Critical Pitfall below — FK constraint requires children deleted BEFORE consent_events.**
- D-09: Hard delete order for full-account deletion: trigger child-profile deletion for each child, then DELETE device_tokens for all clerk_user_ids, then DELETE Clerk Organization.
- D-10: Deletion endpoints return structured JSON: `{deleted_children, deleted_events, deleted_consent_events, deleted_notification_preferences, deleted_device_tokens, requested_by, requested_at}`.

**Erasure Audit Log**
- D-11: `erasure_audit` schema: `{id UUID PK, clerk_user_id TEXT NOT NULL, action TEXT NOT NULL, target_id UUID NOT NULL, target_type TEXT NOT NULL, deleted_at TIMESTAMPTZ NOT NULL}`.
- D-12: 90-day purge is sweep-on-request: every deletion call begins with `DELETE FROM erasure_audit WHERE deleted_at < NOW() - INTERVAL '90 days'` inside the same transaction.

**SQLDelight Client Schema**
- D-13: `2.sqm` creates three tables: `children`, `potty_events`, `consent_events`. No notification_preferences, device_tokens, or erasure_audit on client.
- D-14: Client-side `potty_events` adds `sync_status TEXT NOT NULL DEFAULT 'pending'` (local-only, not on server).
- D-15: Client-side `potty_events` retains `deleted_at` and `deleted_by` for soft-delete visibility during sync.

### Claude's Discretion
- Exact goose migration file numbering (e.g., `00002_schema.sql`)
- Choice of `pgx/v5` vs `database/sql` for Go database access
- Index selection on `potty_events(child_id, occurred_at)` and `children(clerk_org_id)`
- Whether to use a single migration file or multiple (one per table)

### Deferred Ideas (OUT OF SCOPE)
- Stool consistency tracking
- Separate accident subtypes (accident_pee / accident_poo)
- consent_texts table in DB
- Row-count detail in erasure response beyond D-10 fields
- pg_cron / Railway cron for audit purge
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REQ-008 | children table stores only: id, clerk_org_id, nickname, birth_month, birth_year, created_at, updated_at — no PII | D-05 adds consent_event_id as required FK; success criterion conflict noted below |
| REQ-009 | Parental consent recorded before child profile created; self-attestation checkbox | D-04 locked; atomic transaction in D-07 enforces ordering |
| REQ-010 | consent_events row inserted before children row; fields: clerk_user_id, child_id, consented_at, app_version; no IP | D-05/D-06 supersede; note no child_id in consent_events per D-05 flip |
| REQ-011 | Deletion cascade — child profile: hard DELETE potty_events, notification_preferences, consent_events, children | FK-safe deletion order in Architecture Patterns section |
| REQ-012 | Deletion cascade — account: child-profile deletion for all children, DELETE device_tokens, DELETE Clerk Org | D-09 pattern |
| REQ-013 | Verified deletion endpoints for both child-profile and account deletion — not optional | Endpoints in openapi.yaml; Go handlers on Server struct |
| REQ-014 | Erasure right: clearly labelled, hard DELETE, confirmation response, audit log purged after 90 days | D-10 response shape; D-11/D-12 audit pattern |
| REQ-NF-003 | No automatic data expiry — user-initiated deletion only | No cron or TTL; sweep-on-request per D-12 is bounded to audit table only |
| REQ-C-001 | COPPA: parental consent before collecting child data; self-attestation minimum | Enforced at DB level (FK) and application level (atomic handler) |
| REQ-C-002 | GDPR: right to erasure, data minimisation, right to portability (v2), lawful basis | Erasure via hard DELETE cascade; portability deferred to v2 |
| REQ-C-003 | PIPEDA / Law 25: consent and erasure obligations | Satisfied by same consent gate and erasure cascade |
| REQ-C-004 | POPIA: consent and erasure framework | Satisfied by same mechanism |
| REQ-C-005 | Parent/caregiver PII lives in Clerk only; DB stores only clerk_user_id and clerk_org_id as FKs | Enforced in schema — no name/email/phone columns anywhere |
| REQ-C-008 | Erasure audit log: who, when, what; purged after 90 days | erasure_audit table + sweep-on-request (D-12) |
| REQ-C-009 | No IP address in consent_events | D-06 schema has no IP column; Go handler must not write one |
</phase_requirements>

---

## Summary

Phase 2 is a schema-and-API phase with no UI deliverables. The work splits into four areas: (1) a goose SQL migration that creates all six server-side tables with correct types, constraints, and indexes; (2) three Go HTTP handler implementations (`POST /v1/children`, `DELETE /v1/children/{id}`, `DELETE /v1/account`) added to the existing `Server{}` struct after updating `api/openapi.yaml` and re-running `make generate`; (3) a SQLDelight `2.sqm` migration with SQLite-compatible CREATE TABLE statements for the three client-side tables; (4) updating docs/04-data-model.md to reflect D-01 (event_type values) and D-05 (FK direction flip).

The most important architectural fact in this phase: the FK direction (D-05) inverts the deletion order from what docs/04-data-model.md shows. With `children.consent_event_id REFERENCES consent_events(id)`, the children row must be hard-deleted before the consent_events row or PostgreSQL will throw a foreign key constraint violation. This contradicts the literal wording of D-08 in CONTEXT.md and must be resolved correctly in the plan.

The backend currently has no DB connection in `cmd/server/main.go` and no DB field on the `Server{}` struct — both must be added in this phase before any of the three new handlers can work.

**Primary recommendation:** Implement in a single goose migration file (`00002_schema.sql`) with all six tables in FK-dependency order, add `*sql.DB` to the Server struct, update openapi.yaml for three new endpoints, implement handlers using `database/sql` transactions, add placeholder auth header extraction clearly marked for Phase 3 replacement, and write `2.sqm` with SQLite types.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Consent gate enforcement | Database | API / Backend | FK constraint (D-05) is the primary gate; Go handler is the application-layer gate (D-07) |
| Consent recording | API / Backend | Database | Handler writes the consent_events row; DB schema enforces NOT NULL |
| Erasure cascade | API / Backend | Database | Handler executes explicit DELETEs in FK-safe order; no DB-level cascade triggers used |
| Audit log | API / Backend | Database | Handler writes erasure_audit row and purges stale rows; DB holds the table |
| Client schema | Mobile (KMP shared) | — | SQLDelight 2.sqm creates SQLite tables for offline-first; server schema is independent |
| Auth claim extraction | API / Backend | — | clerk_user_id / clerk_org_id come from JWT (Phase 3); Phase 2 uses placeholder headers |
| Data minimisation enforcement | Database | — | Column constraints (no extra columns in children); documented in REQ-008 |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `github.com/lib/pq` | v1.12.3 | PostgreSQL driver for database/sql | Already in go.mod; works with existing migrate/main.go pattern |
| `database/sql` (stdlib) | Go 1.25.5 | Database access, transaction management | Already used in cmd/migrate/main.go; consistent with project pattern |
| `github.com/pressly/goose/v3` | v3.27.1 | SQL migration runner | Already in go.mod; existing 00001_init.sql pattern to follow |
| `app.cash.sqldelight` | 2.3.2 | Client-side SQLite schema + migrations | Already configured with verifyMigrations = true in shared/build.gradle.kts |
| `github.com/oapi-codegen/oapi-codegen/v2` | v2.7.1 | OpenAPI → Go server interface generator | Already generating generated.go; make generate command in Makefile |

### Claude's Discretion: pgx/v5 vs database/sql

The project already uses `database/sql + lib/pq`. The Discretion allows switching to `pgx/v5`. Research finding: pgx/v5 is faster and gives access to PostgreSQL-specific features (LISTEN/NOTIFY, COPY, better array types). However:

- Switching requires updating go.mod, all import paths, and the `sql.Open("postgres", ...)` call
- Phase 2 does not use any pgx-specific features (no LISTEN/NOTIFY, no COPY, no complex array types)
- The migration runner already uses `lib/pq` + `database/sql`
- Mixing drivers in the same binary is possible but confusing

**Recommendation:** Stay with `database/sql + lib/pq` for Phase 2. If pgx/v5 is wanted, migrate the whole backend in a dedicated refactor, not mid-phase.

**Installation (no new packages needed — already in go.mod):**
```bash
# No new go packages required for Phase 2 backend
# SQLDelight already configured in shared/build.gradle.kts
```

---

## Package Legitimacy Audit

The Go ecosystem is not supported by the package-legitimacy seam (npm/pypi/crates only). All Go packages used in Phase 2 are verified by their presence in `backend/go.mod` and `shared/build.gradle.kts`. No new external packages are introduced.

| Package | Source | Presence | Disposition |
|---------|--------|----------|-------------|
| `github.com/lib/pq` v1.12.3 | go.mod | Confirmed in go.mod | Approved — existing dependency |
| `github.com/pressly/goose/v3` v3.27.1 | go.mod | Confirmed in go.mod | Approved — existing dependency |
| `github.com/oapi-codegen/oapi-codegen/v2` v2.7.1 | Makefile + generated.go | Confirmed via make generate output | Approved — existing toolchain |
| `app.cash.sqldelight` 2.3.2 | libs.versions.toml | Confirmed in version catalog | Approved — existing dependency |

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious:** none

---

## Architecture Patterns

### System Architecture Diagram

```
POST /v1/children
  ┌──────────────┐      ┌──────────────────────────┐
  │  Go Handler  │─TX──▶│  INSERT consent_events   │
  │  (Server{})  │      │  (get returned UUID)     │
  └──────────────┘      └──────────────────────────┘
         │                          │ consent_event_id
         │              ┌──────────────────────────┐
         │              │  INSERT children         │
         │              │  (FK → consent_events)   │
         │              └──────────────────────────┘
         └─────────────── COMMIT ──────────────────

DELETE /v1/children/{id}  (FK-safe order)
  ┌──────────────┐
  │  Go Handler  │─TX─▶ PURGE erasure_audit (>90d)
  │              │    ▶ SELECT consent_event_id FROM children
  │              │    ▶ DELETE potty_events WHERE child_id
  │              │    ▶ DELETE notification_preferences WHERE child_id
  │              │    ▶ DELETE children WHERE id        ← FK released here
  │              │    ▶ DELETE consent_events WHERE id  ← now safe
  │              │    ▶ INSERT erasure_audit
  └──────────────┘    ▶ COMMIT → return ErasureConfirmation JSON

DELETE /v1/account
  ┌──────────────┐
  │  Go Handler  │─TX─▶ [child deletion loop per child in org]
  │              │    ▶ DELETE device_tokens WHERE clerk_user_id IN (org members)
  │              │    ▶ INSERT erasure_audit (action='account_deletion')
  │              │    ▶ COMMIT → return ErasureConfirmation JSON
  │              │    ▶ [Clerk API call to delete Organization — outside DB TX]
  └──────────────┘
```

### Recommended Project Structure

No new top-level directories needed. Changes are within existing directories:

```
backend/
├── cmd/server/main.go          ← add sql.Open + pass *sql.DB to Server
├── internal/api/
│   ├── server.go               ← add DB *sql.DB field; new handler methods
│   ├── generated.go            ← regenerated by make generate (do not edit)
│   └── problem.go              ← unchanged; use WriteProblem for errors
├── db/migrations/
│   └── 00002_schema.sql        ← all 6 server-side tables in FK-dependency order
api/
└── openapi.yaml                ← add 3 new endpoints before running make generate
shared/src/commonMain/sqldelight/
└── migrations/
    └── 2.sqm                   ← SQLite CREATE TABLE for children, potty_events, consent_events
docs/
└── 04-data-model.md            ← update event_type values (D-01) and FK direction (D-05)
```

### Pattern 1: Goose Migration File Structure

**What:** Single SQL file with `-- +goose Up` / `-- +goose Down` markers. All tables in FK-dependency order. [ASSUMED — goose official docs pattern; confirmed via pressly/goose README and migration example in codebase]

**When to use:** Any schema change. Single file preferred (Claude's Discretion) to keep the upgrade atomic.

```sql
-- Source: pressly/goose README + existing 00001_init.sql pattern [ASSUMED]
-- +goose Up

-- ENUM types first (referenced by tables)
CREATE TYPE event_type AS ENUM ('pee', 'poo', 'both', 'accident', 'tried');
CREATE TYPE device_platform AS ENUM ('android', 'ios');

-- consent_events first: children FK references it
CREATE TABLE consent_events (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_user_id    TEXT        NOT NULL,
    consented_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    app_version      TEXT        NOT NULL,
    consent_text_version TEXT    NOT NULL
);

-- children: FK to consent_events (D-05 — this is the consent gate)
CREATE TABLE children (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_org_id     TEXT        NOT NULL,
    nickname         TEXT        NOT NULL,
    birth_month      INTEGER     NOT NULL CHECK (birth_month BETWEEN 1 AND 12),
    birth_year       INTEGER     NOT NULL,
    consent_event_id UUID        NOT NULL REFERENCES consent_events(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_children_clerk_org_id ON children(clerk_org_id);

-- potty_events: FK to children; id is client-generated (NOT DEFAULT)
CREATE TABLE potty_events (
    id           UUID        PRIMARY KEY,  -- client-generated; no DEFAULT
    child_id     UUID        NOT NULL REFERENCES children(id),
    logged_by    TEXT        NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by   TEXT,
    event_type   event_type,               -- nullable; NULL = quick-tap
    notes        TEXT,
    deleted_at   TIMESTAMPTZ,
    deleted_by   TEXT
);
CREATE INDEX idx_potty_events_child_occurred ON potty_events(child_id, occurred_at);

-- device_tokens: no FK to children; FK-independent
CREATE TABLE device_tokens (
    id             UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_user_id  TEXT            NOT NULL,
    token          TEXT            NOT NULL,
    platform       device_platform NOT NULL,
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_seen_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- notification_preferences: FK to children
CREATE TABLE notification_preferences (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_user_id  TEXT        NOT NULL,
    child_id       UUID        NOT NULL REFERENCES children(id),
    enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(clerk_user_id, child_id)
);

-- erasure_audit: no FK to any other table (must survive after data deletion)
CREATE TABLE erasure_audit (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_user_id  TEXT        NOT NULL,
    action         TEXT        NOT NULL,   -- 'child_deletion' | 'account_deletion'
    target_id      UUID        NOT NULL,
    target_type    TEXT        NOT NULL,   -- 'child' | 'family'
    deleted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- +goose Down

-- Drop tables in reverse FK-dependency order
DROP TABLE IF EXISTS erasure_audit;
DROP TABLE IF EXISTS notification_preferences;
DROP TABLE IF EXISTS potty_events;
DROP TABLE IF EXISTS device_tokens;
DROP TABLE IF EXISTS children;
DROP TABLE IF EXISTS consent_events;

-- Drop types after all tables dropped
DROP TYPE IF EXISTS device_platform;
DROP TYPE IF EXISTS event_type;
```

**Critical note on `children` column count:** REQ-008 lists 7 columns (id, clerk_org_id, nickname, birth_month, birth_year, created_at, updated_at). D-05 adds `consent_event_id` as a required FK. Success criterion #3 lists 7 columns with "no additional columns". This is a conflict: D-05 (locked implementation decision) requires the 8th column `consent_event_id`, which is the mechanism that enforces the consent gate. Success criterion #3's intent is to prohibit PII columns (gender, photo, legal name) — not to prohibit required FKs. **Proceed with 8 columns including consent_event_id per D-05.**

### Pattern 2: Server Struct Upgrade

**What:** Add `*sql.DB` to the Server struct. Initialize DB in main.go and pass it in. [ASSUMED — standard Go pattern]

```go
// Source: Go standard library database/sql documentation [ASSUMED]
// backend/internal/api/server.go
package api

import (
    "database/sql"
    "net/http"
)

type Server struct {
    DB *sql.DB
}

// All existing methods (GetHealthz) still compile — no breaking change.
```

```go
// backend/cmd/server/main.go — additions
import (
    "database/sql"
    _ "github.com/lib/pq"
)

db, err := sql.Open("postgres", os.Getenv("DATABASE_URL"))
if err != nil {
    log.Fatal(err)
}
defer db.Close()

// Set connection pool limits (production hygiene)
db.SetMaxOpenConns(25)
db.SetMaxIdleConns(5)

srv := &api.Server{DB: db}
```

### Pattern 3: Atomic Consent-Gate Handler (POST /v1/children)

**What:** Single TX: INSERT consent_events → capture returned id → INSERT children → COMMIT. [ASSUMED — database/sql transaction pattern from official Go docs]

```go
// Source: go.dev/doc/database/execute-transactions [ASSUMED]
// backend/internal/api/server.go

func (s *Server) PostV1Children(w http.ResponseWriter, r *http.Request) {
    // TODO Phase 3: replace with JWT claim extraction
    clerkUserID := r.Header.Get("X-Clerk-User-Id")
    clerkOrgID  := r.Header.Get("X-Clerk-Org-Id")
    if clerkUserID == "" || clerkOrgID == "" {
        WriteProblem(w, 401, "about:blank", "Unauthorized", "missing identity headers")
        return
    }

    var req struct {
        Nickname   string `json:"nickname"`
        BirthMonth int    `json:"birth_month"`
        BirthYear  int    `json:"birth_year"`
        Consent struct {
            AppVersion          string `json:"app_version"`
            ConsentTextVersion  string `json:"consent_text_version"`
        } `json:"consent"`
    }
    if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
        WriteProblem(w, 400, "about:blank", "Bad Request", "invalid JSON body")
        return
    }

    tx, err := s.DB.BeginTx(r.Context(), nil)
    if err != nil {
        WriteProblem(w, 500, "about:blank", "Internal Server Error", "could not begin transaction")
        return
    }
    defer tx.Rollback()

    var consentID string
    err = tx.QueryRowContext(r.Context(),
        `INSERT INTO consent_events (clerk_user_id, consented_at, app_version, consent_text_version)
         VALUES ($1, NOW(), $2, $3) RETURNING id`,
        clerkUserID, req.Consent.AppVersion, req.Consent.ConsentTextVersion,
    ).Scan(&consentID)
    if err != nil {
        WriteProblem(w, 500, "about:blank", "Internal Server Error", "could not record consent")
        return
    }

    var childID string
    err = tx.QueryRowContext(r.Context(),
        `INSERT INTO children (clerk_org_id, nickname, birth_month, birth_year, consent_event_id)
         VALUES ($1, $2, $3, $4, $5) RETURNING id`,
        clerkOrgID, req.Nickname, req.BirthMonth, req.BirthYear, consentID,
    ).Scan(&childID)
    if err != nil {
        WriteProblem(w, 500, "about:blank", "Internal Server Error", "could not create child profile")
        return
    }

    if err = tx.Commit(); err != nil {
        WriteProblem(w, 500, "about:blank", "Internal Server Error", "could not commit transaction")
        return
    }

    w.Header().Set("Content-Type", "application/json")
    w.WriteHeader(201)
    json.NewEncoder(w).Encode(map[string]any{
        "id":         childID,
        "clerk_org_id": clerkOrgID,
        "nickname":   req.Nickname,
        "birth_month": req.BirthMonth,
        "birth_year":  req.BirthYear,
    })
}
```

### Pattern 4: FK-Safe Erasure Cascade (DELETE /v1/children/{id})

**What:** Get consent_event_id FIRST, delete in FK-safe order, insert audit row, return D-10 JSON. [ASSUMED — derived from FK constraint analysis of D-05 schema]

```go
// Source: derived from FK constraint analysis [ASSUMED]
func (s *Server) DeleteV1ChildrenId(w http.ResponseWriter, r *http.Request, id string) {
    // TODO Phase 3: replace with JWT claim extraction
    clerkUserID := r.Header.Get("X-Clerk-User-Id")
    if clerkUserID == "" {
        WriteProblem(w, 401, "about:blank", "Unauthorized", "missing identity header")
        return
    }

    ctx := r.Context()
    tx, err := s.DB.BeginTx(ctx, nil)
    if err != nil {
        WriteProblem(w, 500, "about:blank", "Internal Server Error", "could not begin transaction")
        return
    }
    defer tx.Rollback()

    // D-12: purge audit rows older than 90 days (sweep-on-request)
    tx.ExecContext(ctx, `DELETE FROM erasure_audit WHERE deleted_at < NOW() - INTERVAL '90 days'`)

    // Must capture consent_event_id BEFORE deleting children row
    var consentEventID string
    err = tx.QueryRowContext(ctx,
        `SELECT consent_event_id FROM children WHERE id = $1`, id,
    ).Scan(&consentEventID)
    if err == sql.ErrNoRows {
        WriteProblem(w, 404, "about:blank", "Not Found", "child not found")
        return
    }
    if err != nil {
        WriteProblem(w, 500, "about:blank", "Internal Server Error", "could not look up child")
        return
    }

    // FK-safe deletion order (D-05 FK: children → consent_events)
    r1, _ := tx.ExecContext(ctx, `DELETE FROM potty_events WHERE child_id = $1`, id)
    r2, _ := tx.ExecContext(ctx, `DELETE FROM notification_preferences WHERE child_id = $1`, id)
    tx.ExecContext(ctx, `DELETE FROM children WHERE id = $1`, id)          // FK released
    r3, _ := tx.ExecContext(ctx, `DELETE FROM consent_events WHERE id = $1`, consentEventID) // now safe

    // Insert audit row
    tx.ExecContext(ctx,
        `INSERT INTO erasure_audit (clerk_user_id, action, target_id, target_type, deleted_at)
         VALUES ($1, 'child_deletion', $2, 'child', NOW())`,
        clerkUserID, id,
    )

    if err = tx.Commit(); err != nil {
        WriteProblem(w, 500, "about:blank", "Internal Server Error", "could not commit deletion")
        return
    }

    eventsDeleted, _ := r1.RowsAffected()
    prefsDeleted, _  := r2.RowsAffected()
    consentDeleted, _ := r3.RowsAffected()

    w.Header().Set("Content-Type", "application/json")
    w.WriteHeader(200)
    json.NewEncoder(w).Encode(map[string]any{
        "deleted_children":               1,
        "deleted_events":                 eventsDeleted,
        "deleted_consent_events":         consentDeleted,
        "deleted_notification_preferences": prefsDeleted,
        "deleted_device_tokens":          0, // device tokens only on account deletion
        "requested_by":                   clerkUserID,
        "requested_at":                   time.Now().UTC().Format(time.RFC3339),
    })
}
```

### Pattern 5: SQLDelight 2.sqm (SQLite — not PostgreSQL)

**What:** SQLite-compatible CREATE TABLE statements. No ENUM types (use TEXT). No TIMESTAMPTZ (use TEXT). No DEFAULT gen_random_uuid() (SQLite has no UUID function). [CITED: sqldelight.github.io/sqldelight/2.0.2/multiplatform_sqlite/migrations/]

```sql
-- shared/src/commonMain/sqldelight/migrations/2.sqm
-- Phase 2: client-side schema for children, potty_events, consent_events
-- DO NOT wrap in BEGIN/END TRANSACTION (crashes some SQLite drivers)

-- Source: SQLDelight 2.0 migration docs [CITED: sqldelight.github.io/sqldelight/2.0.2]

CREATE TABLE consent_events (
    id                   TEXT NOT NULL PRIMARY KEY,
    clerk_user_id        TEXT NOT NULL,
    consented_at         TEXT NOT NULL,
    app_version          TEXT NOT NULL,
    consent_text_version TEXT NOT NULL
);

CREATE TABLE children (
    id               TEXT    NOT NULL PRIMARY KEY,
    clerk_org_id     TEXT    NOT NULL,
    nickname         TEXT    NOT NULL,
    birth_month      INTEGER NOT NULL,
    birth_year       INTEGER NOT NULL,
    consent_event_id TEXT    NOT NULL,
    created_at       TEXT    NOT NULL,
    updated_at       TEXT    NOT NULL
);

-- D-14: sync_status is client-only (not on server schema)
-- D-15: deleted_at and deleted_by retained for sync-down visibility
CREATE TABLE potty_events (
    id          TEXT    NOT NULL PRIMARY KEY,
    child_id    TEXT    NOT NULL,
    logged_by   TEXT    NOT NULL,
    occurred_at TEXT    NOT NULL,
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL,
    updated_by  TEXT,
    event_type  TEXT,
    notes       TEXT,
    deleted_at  TEXT,
    deleted_by  TEXT,
    sync_status TEXT    NOT NULL DEFAULT 'pending'
);
```

### Pattern 6: OpenAPI Spec Additions

**What:** Three new path entries in `api/openapi.yaml`. Must be added BEFORE running `make generate`. [ASSUMED — oapi-codegen v2 std-http-server generates method per operationId]

```yaml
# Additions to api/openapi.yaml paths section
  /v1/children:
    post:
      operationId: postV1Children
      summary: Create child profile (consent gate — atomic)
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateChildRequest'
      responses:
        "201":
          description: Child profile created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ChildResponse'
        "400":
          description: Validation error
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
        "401":
          description: Unauthorized

  /v1/children/{id}:
    delete:
      operationId: deleteV1ChildrenId
      summary: Delete child profile (GDPR/COPPA erasure)
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        "200":
          description: Erasure confirmation
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErasureConfirmation'
        "401":
          description: Unauthorized
        "404":
          description: Child not found

  /v1/account:
    delete:
      operationId: deleteV1Account
      summary: Delete family account (GDPR/COPPA erasure — full cascade)
      responses:
        "200":
          description: Erasure confirmation
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErasureConfirmation'
        "401":
          description: Unauthorized
```

### Anti-Patterns to Avoid

- **DB-level ON DELETE CASCADE for erasure:** Using PostgreSQL CASCADE on the FK constraint would auto-delete children when consent_events is deleted — the wrong direction and loses control over deletion order and count tracking.
- **Soft-delete for erasure requests:** Hard DELETE is required by GDPR/COPPA. Soft deletes (`deleted_at`) are only for caregiver-initiated event cleanup (REQ-007), not for erasure requests.
- **Calling `db.ExecContext()` inside a transaction:** Must use `tx.ExecContext()` throughout — mixing `db.Exec()` calls with a live `tx` runs those queries outside the transaction scope.
- **Committing before error checking:** Always check each `tx.ExecContext` error in the erasure cascade before proceeding to avoid partially-deleted state.
- **Writing IP address to consent_events:** The Go handler must not log or write any IP from `r.RemoteAddr` or `X-Forwarded-For` headers to the consent record (REQ-C-009).
- **Using goose Down in production without testing:** goose Down drops all tables. Only use for local development rollback. Never run Down on staging or production.
- **BEGIN/END TRANSACTION in 2.sqm:** SQLDelight handles transactions; wrapping in BEGIN/END causes a crash with some SQLite drivers (confirmed in official docs).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SQL migration versioning | Custom version table | goose | goose manages version tracking, Up/Down, transactional runs; already in go.mod |
| DB transaction boilerplate | Custom transaction wrapper | `database/sql` BeginTx + defer Rollback | Standard pattern; Rollback after Commit is a safe no-op |
| OpenAPI route wiring | Manual http.HandleFunc calls | oapi-codegen + `make generate` | Regeneration keeps routes in sync with spec; path param extraction is auto-generated |
| UUIDs on server | Custom UUID generation | `gen_random_uuid()` PostgreSQL built-in | Available since PostgreSQL 13 with no extension; Railway uses modern PostgreSQL |
| UUID on client | UUIDs in SQLDelight | Client-generated in Kotlin (`java.util.UUID.randomUUID()` or KMP equivalent) | Client-generated IDs enable idempotent retries via `ON CONFLICT (id) DO NOTHING` |
| SQLite type coercion | Manual casting layer | SQLite stores TEXT/INTEGER natively | SQLDelight generates typed Kotlin from the schema — no runtime casting needed |

**Key insight:** In this phase, the hardest problem is the ordering constraint at erasure time (FK direction means children must be deleted before consent_events). Don't try to solve this with DB triggers or cascades — explicit DELETE statements in the handler give you count tracking for the D-10 response and make the compliance story easy to audit.

---

## Common Pitfalls

### Pitfall 1: FK Violation in Erasure Cascade (Critical)

**What goes wrong:** DELETE FROM consent_events WHERE id = $x fails with "update or delete on table violates foreign key constraint" because the children row still has `consent_event_id = $x`.

**Why it happens:** D-08 in CONTEXT.md lists the deletion order as potty_events → notification_preferences → **consent_events → children**. This is reversed from what the FK allows. `children.consent_event_id REFERENCES consent_events(id)` means children holds the FK reference — PostgreSQL refuses to delete a consent_events row that is still referenced.

**How to avoid:** The correct FK-safe order is:
1. SELECT consent_event_id FROM children (save it before deleting)
2. DELETE potty_events WHERE child_id
3. DELETE notification_preferences WHERE child_id
4. DELETE children WHERE id (releases the FK reference)
5. DELETE consent_events WHERE id = $saved_consent_event_id (now safe)

**Warning signs:** Any attempt to test deletion end-to-end against a real DB will immediately surface this as a PostgreSQL error.

### Pitfall 2: Deletion After Transaction Commit in Account Deletion

**What goes wrong:** The Clerk Organization deletion is called inside the DB transaction. If the Clerk API call fails, the DB transaction has already committed — data is deleted but the Clerk Org still exists. Or if the Clerk API is called before commit and fails, the DB data is gone but no cleanup happened.

**Why it happens:** Mixing external API calls with DB transactions.

**How to avoid:** Commit the DB transaction first, then call the Clerk API to delete the organization. If the Clerk call fails after DB commit, the family data is gone from our DB (compliant with erasure) but the Clerk Org lingers — this is acceptable since Clerk holds only PII we don't own. Document this in the handler with a comment.

### Pitfall 3: Non-Unique potty_events.id on Retry

**What goes wrong:** The server INSERT for potty_events uses `DEFAULT gen_random_uuid()` instead of the client-provided id, causing duplicate rows on retry.

**Why it happens:** Developer forgets that potty_events uses client-generated UUIDs (REQ-006).

**How to avoid:** The INSERT for potty_events must be `INSERT INTO potty_events (id, child_id, ...) VALUES ($1, $2, ...)` with `ON CONFLICT (id) DO NOTHING`. The `id` column in the schema must have NO DEFAULT.

### Pitfall 4: Wrong SQL Syntax in 2.sqm

**What goes wrong:** `verifyMigrations = true` in shared/build.gradle.kts means the Android/KMP build will fail if 2.sqm contains invalid SQLite syntax. PostgreSQL syntax (TIMESTAMPTZ, ENUM types, gen_random_uuid()) is not valid SQLite.

**Why it happens:** Developer copies PostgreSQL schema into the SQLDelight migration file.

**How to avoid:** SQLite uses TEXT for UUIDs and timestamps, INTEGER for booleans, and TEXT for enums. No ENUM type creation. No gen_random_uuid() — UUIDs are inserted as TEXT strings generated by Kotlin code.

### Pitfall 5: Missing DB Field on Server Struct

**What goes wrong:** `Server{}` struct has no DB field, so new handler methods cannot access the database. This compiles fine until the handler method tries to use `s.DB`.

**Why it happens:** The existing codebase has an empty `Server{}` struct.

**How to avoid:** Update `type Server struct { DB *sql.DB }` in server.go as the FIRST task, before writing any handler code. Update `cmd/server/main.go` to pass the initialized DB.

### Pitfall 6: goose Down Drops All Data

**What goes wrong:** Running `make migrate-down` on staging drops all tables, destroying test data.

**Why it happens:** The `-- +goose Down` section is designed for development rollback, not production use.

**How to avoid:** In the staging-first workflow, run `make migrate-up` to apply 00002_schema.sql, never run migrate-down on staging/production. Use Down only in local development.

### Pitfall 7: Auth Header Names Are Case-Sensitive in Go

**What goes wrong:** `r.Header.Get("x-clerk-user-id")` works (Go canonicalizes), but the placeholder implementation in Phase 2 needs clear naming since Phase 3 replaces these with JWT claims.

**Why it happens:** Placeholder headers are removed when Phase 3 adds Clerk middleware.

**How to avoid:** Use canonical form `r.Header.Get("X-Clerk-User-Id")` and add `// TODO Phase 3: replace with claims.Subject` comments so Phase 3 executor knows exactly what to replace.

---

## Code Examples

### Goose migration file naming

The existing migration is `00001_init.sql`. The next migration should be `00002_schema.sql` to follow the established zero-padded naming convention. [ASSUMED — derived from existing file naming]

### PostgreSQL gen_random_uuid()

No extension required since PostgreSQL 13. Railway's managed PostgreSQL is on version 15+. [ASSUMED — Railway uses modern PostgreSQL; gen_random_uuid confirmed built-in since PG13]

```sql
-- Correct: built-in since PG13
id UUID PRIMARY KEY DEFAULT gen_random_uuid()

-- Incorrect: requires pgcrypto extension (not needed on modern PG)
-- id UUID PRIMARY KEY DEFAULT pgcrypto.gen_random_uuid()
```

### oapi-codegen method signature for path parameters (std-http-server)

After updating openapi.yaml with `deleteV1ChildrenId` and running `make generate`, the generated ServerInterface will contain: [ASSUMED — oapi-codegen v2 std-http with Go 1.22 pattern]

```go
// In generated.go (DO NOT EDIT):
type ServerInterface interface {
    GetHealthz(w http.ResponseWriter, r *http.Request)
    PostV1Children(w http.ResponseWriter, r *http.Request)
    DeleteV1ChildrenId(w http.ResponseWriter, r *http.Request, id openapi_types.UUID)
    DeleteV1Account(w http.ResponseWriter, r *http.Request)
}
```

The wrapper uses `r.PathValue("id")` (Go 1.22 net/http) internally and passes the parsed UUID to the handler. The server.go implementation must match this exact signature.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `pgcrypto.gen_random_uuid()` | `gen_random_uuid()` built-in | PostgreSQL 13 (2020) | No extension needed in migrations |
| goose v2 (pressly) | goose v3 (pressly) | 2022 | v3 has programmatic API; project uses v3.27.1 |
| oapi-codegen (deepmap fork) | oapi-codegen (oapi-codegen org) | 2023 | Package moved to github.com/oapi-codegen/oapi-codegen; Makefile uses correct new path |
| Go HTTP with third-party mux | Go 1.22 net/http pattern routing | Go 1.22 (2024) | `http.MethodGet + " " + "/path/{id}"` works in stdlib; oapi-codegen std-http leverages this |

**Deprecated/outdated:**
- `github.com/clerkinc/clerk-sdk-go` (v1): EOL April 2025. Project correctly uses v2 only (REQ-NF-010, LOCKED).
- `uuid_generate_v4()`: Requires `uuid-ossp` extension. Superseded by `gen_random_uuid()` built-in.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | goose migration file naming should be `00002_schema.sql` (zero-padded 5 digits) | Standard Stack / Project Structure | Minor — goose requires only sequential numbering, exact format flexible |
| A2 | `gen_random_uuid()` requires no extension on Railway's PostgreSQL | Code Examples | Low — Railway uses PG15+; built-in since PG13 |
| A3 | oapi-codegen generates `DeleteV1ChildrenId` as the method name from `deleteV1ChildrenId` operationId | Code Examples | Medium — method name is derived from operationId; wrong operationId = wrong method name; verify after `make generate` |
| A4 | `database/sql + lib/pq` is sufficient for Phase 2 (no pgx-specific features needed) | Standard Stack | Low — only risk is performance; pgx/v5 can be adopted later |
| A5 | The placeholder auth approach (X-Clerk-User-Id header) is acceptable for Phase 2 local/staging testing | Code Examples | Medium — if endpoints accidentally deployed without Phase 3 auth they are insecure; must be documented |
| A6 | SQLDelight `2.sqm` goes in `shared/src/commonMain/sqldelight/migrations/` (same dir as 1.sqm) | Project Structure | Low — confirmed by 1.sqm path in codebase |
| A7 | Railway's PostgreSQL supports `TIMESTAMPTZ` and `CHECK` constraints as used in the migration | Architecture Patterns | Very low — standard PostgreSQL; Railway uses unmodified PostgreSQL |
| A8 | The `openapi_types.UUID` type is available with the current oapi-codegen v2 + std-http-server configuration | Code Examples | Low — oapi-codegen imports openapi-go/types for UUID format; may need `import "github.com/oapi-codegen/runtime/types"` in server.go |

---

## Open Questions

1. **Success criterion #3 vs D-05 conflict**
   - What we know: Success criterion #3 says "The children table migration contains only id, clerk_org_id, nickname, birth_month, birth_year, created_at, updated_at — no additional columns." D-05 says children gains `consent_event_id`.
   - What's unclear: Was consent_event_id intended to be excluded from the "additional columns" check, or was this criterion written before D-05 was finalized?
   - Recommendation: Treat success criterion #3 as referring to PII columns (no gender, no IP, no legal name). Proceed with consent_event_id per D-05. The planner should include a note that the verification agent should check for ABSENCE of PII columns, not enforce exact column count.

2. **Auth placeholder strategy for Phase 2 endpoints**
   - What we know: Phase 3 adds Clerk JWT middleware. Phase 2 handlers need clerk_user_id and clerk_org_id from somewhere.
   - What's unclear: Is X-header placeholder acceptable, or should the endpoints simply return 503 until Phase 3?
   - Recommendation: Use placeholder X-Clerk-User-Id / X-Clerk-Org-Id headers with prominent TODO comments. This allows integration testing in Phase 2 without requiring Phase 3 to be complete first.

3. **Account deletion scope of device_tokens**
   - What we know: D-09 says hard DELETE device_tokens for all clerk_user_ids "in the org". The device_tokens table has no clerk_org_id column — it only has clerk_user_id.
   - What's unclear: How does the handler know which clerk_user_ids belong to the org without calling the Clerk API? The DB alone cannot answer "who is a member of org X".
   - Recommendation: The DELETE /v1/account handler needs to call the Clerk API to get org member list before deleting device_tokens. This requires the Clerk SDK to be available in Phase 2 handlers. If Clerk SDK is not yet wired in Phase 2, device_token deletion can be a Phase 3 concern (since Phase 3 wires auth and the Clerk SDK). Flag this in the plan.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Go | All backend code | ✓ | 1.25.5 | — |
| goose | `make migrate-up` | ✓ | v3.26.0 (global); v3.27.1 via `go run` in Makefile | Use Makefile target (go run) |
| PostgreSQL (staging) | Migration testing | ✓ | Via Railway staging | Run migrations against Railway staging only |
| psql | Manual DB inspection | ✗ | — | Railway dashboard SQL console |
| SQLDelight tooling | KMP build | ✓ | 2.3.2 via Gradle | — |

**Missing dependencies with no fallback:** none

**Missing dependencies with fallback:**
- psql client: not locally installed; use Railway dashboard SQL console for schema inspection

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Go standard `testing` package |
| Config file | none — no framework config needed |
| Quick run command | `go test ./... -run TestHealthz -v` (from backend/) |
| Full suite command | `go test ./...` (from backend/) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| REQ-008 | children table has only the allowed columns | Integration (DB) | Requires live DB; manual schema inspection or migration test | ❌ Wave 0 |
| REQ-009 / REQ-010 | POST /v1/children with no consent fields returns 400 | Unit (httptest) | `go test ./internal/api/... -run TestPostV1Children` | ❌ Wave 0 |
| REQ-010 / D-05 | POST /v1/children without prior consent_events row is rejected at DB level | Integration (DB) | Manual test or DB integration test | ❌ Wave 0 |
| REQ-011 | DELETE /v1/children/{id} returns 200 with correct JSON shape | Unit (httptest + mock DB) | `go test ./internal/api/... -run TestDeleteV1ChildrenId` | ❌ Wave 0 |
| REQ-013 | DELETE endpoints exist and return non-404 | Unit (httptest) | `go test ./internal/api/... -run TestDeletionEndpoints` | ❌ Wave 0 |
| REQ-C-009 | consent_events table has no IP address column | Schema inspection | Manual check via migration SQL or DB query | ❌ Wave 0 |

**Note on DB tests:** Full integration tests against a live PostgreSQL DB are beyond the scope of unit tests with httptest. Phase 2 unit tests should verify: (a) handler routing is wired (endpoint exists), (b) missing/malformed request body returns 400 with Problem Detail JSON, (c) correct Content-Type headers. The actual DB-level constraint enforcement (consent gate via FK) is validated by running the migration against Railway staging and manually testing the endpoint.

### Sampling Rate

- **Per task commit:** `cd backend && go test ./internal/api/... -v`
- **Per wave merge:** `cd backend && go test ./...`
- **Phase gate:** Full suite green + staging migration applied + `POST /v1/children` smoke test against Railway staging before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `backend/internal/api/children_handler_test.go` — covers REQ-009, REQ-010, REQ-013 (httptest-based, no real DB)
- [ ] `backend/internal/api/account_handler_test.go` — covers REQ-012, REQ-013 (httptest-based)
- [ ] No test framework install needed — `testing` package is stdlib

*(Existing server_test.go pattern: `httptest.NewRecorder()` + `httptest.NewRequest()` + `api.HandlerFromMux()` — new tests follow this exact pattern)*

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | Partial | Placeholder X-headers in Phase 2; Clerk JWT in Phase 3 — endpoints must NOT go to production without Phase 3 |
| V3 Session Management | No | Stateless API; session is Clerk's responsibility |
| V4 Access Control | Yes | DELETE endpoints must verify the requesting user owns the child org; Phase 2 placeholder checks header; Phase 3 adds JWT claim verification |
| V5 Input Validation | Yes | Validate birth_month (1-12), birth_year (reasonable range), nickname (non-empty, max length), consent fields (non-empty) |
| V6 Cryptography | No | No cryptographic operations in Phase 2 |

### Known Threat Patterns for Go + PostgreSQL

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection via clerk_user_id from header | Tampering | Parameterized queries with `$1` placeholders — already used in all code examples |
| Erasure of another user's child data | Tampering / Elevation | Verify `children.clerk_org_id` matches requesting user's org before deleting (Phase 3 adds JWT org check; Phase 2 placeholder must be clearly marked TODO) |
| Missing audit row on deletion failure | Repudiation | Audit INSERT inside same TX — if TX rolls back, audit row rolls back too (no orphaned audit without deletion) |
| Deletion endpoint reachable without auth | Elevation of Privilege | MUST NOT deploy without Phase 3 auth middleware; document staging-only deployment |

---

## Sources

### Primary (MEDIUM confidence)
- Codebase inspection — `backend/go.mod`, `backend/Makefile`, `api/codegen.yaml`, `backend/internal/api/generated.go`, `backend/cmd/server/main.go`, `backend/internal/api/server.go`, `shared/build.gradle.kts`, `gradle/libs.versions.toml`, `backend/db/migrations/00001_init.sql`, `shared/src/commonMain/sqldelight/migrations/1.sqm` — confirmed all existing patterns and versions
- `.planning/phases/02-compliance-privacy-architecture/02-CONTEXT.md` — locked decisions D-01 through D-15

### Secondary (MEDIUM confidence)
- [SQLDelight 2.0 Migration Docs](https://sqldelight.github.io/sqldelight/2.0.2/multiplatform_sqlite/migrations/) — .sqm syntax, no BEGIN/END, `verifyMigrations` behavior
- [Go database/sql transaction docs](https://go.dev/doc/database/execute-transactions) — BeginTx, defer Rollback, Commit pattern

### Tertiary (LOW confidence — ASSUMED tag in claims)
- [pressly/goose README](https://github.com/pressly/goose) — migration file structure and ENUM in SQL migrations
- [oapi-codegen v2 GitHub](https://github.com/oapi-codegen/oapi-codegen) — std-http-server path parameter handling with Go 1.22

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all packages confirmed in go.mod and build files
- Schema design: HIGH — locked decisions in CONTEXT.md; FK analysis verified against PostgreSQL semantics
- Architecture patterns: MEDIUM — transaction and handler patterns from official Go docs; oapi-codegen method names ASSUMED
- Pitfalls: HIGH — FK violation in D-08 is verifiable from the schema; others from standard Go/PG patterns

**Research date:** 2026-06-26
**Valid until:** 2026-07-26 (stable stack; no fast-moving dependencies)
