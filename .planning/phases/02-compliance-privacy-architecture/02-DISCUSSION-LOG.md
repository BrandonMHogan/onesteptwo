# Phase 2: Compliance & Privacy Architecture - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-26
**Phase:** 2-Compliance & Privacy Architecture
**Areas discussed:** Event type enum, Consent gate enforcement, Audit purge mechanism, SQLDelight client schema

---

## Event Type Enum

| Option | Description | Selected |
|--------|-------------|----------|
| REQ-030 list: pee, poo, both, accident_pee, accident_poo, tried | 6 values; splits accidents by type | |
| docs/04-data-model.md list: pee, poo, both, accident, dry | 5 values; 'dry' = stayed dry | |
| **pee, poo, both, accident, tried — 5 values** | One accident type, 'tried' for attempt-without-output, drop 'dry' | ✓ |
| Merge both lists (7 values) | All variants from both sources | |

**User's choice:** 5-value list after researching industry patterns

**Notes:** User requested online research before deciding. Research showed most potty training apps use 4–5 values (Potty Toilet Training iOS: wee, poo, nothing, accident). No major app splits accident into pee/poo subtypes in quick-tap UI. Decision to drop 'dry' (redundant with streak tracking) and not split accidents (keeps toast chips at 5 for fast UX). User also requested research into whether a custom/freeform option made sense — decided not to include for v1 due to schema and display complexity.

**PostgreSQL storage:** Native ENUM type (`CREATE TYPE event_type AS ENUM (...)`) — provides DB-level enforcement and type safety. Chosen over VARCHAR + CHECK constraint.

---

## Consent Gate Enforcement

| Option | Description | Selected |
|--------|-------------|----------|
| Flip FK: children.consent_event_id NOT NULL | DB can't create child without consent row | ✓ |
| BEFORE INSERT trigger on children | Trigger checks consent_events before insert | |
| Single TX Go handler only (DEFERRABLE FK) | App-level enforcement, deferred constraint | |

**User's choice:** Flip FK (with single atomic TX, not two API calls)

**Notes:** User asked several research questions before deciding:

1. *Is consent per child or per account?* — Researched COPPA FTC FAQ. Answer: per child. "You must keep a record of having obtained verifiable parental consent for each child from which you collect personal data."

2. *What data must be stored in the consent record? Is a checkbox enough?* — Researched COPPA requirements. Key finding: OneStepTwo is parent-uses-app (not child-uses-app). The FTC's stricter VPC mechanisms (credit card, video call) target cases where the child is the app user. Self-attestation checkbox IS acceptable for our model. Required fields: who consented, when, which consent copy they saw.

3. *Should consent text be stored in its own table?* — User chose: consent_text_version VARCHAR field + full text in app strings/git. Separate table deemed overkill for v1.

4. *What about abandoned consent records (consent created, child never created)?* — User highlighted this as a concern with the two-step approach. Resolution: use a single atomic TX (POST /v1/children includes consent payload). No orphaned records possible.

Final consent_events schema: `{id, clerk_user_id, consented_at, app_version, consent_text_version}` — no child_id. FK goes the other way: children.consent_event_id → consent_events.

---

## Audit Purge Mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| **Sweep-on-request inside deletion handler** | Purge old rows every time a deletion is called | ✓ |
| Railway cron job calling DELETE /internal/audit/purge | Scheduled purge, requires Railway cron + internal endpoint | |
| pg_cron PostgreSQL extension | DB-level scheduled job | |

**User's choice:** Sweep-on-request (recommended option)

**Notes:** Simplest approach — zero infrastructure beyond the deletion handler itself. Works for a small user base where deletions are infrequent. User accepted the caveat that purge only runs during deletion events. pg_cron availability on Railway-managed Postgres was noted as uncertain.

**Erasure audit row fields:** `clerk_user_id, action, target_id, target_type, deleted_at` — minimal but complete. User declined the `row_counts JSON` variant.

---

## SQLDelight Client Schema

| Option | Description | Selected |
|--------|-------------|----------|
| children + potty_events only | Minimal for Phase 2 | |
| **children + potty_events + consent_events** | Includes consent for onboarding wizard (Phase 5) | ✓ |
| All tables (+ notification_preferences) | Full schema in one shot | |

**User's choice:** children + potty_events + consent_events

**Notes:** consent_events included because the Phase 5 onboarding wizard needs to record consent locally before syncing. Including it now avoids a later 3.sqm addition. notification_preferences deferred to Phase 8.

**Client-side potty_events divergence:** Adds `sync_status TEXT NOT NULL DEFAULT 'pending'` (local-only offline queue column). All other columns mirror server schema exactly, including `deleted_at` and `deleted_by`.

---

## Claude's Discretion

- Exact goose migration file numbering within Phase 2
- Go database driver choice (pgx/v5 vs database/sql)
- Index selection on potty_events(child_id, occurred_at) and children(clerk_org_id)
- Whether to use a single migration file or multiple

## Deferred Ideas

- Stool consistency tracking (normal/hard/loose) — medical-grade, out of scope v1
- accident_pee / accident_poo subtypes — 6+ chip UX cost > data value for v1
- consent_texts table — version string + git sufficient for v1
- Row-count detail in erasure response — easy to add post-launch if needed
- pg_cron / scheduled cron for audit purge — revisit when deletion volume grows
