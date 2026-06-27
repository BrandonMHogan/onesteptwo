---
phase: 02-compliance-privacy-architecture
verified: 2026-06-27T00:30:00Z
status: human_needed
score: 19/20 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 18/20
  gaps_closed:
    - "All Go API error responses use RFC 7807 Problem Details (REQ-NF-001) — ProblemErrorHandler added in error_handler.go, HandlerWithOptions wired in main.go"
    - "DELETE /v1/account meets REQ-012 fully — org-wide device_tokens deletion via pq.Array/ANY, deterministic UUIDv5 audit target_id, post-commit Clerk Organization deletion"
    - "REQUIREMENTS.md traceability stale — REQ-011, REQ-012, REQ-013, REQ-014, REQ-C-002, REQ-C-003, REQ-C-004, REQ-C-008 now marked Phase 2 | Complete"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Run ./gradlew :shared:verifySqlDelightMigration from the repo root"
    expected: "BUILD SUCCESSFUL — applying 1.sqm then 2.sqm produces the .sq current schema; verifyMigrations = true gate passes"
    why_human: "Gradle/Android SDK not available in this environment; SUMMARY claims BUILD SUCCESSFUL but this must be run to confirm"
  - test: "Staging smoke test — create then delete a child"
    expected: "POST /v1/children with valid headers → 201 ChildResponse. DELETE /v1/children/{id} with the returned id → 200 D-10 JSON. Query DB: no row in children, potty_events, consent_events; one row in erasure_audit with action='child_deletion'."
    why_human: "Requires live Railway staging PostgreSQL; unit tests exercise only nil-DB auth/validation paths"
  - test: "Staging smoke test — account erasure (org-wide)"
    expected: "With two children and two org members (two distinct clerk_user_ids): DELETE /v1/account returns 200 D-10 JSON with deleted_children=2 and deleted_device_tokens>=2. DB query shows zero children rows for clerk_org_id; zero device_tokens rows for both clerk_user_ids; one erasure_audit row with action='account_deletion' and a UUIDv5 target_id. Clerk org is deleted (verify via Clerk dashboard or API)."
    why_human: "Requires pre-populated DB state with multiple org members; verifies org-wide device_tokens sweep and post-commit Clerk org deletion"
---

# Phase 02: Compliance Privacy Architecture — Verification Report

**Phase Goal:** All legal obligations are enforced in the database schema and Go API before any child profile can be created — consent gate, erasure cascade, audit log, and data minimisation constraints are provably correct.
**Verified:** 2026-06-27T00:30:00Z
**Status:** human_needed
**Re-verification:** Yes — after gap closure (plans 02-05 and 02-06)

---

## Re-Verification Summary

The two BLOCKERs from the initial verification (2026-06-26T22:00:00Z) are both **CLOSED**:

**Gap 1 CLOSED — REQ-NF-001:** `error_handler.go` implements `ProblemErrorHandler` using `errors.As` against `*InvalidParamFormatError`, delegating to `WriteProblem` with only the static OpenAPI parameter name (never `err.Error()`). `main.go` line 31 replaces the old `api.HandlerFromMux` call with `api.HandlerWithOptions(srv, api.StdHTTPServerOptions{BaseRouter: mux, ErrorHandlerFunc: api.ProblemErrorHandler})`. `TestProblemErrorHandler_InvalidUUIDPathParam` confirms `DELETE /v1/children/not-a-uuid` returns 400 `application/problem+json` and the raw Go error string "Invalid format for parameter" does not appear in the response body. Test PASSES.

**Gap 2 CLOSED — REQ-012:** `DeleteV1Account` was rewritten. `clerk.go` introduces `ClerkOrgClient` interface with `ListOrgMemberUserIDs` and `DeleteOrganization`. The handler now fetches all org member user IDs BEFORE `BeginTx` (server.go line 269), deletes device_tokens for all members via `DELETE FROM device_tokens WHERE clerk_user_id = ANY($1)` with `pq.Array(memberIDs)` (line 348), uses a deterministic UUIDv5 (`uuid.NewSHA1(uuid.NameSpaceURL, []byte("onesteptwo:org:"+clerkOrgID))`) as the erasure_audit target_id (line 360), and calls `s.Clerk.DeleteOrganization` post-commit (line 382). `main.go` line 29 wires `srv.Clerk = api.NewClerkClient(os.Getenv("CLERK_SECRET_KEY"))`. `TestDeleteV1Account_ClerkMemberFetchError` confirms the pre-tx failure path returns 500 before any DB modification.

