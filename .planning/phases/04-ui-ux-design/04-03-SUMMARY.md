---
phase: 04-ui-ux-design
plan: "03"
subsystem: ui
tags: [wireframes, ascii, lo-fi, screens, states, accessibility, design-tokens]

# Dependency graph
requires:
  - phase: 04-ui-ux-design
    provides: "04-UI-SPEC.md approved screen inventory (24 screens), component specs, copywriting contract"
  - phase: 04-ui-ux-design
    provides: "DESIGN-TOKENS.md token names (color, typography, spacing, radius, elevation, motion)"
provides:
  - "docs/WIREFRAMES.md — 24 ASCII lo-fi wireframes covering all screen/state variants (Groups A–F)"
  - "REQ-009 verbatim consent copy rendered in wireframe 9 (Step 4)"
  - "REQ-014 delete flow shown in color.error, 2 taps from Settings (wireframe 20)"
  - "REQ-035 4-tab bar annotated on every main-app wireframe; hidden states annotated with reason"
  - "REQ-031 Child Switcher role=radio bottom sheet (wireframe 23)"
  - "Role-differentiated Settings: Admin (4 sections) vs Caregiver (2 sections, wireframe 21)"
affects: [phase-05-core-event-logging, phase-06-offline-sync, phase-07-push-notifications]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ASCII wireframe format: 40-char-wide ┌─┐ box + ·-prefixed annotation bullets + Ref: UI-SPEC line"
    - "Six wireframe groups (A–F) keyed to UI-SPEC section inventory for traceability"
    - "Heatmap intensity tints: empty/low/medium/high mapped to primary-100/400/700 tokens"

key-files:
  created: []
  modified:
    - "docs/WIREFRAMES.md — Groups A–F, 24 wireframes with token annotations and UI-SPEC Ref lines"

key-decisions:
  - "Wireframes split across two tasks (Groups A–C in prior session, Groups D–F in this session) to manage size; single file, append-only approach preserves prior work"
  - "History Day-Detail wireframe annotates tab-bar-hidden as full-screen push detail flow (REQ-035 exception)"
  - "Settings Caregiver wireframe shows Family/Children sections as fully hidden (not visually hidden) per T-04-09 threat mitigation"
  - "Bottom sheets (Event Detail, Child Switcher) represented as lower-half overlay within full 40-char box using scrim divider annotation"

patterns-established:
  - "Wireframe 20 (Settings Admin) establishes two-tap reachability pattern for REQ-014 delete flow"
  - "Group F generic Loading/Error wireframe serves as reusable pattern for all data-fetch screens"

requirements-completed: [REQ-035]

# Metrics
duration: 45min (Task 2 + summary; Task 1 completed in prior session)
completed: 2026-06-30
---

# Phase 04 Plan 03: Wireframes Summary

**24 ASCII lo-fi wireframes covering all screen/state variants (Groups A–F) with token annotations, a11y callouts, and UI-SPEC cross-references; consent copy (REQ-009) and delete-flow (REQ-014) compliance wired in**

## Performance

- **Duration:** ~45 min (Task 2 + summary; Task 1 executed in a prior session)
- **Started:** Task 1 prior session; Task 2 2026-06-30
- **Completed:** 2026-06-30
- **Tasks:** 2 of 2
- **Files modified:** 1

## Accomplishments

- Appended Groups D–F (10 wireframes, screens 15–24) to docs/WIREFRAMES.md completing the full 24-screen inventory
- History heatmap (wireframe 15) shows purple intensity tints (empty/low/medium/high) with legend, week/month labels, and REQ-033 drill-down annotation
- Settings Admin (wireframe 20) shows "Delete my data" in color.error reachable in two taps (REQ-014, T-04-07 mitigated)
- Caregiver Settings (wireframe 21) explicitly removes Family and Children sections from view tree (T-04-09 mitigated)
- Event Detail and Child Switcher bottom sheets (wireframes 22–23) specify field order, radius.lg corners, role=radio for REQ-031

## Task Commits

Each task was committed atomically:

1. **Task 1: Wireframe Groups A–C (Auth+Org, Onboarding, Home) — wireframes 1–14** - `cabf902` (feat) — prior session
2. **Task 2: Wireframe Groups D–F (History, Progress+Settings, Sheets) — wireframes 15–24** - `5fb627b` (feat)

**Plan metadata:** (docs: complete wireframes plan — committed below)

## Files Created/Modified

- `docs/WIREFRAMES.md` — Groups A–F, 24 ASCII wireframes with token/a11y annotations and UI-SPEC Ref lines (417 lines appended in Task 2, 584 lines from Task 1)

## Decisions Made

- Wireframes split across two sessions (Groups A–C first, Groups D–F second); single-file append-only approach preserved prior work without collision
- Bottom sheets rendered as lower-half overlay within the standard 40-char ASCII box with a scrim divider line, avoiding nested boxes
- History Day-Detail explicitly annotates tab-bar-hidden as a REQ-035 exception (full-screen push detail flow)
- Generic Loading/Error wireframe (wireframe 24) captured as a reusable pattern for all data-fetch screens

## Deviations from Plan

None — plan executed exactly as written. Groups D–F appended following the exact format of Groups A–C.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- docs/WIREFRAMES.md is complete: 24 wireframes, 24 UI-SPEC Ref lines, REQ-009/REQ-014/REQ-031/REQ-035 compliance annotations
- Phase 5 engineers can implement any screen directly from its wireframe + the referenced UI-SPEC component section
- No blockers for Phase 5 (Core Event Logging)

## Self-Check

- [x] docs/WIREFRAMES.md exists and has 24 `### ` headings (`grep -c '^### '` returns 24)
- [x] `grep -c 'Ref: UI-SPEC'` returns 24
- [x] "Delete my data" present with color.error annotation
- [x] "Switch child" present in Child Switcher sheet
- [x] "Save details", "Add a note (optional)", "Logged at" present in Event Detail sheet
- [x] "No events yet" present in History Empty State
- [x] Groups D, E, F headings present
- [x] Heatmap intensity (low/medium/high) referenced with legend (less/more)
- [x] Family and Children sections documented as hidden in Caregiver Settings
- [x] History Day-Detail annotated as tab-bar-hidden full-screen flow

---
*Phase: 04-ui-ux-design*
*Completed: 2026-06-30*
