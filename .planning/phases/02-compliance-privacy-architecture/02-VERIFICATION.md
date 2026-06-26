---
phase: 02-compliance-privacy-architecture
verified: 2026-06-26T22:00:00Z
status: gaps_found
score: 18/20 must-haves verified
overrides_applied: 0
gaps:
  - truth: "DELETE /v1/account meets REQ-012 fully (all org member device_tokens deleted, Clerk Organization deleted)"
    status: failed
    reason: "The handler only deletes device_tokens for the requesting clerkUserID, not all org members. The Clerk Organization is not deleted. TODO comments defer these to Phase 3 (Clerk org) and Phase 8 (device_tokens), but REQUIREMENTS.md assigns REQ-012 to Phase 2 with 'Pending' status. No later phase plan has been verified to explicitly claim this work."
    artifacts:
      - path: "backend/internal/api/server.go"
        issue: "Line 330: DELETE FROM device_tokens WHERE clerk_user_id = $1 — only requesting user's tokens; org-wide deletion absent. Line 341: gen_random_uuid() as target_id makes audit row untraceble to the actual org."
    missing:
      - "Delete device_tokens for all org member clerk_user_ids (requires Clerk member list API or Phase 3 wiring)"
      - "Delete the Clerk Organization (REQ-012 final step)"
      - "Update REQUIREMENTS.md traceability row for REQ-011, REQ-012, REQ-014 from 'Pending' to 'Complete'"
  - truth: "All Go API error responses use RFC 7807 Problem Details (REQ-NF-001)"
    status: failed
    reason: "main.go calls api.HandlerFromMux(srv, mux) without a custom ErrorHandlerFunc. The generated default handler calls http.Error() for path-parameter binding failures (e.g., malformed UUID in DELETE /v1/children/{id}), returning Content-Type: text/plain and a raw Go error string. This is live code, not deferred, and violates REQ-NF-001 for any request with a non-UUID id path parameter. Code review CR-02 flagged this as Critical."
    artifacts:
      - path: "backend/cmd/server/main.go"
        issue: "Line 30: api.HandlerFromMux(srv, mux) uses generated default error handler (http.Error / text/plain)"
      - path: "backend/internal/api/generated.go"
        issue: "Lines 259-263: default ErrorHandlerFunc uses http.Error, not WriteProblem"
    missing:
      - "Replace HandlerFromMux with HandlerWithOptions supplying a custom ErrorHandlerFunc that calls WriteProblem"
human_verification:
  - test: "Run ./gradlew :shared:verifySqlDelightMigration from the repo root"
    expected: "BUILD SUCCESSFUL — applying 1.sqm then 2.sqm produces the .sq current schema; verifyMigrations = true gate passes"
    why_human: "Gradle/Android SDK not available in this environment; SUMMARY claims BUILD SUCCESSFUL but this must be run to confirm"
  - test: "Staging smoke test — create then delete a child"
    expected: "POST /v1/children with valid headers → 201 ChildResponse. DELETE /v1/children/{id} with the returned id → 200 D-10 JSON. Query DB: no row in children, potty_events, consent_events; one row in erasure_audit with action='child_deletion'."
    why_human: "Requires live Railway staging PostgreSQL; unit tests exercise only nil-DB auth/validation paths"
  - test: "Staging smoke test — account erasure"
    expected: "DELETE /v1/account with valid headers → 200 D-10 JSON with deleted_children > 0 for the org. Query DB: zero children rows for that clerk_org_id; erasure_audit row with action='account_deletion'."
    why_human: "Requires live DB and org with pre-populated children; verifies the per-child cascade loop runs correctly end-to-end"
---

# Phase 02: Compliance Privacy Architecture — Verification Report

