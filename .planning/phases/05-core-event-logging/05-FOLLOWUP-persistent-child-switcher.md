# Phase 5 Follow-Up: Persistent Cross-Tab Child Switcher + Swipe Gesture

> **Paste this whole file into a new Claude Code session to kick off the work.** It is self-contained — the new session has no memory of any prior conversation, so don't assume it knows anything beyond what's written here and what it can read from the repo.

## What's being asked

Before Phase 5 is considered fully closed, extend the child-switcher design in two ways:

1. **Make the child-switcher banner persistent across Home, History, and Progress** — not Home-only. Right now (see "Current state" below) only the Home tab shows the active child's name with a tap-to-switch affordance; History and Progress have no such banner at all. The banner should appear at the top of all three tabs so a caregiver can swap the active child from wherever they are, without first navigating back to Home.
2. **Add a swipe gesture to rotate between children** — when a family has 2+ children, swiping left or right (on the banner, or possibly the screen generally — this is one of the things to nail down) should cycle the active child, with a card-swap-style animation, as an alternative to tapping the banner to open the existing bottom-sheet list.

This is a deliberate, requested revision to the Phase 4 UI spec and the Phase 5 requirements — it supersedes the original Home-only, tap-only design. It is **not** a bug or a Stage 1/2 implementation mistake. See "Why this file exists" below for how to make that clear in the docs.

## Current state (what exists today, as of Phase 5 Stage 2 merge)

- **Spec:** `.planning/REQUIREMENTS.md` REQ-031: *"the home screen must display the active child's name and a visible switcher control when the family's Clerk org has more than one child profile. Selecting a different child updates the active child context across all tabs (Home, History, Progress). A family with one child shows no switcher."* — note it already says switching propagates to all three tabs, it just says the *control* lives only on the home screen.
- **Spec:** `.planning/phases/04-ui-ux-design/04-UI-SPEC.md` §Component Inventory → "9. Child Switcher (D-22)" describes a **Home header only**: child name (Title 20sp) + chevron icon, combined 48dp touch target, tapping opens a bottom sheet (§Component 9 / `docs/WIREFRAMES.md` "Child Switcher — Bottom Sheet"). Chevron and tap are hidden entirely for single-child families.
- **Code:**
  - `androidApp/src/main/kotlin/com/onesteptwo/android/viewmodel/ChildSelectionViewModel.kt` — already holds `children: StateFlow<List<Children>>` and `activeChild: StateFlow<Children?>`, constructed once above the tab `NavHost` in `MainTabNavigation.kt` so Home/History/Progress already share one active-child context. This part doesn't need to change.
  - `androidApp/src/main/kotlin/com/onesteptwo/android/ui/home/HomeScreen.kt` — the header row (child name + chevron, tap opens `ChildSwitcherSheet`) is currently written *inline* inside `HomeScreen`'s composable body, not extracted into a reusable component.
  - `androidApp/src/main/kotlin/com/onesteptwo/android/ui/home/ChildSwitcherSheet.kt` — the existing bottom-sheet list (checkmark on active row), reusable as-is.
  - `androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/HistoryScreen.kt` — Phase 5 Stage 2 just added the rolling heatmap here; there is **no header/banner at all** currently, just the heatmap or an empty-state.
  - `androidApp/src/main/kotlin/com/onesteptwo/android/ui/progress/ProgressScreen.kt` — still a Stage 1 placeholder ("Streaks and milestones... coming soon"), no header/banner, full Progress UI is Phase 7 scope (REQ-034).

## Why this file exists (documentation trail)

`.planning/phases/05-core-event-logging/05-CONTEXT.md` has been updated with a pointer to this file (see its "Post-Stage-2 Follow-Up" note near the end) specifically so that nobody auditing Phase 5 later looks at the Home-only/tap-only switcher that Stage 1 built, compares it against 04-UI-SPEC.md's Component 9 (which still describes Home-only as of this writing), and concludes Stage 1 deviated from spec. It didn't — this file is the record that the *spec itself* is being revised, on purpose, after Stage 1/2 shipped. When you update the spec docs (see below), make that explicit: don't silently overwrite Component 9's old text, mark it as superseded and dated.

## Required first step: use the grilling skill

This change has more open design questions than it looks like at first glance, and several of them materially affect the wireframes/spec text and the implementation. **Before writing or editing any spec document, invoke the `superpowers:grilling` skill** to interview the user about the design. Do not guess at the answers below — they're listed to make sure grilling actually covers them, not to pre-empt the interview.

