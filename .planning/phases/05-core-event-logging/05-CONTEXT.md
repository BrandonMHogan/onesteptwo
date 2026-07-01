# Phase 5: Core Event Logging - Context

**Gathered:** 2026-06-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 5 delivers the onboarding wizard (family creation + consent + first child profile), the 4-tab navigation shell, one-tap potty event logging with a details-later flow, the History heatmap tab, the Progress tab's child switcher (full Progress *data* is Phase 7 — Phase 5 only needs the switcher to update the active child context across tabs), and the Settings tab (family/children/notifications/account management). Engineers implement from `docs/WIREFRAMES.md` and `.planning/phases/04-ui-ux-design/04-UI-SPEC.md` — Phase 4 already produced the full visual/interaction spec; Phase 5 is implementation, not design.

**Hard scope line:** potty events are a fully local, single-device feature in Phase 5. No Go API calls exist for events at all — they live only in SQLDelight, written with `sync_status` semantics reserved for Phase 6. Cross-device/cross-caregiver visibility of events does not happen until Phase 6 ships the sync layer. Settings actions (child CRUD, notification preferences, invite/remove caregiver) are *not* part of this boundary — they hit real Go/Clerk APIs directly in Phase 5, since they are admin actions, not an offline-first event log.

</domain>

<decisions>
## Implementation Decisions

### Phase 5 / Phase 6 Boundary
- **D-01:** Potty events are **fully local-only in Phase 5** — SQLDelight is the only store touched; no `/v1/events` Go endpoints are built and no network call is attempted for event writes/edits/deletes. Phase 6 owns the entire sync layer (POST, retry-on-reconnect, pending-sync indicator) per REQ-004/005. This matches `docs/SCREEN-FLOWS.md`'s existing "Offline note" (steps 2/5/7 write to SQLDelight only; network sync occurs separately) — Phase 5 just had to commit to it explicitly as a phase boundary.
- **D-02:** Settings actions **do** hit real backend APIs directly in Phase 5: child add/edit/remove (Go endpoints), notification preference toggle (Go endpoint, see D-03), and invite/remove caregiver (direct Clerk SDK calls, see D-08). These are synchronous admin actions, not subject to the offline-first event model, and reasonably require connectivity.

### Notification Preferences
- **D-03:** Phase 5 builds the **real** `notification_preferences` table + upsert endpoint (REQ-022) and persists the Settings toggle for real — not stubbed. Phase 8 only adds the FCM-sending logic on top; it does not need to revisit Settings UI or storage.

### Event Type Enum (contradiction found and resolved)
- **D-04:** `event_type` is the **5-value list** `pee, poo, both, accident, tried` — single `accident` category, no `accident_pee`/`accident_poo` split. This was already locked in Phase 2 (`02-CONTEXT.md` D-01: "6+ chip UX cost > data value for v1") and matches the applied migration (`backend/db/migrations/00002_schema.sql`). Phase 4's wireframes/UI-SPEC and `.planning/REQUIREMENTS.md` REQ-030 had regressed to the rejected 6-value split — corrected in this session across `docs/WIREFRAMES.md`, `docs/SCREEN-FLOWS.md`, `.planning/phases/04-ui-ux-design/04-UI-SPEC.md`, `.planning/phases/04-ui-ux-design/04-CONTEXT.md`, `.planning/REQUIREMENTS.md`, and `.planning/ROADMAP.md`.

### Event Time Editing
- **D-05:** The log button always writes `occurred_at = now()` at tap time — no backfill at creation. The Event Detail sheet's "Logged at [HH:mm]" field allows adjusting the time **within the same calendar day only** — no date picker, matching the wireframe exactly as drawn. An edit that would move the event to a different day is out of scope for v1.

### Event Edit & Delete (gap found and resolved)
- **D-06:** Every event card in History Day-Detail is tappable (not just pending/no-type cards) — tapping reopens the Event Detail bottom sheet pre-filled with that event's type/note/time. A **"Delete event"** destructive action lives inside that same sheet, using the same `AlertDialog`/`.alert` confirmation pattern already specified for remove-child/remove-caregiver/delete-my-data (REQ-007 requires this capability; the Phase 4 wireframes had no affordance for it at all).

### Heatmap Window
- **D-07:** The History heatmap **grows incrementally from week 1**, capping at a fixed 12 weeks — it does not pre-render empty weeks before they exist, and it does not scroll/paginate to load older history beyond 12 weeks in v1.

### Caregiver Removal
- **D-08:** Removing a caregiver is a **direct Clerk SDK org-membership-removal call** from the app — no new Go endpoint — consistent with REQ-017's "no custom invitation backend logic" for invites. Accepted minor gap: that caregiver's `notification_preferences` rows become orphaned (stale `clerk_user_id` no longer in the org); no cleanup job exists in v1.

### Per-Child Consent
- **D-09:** "Add child" from Settings **repeats the full consent screen** (self-attestation checkbox + plain-language explanation) and inserts a fresh `consent_events` row before the new `children` row — identical to the onboarding wizard's consent step. This is what the schema already enforces (D-05 in Phase 2: `consent_events` has no `child_id`, so there is no per-family shortcut), and matches a per-child reading of COPPA/GDPR consent.