**Phase Goal:** Implement the compliance, privacy, and consent-gate architecture required to safely store children's data — a PostgreSQL schema (server) and SQLDelight schema (client) that enforce COPPA/GDPR obligations at the database level, with Go API endpoints that make violating the consent gate physically impossible.
**Verified:** 2026-06-26T22:00:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Plan | Truth | Status | Evidence |
|---|------|-------|--------|----------|
| 1 | 01 | Applying 00002_schema.sql creates all six server-side tables in valid FK-dependency order | VERIFIED | File exists; grep confirms all 6 CREATE TABLE statements in correct order |
| 2 | 01 | A children row physically cannot exist without referencing an existing consent_events row (D-05 FK gate) | VERIFIED | Line 26: `consent_event_id UUID NOT NULL REFERENCES consent_events(id)` |
| 3 | 01 | consent_events has no IP-address column and no child_id column | VERIFIED | grep of consent_events block returns 0 matches for 'ip'; no child_id column present |
| 4 | 01 | children has no PII columns (no legal name, full DOB, gender, photo, medical) | VERIFIED | grep of children block returns 0 matches for gender/photo/legal_name/full_name/dob |
| 5 | 01 | No table imposes a TTL or automatic-expiry mechanism | VERIFIED | grep for ON DELETE CASCADE returns 0; no TTL/expiry columns in any table |
| 6 | 02 | OpenAPI spec declares POST /v1/children, DELETE /v1/children/{id}, DELETE /v1/account | VERIFIED | api/openapi.yaml contains all three operationIds: postV1Children, deleteV1ChildrenId, deleteV1Account |
| 7 | 02 | generated.go ServerInterface lists PostV1Children, DeleteV1ChildrenId, DeleteV1Account | VERIFIED | All three methods confirmed in generated.go lines 65-71 |
| 8 | 02 | Server struct holds *sql.DB and main.go opens the DB and passes it in | VERIFIED | server.go line 14: `DB *sql.DB`; main.go line 28: `&api.Server{DB: db}` with pool limits |
| 9 | 02 | POST /v1/children inserts consent_events row then children row in one transaction | VERIFIED | server.go lines 84-127: BeginTx → INSERT consent_events RETURNING id → INSERT children using captured id → Commit |
| 10 | 02 | POST /v1/children rejects no body (400), invalid input (400), missing identity headers (401) | VERIFIED | go test passes: MissingBody, MalformedBody, MissingAuthHeaders, MissingClerkUserId, InvalidInput (5 subtests) all PASS |
| 11 | 02 | Consent handler never reads or writes an IP address | VERIFIED | No r.RemoteAddr or X-Forwarded-For in server.go; consent INSERT column list is exactly clerk_user_id, consented_at, app_version, consent_text_version |
| 12 | 03 | DELETE /v1/children/{id} hard-deletes potty_events → notification_preferences → children → consent_events in FK-safe order | VERIFIED | server.go lines 188-207: exact FK-safe order confirmed; SELECT consent_event_id captured before any delete |
| 13 | 03 | DELETE /v1/account hard-deletes every child in org (cascade) and writes account-deletion audit row | PARTIAL | Child cascade per org IS implemented (lines 270-324). Device_token deletion is requesting-user-only (line 330). Clerk org deletion is absent. REQ-012 partial. |
| 14 | 03 | Every deletion writes one erasure_audit row inside the same transaction | VERIFIED | Both handlers: INSERT INTO erasure_audit inside BeginTx before Commit |
| 15 | 03 | Every deletion first purges erasure_audit rows older than 90 days in the same transaction | VERIFIED | Both handlers: DELETE FROM erasure_audit WHERE deleted_at < NOW() - INTERVAL '90 days' at start of each tx |
| 16 | 03 | Both endpoints return D-10 structured JSON confirmation, not 204 | VERIFIED | Both return 200 with Content-Type: application/json and all seven D-10 keys |
| 17 | 03 | Missing identity header → 401; non-existent child → 404 | VERIFIED | Tests TestDeleteV1ChildrenId_MissingAuthHeader and TestDeleteV1Account_MissingAuthHeader pass; sql.ErrNoRows → 404 in handler |
| 18 | 04 | SQLDelight 2.sqm migration creates consent_events, children, potty_events in SQLite-valid syntax | VERIFIED | 2.sqm exists; grep confirms 3 CREATE TABLE statements, sync_status column, no PostgreSQL-specific syntax |
| 19 | 04 | potty_events carries sync_status defaulting to 'pending' and retains deleted_at/deleted_by | VERIFIED | PottyEvents.sq confirmed: sync_status TEXT NOT NULL DEFAULT 'pending', deleted_at TEXT, deleted_by TEXT |
| 20 | 04 | verifyMigrations passes — applying 1.sqm then 2.sqm yields the current .sq schema | UNCERTAIN | SUMMARY claims BUILD SUCCESSFUL; 3.db schema snapshot exists and schemaOutputDirectory configured in build.gradle.kts. Cannot run Gradle in this environment — requires human verification. |

