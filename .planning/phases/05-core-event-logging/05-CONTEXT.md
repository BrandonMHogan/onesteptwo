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

### Persistent Cross-Tab Child Switcher + Swipe Gesture (post-Stage-2 revision)
- **D-12:** This is a deliberate revision of REQ-031 and 04-UI-SPEC.md §Component 9, gathered via a `superpowers:grilling` session on 2026-07-01 per `05-FOLLOWUP-persistent-child-switcher.md`. It **supersedes** the Home-only, tap-only design Stage 1 built (not a bug — see the `post_stage2_followup` note below). Decisions:
  - **Placement:** the switcher banner replaces the existing name element on all three tabs — Home's inline header, and newly-added banners on History (above the heatmap) and Progress (replacing the plain 14sp "Active child name" line from 04-UI-SPEC.md §Main App — Progress Tab item 1).
  - **Single-child families:** the banner **always renders** (name + age, centered) even with one child — it is only the *interactive* affordance (tap-to-open-sheet, swipe-to-cycle) that is absent. This refines REQ-031's "shows no switcher" to mean "no interactive control," not "no banner." Layout stays visually consistent across family sizes and gives History/Progress a name label they previously lacked entirely.
  - **Banner visual design:** centered stack — child name (Title 20sp semibold) centered at top, age (formatted "Xy Ym", computed from `birth_month`/`birth_year` vs. current date) as a smaller styled caption centered directly below it, and — only when the family has 2+ children — a row of carousel-style page-indicator dots centered below the age, one dot per child, active dot filled. The dots replace the old chevron icon as the "this is interactive" signal; no chevron in the new design. No letter-circle avatar (none exists in the current code either). A future per-child emoji/icon (not a photo — REQ-008 still forbids photos) will slot to the left of the name; that field does not exist yet and is out of scope here.
  - **Swipe mechanism:** the entire tab screen becomes a horizontal pager (Compose `HorizontalPager`, one page per child) — banner and all tab content (heatmap, log button, streak stats) page together as a single unit, tracking the drag live. This is a "one-handed carousel" gesture, not a banner-only micro-interaction. `HorizontalPager` only claims the horizontal axis, so it does not conflict with History's/Progress's existing vertical scrolling.
  - **Wrap-around:** looping — swiping past the last child goes to the first, and vice versa. `HorizontalPager` has no native loop support, so implementation needs a virtual/modulo page-index trick (flag this explicitly in the implementation plan).
  - **Order:** matches the existing `ChildSwitcherSheet` order — `children` sorted by `created_at ASC` (`Children.sq` `selectAll`). No new ordering introduced.
  - **Accessibility:** the existing tap-the-banner-to-open-`ChildSwitcherSheet` path is unchanged and remains the sole accessible path (swipe is not screen-reader-accessible). The banner's `contentDescription` is updated to mention both affordances, e.g. `"[Name], active child. Double tap to open child list, swipe left or right to switch."`
  - **Scope limit:** the banner/pager appears **only** on the three tab roots (Home, History, Progress). It does **not** appear on History Day-Detail (full-screen push, tab bar hidden per REQ-035) or on any Settings sub-screen (Add/Edit child, Invite caregiver) — unchanged from today.
  - **Animation:** `HorizontalPager`'s built-in default fling/snap animation, documented in 04-UI-SPEC.md as "Pager default" — same precedent as the Bottom Sheet open/close row (D-35), which also defers to platform/framework default rather than a hand-picked tween. No new duration/easing token invented.
  - **Requirement numbering:** amended REQ-031 in place (a refinement of the same requirement — same control, wider reach — not additive new scope), per repo convention (see the Phase 2 D-04 event_type correction for precedent).
  - **Verified in code, not re-decided:** History's heatmap (`HeatmapView.kt`) and the planned Progress layout already grow vertically (weeks stack as rows going down; no horizontal scroll exists today), so the new whole-screen horizontal pager does not require any heatmap/Progress layout-direction changes.

### Child Switcher Swipe: Depth Transform (post-implementation revision)
- **D-13:** After D-12 shipped and was tested live on-device, the user asked for more tactile depth on the swipe transition than the flat "Pager default" motion alone gave — this **revises** D-12's Animation line (04-UI-SPEC.md §Motion Tokens "Child switcher swipe/page (D-12)") to add a **subtle depth parallax page transform** layered on top of the same underlying scroll physics: as a page moves away from center (in either direction, by drag or by `animateScrollToPage`), it scales down from 100% to 95% and its `shadowElevation` drops from `elevation.raised` (2dp, per docs/DESIGN-TOKENS.md §Elevation Tokens) to `elevation.flat` (0dp); the centered page is always at 100% scale / 2dp elevation. Both values move together, driven by `((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)`.
  - **What this does NOT change:** the scroll/fling/snap animation itself is still 100% `HorizontalPager` built-in default — no `AnimationSpec` was added to `animateScrollToPage` or anywhere else. This is a per-frame **visual transform** (`Modifier.graphicsLayer { scaleX/scaleY/shadowElevation }`) read directly off the pager's live scroll position inside the page's `graphicsLayer` block (the standard, official Compose Foundation pattern for Pager page transformations — still Compose built-in APIs only, D-30/D-31 unaffected).
  - **Why these exact values:** scale range (100%→95%) and elevation range (2dp→0dp) reuse existing docs/DESIGN-TOKENS.md tokens (`elevation.raised`/`elevation.flat`) rather than inventing new ones, consistent with this repo's "no new tokens without justification" pattern established elsewhere in 04-UI-SPEC.md.
  - Two alternate interpretations were offered and rejected: (a) incoming-card-slides-fully-over-outgoing (asymmetric depth swap), and (b) outgoing-card-lifts-and-exits-while-incoming-stays-flat (asymmetric lift-and-toss). Both were rejected in favor of the symmetric, subtler "both cards gently depth-differentiate while sliding together" effect described above.

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

<post_stage2_followup>
## Post-Stage-2 Follow-Up: Persistent Cross-Tab Child Switcher + Swipe Gesture

**Status:** Designed (grilling session complete 2026-07-01, see D-12). Not yet implemented.

The user requested extending the child switcher (04-UI-SPEC.md §Component 9 / REQ-031) beyond its original Home-only, tap-only design: a persistent switcher banner on Home, History, *and* Progress, plus a whole-screen swipe-left/right gesture (a `HorizontalPager` carousel) to rotate the active child, as an addition to the existing tap-to-open-sheet flow. The full design is now locked in as **D-12** above.

**This is a deliberate, requested spec revision — not a Stage 1/2 implementation gap.** Stage 1 built exactly what 04-UI-SPEC.md Component 9 and REQ-031 specified at the time (Home-only header, tap-only). If you're auditing Phase 5 and comparing the shipped Stage-1/2 code against text written *before* D-12 landed, this is why they were out of sync — the follow-up hadn't been designed yet. See `.planning/phases/05-core-event-logging/05-FOLLOWUP-persistent-child-switcher.md` for the original brief, and D-12 for the resolved design. Remaining work: update REQUIREMENTS.md/04-UI-SPEC.md/WIREFRAMES.md/SCREEN-FLOWS.md per D-12, then implement.

</post_stage2_followup>

---

*Phase: 5-Core Event Logging*
*Context gathered: 2026-06-30*
*Post-Stage-2 follow-up noted: 2026-07-01*