### Pending-Details Banner Scope
- **D-10:** The Home screen's `[N need details]` chip counts **only the currently active child's** events with `event_type = NULL` — not all children in the family combined. Consistent with REQ-031 (switching active child updates Home/History/Progress together as one unit).

### Admin Model (clarified, not re-decided)
- **D-11 (clarification, not new):** v1 supports exactly one admin per family. REQ-017 always assigns `org:caregiver` on invite — there is no promote-to-admin or invite-as-admin path. The Settings Family list therefore never renders a remove `[✕]` action on the admin's own row.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Requirements
- `.planning/REQUIREMENTS.md` — REQ-003, REQ-006, REQ-007 (event data rules, local-only in Phase 5), REQ-030 (event_type, corrected to 5 values), REQ-031 (child switcher), REQ-032 (pending banner, active-child-only per D-10), REQ-033 (heatmap, 12-week cap per D-07), REQ-035 (4-tab nav, Phase 4 complete), REQ-036 (onboarding wizard)
- `.planning/PROJECT.md` — Stack, locked decisions, non-goals
- `.planning/ROADMAP.md` §Phase 5 — Success criteria

### UI/UX Spec (Phase 4 output — implement from this, no new design decisions)
- `docs/WIREFRAMES.md` — All 24 wireframes; corrected for D-04 (event_type) and pending D-06 (event edit/delete needs a new wireframe note for "tap any card" + delete-in-sheet — not yet drawn, flag for plan)
- `docs/SCREEN-FLOWS.md` — Log → toast → sheet flow; offline note already states local-only-in-Phase-5 (D-01)
- `docs/DESIGN-TOKENS.md` — Colors, type, spacing
- `.planning/phases/04-ui-ux-design/04-UI-SPEC.md` — Component specs, destructive-action confirmation table (§Destructive Action Confirmations — reuse for D-06's delete-event flow; needs a new row added: "Remove event")

### Data Model & Backend
- `docs/04-data-model.md` — `potty_events`, `notification_preferences`, `children`, `consent_events` schemas; deletion cascade order
- `backend/db/migrations/00002_schema.sql` — Authoritative `event_type` ENUM (5 values, confirms D-04)
- `api/openapi.yaml` — Current state: only `GET /healthz`, `POST/DELETE /v1/children`, `DELETE /v1/account` exist. Phase 5 must add: `GET /v1/children` (list), `PATCH /v1/children/{id}` (edit), `GET/PUT /v1/notification-preferences` (or similar, per D-03). Phase 5 must **not** add `/v1/events` (per D-01 — that's Phase 6).

### Auth & Roles
- `docs/06-auth.md` — org:admin vs org:caregiver; Settings section visibility gating

### Existing Code (Phase 3 reusable assets)
- `androidApp/.../ui/settings/InviteCaregiverScreen.kt` and `iosApp/.../Settings/InviteCaregiverView.swift` — already built in Phase 3; Phase 5 wires Settings tab navigation into these rather than rebuilding them
- `androidApp/.../ui/PostAuthStub.kt` — placeholder Phase 5 replaces with the real 4-tab nav shell

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `InviteCaregiverScreen`/`InviteCaregiverView` (Android + iOS, Phase 3) — fully built, just needs to be reachable from the new Settings tab
- `OrgPickerScreen`/`OrgPickerView` (Phase 3) — multi-org picker, precedes Phase 5's onboarding/main-app entry point
- `PostAuthStub.kt` (Android) — current post-auth landing point; Phase 5 replaces it with the 4-tab nav + onboarding wizard gate

### Established Patterns
- Clerk owns family/org membership (invite, remove) — Go backend never touches membership directly, only child/event/notification data (D-08 extends this pattern to removal)
- SQLDelight is local source of truth; Go API is the sync target, not the primary write path, for the event log specifically (D-01)
- Destructive actions use native `AlertDialog`/`.alert` with a consistent title/body/confirm/dismiss shape already tabulated in 04-UI-SPEC.md §Destructive Action Confirmations

### Integration Points
- Child switcher (component 9) already specified in Phase 4; Phase 5 wires it to update Home/History/Progress active-child context, and now also gates the pending-details banner (D-10) and notification toggle list to the active child
- Event Detail bottom sheet (component 7) already specified for the post-log "add details" path; D-06 extends its reuse to "edit/delete any existing event" from Day-Detail

</code_context>

<specifics>
## Specific Ideas

- Heatmap should feel alive from day one — a brand-new family sees one tiny week-1 column, not a wall of 12 weeks of empty cells (D-07).
- "Delete event" should not be a new UI pattern — reuse the exact destructive-confirmation shape already used everywhere else in the app (D-06).

</specifics>

<deferred>
## Deferred Ideas

- Date-changing event edits (moving an event to a different day) — out of scope for v1 (D-05); revisit if users request retroactive cross-day logging.
- Cleanup of orphaned `notification_preferences` rows after caregiver removal — accepted gap for v1 (D-08).
- Multi-admin / promote-to-admin — not part of v1's role model (D-11); no requirement currently calls for it.

</deferred>

---

*Phase: 5-Core Event Logging*
*Context gathered: 2026-06-30*