**Score:** 17/20 truths verified (1 PARTIAL = gap, 1 UNCERTAIN = human needed, 1 additional gap from REQ-NF-001)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/db/migrations/00002_schema.sql` | 6 tables, 2 enums, goose Up/Down | VERIFIED | 96 lines; all 6 tables, 2 enums, correct FK ordering, Down section present |
| `docs/04-data-model.md` | D-01 event_type values, D-05 FK direction | VERIFIED | consent_event_id present; pee/poo/both/accident/tried values; no accident_pee/accident_poo |
| `api/openapi.yaml` | 3 new paths + 4 component schemas | VERIFIED | postV1Children, deleteV1ChildrenId, deleteV1Account; CreateChildRequest, ChildResponse, ErasureConfirmation, ProblemDetail |
| `backend/internal/api/generated.go` | ServerInterface with 3 new methods | VERIFIED | PostV1Children, DeleteV1ChildrenId, DeleteV1Account on ServerInterface |
| `backend/internal/api/server.go` | DB-backed Server + all 3 handlers | VERIFIED | Server.DB *sql.DB; PostV1Children, DeleteV1ChildrenId (full), DeleteV1Account (full, REQ-012 partial) |
| `backend/cmd/server/main.go` | sql.Open + pool limits + Server{DB:} | VERIFIED | SetMaxOpenConns(25), SetMaxIdleConns(5), api.Server{DB: db} |
| `backend/internal/api/children_handler_test.go` | TestPostV1Children_* | VERIFIED | 5 test functions, all PASS |
| `backend/internal/api/erasure_handler_test.go` | TestDeleteV1ChildrenId_*, TestDeleteV1Account_* | VERIFIED | MissingAuthHeader tests for both endpoints, PASS |
| `shared/.../db/ConsentEvents.sq` | SQLite consent_events, no child_id, no IP | VERIFIED | 5 columns matching spec; no child_id, no ip column |
| `shared/.../db/Children.sq` | SQLite children, consent_event_id, no PII | VERIFIED | 8 columns; consent_event_id TEXT NOT NULL; no PII columns |
| `shared/.../db/PottyEvents.sq` | sync_status, deleted_at/by, no ENUM | VERIFIED | 12 columns; sync_status TEXT NOT NULL DEFAULT 'pending'; deleted_at/deleted_by TEXT |
| `shared/.../migrations/2.sqm` | Creates all 3 client tables, no PG syntax | VERIFIED | 3 CREATE TABLE statements, sync_status present, no TIMESTAMPTZ/CREATE TYPE/BEGIN-END |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| children.consent_event_id | consent_events.id | NOT NULL FOREIGN KEY | VERIFIED | `consent_event_id UUID NOT NULL REFERENCES consent_events(id)` — line 26 of migration |
| potty_events.child_id | children.id | FOREIGN KEY | VERIFIED | `child_id UUID NOT NULL REFERENCES children(id)` — line 37 of migration |
| PostV1Children handler | consent_events + children tables | BeginTx + RETURNING id | VERIFIED | Single transaction, consent row first, child row second using captured UUID |
| main.go | api.Server.DB | sql.Open + struct field | VERIFIED | `&api.Server{DB: db}` at line 28 of main.go |
| DeleteV1ChildrenId | consent_events via children.consent_event_id | SELECT before DELETE, FK-safe order | VERIFIED | SELECT consent_event_id FROM children WHERE id = $1 captured; DELETE children before DELETE consent_events |
| Both delete handlers | erasure_audit | D-12 purge + INSERT in same tx | VERIFIED | Purge >90d and INSERT audit both inside BeginTx...Commit |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| PostV1Children | consentEventID | INSERT INTO consent_events RETURNING id | Yes — DB-generated UUID returned | FLOWING (at staging) |
| PostV1Children | childID | INSERT INTO children RETURNING id | Yes — DB-generated UUID returned | FLOWING (at staging) |
| DeleteV1ChildrenId | consentEventID | SELECT consent_event_id FROM children | Yes — real DB row | FLOWING (at staging) |
| DeleteV1Account | children slice | SELECT id, consent_event_id FROM children WHERE clerk_org_id | Yes — real DB rows | FLOWING (at staging) |

Note: All DB-dependent paths require staging environment. Unit tests use nil DB and cover only auth/validation gates.

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Go build compiles | `go build ./...` | Exit 0 | PASS |
| PostV1Children tests | `go test ./internal/api/... -run TestPostV1Children -v` | All 8 subtests PASS | PASS |
| DeleteV1ChildrenId test | `go test ./internal/api/... -run TestDeleteV1ChildrenId -v` | PASS | PASS |
| DeleteV1Account test | `go test ./internal/api/... -run TestDeleteV1Account -v` | PASS | PASS |
| consent_events has no IP column | `grep -A8 'CREATE TABLE consent_events' 00002_schema.sql \| grep -ci 'ip'` | 0 | PASS |
| children has no PII columns | `grep -A15 'CREATE TABLE children' 00002_schema.sql \| grep -Eic 'gender\|photo\|legal_name\|full_name\|dob'` | 0 | PASS |
| No ON DELETE CASCADE in schema | `grep -c 'ON DELETE CASCADE' 00002_schema.sql` | 0 | PASS |
| SQLDelight sync_status present | grep in PottyEvents.sq and 2.sqm | found in both | PASS |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| REQ-008 | 02-01 | Child profile stores only id, clerk_org_id, nickname, birth_month, birth_year, created_at, updated_at | SATISFIED | migration: 8 columns including consent_event_id (compliance FK); no PII columns confirmed |
| REQ-009 | 02-02 | Parental consent recorded before child profile created | SATISFIED | PostV1Children: consent_events INSERT before children INSERT in single tx |
| REQ-010 | 02-02 | consent_events row inserted before children row; no IP stored | PARTIALLY SATISFIED | Order enforced. NOTE: REQ-010 text lists child_id as required field but D-05 overrides this (no child_id in consent_events). docs/04-data-model.md reconciles this. No IP confirmed. |
| REQ-011 | 02-03 | Child deletion cascade: potty_events, notification_preferences, consent_events hard-deleted | SATISFIED | DeleteV1ChildrenId: FK-safe cascade confirmed in code and tests |
| REQ-012 | 02-03 | Account deletion: all org children, all org member device_tokens, Clerk Organization | PARTIALLY SATISFIED | All org children cascade: done. Device_tokens for all org members: NOT DONE (requesting user only). Clerk Organization deletion: NOT DONE. TODO Phase 3/8 comments present. REQUIREMENTS.md shows "Pending". |
| REQ-013 | 02-02, 02-03 | Verified deletion endpoints for child and account | SATISFIED | Both DELETE endpoints implemented, return D-10 confirmation |
| REQ-014 | 02-03 | Erasure right: clear deletion, confirmation response, audit log, 90-day purge | SATISFIED | D-10 JSON response; erasure_audit INSERT in tx; D-12 sweep-on-request implemented |
| REQ-NF-003 | 02-01 | No automatic data expiry | SATISFIED | No ON DELETE CASCADE; no TTL columns; erasure_audit 90d purge is manual sweep-on-request |
| REQ-C-001 | 02-02 | COPPA: parental consent gate before any child data | SATISFIED | consent_event_id NOT NULL FK is DB-level enforcement; PostV1Children atomic tx is application-level enforcement |
| REQ-C-002 | 02-03 | GDPR: right to erasure | SATISFIED | Hard-delete cascade implemented for both child and account |
| REQ-C-003 | 02-03 | PIPEDA/Law 25: consent and erasure | SATISFIED | Same mechanism as REQ-C-002 |
| REQ-C-004 | 02-03 | POPIA: consent and erasure | SATISFIED | Same mechanism as REQ-C-002 |
| REQ-C-005 | 02-01, 02-04 | Parent/caregiver PII in Clerk only; DB stores only clerk_user_id/clerk_org_id | SATISFIED | Server schema: no PII confirmed. Client schema: only TEXT clerk_user_id/clerk_org_id identifiers |
| REQ-C-008 | 02-03 | Erasure audit log with 90-day self-purge | SATISFIED | erasure_audit table exists; D-12 sweep-on-request in both handlers |
| REQ-C-009 | 02-01, 02-02 | No IP address stored in consent_events | SATISFIED | Schema has no IP column; handler reads no r.RemoteAddr or X-Forwarded-For |

**Orphaned requirements check:** REQUIREMENTS.md traceability shows REQ-011, REQ-012, REQ-014 as "Phase 2 | Pending" even though implementation exists. The traceability table was not updated after Phase 2 completed.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| backend/cmd/server/main.go | 30 | `api.HandlerFromMux(srv, mux)` without custom ErrorHandlerFunc | BLOCKER | Invalid UUID path params return `text/plain` with raw Go error string, violating REQ-NF-001. Confirmed in generated.go lines 259-263. |
| backend/internal/api/server.go | 341 | `gen_random_uuid()` as target_id in account_deletion audit row | WARNING | Audit row for account_deletion is not traceable to any org or user. Makes erasure_audit useless for account deletion verification (CR-02 from code review WR-02). Phase 3 TODO noted. |
| backend/internal/api/server.go | 184 | `// TODO Phase 3: verify children.clerk_org_id` (IDOR gap T-2-02) | WARNING | DeleteV1ChildrenId has no org ownership check. Any user with any non-empty header can delete any child UUID. Plan explicitly accepts this as Phase 2 limitation (threat model T-2-02). Must not reach production before Phase 3. |
| backend/cmd/server/main.go | 20-24 | No `db.PingContext()` at startup | WARNING | Server starts and reports ready even if DB is unreachable; first request fails with 500. Not a compliance gap but a production reliability gap (code review WR-03). |
| shared/.../PottyEvents.sq | 12 | `sync_status TEXT NOT NULL DEFAULT 'pending'` without CHECK constraint | WARNING | Any string value accepted (typos remain unsynchronized silently). Code review WR-05. Fix requires new 3.sqm migration. |

