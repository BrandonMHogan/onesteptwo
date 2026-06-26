# Phase 2: Compliance & Privacy Architecture - Context

**Gathered:** 2026-06-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 2 locks all legal obligations into the database schema and Go API before any child profile can be created. Deliverables: goose migrations for `children`, `consent_events`, `potty_events`, `device_tokens`, `notification_preferences`, and `erasure_audit` tables; the consent gate (`POST /v1/children` atomic handler); erasure cascade endpoints (child-profile deletion + full-account deletion); erasure audit log with 90-day sweep-on-request purge; and the SQLDelight `2.sqm` client-side migration. No application-layer UI, sync logic, or notification handling is built in this phase.

</domain>

<decisions>
## Implementation Decisions

### Event Type Enum

- **D-01:** The canonical event type list is **`pee, poo, both, accident, tried`** (5 values). This supersedes the older docs/04-data-model.md list (`pee, poo, both, accident, dry`) and the REQ-030 list (`pee, poo, both, accident_pee, accident_poo, tried`). The winning list was chosen to: (a) keep toast chip count at 5 for fast one-tap UX, (b) treat accidents as a single category (splitting pee/poo accident is marginal data gain vs. UX cost), (c) include `tried` (attempt without output), (d) drop `dry` (redundant with streak tracking — a dry day is simply a day with no events logged).
- **D-02:** `event_type` is stored as a **PostgreSQL native ENUM**: `CREATE TYPE event_type AS ENUM ('pee', 'poo', 'both', 'accident', 'tried')`. Column is nullable — `NULL` means quick-tap (no type selected yet). Adding new values later requires `ALTER TYPE ... ADD VALUE` (safe, no table rewrite).

### Consent Gate

- **D-03:** Consent is **per child**, not per account. COPPA requires a consent record for each child from whom data is collected. This aligns with the existing design intent.
- **D-04:** **Self-attestation checkbox is the legally acceptable consent mechanism** for OneStepTwo. The app users are adults (parents/caregivers) — children don't use the app. The FTC's stricter Verified Parental Consent mechanisms (credit card, video call) target apps where the child is the app user. Our consent screen: checkbox ("I confirm I am the parent or legal guardian of this child and am 18 years of age or older") + plain-language data explanation. This satisfies COPPA, GDPR, PIPEDA, and POPIA for v1. Legal counsel must confirm before Phase 9 store submission.
- **D-05:** **FK direction is flipped from docs/04-data-model.md.** The new design: `consent_events` has no `child_id` column. Instead, `children` gains `consent_event_id UUID NOT NULL REFERENCES consent_events(id)`. This means the DB physically cannot create a children row without referencing an existing consent_events row — no trigger needed.
- **D-06:** `consent_events` schema: `{id UUID PK, clerk_user_id TEXT NOT NULL, consented_at TIMESTAMPTZ NOT NULL, app_version TEXT NOT NULL, consent_text_version TEXT NOT NULL}`. The `consent_text_version` field (e.g., `'v1'`, `'v1.1'`) identifies which version of the consent copy the parent saw. The full consent text lives in the app's strings file, versioned in git. If consent text changes, a new version string is incremented and parents who consented under an older version can be identified for re-consent if needed.
- **D-07:** **`POST /v1/children` is a single atomic transaction.** Request payload includes both child data and consent acknowledgment: `{nickname, birth_month, birth_year, consent: {app_version, consent_text_version}}`. Go handler: BEGIN TX → INSERT consent_events (capturing the generated id) → INSERT children with `consent_event_id` pointing to the just-inserted consent row → COMMIT. No orphaned consent records are possible. The DB NOT NULL FK provides the enforcement gate.

### Erasure Cascade

