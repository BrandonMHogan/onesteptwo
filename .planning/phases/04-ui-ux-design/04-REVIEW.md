---
phase: 04-ui-ux-design
reviewed: 2026-06-30T00:00:00Z
depth: standard
files_reviewed: 3
files_reviewed_list:
  - docs/DESIGN-TOKENS.md
  - docs/SCREEN-FLOWS.md
  - docs/WIREFRAMES.md
findings:
  critical: 5
  warning: 8
  info: 0
  total: 13
status: issues_found
---

# Phase 4: Code Review Report

**Reviewed:** 2026-06-30
**Depth:** standard
**Files Reviewed:** 3
**Source of Truth:** `.planning/phases/04-ui-ux-design/04-UI-SPEC.md`
**Status:** issues_found

---

## Summary

Three Phase 4 deliverables were reviewed — `DESIGN-TOKENS.md`, `SCREEN-FLOWS.md`, and `WIREFRAMES.md` — against `04-UI-SPEC.md` as the canonical source of truth. `SCREEN-FLOWS.md` is largely accurate: all navigation paths, copy, animation specs, and REQ-0xx citations match the spec. The auth/onboarding flows are complete and internally consistent.

`DESIGN-TOKENS.md` contains one critical self-contradiction in its primary color scale table that directly contradicts the heatmap intensity color table within the same document.

`WIREFRAMES.md` is where the majority of defects concentrate. Five blockers were identified: all four wizard step progress indicators are off by one position, the milestone badge grid shows badge names that don't exist in the spec, the child switcher sheet typography contradicts the component spec, and a navigation link is colored with the accent token in explicit violation of the accent reservation rule. Eight warnings round out issues with typography system violations, layout discrepancies, a missing UI element, and accessibility annotation format mismatches.

A Phase 5 engineer implementing from these wireframes without cross-referencing the UI-SPEC would produce incorrect milestone milestones, wrong progress indicators, wrong child-row text sizes, and incorrect dark-mode heatmap colors.

---

## Critical Issues

### CR-01: DESIGN-TOKENS.md — primary-300 usage annotation contradicts Heatmap Intensity Colors table

**File:** `docs/DESIGN-TOKENS.md:39`