No `TBD`, `FIXME`, or `XXX` debt markers found in phase-modified files. `TODO` markers present but all reference explicit future phases (Phase 3, Phase 8) — not unreferenced.

---

### Human Verification Required

#### 1. SQLDelight Migration Verification

**Test:** From the repo root, run `./gradlew :shared:verifySqlDelightMigration --console=plain`
**Expected:** BUILD SUCCESSFUL — applying 1.sqm (empty) then 2.sqm produces exactly the .sq current schema; verifyMigrations = true gate passes
**Why human:** Gradle/Android SDK required; 3.db schema snapshot exists and schemaOutputDirectory is configured (SUMMARY claims BUILD SUCCESSFUL), but the build must be run to confirm

#### 2. Staging Smoke Test — Child Creation and Deletion

**Test:** Against Railway staging (after `make migrate-up` applies 00002_schema.sql): POST /v1/children with valid headers and body → capture child id → DELETE /v1/children/{id} with same user header → query DB for children, potty_events, consent_events, erasure_audit
**Expected:** POST returns 201 ChildResponse. DELETE returns 200 with D-10 JSON. DB query shows zero rows in children/potty_events/consent_events for that child; one row in erasure_audit with action='child_deletion'
**Why human:** Requires live PostgreSQL and Railway deployment; nil-DB unit tests cover only auth/validation paths