**Gap 3 CLOSED — Traceability:** REQUIREMENTS.md traceability table updated — REQ-011, REQ-012, REQ-013, REQ-014, REQ-C-002, REQ-C-003, REQ-C-004, REQ-C-008 all now show "Phase 2 | Complete".

All 19 automated tests pass (`go test ./internal/api/... -v`). `go build ./...` exits 0.

No automated gaps remain. Phase status advances from `gaps_found` to `human_needed`. Three human verification items require staging environment.

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
| 7 | 02 | generated.go ServerInterface lists PostV1Children, DeleteV1ChildrenId, DeleteV1Account | VERIFIED | All three methods confirmed in generated.go |
| 8 | 02 | Server struct holds *sql.DB and ClerkOrgClient; main.go opens the DB, creates Clerk client, and passes both in | VERIFIED | server.go line 17-18: `DB *sql.DB` and `Clerk ClerkOrgClient`; main.go lines 28-29: `&api.Server{DB: db}` then `srv.Clerk = api.NewClerkClient(...)` |
| 9 | 02 | POST /v1/children inserts consent_events row then children row in one transaction | VERIFIED | server.go lines 84-127: BeginTx → INSERT consent_events RETURNING id → INSERT children using captured id → Commit |
| 10 | 02 | POST /v1/children rejects no body (400), invalid input (400), missing identity headers (401) | VERIFIED | go test passes: MissingBody, MalformedBody, MissingAuthHeaders, MissingClerkUserId, InvalidInput (5 subtests) all PASS |
| 11 | 02 | Consent handler never reads or writes an IP address | VERIFIED | No r.RemoteAddr or X-Forwarded-For in server.go; consent INSERT column list is exactly clerk_user_id, consented_at, app_version, consent_text_version |
| 12 | 03 | DELETE /v1/children/{id} hard-deletes potty_events → notification_preferences → children → consent_events in FK-safe order | VERIFIED | server.go lines 188-207: exact FK-safe order confirmed; SELECT consent_event_id captured before any delete |
| 13 | 03 | DELETE /v1/account hard-deletes every child in org (cascade), all org-member device_tokens, and deletes the Clerk Organization | VERIFIED | server.go: pre-tx ListOrgMemberUserIDs (line 269), pq.Array ANY delete (line 348), post-commit DeleteOrganization (line 382), deterministic UUIDv5 audit (line 360). All REQ-012 requirements met. |
| 14 | 03 | Every deletion writes one erasure_audit row inside the same transaction | VERIFIED | Both handlers: INSERT INTO erasure_audit inside BeginTx before Commit |
| 15 | 03 | Every deletion first purges erasure_audit rows older than 90 days in the same transaction | VERIFIED | Both handlers: DELETE FROM erasure_audit WHERE deleted_at < NOW() - INTERVAL '90 days' at start of each tx |
| 16 | 03 | Both endpoints return D-10 structured JSON confirmation, not 204 | VERIFIED | Both return 200 with Content-Type: application/json and all seven D-10 keys |
| 17 | 03 | Missing identity header → 401; non-existent child → 404 | VERIFIED | Tests TestDeleteV1ChildrenId_MissingAuthHeader and TestDeleteV1Account_MissingAuthHeader pass; sql.ErrNoRows → 404 in handler |
| 18 | 04 | SQLDelight 2.sqm migration creates consent_events, children, potty_events in SQLite-valid syntax | VERIFIED | 2.sqm exists; grep confirms 3 CREATE TABLE statements, sync_status column, no PostgreSQL-specific syntax |
| 19 | 04 | potty_events carries sync_status defaulting to 'pending' and retains deleted_at/deleted_by | VERIFIED | PottyEvents.sq confirmed: sync_status TEXT NOT NULL DEFAULT 'pending', deleted_at TEXT, deleted_by TEXT |
| 20 | 04 | verifyMigrations passes — applying 1.sqm then 2.sqm yields the current .sq schema | UNCERTAIN | SUMMARY claims BUILD SUCCESSFUL; 3.db schema snapshot exists and schemaOutputDirectory configured in build.gradle.kts. Cannot run Gradle in this environment — requires human verification. |

