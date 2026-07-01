# Phase 5: Core Event Logging - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-30
**Phase:** 5-Core Event Logging
**Areas discussed:** Notification preferences scope, event_type contradiction, event time editing, event edit/delete UX, heatmap window, per-child consent, Phase 5/6 networking boundary, Settings API scope, caregiver removal, pending-banner scope

---

## Notification Preferences Scope

REQ-022 (notification_preferences table + upsert endpoint) is tagged Phase 8 in REQUIREMENTS.md, but the Phase 5 Settings tab success criterion requires a working toggle.

| Option | Description | Selected |
|--------|-------------|----------|
| Build real write-path now | Phase 5 creates the table + upsert endpoint for real | ✓ |
| Stub for Phase 5 | Toggle renders but doesn't persist; built for real in Phase 8 | |

**User's choice:** Build real write-path now (recommended option)

---

## event_type Enum Contradiction

`backend/db/migrations/00002_schema.sql` (applied, Phase 2) defines a 5-value ENUM (`pee, poo, both, accident, tried`). `docs/WIREFRAMES.md` and `.planning/REQUIREMENTS.md` REQ-030 (Phase 4) specify 6 values, splitting `accident_pee`/`accident_poo`. Inserting the split value would fail against the applied schema.

| Option | Description | Selected |
|--------|-------------|----------|
| Collapse UI to single Accident | Fix WIREFRAMES.md/REQ-030 to match the applied 5-value enum | ✓ |
| Split DB enum to match UI | ALTER TYPE to add the two split values | |

**User's choice:** Collapse UI to single Accident (recommended option)

**Notes:** Post-decision investigation found this was *already* locked in Phase 2's `02-CONTEXT.md` D-01, with the split explicitly considered and rejected ("6+ chip UX cost > data value for v1"). Phase 4 had regressed to the rejected list. Confirms the decision was correct, not just a new call.

---

## Event Time Editing — Backfill Scope

The Event Detail sheet's "time adjustment" field is wireframed as time-only (`Logged at [HH:mm]`, no date picker).

| Option | Description | Selected |
|--------|-------------|----------|
| Same-day time-only | occurred_at nudge within the logged day only, matches wireframe | |
| Full backfill (date + time) | Caregivers can log/move an event to a previous day entirely | |

**User's free-text answer:** "The log button for now just logs right at the time. But we do have a means of editing (or should) so if they wanted to adjust the times later then can" — required a follow-up to pin down date-vs-time-only scope.

**Follow-up question:**

| Option | Description | Selected |
|--------|-------------|----------|
| Time-of-day only, same day | Matches current wireframe exactly | ✓ |
| Full date + time edit | Requires a new date picker, wireframe change | |

**User's choice:** Time-of-day only, same day

---

## Event Edit/Delete UX Gap

REQ-007 requires caregivers be able to delete a duplicate/incorrect event (soft delete columns already exist in the schema), but no wireframe affordance exists for it — only pending (no-type) cards are tappable, and there's no delete gesture anywhere on an event card.

| Option | Description | Selected |
|--------|-------------|----------|
| Tap card to edit, delete inside sheet | Every event card reopens the Event Detail sheet pre-filled; "Delete event" lives inside the sheet, reusing the existing destructive-confirmation pattern | ✓ |
| Swipe/long-press to delete, tap to edit | Mirror the Family section's swipe/long-press pattern for events specifically | |

**User's choice:** Tap card to edit, delete inside sheet (recommended option)

---

## Heatmap Rolling Window

REQ-033 says "rolling window... most recent days and weeks" with no concrete week count or scroll behavior specified anywhere.

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed ~12 weeks, no scroll | Bounded SQLDelight query, matches Home tab's non-scrollable precedent | ✓ (with nuance) |
| Scrollable, loads older weeks | Indefinite scroll-back like GitHub's contribution graph | |

**User's choice:** "it should max at 12 weeks no scroll, as you recommended. but it should grow to that. Like if the user is on their first week, just show week one. That way its not a ton of empty sections" — confirmed the 12-week cap, added the incremental-growth nuance (D-07).

---

## Per-Child Consent on "Add Child"

`consent_events` has no `child_id` column; `children.consent_event_id` requires a fresh consent_events row per child (Phase 2 D-05 schema gate). Unclear whether Settings' "Add child" flow repeats the full consent screen or treats consent as a one-time family-level action.

| Option | Description | Selected |
|--------|-------------|----------|
| Repeat full consent screen per child | Matches the DB gate exactly; per-child legal reading | ✓ |
| One-time family consent | Would require relaxing or cloning the DB gate | |

**User's choice:** Repeat full consent screen per child (recommended option)

---

## Phase 5 / Phase 6 Networking Boundary

REQ-004 (SQLDelight write → Ktor POST → retry-on-reconnect) is tagged Phase 6, but Phase 5's own success criteria never mention a server round-trip for events — only "registered immediately in SQLDelight." Backend currently has zero event endpoints.

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 5 fully local-only | No Go API calls for events at all in Phase 5; Phase 6 owns all event networking | ✓ |
| Phase 5 builds endpoints + basic sync | Phase 5 builds /v1/events and a simple non-retrying POST; Phase 6 only adds retry/queue polish | |

**User's choice:** Phase 5 fully local-only (recommended option)

**Follow-up — does this extend to Settings actions?**

| Option | Description | Selected |
|--------|-------------|----------|
| Settings hits real APIs directly now | Child CRUD, notification toggle, invite all call live endpoints/Clerk in Phase 5 | ✓ |
| Defer all backend writes | Settings also stubbed/local-only until later phases | |

**User's choice:** Settings hits real APIs directly now (recommended option) — resolves the apparent tension with the earlier notification-preferences decision: the local-only boundary is specific to the append-only event log, not Settings actions generally.

---

## Caregiver Removal

REQ-017 says invites use a direct Clerk SDK call with "no custom invitation backend logic." No equivalent requirement exists for removal.

| Option | Description | Selected |
|--------|-------------|----------|
| Direct Clerk SDK call | No new Go endpoint, consistent with invite | ✓ |
| New Go endpoint wraps removal | Gives the backend a hook for additional bookkeeping (e.g. notification_preferences cleanup) | |

**User's choice:** Direct Clerk SDK call (recommended option) — accepted the resulting orphaned-notification_preferences-row gap as a known v1 limitation.

---

## Pending-Details Banner Scope

REQ-032's "[N need details]" chip count is unspecified for multi-child families — active child only, or family-wide?

| Option | Description | Selected |
|--------|-------------|----------|
| Active child only | Consistent with REQ-031 (switching child updates Home/History/Progress together) | ✓ |
| All children combined | Family-wide need-attention count regardless of active child | |

**User's choice:** Active child only (recommended option)

---

*Phase: 5-Core Event Logging*
*Discussion logged: 2026-06-30*
