---
phase: 04-ui-ux-design
plan: "02"
subsystem: ui
tags: [navigation, screen-flows, mermaid, onboarding, wizard, auth, clerk]

requires:
  - phase: 04-ui-ux-design/04-01
    provides: DESIGN-TOKENS.md (token reference); 04-UI-SPEC.md approved (source of truth)

provides:
  - docs/SCREEN-FLOWS.md — auth+onboarding Mermaid flow, main-app navigation Mermaid, Home Tab interaction numbered sequence, platform-specific navigation notes

affects: [05-mobile-ui-build, 04-03-PLAN (wireframes)]

tech-stack:
  added: []
  patterns:
    - "Mermaid flowchart TD for navigation diagrams with plain-text numbered fallback (Assumption A1)"
    - "Blockquote callout pattern (> **Exception (D-NN):**) for platform-specific notes"
    - "Numbered sequence pattern (1–N steps) for in-screen interaction chains (matching docs/07-sync-and-notifications.md style)"

key-files:
  created:
    - docs/SCREEN-FLOWS.md
  modified: []

key-decisions:
  - "SCREEN-FLOWS.md uses two Mermaid flowchart TD diagrams (auth/onboarding + main-app) plus a plain-text numbered fallback beneath the auth diagram for Mermaid-unsupported viewers (T-04-05 accept disposition)"
  - "Both Task 1 and Task 2 content written in a single atomic Write call — document structure was fully planned before writing, making partial-write + append unnecessary and error-prone"

patterns-established:
  - "Screen-flow docs: H1 title + > blockquote source citation; ## section headings only (no ####); fenced Mermaid blocks for diagrams"
  - "In-screen interaction chains: numbered list referencing SQLDelight, animation timing, and platform APIs — not a separate Mermaid diagram"

requirements-completed: [REQ-035]

duration: ~2min
completed: 2026-06-30
---

# Phase 04 Plan 02: Screen Flows Summary

**Navigation reference document covering every user path — auth, org-picker, onboarding wizard (with consent gate), 4-tab main app, history drill-down — plus the log→toast→sheet interaction chain and platform-specific navigation exceptions**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-06-30T01:16:06Z
- **Completed:** 2026-06-30T01:17:48Z
- **Tasks:** 2 (both completed; content written atomically in single file creation)
- **Files modified:** 1

## Accomplishments

- Created `docs/SCREEN-FLOWS.md` with two Mermaid navigation diagrams (auth+onboarding flow, main-app 4-tab shell)
- Documented the consent gate node (Wizard Step 4, T-04-03 mitigation) with CTA disabled until checkbox checked
- Added plain-text numbered fallback beneath auth Mermaid diagram for Mermaid-unsupported viewers
- Documented the 7-step log→toast→sheet interaction chain as a numbered sequence (T-04-04 mitigation)
- Recorded all platform-specific navigation exceptions (D-10, D-32, wizard linearity, auth→main-app stack replace)
- Tab-bar visibility rule (REQ-035) annotated in both the diagram nodes and a dedicated blockquote callout

## Task Commits

1. **Task 1: Create auth/onboarding + main app navigation diagrams** - `c474e8b` (docs)
   - Note: Task 2 content (Home Tab interaction flow + platform notes) was included in this same commit — document was fully planned and written atomically

**Plan metadata:** _(pending final commit)_

## Files Created/Modified

- `docs/SCREEN-FLOWS.md` — 87 lines; four sections: Auth + Onboarding Flow (Mermaid + text fallback), Main App Navigation (Mermaid + REQ-035 callout), Home Tab Interaction Flow (7-step numbered list), Platform-Specific Navigation Notes (four blockquote callouts)

## Decisions Made

- SCREEN-FLOWS.md uses `flowchart TD` Mermaid syntax (GitHub-renderable) with plain-text fallback rather than ASCII-only diagrams — justified because UI-SPEC.md navigation patterns are graph-structured and Mermaid renders them more clearly than ASCII box-drawing
- Both tasks' content written in single atomic Write — the document structure was fully designed before writing; partial writes would have required re-reading the file mid-task without value

## Deviations from Plan

None — plan executed exactly as written. Both tasks were completed with all acceptance criteria passing. Task 2 content was included in the Task 1 write call (a practical implementation detail, not a functional deviation — the file is complete and all criteria are met).

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- `docs/SCREEN-FLOWS.md` is ready for Phase 5 engineers to wire NavigationStack / NavHost routes and the tab shell
- All four tab names, tab-bar visibility rules (REQ-035), and back-navigation exceptions (D-10) are documented
- Consent gate (Step 4, T-04-03) and wizard linearity constraints are captured for Phase 5 navigation implementation
- Plan 04-03 (Wireframes) and Plan 04-04 can proceed — SCREEN-FLOWS.md is the navigation reference they build on

---
*Phase: 04-ui-ux-design*
*Completed: 2026-06-30*