**Issue:** The Primary Purple Scale table labels `primary-300` (#D8B4FE) as `"Primary accent in dark mode; heatmap medium (dark)"`. Two tables lower in the same file, the Heatmap Intensity Colors table shows primary-300 is used for **High** intensity in dark mode (6+ events), while `primary-500` (#A855F7) is the **Medium** dark-mode color. The usage annotation is wrong and the two tables contradict each other. A Phase 5 engineer reading the usage column of the Primary Purple Scale table would implement `#D8B4FE` for medium-intensity dark-mode heatmap cells, giving the wrong tint for 3–5 event days.

**Fix:**
```markdown
| primary-300 | #D8B4FE | Primary accent in dark mode; heatmap high (dark) |
```

---

### CR-02: WIREFRAMES.md — Milestone badge wireframe shows badge names not defined in the spec

**File:** `docs/WIREFRAMES.md:723-728`

**Issue:** The Progress Tab wireframe renders four milestone badges labeled: "First trip ✓", "5-day streak ✓", "30-day streak", and "100 events". UI-SPEC.md §Milestone Badge (Component 5, REQ-034) defines exactly four milestones:
1. First trip
2. Accident-free day
3. 7-day streak
4. 30-day streak

"5-day streak" does not exist in the spec (it should be either "Accident-free day" or "7-day streak"). "100 events" is not a defined milestone at all. A Phase 5 engineer implementing from this wireframe would build two incorrect milestones that will never match backend unlock conditions.

**Fix:**
```
┌──────────┐  ┌──────────┐
│ 🏅 First │  │ 🔒 Acc.  │
│  trip ✓  │  │ free day │
└──────────┘  └──────────┘
┌──────────┐  ┌──────────┐
│ 🔒 7-day │  │ 🔒 30-day│
│ streak   │  │ streak   │
└──────────┘  └──────────┘
```
Update annotations to match the four milestone names in UI-SPEC.md §Component 5.

---

### CR-03: WIREFRAMES.md — All four wizard step dot indicators are off by one position

**File:** `docs/WIREFRAMES.md:264, 302, 344, 390`

**Issue:** Every onboarding wizard step wireframe has the active dot one position to the left of where it should be:

| Step | Wireframe shows | Should show | Annotation (correct) |
|------|----------------|-------------|----------------------|
| Step 2 | `● ○ ○ ○ ○` | `○ ● ○ ○ ○` | "dot 2 active" |
| Step 3 | `○ ● ○ ○ ○` | `○ ○ ● ○ ○` | "step 3 active" |
| Step 4 | `○ ○ ● ○ ○` | `○ ○ ○ ● ○` | "step 4 active" |
| Step 5 | `○ ○ ○ ● ○` | `○ ○ ○ ○ ●` | (final step) |

The annotations are correct; the ASCII art indicators are wrong. A Phase 5 engineer copy-implementing the dot state from the visual pattern would show dot 1 as active on step 2, dot 2 on step 3, etc. — i.e., always one step behind.

**Fix:** Correct each step's dot pattern:
```
Step 2:  ○ ● ○ ○ ○
Step 3:  ○ ○ ● ○ ○
Step 4:  ○ ○ ○ ● ○
Step 5:  ○ ○ ○ ○ ●
```

---

### CR-04: WIREFRAMES.md — Child Switcher sheet typography contradicts Component 9 spec

**File:** `docs/WIREFRAMES.md:951`

**Issue:** The Child Switcher Bottom Sheet wireframe annotation reads:

> "Typography: child name rows Body 16sp, color.on-background; active checkmark color.primary"

UI-SPEC.md §Child Switcher (Component 9) specifies:

> "Each child row: **Title (20sp)** label + checkmark if active, 48dp minimum row height"

Body 16sp and Title 20sp are two different typography levels. A Phase 5 engineer implementing from the wireframe annotation alone would render child names at 16sp instead of 20sp.

**Fix:**
```markdown
· Typography: child name rows Title 20sp semibold, color.on-background; active checkmark color.primary
```

---

### CR-05: WIREFRAMES.md — "Sign up" navigation link assigned color.primary in violation of accent reservation rule

**File:** `docs/WIREFRAMES.md:47`

**Issue:** The Sign In wireframe annotation states:

> "Color: 'Sign up' link color.primary"

UI-SPEC.md §Color §60/30/10 Split explicitly prohibits this:

> "Accent is NOT used for: secondary actions, **navigation links**, general text, dividers, or any ambient decoration."

The "Don't have an account? Sign up" and "Already have an account? Sign in" links are navigation links between auth screens — exactly the category excluded from accent usage. The design system's accent reservation is a core principle (D-02, D-24), and violating it here undermines the visual hierarchy that makes the Log button and primary CTAs the dominant accent elements.

**Fix:**
```markdown
· Color: "Sign up" link color.on-surface (70% opacity) or color.on-background — not color.primary
```
Apply the same correction to the "Already have an account? Sign in" link in the Sign Up wireframe (line 136).

---

## Warnings

### WR-01: WIREFRAMES.md — "Body 16sp italic" in History Day-Detail contradicts the two-weight typography system

**File:** `docs/WIREFRAMES.md:688`

**Issue:** The History Day-Detail event card annotation specifies note text as "Body 16sp **italic**." The design system (UI-SPEC.md §Typography, DESIGN-TOKENS.md §Typography Tokens) defines exactly two weights: regular (400) and semibold (600). Italic is not defined anywhere in the type system. No italic style is mentioned in any other wireframe or spec section.

**Fix:** Either remove "italic" (use Body 16sp regular, consistent with defined weights) or add an explicit italic exception to DESIGN-TOKENS.md Typography with a rationale. If notes use regular weight, update the annotation:
```markdown
· Typography: note text Body 16sp regular, color.on-surface; sync status Caption 12sp
```

---

### WR-02: WIREFRAMES.md — Heatmap month labels placed at end of rows; UI-SPEC specifies "start of each month row"

**File:** `docs/WIREFRAMES.md:596-601`

**Issue:** The History heatmap wireframe shows month labels at the rightmost column of each row:
```
│  ·   ·   ░   ▒   █   ▒   ░   Apr   │
│  █   █   ▒   ·   █   █   ░   May   │
```
UI-SPEC.md §History Tab states: "Month labels at **start** of each month row." In LTR layout, "start" = left. The wireframe places them at the end (right). A Phase 5 engineer will implement month labels on the wrong side.

**Fix:** Reposition month labels to the left of each row:
```
│  Apr  ·   ·   ░   ▒   █   ▒   ░   │
│  May  █   █   ▒   ·   █   █   ░   │
```

---

### WR-03: WIREFRAMES.md — Toast wireframe is missing the "add details" action described in SCREEN-FLOWS.md and UI-SPEC.md

**File:** `docs/WIREFRAMES.md:518-526`, cross-referenced with `docs/SCREEN-FLOWS.md:73`

**Issue:** The Toast wireframe (Group C) shows only six event-type chips (Pee, Poo, Both, Accident (pee), Accident (poo), Tried) with no "add details" link or button. However, both SCREEN-FLOWS.md step 6 and UI-SPEC.md §Navigation §Log Button → Toast → Bottom Sheet Flow describe an explicit "add details" action in the toast that opens the Event Detail bottom sheet without requiring a chip selection first:

> "User taps **'add details' in the toast** (or taps a pending-details event card later) → bottom sheet opens"

This interaction path is referenced in both documents but is entirely absent from the toast wireframe. Phase 5 would implement a toast with no way to open the detail sheet directly from the toast.

**Fix:** Either (a) add an "add details" text link at the bottom of the toast with annotation specifying its text style and tap behavior, or (b) if the "add details" action is only reachable from a pending-details event card (not the toast), correct SCREEN-FLOWS.md and UI-SPEC.md §Navigation to remove the "in the toast" reference and update both documents to match the wireframe.

---

### WR-04: WIREFRAMES.md — Unlocked badge accessibility label missing "on [date]" component

**File:** `docs/WIREFRAMES.md:742`

**Issue:** The Progress Tab wireframe annotates unlocked milestone badges with:

> "contentDescription 'First trip milestone, unlocked'"

UI-SPEC.md §Accessibility Contract specifies the unlocked badge announcement as:

> "Milestone badge — unlocked | '**[Badge name], unlocked on [date]**' (non-interactive)"

The wireframe omits the `on [date]` suffix. For screen reader users, this removes the date context from badge announcements, which is part of the defined accessibility contract.

**Fix:**
```markdown
· a11y: unlocked badge role=none, contentDescription "[Badge name], unlocked on [date]"
```
Example: "First trip milestone, unlocked on June 3"

---

### WR-05: WIREFRAMES.md — Sign In form-level error uses Body 16sp; Sign Up field-level error uses Label 14sp; inconsistency unexplained

**File:** `docs/WIREFRAMES.md:90` (Sign In), `docs/WIREFRAMES.md:132` (Sign Up)

**Issue:** The Sign In error state annotation reads: "Typography: error text **Body 16sp**, color.error". The Sign Up error annotation reads: "Typography: error **Label 14sp**, color.error." UI-SPEC.md §Typography usage mapping assigns "error messages" to the Label (14sp) role, not Body. Neither wireframe annotation explains the distinction or which size applies in which context. The inconsistency will force Phase 5 to guess which size is authoritative.

**Fix:** Decide which size is correct for each context and document the rule explicitly. If Label 14sp is the standard for all auth error messages (consistent with the typography mapping), update the Sign In error annotation to match. If Body 16sp is intentional for form-level (full-width) errors while Label 14sp is for inline field errors, add this rule to DESIGN-TOKENS.md §Typography Tokens.

---

### WR-06: WIREFRAMES.md — Heatmap non-empty cell accessibility description format conflicts with UI-SPEC accessibility contract

**File:** `docs/WIREFRAMES.md:617`

**Issue:** The History heatmap wireframe annotates non-empty cells with:

> "contentDescription '[Weekday], [Month Day], [N] events, tap to see details'"

UI-SPEC.md §Accessibility Contract specifies:

> "Heatmap cell — non-empty | '**[N] events on [Mon, Jan 1]**, button'"

The wireframe format reverses the order (weekday+date before count vs. count before date), uses a comma-separated date format instead of the compact `[Mon, Jan 1]` format, and adds "tap to see details" which is not in the spec. The "button" role suffix is also omitted from the wireframe annotation.

**Fix:**
```markdown
· a11y: non-empty cell role=button, contentDescription "[N] events on [Mon, Jan 1]"
        example: "6 events on Thu, Jun 12, button"
· a11y: empty cell role=none (non-interactive), contentDescription "[Mon, Jan 1], no events"
```

---

### WR-07: WIREFRAMES.md — Invite Caregiver wireframe missing tab bar; SCREEN-FLOWS.md states Settings sub-screens retain tab bar

**File:** `docs/WIREFRAMES.md:220-251`

**Issue:** The Invite Caregiver wireframe in Group A shows a `← Back` navigation header but no bottom tab bar. SCREEN-FLOWS.md §Main App Navigation note explicitly states:

> "It is visible on all four main tabs (Home, History, Progress, Settings) **and on the Settings sub-screens**."

The Invite Caregiver screen is reachable from Settings → Family → "Invite caregiver" row, making it a Settings sub-screen. By the stated tab bar rule, the tab bar should be visible. The wireframe silently omits it. A Phase 5 engineer will hide the tab bar on this screen.

**Fix:** Add a second Invite Caregiver wireframe variant labeled "Accessed from Settings" that shows the tab bar, with an annotation:

> "Note: tab bar visible when accessed from Settings (Settings sub-screen rule, REQ-035); tab bar hidden when this screen appears as part of an admin-only onboarding variant if applicable."

Alternatively, add a note directly to the existing wireframe: "Tab bar visible when accessed via Settings."

---

### WR-08: WIREFRAMES.md — OTP step shows 5 input cells; Clerk's default email OTP is 6 digits

**File:** `docs/WIREFRAMES.md:83`, `docs/WIREFRAMES.md:95`

**Issue:** The Sign In error/OTP wireframe shows 5 OTP input cells:

```
│  [  ] [  ] [  ] [  ] [  ]   │
```

The accessibility annotation reads "traversalIndex ordered 1–5." Clerk's email OTP codes are 6 digits by default. If the project uses Clerk's default configuration (no custom override), the wireframe is short one cell and the traversal index is wrong (should be 1–6). An engineer implementing 5 cells would cause OTP entry to fail for all users.

**Fix:** Verify Clerk OTP length for this project's Clerk configuration. If 6 digits (default), update the wireframe to show 6 cells and update the accessibility annotation to "traversalIndex ordered 1–6."

---

_Reviewed: 2026-06-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
