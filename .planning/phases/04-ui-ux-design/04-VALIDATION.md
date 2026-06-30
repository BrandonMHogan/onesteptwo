---
phase: 04
slug: ui-ux-design
status: draft
nyquist_compliant: false
wave_0_complete: true
created: 2026-06-29
---

# Phase 04 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Manual checklist — no automated test runner |
| **Config file** | none — documentation phase, no framework install needed |
| **Quick run command** | `ls docs/DESIGN-TOKENS.md docs/SCREEN-FLOWS.md docs/WIREFRAMES.md` |
| **Full suite command** | Manual review of each success criterion against the output files |
| **Estimated runtime** | ~5 minutes (manual review) |

---

## Sampling Rate

- **After every task commit:** Run `ls docs/*.md` — confirm expected file exists
- **After every plan wave:** Manual review of SC completeness for that wave's artifacts
- **Before `/gsd-verify-work`:** All 5 success criteria satisfied
- **Max feedback latency:** Immediate (file existence check)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| SC-1 | — | 1 | REQ-035 | — | SCREEN-FLOWS.md covers all paths in SC #1 list | manual | `grep -c "flowchart\|stateDiagram" docs/SCREEN-FLOWS.md` | ❌ Wave 1 | ⬜ pending |
| SC-2 | — | 2 | REQ-035 | — | WIREFRAMES.md has 22+ screen/state wireframes | manual | `grep -c "^###" docs/WIREFRAMES.md` (≥22) | ❌ Wave 2 | ⬜ pending |
| SC-3 | — | 1 | REQ-035 | — | DESIGN-TOKENS.md has Color, Typography, Spacing, Radii, Elevation, Motion sections | manual | `grep -c "^## " docs/DESIGN-TOKENS.md` (≥6) | ❌ Wave 1 | ⬜ pending |
| SC-4 | — | existing | REQ-035 | — | UI-SPEC.md §Component Inventory covers all 12 components | manual | `grep -c "^### [0-9]" .planning/phases/04-ui-ux-design/04-UI-SPEC.md` (=12) | ✅ Existing | ⬜ pending |
| REQ-035 | — | 2 | REQ-035 | — | Tab bar (4 tabs, active/inactive) documented in wireframes + tokens | manual | `grep -c "Tab Bar\|NavigationBar\|TabView" docs/WIREFRAMES.md` | ❌ Wave 2 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*None — no test infrastructure needed for a documentation phase. The verification is a manual checklist. Wave 0 is complete by default.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Screen flow diagram covers all user paths (SC #1) | REQ-035 | Documentation content review | Open docs/SCREEN-FLOWS.md and confirm each path from SC #1 list has a corresponding flow node/arrow |
| Wireframes exist for every distinct screen and state (SC #2) | REQ-035 | ASCII/visual content review | Open docs/WIREFRAMES.md and count 22+ headings covering Auth+Org (6), Onboarding Wizard (4), Home+Overlays (4), History Tab (3), Progress+Settings (4), Sheets+States (3) |
| Design tokens cover both platforms with dark mode (SC #3) | REQ-035 | Token value accuracy check | Open docs/DESIGN-TOKENS.md and confirm Color, Typography, Spacing, Radii, Elevation, Motion sections include Compose + SwiftUI equivalents and dark mode variants |
| Component specs cover all 12 components (SC #4) | REQ-035 | Completeness audit | Confirm UI-SPEC.md §Component Inventory lists all 12 components with active/inactive/loading/error states |
| Navigation + animation patterns documented (SC #5) | REQ-035 | Pattern completeness review | Confirm docs/WIREFRAMES.md or docs/SCREEN-FLOWS.md includes timing specs and platform-specific motion guidance |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency acceptable for documentation phase
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
