---
status: testing
phase: 04-ui-ux-design
source: [04-01-SUMMARY.md, 04-02-SUMMARY.md, 04-03-SUMMARY.md, 04-04-SUMMARY.md]
started: 2026-06-30T13:30:00Z
updated: 2026-06-30T13:30:00Z
---

## Current Test
<!-- OVERWRITE each test - shows where we are -->

number: 1
name: Design Token Reference
expected: |
  Open docs/DESIGN-TOKENS.md. You should see 6 top-level sections (##): Color, Typography, Spacing, Corner Radius, Elevation, and Motion. Color should include semantic tokens, a primary scale (primary-100 through primary-900), and heatmap tints. Motion should include durations (150ms/300ms/400ms) and easing values.
awaiting: user response

## Tests

### 1. Design Token Reference
expected: Open docs/DESIGN-TOKENS.md. You should see 6 top-level sections (##): Color, Typography, Spacing, Corner Radius, Elevation, and Motion. Color should include semantic tokens, a primary scale (primary-100 through primary-900), and heatmap tints. Motion should include durations (150ms/300ms/400ms) and easing values.
result: [pending]

### 2. Screen Flow Diagrams
expected: Open docs/SCREEN-FLOWS.md. You should see two Mermaid flowchart diagrams: one for auth+onboarding (Sign In → OTP → Org Picker → Onboarding Wizard) and one for the main 4-tab app shell (Home/History/Progress/Settings). A plain-text numbered fallback should appear beneath the auth diagram. Platform-specific notes (tab bar visibility, back-nav exceptions) should appear as blockquote callouts.
result: [pending]

### 3. Home Tab Interaction Chain
expected: Still in docs/SCREEN-FLOWS.md, find the "Home Tab Interaction Flow" section. You should see a numbered sequence (1–7 steps) describing the log→toast→sheet interaction: tap Log Event → bottom sheet opens → fill form → submit → optimistic toast → sheet dismisses → list updates. Animation timing (150ms/300ms) should be referenced.
result: [pending]

### 4. All 24 Wireframes Present
expected: Open docs/WIREFRAMES.md. Scroll through and count the ### headings — there should be 24 wireframes covering Groups A–F: Auth+Org (1–5), Onboarding (6–10), Home (11–14), History (15–17), Progress+Settings (18–21), and Sheets+States (22–24). Each wireframe should have a "Ref: UI-SPEC" line for traceability.
result: [pending]

### 5. Key Requirement Wireframes
expected: In docs/WIREFRAMES.md, verify 3 specific requirements: (1) Wireframe 9 (Onboarding Step 4) shows the legal consent copy with the CTA button disabled until the checkbox is checked. (2) Wireframe 20 (Settings Admin) shows "Delete my data" annotated in color.error. (3) Wireframes for the main app show a 4-tab bar (Home/History/Progress/Settings) with active/inactive token annotations.
result: [pending]

### 6. UI-SPEC Checker Sign-Off
expected: Open .planning/phases/04-ui-ux-design/04-UI-SPEC.md and scroll to the bottom (Checker Sign-Off section). You should see all 6 dimension checkboxes marked checked [x], and the Approval line should read "2026-06-29 — all six dimensions PASS" (not "pending").
result: [pending]

## Summary

total: 6
passed: 0
issues: 0
pending: 6
skipped: 0
blocked: 0

## Gaps

[none yet]