Questions grilling should surface (non-exhaustive — let the skill do its job, but make sure these don't get skipped):

- **Where does the banner live relative to each screen's existing content?** On Home it likely replaces the current inline header. On History, does it sit above the heatmap (and above the day-of-week labels), or is it a totally separate bar? On Progress, does it replace the current "Alex (Label 14sp)" line from the Phase 4 spec (§Main App — Progress Tab item 1, "Active child name") with the interactive banner, or coexist with it?
- **Single-child families:** REQ-031 says no switcher shows at all when there's one child. Does that still hold for the persistent banner (i.e. it's fully absent, not just non-interactive), or should the banner now always render (just showing the one name, no chevron/no swipe) for visual consistency across tabs?
- **Swipe target area:** the whole screen, or just the banner strip? History and Progress both have their own scrollable content (heatmap can scroll vertically once it's tall; Progress will eventually be a scrollable stat list per Phase 7) — a full-screen horizontal swipe recognizer needs to coexist with vertical scroll gestures without misfiring. Decide the hit-testing area explicitly.
- **Animation:** what does "card animation effect" mean concretely — slide-and-fade (like the existing Log→Toast slide), a literal card-stack/3D flip, or a simple cross-fade of the name label? Get a decision, not a vague description, since it needs to become a concrete Motion Tokens entry in 04-UI-SPEC.md (that doc already has a §Motion Tokens section and a §Specific Animation Specs subsection with exact durations/easings for other components — this needs the same treatment).
- **Wrap-around:** does swiping past the last child loop back to the first (and vice versa), or stop/bounce at the ends?
- **Order:** confirm the swipe order matches the existing switcher-sheet list order (children sorted by `created_at ASC`, per `ChildrenRepository.observeAll()` / `Children.sq`'s `selectAll`) rather than inventing a new ordering.
- **Accessibility:** a swipe-only interaction is not screen-reader-accessible. Confirm the tap-to-open-sheet path (and the sheet's own row-selection) remains as the accessible fallback, and that the banner's `contentDescription` communicates both affordances (already partially covered by 04-UI-SPEC.md's existing a11y contract for Component 9 — check whether it needs new language for "swipe left or right to switch").
- **Does the banner appear on non-tab-root screens?** History's Day-Detail (`history/day/{date}`) is a full-screen push destination with the tab bar hidden (05-CONTEXT.md D-06/D-07 territory) — confirm the banner should NOT appear there (matching "tab bar hidden" scope), and similarly confirm it should not appear inside Settings sub-screens (Add/Edit child, Invite caregiver).
- **Requirement numbering:** does this warrant amending REQ-031's text in place, or a new REQ (e.g. REQ-037) that references/extends REQ-031? Get the user's preference — this repo's convention (see `.planning/REQUIREMENTS.md`'s existing entries and the Phase 2 "D-04" event_type correction) is to correct in place when the change is a refinement of the same requirement, and add a new REQ when it's additive new scope. This one reads as a refinement (same requirement, control's reach is being widened) but confirm rather than assume.

## After grilling: update the spec

Once the design is settled, update — in this order, since later docs reference earlier ones:

1. **`.planning/phases/05-core-event-logging/05-CONTEXT.md`** — add a new lettered decision (next available, currently D-01 through D-11 exist) documenting exactly what was decided in the grilling session and why, explicitly noting it supersedes/extends REQ-031 and 04-UI-SPEC.md Component 9. Follow the existing `<decisions>` block's format (bold `D-NN:` lead sentence, then supporting rationale, cross-referencing the relevant WIREFRAMES/UI-SPEC sections it invalidates/extends).
2. **`.planning/REQUIREMENTS.md`** — update REQ-031's text (or add the new REQ, per the grilling outcome) and its Phase/Status row in the tracking table.
3. **`.planning/phases/04-ui-ux-design/04-UI-SPEC.md`** — update §Component Inventory item 9 (Child Switcher) to describe the cross-tab banner + swipe behavior; add the swipe animation to §Motion Tokens/§Specific Animation Specs; update the Screen Inventory entries for Home Tab, History Tab, and Progress Tab to mention the shared banner. Mark the old Home-only text as superseded (don't just delete it silently — a one-line "superseded by 05-FOLLOWUP-persistent-child-switcher.md, see 05-CONTEXT.md D-NN" note is enough) so the change history stays legible.
4. **`docs/WIREFRAMES.md`** — update the Home/History/Progress wireframe ASCII mockups to show the banner in each, and add any new wireframe needed for the swipe interaction/animation states.
5. **`docs/SCREEN-FLOWS.md`** — update or add a flow describing the swipe-to-switch interaction alongside the existing tap-to-open-sheet flow.

## After the spec: implement

Once the spec docs above are updated and you've confirmed the design with the user, build it. Given the scope (a shared component used across 3 screens, a gesture-detection layer, and an animation), this is a good candidate for `superpowers:writing-plans` → either `superpowers:subagent-driven-development` or `superpowers:executing-plans`, the same way Phase 5 Stage 2 was built. Key implementation notes to carry into that plan:

- Extract a shared composable (e.g. `ChildSwitcherBanner`) out of `HomeScreen.kt`'s current inline header so History and Progress can reuse the exact same component rather than duplicating it three times.
- `ChildSelectionViewModel` already provides everything the banner needs (`children`, `activeChild`, `selectChild(...)`) — this shouldn't require new ViewModel-layer work beyond maybe an index-based "next"/"previous" helper for swipe.
- Reuse the existing `ChildSwitcherSheet.kt` for the tap path; the swipe path is additive, not a replacement.
