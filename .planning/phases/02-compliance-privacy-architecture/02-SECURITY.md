---
phase: 02
slug: compliance-privacy-architecture
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-27
---

# Phase 02 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Application → PostgreSQL | All writes to child/consent data cross here; the schema is the last line of compliance enforcement | PII-adjacent (child nickname, consent timestamps) |
| Migration author → schema | A malformed or over-broad schema silently weakens compliance (extra PII columns, missing FKs) | DDL |
| Client → POST /v1/children | Untrusted JSON body + identity headers; Phase 2 uses placeholder auth, Phase 3 replaces with JWT | child registration payload |
| Handler → PostgreSQL | Consent + child writes; must be atomic and fully parameterized | INSERT consent_events + children |
| Client → DELETE /v1/children/{id} | Destructive request; Phase 2 placeholder identity headers, Phase 3 adds JWT org check | child-deletion intent |
| Client → DELETE /v1/account | Destructive request; org-wide erasure; Phase 2 placeholder auth, Phase 3 JWT | org-deletion intent |
| Handler → Clerk API | Pre-tx org member list read + post-commit Organization deletion cross the network boundary | Clerk org/user IDs |
| App code → on-device SQLite | Local source of truth for offline-first; data minimisation must hold on-device too | client-side potty events + children |
| Migration → build verification | `verifyMigrations = true` is the gate that an invalid/PII-bearing client schema cannot ship | client SQLite DDL |
| Client → oapi-codegen wrapper | Untrusted path parameters (e.g. `{id}`) parsed before handler runs; binding failures must not leak Go internals | path param strings |
| Wrapper → ErrorHandlerFunc | Single rendering point for all binding errors; must emit RFC 7807, not text/plain | error metadata |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-2-01 | Tampering | consent gate — child row without a consent event | mitigate | `children.consent_event_id NOT NULL REFERENCES consent_events(id)` (DB-enforced); BeginTx inserts consent first, child second | closed |
| T-2-02 | Tampering / Elevation of Privilege | IDOR — erasing another org's child or account (no JWT org check in Phase 2) | accept (Phase 2) → mitigate (Phase 3) | Placeholder header returns 401 when absent; `// TODO Phase 3` JWT org check marks the closure point; **MUST NOT ship to production before Phase 3** | closed |
| T-2-03 | Information Disclosure | IP address captured into `consent_events` | mitigate | Handler reads no `r.RemoteAddr` / `X-Forwarded-For`; `consent_events` schema has no `ip` or `remote_addr` column (REQ-C-009) | closed |
| T-2-04 | Repudiation | deletion with no audit trail | mitigate | `erasure_audit` INSERT runs inside the same transaction as the deletes; rollback removes both — no deletion without an audit row | closed |
| T-2-05 | Tampering | SQL injection via path params, body fields, or identity headers | mitigate | All SQL uses `$N` placeholders; member IDs bound via `pq.Array($1)`; acceptance criteria forbid `fmt.Sprintf` SQL | closed |
| T-2-06 | Elevation of Privilege | API endpoint reachable without authentication in production | mitigate (Phase 2) | Placeholder header check returns 401 when `X-Clerk-User-Id` / `X-Clerk-Org-Id` absent; `// TODO Phase 3` marks JWT replacement; **MUST NOT ship before Phase 3** | closed |
| T-2-07 | Information Disclosure | PII leakage via extra server or client schema columns | mitigate | Server: `children` has no `gender`/`photo`/`legal_name`/`dob` (REQ-008, REQ-C-005); Client SQLite: stores only `clerk_user_id`/`clerk_org_id` identifiers, no parent PII (REQ-C-005) | closed |
| T-2-08 | Tampering | Wrong-direction DB cascade deleting children when consent removed | mitigate | No `ON DELETE CASCADE` on any FK; deletion order handled explicitly in handlers | closed |
| T-2-09 | Denial of Service | Oversized or garbage request body | accept | Input validation bounds nickname length and numeric ranges; rate limiting deferred to Phase 9 (REQ-NF-004) | closed |
| T-2-10 | Tampering | FK violation leaving partially-deleted / inconsistent state | mitigate | FK-safe deletion order (children before consent_events); every `ExecContext` error checked before `Commit`; `defer Rollback` on any failure | closed |
| T-2-11 | Repudiation | Audit table unbounded growth / audit row untraceable to org | mitigate | D-12 sweep purges `erasure_audit` rows >90 days at start of each deletion tx (REQ-C-008); `target_id` is deterministic UUIDv5 of `clerk_org_id` — recomputable to locate any org's audit rows | closed |
| T-2-12 | Tampering | Invalid or un-versioned client SQLite migration corrupts local DB on upgrade | mitigate | `verifyMigrations = true` gates the build; `2.sqm` is sequential with no gaps (REQ-025); no `BEGIN`/`END` wrapper in migration file | closed |
| T-2-12† | Information Disclosure | `text/plain` raw Go error string returned for malformed path params (CR-02) | mitigate | `ProblemErrorHandler` writes a generic RFC 7807 detail naming only the static OpenAPI param; `err.Error()` is discarded and never passed to `WriteProblem` | closed |
| T-2-13 | Tampering | Inconsistent error `Content-Type` lets clients/proxies mis-handle binding errors | mitigate | All binding failures funnel through `WriteProblem` which sets `Content-Type: application/problem+json` and mirrors the HTTP status | closed |
| T-2-14 | Tampering | Partial erasure if Clerk org deletion runs inside the tx and fails | mitigate | Clerk member-list fetch happens before `BeginTx`; `DeleteOrganization` is called only after `tx.Commit`; a Clerk failure is logged (not rolled back) because DB data is already erased | closed |
| T-2-15 | Information Disclosure | Leaking Clerk API error bodies to API clients | mitigate | `clerk.go` returns status-coded errors without response bodies; handler maps them to a generic RFC 7807 500 `detail` | closed |
| T-2-SC | Tampering | Supply-chain compromise via new package installs | accept | No new packages introduced across any plan step; `goose`, `lib/pq`, `oapi-codegen`, SQLDelight 2.3.2, and `github.com/google/uuid` were already pinned in `go.mod` / version catalog (RESEARCH Package Legitimacy Audit) | closed |

*† T-2-12 ID reused across Plan 04 (client migration integrity) and Plan 05 (error disclosure). Both threats are distinct; the collision is an artifact of plan authoring order. Both are closed.*

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-02-01 | T-2-02 | IDOR (no JWT org ownership check) deferred to Phase 3 by design. All affected endpoints carry `// TODO Phase 3` guard and **MUST NOT ship to production before Phase 3 auth middleware is wired.** | plan-time design decision | 2026-06-27 |
| AR-02-02 | T-2-06 | Placeholder auth (header presence check only) deferred to Phase 3 by design. Placeholder returns 401 for absent headers. **MUST NOT ship to production before Phase 3.** | plan-time design decision | 2026-06-27 |
| AR-02-03 | T-2-09 | Basic input-validation bounds on nickname length and numeric ranges are present; full rate limiting deferred to Phase 9 (REQ-NF-004). Risk surface is low at current traffic scale. | plan-time scope decision | 2026-06-27 |
| AR-02-04 | T-2-SC | No new packages introduced across any plan step. All dependencies were already present and vetted in the existing `go.mod` / Android version catalog at the start of Phase 2. | RESEARCH Package Legitimacy Audit | 2026-06-27 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-27 | 17 | 17 | 0 | gsd-secure-phase (Claude) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-06-27