**Score:** 19/20 truths verified (1 UNCERTAIN requires human verification)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/db/migrations/00002_schema.sql` | 6 tables, 2 enums, goose Up/Down | VERIFIED | 96 lines; all 6 tables, 2 enums, correct FK ordering, Down section present |
| `docs/04-data-model.md` | D-01 event_type values, D-05 FK direction | VERIFIED | consent_event_id present; pee/poo/both/accident/tried values; no accident_pee/accident_poo |
| `api/openapi.yaml` | 3 new paths + 4 component schemas | VERIFIED | postV1Children, deleteV1ChildrenId, deleteV1Account; CreateChildRequest, ChildResponse, ErasureConfirmation, ProblemDetail |
| `backend/internal/api/generated.go` | ServerInterface with 3 new methods | VERIFIED | PostV1Children, DeleteV1ChildrenId, DeleteV1Account on ServerInterface |
| `backend/internal/api/server.go` | DB-backed Server + ClerkOrgClient field + all 3 handlers | VERIFIED | Server.DB *sql.DB; Server.Clerk ClerkOrgClient; all 3 handlers fully implemented per REQ-012 |
| `backend/internal/api/error_handler.go` | ProblemErrorHandler for oapi-codegen binding failures | VERIFIED | 25-line file; errors.As against *InvalidParamFormatError; WriteProblem with static param name only (T-2-12) |
| `backend/internal/api/clerk.go` | ClerkOrgClient interface + httpClerkClient with ListOrgMemberUserIDs and DeleteOrganization | VERIFIED | 128-line file; interface defined; httpClerkClient implements both methods with proper PII guard (T-2-15) |
| `backend/cmd/server/main.go` | sql.Open + pool limits + Server{DB:} + Clerk client + HandlerWithOptions | VERIFIED | SetMaxOpenConns(25), SetMaxIdleConns(5), api.Server{DB: db}, srv.Clerk = api.NewClerkClient(...), HandlerWithOptions with ProblemErrorHandler |
| `backend/internal/api/children_handler_test.go` | TestPostV1Children_* | VERIFIED | 5 test functions, all PASS |
| `backend/internal/api/erasure_handler_test.go` | TestDeleteV1ChildrenId_*, TestDeleteV1Account_*, TestDeleteV1Account_ClerkMemberFetchError | VERIFIED | 3 test functions; stubClerk test double; all PASS |
| `backend/internal/api/error_handler_test.go` | TestProblemErrorHandler_InvalidUUIDPathParam | VERIFIED | Tests RFC 7807 response for non-UUID path param; asserts Content-Type, status 400, and absence of raw Go error string |
| `shared/.../db/ConsentEvents.sq` | SQLite consent_events, no child_id, no IP | VERIFIED | 5 columns matching spec; no child_id, no ip column |
| `shared/.../db/Children.sq` | SQLite children, consent_event_id, no PII | VERIFIED | 8 columns; consent_event_id TEXT NOT NULL; no PII columns |
| `shared/.../db/PottyEvents.sq` | sync_status, deleted_at/by, no ENUM | VERIFIED | 12 columns; sync_status TEXT NOT NULL DEFAULT 'pending'; deleted_at/deleted_by TEXT |
| `shared/.../migrations/2.sqm` | Creates all 3 client tables, no PG syntax | VERIFIED | 3 CREATE TABLE statements, sync_status present, no TIMESTAMPTZ/CREATE TYPE/BEGIN-END |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| children.consent_event_id | consent_events.id | NOT NULL FOREIGN KEY | VERIFIED | `consent_event_id UUID NOT NULL REFERENCES consent_events(id)` — migration line 26 |
| potty_events.child_id | children.id | FOREIGN KEY | VERIFIED | `child_id UUID NOT NULL REFERENCES children(id)` — migration line 37 |
| PostV1Children handler | consent_events + children tables | BeginTx + RETURNING id | VERIFIED | Single transaction, consent row first, child row second using captured UUID |
| main.go | api.Server.DB | sql.Open + struct field | VERIFIED | `&api.Server{DB: db}` at line 28 of main.go |
| main.go | api.Server.Clerk | NewClerkClient + struct field | VERIFIED | `srv.Clerk = api.NewClerkClient(os.Getenv("CLERK_SECRET_KEY"))` at line 29 of main.go |
| main.go | ProblemErrorHandler | HandlerWithOptions ErrorHandlerFunc | VERIFIED | `api.HandlerWithOptions(srv, api.StdHTTPServerOptions{BaseRouter: mux, ErrorHandlerFunc: api.ProblemErrorHandler})` at line 31 |
| DeleteV1Account | all org member device_tokens | pre-tx ListOrgMemberUserIDs + pq.Array ANY | VERIFIED | memberIDs fetched before BeginTx; DELETE via ANY($1) deletes all org members' tokens |
| DeleteV1Account | Clerk Organization | post-commit DeleteOrganization | VERIFIED | s.Clerk.DeleteOrganization called after tx.Commit; failure logged but does not fail response |
| DeleteV1ChildrenId | consent_events via children.consent_event_id | SELECT before DELETE, FK-safe order | VERIFIED | SELECT consent_event_id FROM children WHERE id = $1 captured; DELETE children before DELETE consent_events |
| Both delete handlers | erasure_audit | D-12 purge + INSERT in same tx | VERIFIED | Purge >90d and INSERT audit both inside BeginTx...Commit |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| PostV1Children | consentEventID | INSERT INTO consent_events RETURNING id | Yes — DB-generated UUID returned | FLOWING (at staging) |
| PostV1Children | childID | INSERT INTO children RETURNING id | Yes — DB-generated UUID returned | FLOWING (at staging) |
| DeleteV1ChildrenId | consentEventID | SELECT consent_event_id FROM children | Yes — real DB row | FLOWING (at staging) |
| DeleteV1Account | memberIDs | Clerk REST API /v1/organizations/{orgID}/memberships | Yes — live Clerk API response | FLOWING (at staging, requires CLERK_SECRET_KEY) |
| DeleteV1Account | children slice | SELECT id, consent_event_id FROM children WHERE clerk_org_id | Yes — real DB rows | FLOWING (at staging) |
| DeleteV1Account | orgTargetID | uuid.NewSHA1(uuid.NameSpaceURL, []byte("onesteptwo:org:"+clerkOrgID)) | Yes — deterministic derivation from real clerk_org_id | FLOWING |

Note: DB-dependent paths require staging environment. Unit tests use nil DB and cover only auth/validation/Clerk-error gates.

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Go build compiles | `go build ./...` | Exit 0 | PASS |
| All API tests | `go test ./internal/api/... -v` | All 19 tests PASS | PASS |
| ProblemErrorHandler returns RFC 7807 | `TestProblemErrorHandler_InvalidUUIDPathParam` | 400, application/problem+json, no raw Go error string | PASS |
| Clerk member fetch error → 500 pre-tx | `TestDeleteV1Account_ClerkMemberFetchError` | 500, application/problem+json; nil DB safe | PASS |
| HandlerWithOptions wiring in main.go | `grep -n HandlerWithOptions cmd/server/main.go` | Line 31 found with ProblemErrorHandler | PASS |
| org-wide ANY delete pattern | `grep -n 'ANY\|pq.Array' internal/api/server.go` | Line 348: `DELETE FROM device_tokens WHERE clerk_user_id = ANY($1)` | PASS |
| Deterministic UUIDv5 for audit | `grep -n 'NewSHA1' internal/api/server.go` | Line 360: `uuid.NewSHA1(uuid.NameSpaceURL, []byte("onesteptwo:org:"+clerkOrgID))` | PASS |
| consent_events has no IP column | `grep -A8 'CREATE TABLE consent_events' 00002_schema.sql \| grep -ci 'ip'` | 0 | PASS |
| children has no PII columns | `grep -A15 'CREATE TABLE children' 00002_schema.sql \| grep -Eic 'gender\|photo\|legal_name\|full_name\|dob'` | 0 | PASS |
| No ON DELETE CASCADE in schema | `grep -c 'ON DELETE CASCADE' 00002_schema.sql` | 0 | PASS |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| REQ-008 | 02-01 | Child profile stores only id, clerk_org_id, nickname, birth_month, birth_year, created_at, updated_at | SATISFIED | migration: 8 columns including consent_event_id (compliance FK); no PII columns confirmed |
| REQ-009 | 02-02 | Parental consent recorded before child profile created | SATISFIED | PostV1Children: consent_events INSERT before children INSERT in single tx |
| REQ-010 | 02-02 | consent_events row inserted before children row; no IP stored | SATISFIED | Order enforced in single tx. No IP source read; column list is exactly the four D-06 fields. NOTE: REQ-010 text mentions child_id but D-05 overrides this. |
| REQ-011 | 02-03 | Child deletion cascade: potty_events, notification_preferences, consent_events hard-deleted | SATISFIED | DeleteV1ChildrenId: FK-safe cascade confirmed in code and tests; REQUIREMENTS.md Complete |
| REQ-012 | 02-03, 02-06 | Account deletion: all org children, all org member device_tokens, Clerk Organization | SATISFIED | All org children cascade: done. Device_tokens for ALL org members via Clerk member list + pq.Array ANY: done. Clerk Organization deleted post-commit: done. REQUIREMENTS.md Complete |
| REQ-013 | 02-02, 02-03 | Verified deletion endpoints for child and account | SATISFIED | Both DELETE endpoints implemented, return D-10 confirmation |
| REQ-014 | 02-03 | Erasure right: clear deletion, confirmation response, audit log, 90-day purge | SATISFIED | D-10 JSON response; erasure_audit INSERT in tx; D-12 sweep-on-request; REQUIREMENTS.md Complete |
| REQ-NF-001 | 02-05 | RFC 7807 Problem Details for all API errors | SATISFIED | ProblemErrorHandler covers path-param binding failures; WriteProblem covers all handler error paths; HandlerWithOptions wired in main.go; REQUIREMENTS.md Complete |
| REQ-NF-003 | 02-01 | No automatic data expiry | SATISFIED | No ON DELETE CASCADE; no TTL columns; erasure_audit 90d purge is manual sweep-on-request |
| REQ-C-001 | 02-02 | COPPA: parental consent gate before any child data | SATISFIED | consent_event_id NOT NULL FK is DB-level enforcement; PostV1Children atomic tx is application-level enforcement |
| REQ-C-002 | 02-03 | GDPR: right to erasure | SATISFIED | Hard-delete cascade implemented for both child and account; REQUIREMENTS.md Complete |
| REQ-C-003 | 02-03 | PIPEDA/Law 25: consent and erasure | SATISFIED | Same mechanism as REQ-C-002; REQUIREMENTS.md Complete |
| REQ-C-004 | 02-03 | POPIA: consent and erasure | SATISFIED | Same mechanism as REQ-C-002; REQUIREMENTS.md Complete |
| REQ-C-005 | 02-01, 02-04 | Parent/caregiver PII in Clerk only; DB stores only clerk_user_id/clerk_org_id | SATISFIED | Server schema: no PII confirmed. Client schema: only TEXT clerk_user_id/clerk_org_id identifiers |
| REQ-C-008 | 02-03 | Erasure audit log with 90-day self-purge | SATISFIED | erasure_audit table exists; D-12 sweep-on-request in both handlers; account_deletion target_id is deterministic UUIDv5 (traceable); REQUIREMENTS.md Complete |
| REQ-C-009 | 02-01, 02-02 | No IP address stored in consent_events | SATISFIED | Schema has no IP column; handler reads no r.RemoteAddr or X-Forwarded-For |

**Traceability check:** All Phase 2 requirements in REQUIREMENTS.md now marked "Phase 2 | Complete" — confirmed for REQ-008 through REQ-014, REQ-NF-001, REQ-NF-003, REQ-C-001 through REQ-C-005, REQ-C-008, REQ-C-009.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `backend/internal/api/server.go` | 382-384 | Clerk org deletion failure logged but does not fail the 200 response (CR-03) | WARNING | Intentional Pitfall-2 design: DB erasure committed before Clerk call; a failing Clerk delete leaves a Clerk org that holds only PII Clerk owns. GDPR/COPPA DB obligations are already fulfilled. A lingering Clerk org must be cleaned up manually if the log fires. This is an accepted architectural trade-off documented in the SUMMARY. |
| `backend/internal/api/server.go` | 188 | `// TODO Phase 3: verify children.clerk_org_id` (IDOR gap T-2-02) | WARNING | DeleteV1ChildrenId has no org ownership check. Any user with any non-empty header can delete any child UUID. Plan explicitly accepts this as Phase 2 limitation (threat model T-2-02). Must not reach production before Phase 3. |
| `backend/cmd/server/main.go` | 20-24 | No `db.PingContext()` at startup | WARNING | Server starts and reports ready even if DB is unreachable; first request fails with 500. Not a compliance gap but a production reliability gap (code review WR-03). |
| `shared/.../PottyEvents.sq` | 12 | `sync_status TEXT NOT NULL DEFAULT 'pending'` without CHECK constraint | WARNING | Any string value accepted (typos remain unsynchronized silently). Code review WR-05. Fix requires new 3.sqm migration. |

