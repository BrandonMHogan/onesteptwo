---
phase: 04-ui-ux-design
fixed_at: 2026-06-30T00:00:00Z
review_path: .planning/phases/04-ui-ux-design/04-REVIEW.md
iteration: 1
findings_in_scope: 13
fixed: 13
skipped: 0
status: all_fixed
---

# Phase 4: Code Review Fix Report

**Fix Scope:** critical_warning (Critical + Warning)
**Iteration:** 1
**Fixed at:** 2026-06-30

## Summary

- Findings in scope: 13 (5 Critical, 8 Warning)
- Fixed: 13
- Skipped: 0

---

## Fixed Issues

### CR-01 — DESIGN-TOKENS.md: primary-300 heatmap usage annotation corrected

**File:** `docs/DESIGN-TOKENS.md`
**Commit:** 14fdea3
**Change:** Changed usage annotation for `primary-300` (#D8B4FE) from `"Primary accent in dark mode; heatmap medium (dark)"` to `"Primary accent in dark mode; heatmap high (dark)"`. This eliminates the self-contradiction with the Heatmap Intensity Colors table, which correctly maps primary-300 to High intensity (6+ events) in dark mode.

---

### CR-02 — WIREFRAMES.md: Milestone badge wireframe corrected to match spec

**File:** `docs/WIREFRAMES.md`
**Commit:** 416f343
**Change:** Updated the 2×2 milestone badge grid in the Progress Tab wireframe. Replaced the erroneous "5-day streak" and "100 events" badges with the four milestones defined in UI-SPEC.md §Component 5 (REQ-034):
1. First trip (unlocked — 🏅)
2. Accident-free day (locked — 🔒)
3. 7-day streak (locked — 🔒)
4. 30-day streak (locked — 🔒)

---

### CR-03 — WIREFRAMES.md: Wizard step progress dots corrected

**File:** `docs/WIREFRAMES.md`
**Commit:** 1a251d6
**Change:** Fixed all four wizard step dot indicators that were each one position to the left of the active step:
- Step 2: `● ○ ○ ○ ○` → `○ ● ○ ○ ○`
- Step 3: `○ ● ○ ○ ○` → `○ ○ ● ○ ○`
- Step 4: `○ ○ ● ○ ○` → `○ ○ ○ ● ○`
- Step 5: `○ ○ ○ ● ○` → `○ ○ ○ ○ ●`

---

### CR-04 — WIREFRAMES.md: Child Switcher row typography corrected to Title 20sp

**File:** `docs/WIREFRAMES.md`
**Commit:** 17e8eca
**Change:** Updated the Child Switcher Bottom Sheet annotation from `"child name rows Body 16sp"` to `"child name rows Title 20sp semibold"` to match UI-SPEC.md §Component 9 which specifies Title (20sp) for each child row.

---

### CR-05 — WIREFRAMES.md: Navigation link colors corrected to remove color.primary

**File:** `docs/WIREFRAMES.md`
**Commit:** 92a5a43
**Change:** Two navigation link color annotations fixed to comply with the accent reservation rule (UI-SPEC.md §Color §60/30/10 Split):
- Sign In wireframe: `"Sign up" link color.primary` → `"Sign up" link color.on-surface (70% opacity) or color.on-background — not color.primary`
- Sign Up wireframe: Added explicit annotation `"Sign in" link color.on-surface (70% opacity) or color.on-background — not color.primary` (previously had no color annotation for the nav link)

---

### WR-01 — WIREFRAMES.md: Undefined italic weight removed from note text annotation

**File:** `docs/WIREFRAMES.md`
**Commit:** 7593572
**Change:** Updated History Day-Detail note text annotation from `"Body 16sp italic"` to `"Body 16sp regular, color.on-surface"`. The design system defines only regular (400) and semibold (600) weights; italic is not defined anywhere in the typography system.

---

### WR-02 — WIREFRAMES.md: Heatmap month labels repositioned to start of rows

**File:** `docs/WIREFRAMES.md`
**Commit:** e3fc3de
**Change:** Moved month labels from the right end of each heatmap row to the left start position, matching UI-SPEC.md §History Tab which specifies "Month labels at start of each month row." Also updated the day-of-week header row to add the corresponding left indent. Before: `·  ·  ░  ▒  █  ▒  ░  Apr`. After: `Apr  ·  ·  ░  ▒  █  ▒  ░`.

---

### WR-03 — WIREFRAMES.md: "add details" action added to toast wireframe

**File:** `docs/WIREFRAMES.md`
**Commit:** f9d305b
**Change:** Added the missing "add details" text link at the bottom of the toast wireframe, and added an annotation specifying its style (Label 14sp, color.on-surface) and tap behavior (opens Event Detail bottom sheet without requiring a chip selection first). This matches SCREEN-FLOWS.md step 6 and UI-SPEC.md §Log Button → Toast → Bottom Sheet Flow.

---

### WR-04 — WIREFRAMES.md: Unlocked badge accessibility label updated with "on [date]"

**File:** `docs/WIREFRAMES.md`
**Commit:** 8688827
**Change:** Updated the unlocked badge `contentDescription` from `"First trip milestone, unlocked"` to `"[Badge name], unlocked on [date]"` with an example (`"First trip milestone, unlocked on June 3"`), matching the UI-SPEC.md §Accessibility Contract format.

---

### WR-05 — WIREFRAMES.md: Sign In error annotation standardized to Label 14sp

**File:** `docs/WIREFRAMES.md`
**Commit:** a38f8bf
**Change:** Updated the Sign In Error State error text annotation from `"Body 16sp"` to `"Label 14sp"` to match both the Sign Up annotation and the UI-SPEC.md §Typography usage mapping which assigns "error messages" to the Label (14sp) role.

---

### WR-06 — WIREFRAMES.md: Heatmap cell accessibility format corrected to match spec

**File:** `docs/WIREFRAMES.md`
**Commit:** af8935c
**Change:** Replaced the incorrect heatmap cell accessibility annotations with the format from UI-SPEC.md §Accessibility Contract:
- Non-empty cell: `role=button, contentDescription "[N] events on [Mon, Jan 1]"` (e.g. "6 events on Thu, Jun 12, button")
- Empty cell: `role=none (non-interactive), contentDescription "[Mon, Jan 1], no events"`

Previously the format had date before count, used a verbose date format, included "tap to see details" (not in spec), and omitted the "button" role suffix.

---

### WR-07 — WIREFRAMES.md: Tab bar visibility note added to Invite Caregiver wireframe

**File:** `docs/WIREFRAMES.md`
**Commit:** 0eb20b2
**Change:** Added note to the Invite Caregiver wireframe: `"Tab bar visible when accessed via Settings (Settings sub-screen rule, REQ-035)"`. This reflects SCREEN-FLOWS.md's explicit statement that Settings sub-screens retain the bottom tab bar.

---

### WR-08 — WIREFRAMES.md: OTP wireframe updated to 6 cells with Clerk note

**File:** `docs/WIREFRAMES.md`
**Commit:** dae37de
**Change:** Updated the OTP step wireframe from 5 input cells to 6 (`[  ] [  ] [  ] [  ] [  ] [  ]`), changed the traversal index annotation from `1–5` to `1–6`, and added a note: "6 cells shown — Clerk default email OTP is 6 digits; verify if project uses custom Clerk configuration."

---

## Skipped Issues

None — all 13 in-scope findings were applied successfully.

---

_Fixed: 2026-06-30_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
