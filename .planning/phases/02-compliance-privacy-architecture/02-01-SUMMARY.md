---
phase: 02-compliance-privacy-architecture
plan: 01
subsystem: database
tags: [postgresql, schema, migration, goose, compliance, data-minimisation, consent-gate]
dependency_graph:
  requires: []
  provides:
    - backend/db/migrations/00002_schema.sql
    - docs/04-data-model.md (reconciled)
  affects:
    - Phase 02 plans 02-03 (Go handlers require this schema)
    - Phase 02 plan 02-04 (SQLDelight 2.sqm mirrors server schema)
tech_stack:
  added: []
  patterns:
    - goose Up/Down SQL migration with FK-dependency-ordered DDL
    - gen_random_uuid() built-in (no extension) for server-side UUIDs
    - client-generated UUID pattern (no DEFAULT on potty_events.id)
key_files:
  created:
    - backend/db/migrations/00002_schema.sql
  modified:
    - docs/04-data-model.md
decisions:
  - D-01: event_type ENUM = ('pee','poo','both','accident','tried') — supersedes legacy 'dry' and split accident values
  - D-02: event_type is PostgreSQL native ENUM; nullable (NULL = quick-tap)
  - D-05: FK direction — children.consent_event_id NOT NULL REFERENCES consent_events(id); consent_events has no child_id
  - D-06: consent_events schema — no IP, no PII beyond clerk_user_id
metrics:
  duration: ~3 min
  completed: 2026-06-26
  tasks_completed: 2
  files_created: 1
  files_modified: 1
---

# Phase 02 Plan 01: Schema Migration and Data Model Reconciliation Summary

**One-liner:** Goose migration 00002_schema.sql creates six PostgreSQL tables with consent gate FK (children.consent_event_id NOT NULL REFERENCES consent_events) and no PII columns; docs/04-data-model.md reconciled with D-01 event_type values and D-05 FK direction.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Write the 00002_schema.sql goose migration | 51b1f83 | backend/db/migrations/00002_schema.sql |
| 2 | Reconcile docs/04-data-model.md with D-01 and D-05 | 9ea42cb | docs/04-data-model.md |

## What Was Built

**Task 1 — 00002_schema.sql:**

A single goose migration file defining the complete server-side relational schema for OneStepTwo. The file begins with `-- +goose Up` (goose convention) and creates in FK-dependency order:

1. `CREATE TYPE event_type AS ENUM ('pee', 'poo', 'both', 'accident', 'tried')` — D-01 canonical values
2. `CREATE TYPE device_platform AS ENUM ('android', 'ios')`
3. `consent_events` — 5 columns (id, clerk_user_id, consented_at, app_version, consent_text_version); no child_id, no IP
4. `children` — 8 columns including `consent_event_id UUID NOT NULL REFERENCES consent_events(id)`; no PII; birth_month CHECK (1-12); index on clerk_org_id
5. `potty_events` — id has NO DEFAULT (client-generated); event_type nullable; soft-delete columns; index on (child_id, occurred_at)
6. `device_tokens` — no FK to children
7. `notification_preferences` — UNIQUE(clerk_user_id, child_id); FK to children
8. `erasure_audit` — no FK to any table (survives after data deletion per D-11)

The `-- +goose Down` section drops tables in reverse FK-dependency order with a prominent warning that it is for local development only and must never run on staging or production.

No `ON DELETE CASCADE` appears anywhere — deletion ordering is handled explicitly in application code (Plan 03 handlers) to preserve count tracking for GDPR erasure responses.

`goose validate` exits 0 on the completed file.

**Task 2 — docs/04-data-model.md:**

Updated to align with locked decisions:
- event_type canonical values corrected to: `pee, poo, both, accident, tried`; note added that column is PostgreSQL ENUM and nullable (NULL = quick-tap)
- FK direction corrected: consent_events has no child_id; children.consent_event_id is the consent gate
- Children column list expanded to 8 columns including consent_event_id with a note clarifying it is a compliance FK (not a PII field) and why this overrides REQ-008's original column count
- consent_events documented with consent_text_version field, no-IP constraint (REQ-C-009), and no-PII rule (REQ-C-005)
- Deletion cascade order corrected for D-05 FK direction (children must be deleted before consent_events to release FK reference)

## Verification Results

All acceptance criteria passed before commit:

**Task 1 (migration):**
- goose validate exits 0
- All 6 tables and 2 ENUM types present
- consent_event_id UUID NOT NULL REFERENCES consent_events(id) present in children
- child_id UUID NOT NULL REFERENCES children(id) present in potty_events and notification_preferences
- consent_events block has 0 IP-related columns
- children block has no gender, photo, legal_name, full_name, or dob columns
- UNIQUE(clerk_user_id, child_id) present on notification_preferences
- Both indexes present: idx_children_clerk_org_id and idx_potty_events_child_occurred
- potty_events.id has no DEFAULT (client-generated UUID)
- Down section drops erasure_audit before consent_events
- No ON DELETE CASCADE anywhere

**Task 2 (docs):**
- consent_event_id present in doc
- event_type list reads `pee, poo, both, accident, tried`
- No `accident_pee` or `accident_poo` in file
- consent_events documented with no child_id and referencing children FK direction
- No IP address statement present (REQ-C-009)
- Verify automation command passes

## Deviations from Plan

None — plan executed exactly as written. All acceptance criteria passed on first run.

The only adjustment was that the explanation text for Task 2 initially mentioned deprecated values (`accident_pee`, `accident_poo`, and usage of `dry`) in a historical context, which triggered the plan's grep-based acceptance criteria. Rewording removed those references while preserving the intent of the explanations.

## Known Stubs

None. This plan creates schema DDL and documentation only. No application code, no data flows, no stubs.

## Threat Flags

No new threat surface introduced beyond what the plan's threat model covers. All T-2-xx mitigations are implemented:
- T-2-01 (consent gate): children.consent_event_id NOT NULL REFERENCES consent_events(id) — verified present
- T-2-03 (IP storage): consent_events schema has no ip/remote_addr column — verified by grep (count 0)
- T-2-07 (PII leakage): children has no gender/photo/legal_name/dob — verified by grep
- T-2-08 (cascade direction): no ON DELETE CASCADE anywhere in migration — verified by grep

## Self-Check: PASSED

Files created/exist:
- backend/db/migrations/00002_schema.sql: FOUND
- docs/04-data-model.md (modified): FOUND

Commits exist:
- 51b1f83 (Task 1 migration): FOUND
- 9ea42cb (Task 2 docs): FOUND