No `TBD`, `FIXME`, or `XXX` debt markers found in phase-modified files. `TODO` markers present but all reference explicit future phases (Phase 3, Phase 8) — not unreferenced. The previous BLOCKER anti-pattern (`HandlerFromMux` without custom error handler) is resolved.

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

#### 3. Staging Smoke Test — Account Erasure (org-wide)

**Test:** Create two children for an org with two distinct org members (two clerk_user_ids registered). Issue DELETE /v1/account with the admin's headers. Query DB and Clerk dashboard.
**Expected:** 200 D-10 JSON with deleted_children=2, deleted_device_tokens>=2 (one per member). Zero children rows for that clerk_org_id. Zero device_tokens rows for both clerk_user_ids. erasure_audit row with action='account_deletion' and a UUIDv5 target_id (recompute via `uuid.NewSHA1(url-namespace, "onesteptwo:org:<orgID>")` to verify it matches). Clerk Organization deleted from dashboard.
**Why human:** Requires multi-member org pre-populated in staging DB, live Clerk API with CLERK_SECRET_KEY, and access to Clerk dashboard for org deletion confirmation

---

### Gaps Summary

No automated gaps remain. Both BLOCKERs from the initial verification are closed:

- **Gap 1 (REQ-NF-001)** — CLOSED: `ProblemErrorHandler` wired via `HandlerWithOptions`. Malformed path parameters now return `application/problem+json`. All 19 tests pass including the dedicated RFC 7807 regression test.

- **Gap 2 (REQ-012)** — CLOSED: `DeleteV1Account` performs org-wide device_tokens deletion via Clerk member list + `pq.Array`/`ANY`, uses a deterministic UUIDv5 audit target_id, and deletes the Clerk Organization post-commit. All REQ-012 requirements are implemented.

- **Gap 3 (traceability)** — CLOSED: REQUIREMENTS.md traceability table reflects Phase 2 | Complete for all 7 Phase 2 erasure requirements.

Phase goal is achieved in code. Proceeding to human verification for staging environment validation.

---

_Initial verification: 2026-06-26T22:00:00Z_
_Re-verified: 2026-06-27T00:30:00Z_
_Verifier: Claude (gsd-verifier)_