#### 3. Staging Smoke Test — Account Erasure

**Test:** Create two children for an org via POST /v1/children, then DELETE /v1/account with the org's headers. Query DB.
**Expected:** 200 D-10 JSON with deleted_children=2. Zero children rows for that clerk_org_id. erasure_audit row with action='account_deletion'.
**Why human:** Requires pre-populated DB state and live environment; verifies the per-child cascade loop runs correctly for multiple children

---

### Gaps Summary

Two gaps block the phase status from passing:

**Gap 1 — REQ-NF-001 violation (live, not deferred):** `main.go` calls `api.HandlerFromMux` without a custom `ErrorHandlerFunc`. The generated default handler uses `http.Error()` which sets `Content-Type: text/plain` and writes the raw Go error string for path-parameter binding failures (e.g., a non-UUID string in `DELETE /v1/children/{id}`). This violates REQ-NF-001 which requires RFC 7807 `application/problem+json` for all error responses. Fix is a 5-line change using `api.HandlerWithOptions` with a custom `ErrorHandlerFunc` that calls `WriteProblem`. Not blocked on any future phase.

**Gap 2 — REQ-012 partial (device_tokens and Clerk org deletion missing):** `DELETE /v1/account` deletes all children in the org and their dependents (done), but deletes device_tokens only for the requesting user and does not delete the Clerk Organization. REQ-012 explicitly requires "hard delete all device_tokens for all clerk_user_ids in the org" and "delete the Clerk Organization." The code has `// TODO Phase 3` comments for these. REQUIREMENTS.md marks REQ-012 as "Phase 2 | Pending." This is a known intentional deferral, but REQ-012 was claimed in Plan 03's `requirements` frontmatter, and the SUMMARY claims it completed.

These are structurally documented in the `gaps:` YAML above for `/gsd-plan-phase --gaps`.

---

_Verified: 2026-06-26T22:00:00Z_
_Verifier: Claude (gsd-verifier)_