- **D-08:** Hard delete cascade order for child-profile deletion: (1) hard DELETE all `potty_events` WHERE `child_id = $1`, (2) hard DELETE all `notification_preferences` WHERE `child_id = $1`, (3) hard DELETE the `consent_events` row for this child (via `children.consent_event_id`), (4) hard DELETE the `children` row. This satisfies REQ-011.
- **D-09:** Hard delete cascade order for full-account deletion: (1) trigger child-profile deletion (D-08) for each child in the org, (2) hard DELETE all `device_tokens` WHERE `clerk_user_id` is any member of the org, (3) DELETE the Clerk Organization (Clerk's responsibility for PII). Result: zero rows in our DB reference that family. Satisfies REQ-012.
- **D-10:** Deletion endpoints return a **structured JSON confirmation** (not 204 No Content): `{deleted_children: N, deleted_events: N, deleted_consent_events: N, deleted_notification_preferences: N, deleted_device_tokens: N, requested_by: clerk_user_id, requested_at: ISO timestamp}`. Satisfies REQ-013 and REQ-014's confirmation requirement.

### Erasure Audit Log

- **D-11:** `erasure_audit` table schema: `{id UUID PK, clerk_user_id TEXT NOT NULL, action TEXT NOT NULL, target_id UUID NOT NULL, target_type TEXT NOT NULL, deleted_at TIMESTAMPTZ NOT NULL}`. `action` values: `'child_deletion'`, `'account_deletion'`. `target_type`: `'child'`, `'family'`.
- **D-12:** **90-day purge is sweep-on-request**: every call to a deletion endpoint begins by running `DELETE FROM erasure_audit WHERE deleted_at < NOW() - INTERVAL '90 days'` inside the same transaction, before writing the new audit row. No Railway cron, no pg_cron extension needed. Purge only runs during deletion events (rare for a small user base — acceptable for v1).

### SQLDelight Client Schema (2.sqm)

- **D-13:** `2.sqm` creates three tables: `children`, `potty_events`, and `consent_events`. Notification preferences, device tokens, and erasure audit are server-only and are not on the client in this phase.
- **D-14:** Client-side `potty_events` adds a `sync_status TEXT NOT NULL DEFAULT 'pending'` column that does not exist in the server schema. This is a local-only column for the offline sync queue (REQ-004). All other columns mirror the server schema exactly.
- **D-15:** Client-side `potty_events` retains `deleted_at` and `deleted_by` columns (mirrors server) for visibility — the client can apply server-side soft deletes when syncing down without schema migration.

### Claude's Discretion

- Exact goose migration file numbering within Phase 2 (e.g., `00002_schema.sql`)
- Choice of `pgx/v5` vs `database/sql` for Go database access
- Index selection on `potty_events(child_id, occurred_at)` and `children(clerk_org_id)` — standard for query patterns but agent decides specifics
- Whether to use a single migration file or multiple (one per table)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope & Requirements
- `.planning/ROADMAP.md §Phase 2` — Phase goal, 5 success criteria (consent gate, erasure cascade, schema minimisation, no-IP constraint, audit log), requirement list
- `.planning/REQUIREMENTS.md` — REQ-008 (children schema), REQ-009 (consent screen), REQ-010 (consent_events before children), REQ-011 (child deletion cascade), REQ-012 (account deletion cascade), REQ-013 (verified deletion endpoints), REQ-014 (erasure right + audit), REQ-NF-003 (no auto-expiry), REQ-C-001 through REQ-C-009 (COPPA/GDPR/PIPEDA/POPIA obligations)

### Data Model & Privacy
- `docs/04-data-model.md` — Original table schemas. **NOTE: event_type enum in this file is outdated — D-01 in this CONTEXT.md supersedes it. FK direction for consent_events → children is also changed per D-05.** All other decisions in this doc remain valid.
- `docs/05-privacy.md` — Right to erasure spec, consent requirements, data minimisation rules, data residency notes

### Stack Decisions (LOCKED)
- `.planning/PROJECT.md §Constraints` — Hard regulatory gates (consent_events ordering, no IP, hard DELETE for erasure) and technical constraints (goose migrations, PostgreSQL, Railway)
- `.planning/PROJECT.md §Stack` — Go module path, Railway deployment, PostgreSQL, Clerk auth pattern (clerk_user_id FK strategy)

### Mobile Schema
- `shared/src/commonMain/sqldelight/migrations/1.sqm` — Empty Phase 1 placeholder; 2.sqm builds on this with real schema
- `.planning/REQUIREMENTS.md REQ-025` — SQLDelight .sqm migration numbering rules (no gaps)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `backend/internal/api/problem.go` — RFC 7807 Problem Details helper already implemented. Use this for all error responses from new endpoints (deletion failures, consent validation errors). Do not write a new error helper.
- `backend/internal/api/server.go` — `Server{}` struct implements the oapi-codegen `ServerInterface`. New handlers for `POST /v1/children`, `DELETE /v1/children/{id}`, `DELETE /v1/account` should follow this same pattern.
- `backend/cmd/migrate/main.go` — goose migration runner. Phase 2 adds `00002_schema.sql` (or similar) to `backend/db/migrations/`. Run staging-first per locked workflow.

### Established Patterns
- `backend/db/migrations/00001_init.sql` — Already exists as the goose migration base. Phase 2 adds the next migration file. No gaps in numbering.
- `shared/src/commonMain/sqldelight/migrations/1.sqm` — Empty Phase 1 SQLDelight migration. Phase 2 adds `2.sqm` with the full client schema.
- All Go handlers registered via oapi-codegen generated interface; any new endpoints must be added to `api/openapi.yaml` first (make generate regenerates `generated.go`).

### Integration Points
- `api/openapi.yaml` — Must be updated with `POST /v1/children`, `DELETE /v1/children/{id}`, `DELETE /v1/account` before any Go handler code is written. Regenerate `backend/internal/api/generated.go` via `make generate`.
- The Go backend has no auth middleware yet (Phase 3 adds Clerk JWT validation). Phase 2 deletion endpoints MUST NOT be deployed without auth — they can be implemented in Phase 2 but should either be auth-gated with a placeholder check or noted as requiring Phase 3 to be secure in production.

</code_context>

<specifics>
## Specific Ideas

- The consent text shown in the app must be versioned in the app's strings file as `consent_v1` (or similar) — the `consent_text_version` value written to the DB must match this identifier so future audits can retrieve the exact copy the parent saw.
- The erasure endpoint response body doubles as the in-app "what was deleted" confirmation (REQ-014 requires it be clearly labelled and easy to find). The JSON shape defined in D-10 should be designed for readability in the app UI as well as for programmatic use.
- docs/04-data-model.md should be updated by the executor to reflect D-01 (event_type values) and D-05 (FK direction flip) — these are now CONTEXT.md decisions that override the doc.

</specifics>

<deferred>
## Deferred Ideas

- **Stool consistency tracking** (normal/hard/loose) — clinical-grade detail, out of scope for v1. May be relevant if the app targets children with medical conditions in a future version.
- **Separate accident subtypes** (accident_pee / accident_poo) — came up during event type discussion; deferred because the UX cost (6+ toast chips) outweighs the data granularity benefit for v1. Revisit if users request it post-launch.
- **consent_texts table** (storing full consent copy in DB) — discussed and deferred in favor of version string + git. Revisit if legal counsel requires in-DB evidence.
- **Row-count detail in erasure response** (e.g., `{potty_events: 42}`) — deferred from D-10; minimal fields chosen for v1. Easy to add if legal or user feedback demands it.
- **pg_cron / Railway cron for audit purge** — deferred in favor of sweep-on-request for v1. When user base grows and deletion events become more frequent, revisit scheduled purge.

</deferred>

---

*Phase: 2-Compliance & Privacy Architecture*
*Context gathered: 2026-06-26*
