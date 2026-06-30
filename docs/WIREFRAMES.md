# Wireframes — OneStepTwo

> Source: `.planning/phases/04-ui-ux-design/04-UI-SPEC.md` §Screen Inventory, §Component Inventory
> Last updated: 2026-06-29
> 24 distinct screen/state wireframes across Groups A–F. Each wireframe includes layout annotations
> and component cross-references. Phase 5 engineers implement each screen from its wireframe and
> the referenced component spec.

---

## Group A — Auth + Org Screens

### Sign In — Default State

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│                                      │
│                                      │
│           OneStepTwo                 │
│                                      │
│  Email                               │
│  ┌──────────────────────────────┐    │
│  │ email@example.com            │    │
│  └──────────────────────────────┘    │
│                                      │
│  Password                            │
│  ┌──────────────────────────────┐    │
│  │ ••••••••          [👁 icon]  │    │
│  └──────────────────────────────┘    │
│                                      │
│  ╔══════════════════════════════╗    │
│  ║           Sign in            ║    │
│  ╚══════════════════════════════╝    │
│                                      │
│    Don't have an account? Sign up    │
│                                      │
│                                      │
└──────────────────────────────────────┘
```

· Typography: "OneStepTwo" Display 28sp semibold, color.on-background
· Typography: field labels Label 14sp, input text Body 16sp
· Typography: CTA "Sign in" Body 16sp semibold, color.on-primary
· Color: CTA background color.primary (#7E22CE light / #D8B4FE dark)
· Color: input field background color.surface-container, border color.outline, radius.sm (8dp)
· Color: "Sign up" link color.primary
· Elevation: CTA button overlay (8dp)
· a11y: email field role=text, password field role=password with toggle, CTA role=button
· a11y: min tap target 48dp for all interactive elements

Ref: UI-SPEC §Auth Screens, §Typography, §Color (component: no dedicated component — system form fields)

---

### Sign In — Error State and OTP Step

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│  [ERROR STATE — inline below fields] │
│                                      │
│           OneStepTwo                 │
│                                      │
│  Email                               │
│  ┌──────────────────────────────┐    │
│  │ email@example.com            │    │
│  └──────────────────────────────┘    │
│  Password                            │
│  ┌──────────────────────────────┐    │
│  │ ••••••••          [👁 icon]  │    │
│  └──────────────────────────────┘    │
│  ⚠ Incorrect email or password.      │
│    Try again.                        │
│  ╔══════════════════════════════╗    │
│  ║           Sign in            ║    │
│  ╚══════════════════════════════╝    │
│  ────── OTP STEP (replaces above) ── │
│  Check your email                    │
│  ┌──────────────────────────────┐    │
│  │  [  ] [  ] [  ] [  ] [  ]   │    │
│  └──────────────────────────────┘    │
│  ⚠ Incorrect or expired code.        │
│    Try again.                        │
└──────────────────────────────────────┘
```

· Typography: error text Body 16sp, color.error (#B91C1C light / #FCA5A5 dark)
· Typography: OTP heading Display 28sp semibold
· Color: error icon color.error; OTP digit cells background color.surface-container, border color.outline
· Color: OTP cell focus ring color.primary (radius.sm)
· Elevation: same as default state
· a11y: error message liveRegion=Polite; OTP inputs role=text, traversalIndex ordered 1–5
· a11y: contentDescription "Enter verification code digit N" per OTP cell

Ref: UI-SPEC §Auth Screens, §Copywriting Contract §Error State Copy, §Accessibility Contract

---

### Sign Up — Default State and Error State

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│                                      │
│           Create account             │
│                                      │
│  Email                               │
│  ┌──────────────────────────────┐    │
│  │ email@example.com            │    │
│  └──────────────────────────────┘    │
│  Password                            │
│  ┌──────────────────────────────┐    │
│  │ ••••••••          [👁 icon]  │    │
│  └──────────────────────────────┘    │
│  [ERROR STATE — below field]         │
│  ⚠ (field-level error text 14sp)     │
│                                      │
│  ╔══════════════════════════════╗    │
│  ║         Create account       ║    │
│  ╚══════════════════════════════╝    │
│                                      │
│    Already have an account? Sign in  │
│                                      │
└──────────────────────────────────────┘
```

· Typography: "Create account" Display 28sp semibold, color.on-background
· Typography: error Label 14sp, color.error; field labels Label 14sp
· Color: CTA background color.primary; error color.error
· Color: input border color.outline; error state border color.error
· Elevation: CTA overlay (8dp)
· a11y: error announced via liveRegion=Polite; password toggle contentDescription "Show/hide password"

Ref: UI-SPEC §Auth Screens, §Copywriting Contract §Error State Copy

---

### Org Picker — List State

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│                                      │
│  Select your family                  │
│                                      │
│  ┌──────────────────────────────┐    │
│  │ The Hogan Family             │    │
│  └──────────────────────────────┘    │
│  ┌──────────────────────────────┐    │
│  │ Smith Family                 │    │
│  └──────────────────────────────┘    │
│  ┌──────────────────────────────┐    │
│  │ Johnson Household            │    │
│  └──────────────────────────────┘    │
│                                      │
│  Signed in as user@example.com       │
│                                      │
│                                      │
│                                      │
└──────────────────────────────────────┘
```

· Typography: "Select your family" Display 28sp semibold, color.on-background
· Typography: org name rows Body 16sp, color.on-surface
· Typography: signed-in-as Label 14sp, color.on-surface 70% opacity
· Color: org row background color.surface, radius.md (12dp), elevation raised (2dp)
· a11y: each org row role=button, contentDescription "[Family name], select"
· a11y: min tap target 48dp per row

Ref: UI-SPEC §Auth Screens (Org Picker), §Component Inventory §Event Card (row pattern reused)

---

### Org Picker — Loading and Error States

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│                                      │
│  Select your family                  │
│                                      │
│  [LOADING STATE]                     │
│                                      │
│          ◌  (spinner)                │
│                                      │
│  [ERROR STATE]                       │
│                                      │
│  Couldn't connect. Check your        │
│  connection and try again.           │
│                                      │
│  ╔══════════════════════════════╗    │
│  ║          Try again           ║    │
│  ╚══════════════════════════════╝    │
│                                      │
│                                      │
└──────────────────────────────────────┘
```

· Typography: error Body 16sp, color.error
· Typography: "Try again" CTA Body 16sp semibold, color.on-primary
· Color: spinner color.primary; error text color.error; CTA background color.primary
· Elevation: CTA overlay (8dp)
· a11y: spinner contentDescription "Loading families"; error liveRegion=Polite
· a11y: "Try again" button role=button, min tap target 48dp

Ref: UI-SPEC §Auth Screens, §Loading State (component 12), §Error State (component 11)

---

### Invite Caregiver — Default, Success, and Error States

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│  ← Back                              │
│                                      │
│  Invite caregiver                    │
│                                      │
│  Email address                       │
│  ┌──────────────────────────────┐    │
│  │ caregiver@example.com        │    │
│  └──────────────────────────────┘    │
│  [ERROR] ⚠ Couldn't send invite.     │
│           Try again.                 │
│                                      │
│  ╔══════════════════════════════╗    │
│  ║          Send invite         ║    │
│  ╚══════════════════════════════╝    │
│                                      │
│  [SUCCESS STATE — replaces form]     │
│  Invite sent to                      │
│  caregiver@example.com               │
│                                      │
└──────────────────────────────────────┘
```

· Typography: "Invite caregiver" Display 28sp semibold, color.on-background
· Typography: CTA "Send invite" Body 16sp semibold, color.on-primary
· Typography: success confirmation Body 16sp, color.on-background
· Color: CTA background color.primary; error color.error; back arrow color.on-surface
· Elevation: CTA overlay (8dp)
· a11y: email field role=text, type=emailAddress; CTA contentDescription "Send invite"
· a11y: success state liveRegion=Polite; error liveRegion=Polite

Ref: UI-SPEC §Auth Screens (Invite Caregiver), §Primary CTAs, §Copywriting Contract §Error State Copy

---

## Group B — Onboarding Wizard

### Step 2 — Family Name Input

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│  ● ○ ○ ○ ○  (step dots 1–5, dot 2   │
│              active = color.primary) │
│                                      │
│  Your family name                    │
│                                      │
│  What should we call your family?    │
│                                      │
│  ┌──────────────────────────────┐    │
│  │ The Hogan Family             │    │
│  └──────────────────────────────┘    │
│                                      │
│                                      │
│                                      │
│  ╔══════════════════════════════╗    │
│  ║        Continue setup        ║    │
│  ╚══════════════════════════════╝    │
│                                      │
│  [TAB BAR HIDDEN — wizard step]      │
└──────────────────────────────────────┘
```

· Typography: "Your family name" Display 28sp semibold, color.on-background
· Typography: subtext Body 16sp regular, color.on-surface 70% opacity
· Typography: input Body 16sp, color.on-background; CTA "Continue setup" Body 16sp semibold
· Color: CTA background color.primary; step dots active color.primary, inactive color.outline
· Color: field background color.surface-container, border color.outline, radius.sm
· Elevation: CTA overlay (8dp)
· a11y: step dots accessibilityLabel "Step 2 of 5"; field role=text
· Note: tab bar hidden throughout all wizard steps (REQ-035)

Ref: UI-SPEC §Admin Onboarding Wizard (Step 2), §Primary CTAs

---

### Step 3 — Child Nickname and Birth Month/Year

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│  ○ ● ○ ○ ○  (step 3 active)         │
│                                      │
│  Your child                          │
│                                      │
│  Nickname                            │
│  ┌──────────────────────────────┐    │
│  │ Alex                         │    │
│  └──────────────────────────────┘    │
│                                      │
│  Birth month       Birth year        │
│  ┌──────────────┐  ┌──────────────┐  │
│  │ June      ▾  │  │ 2022      ▾  │  │
│  └──────────────┘  └──────────────┘  │
│                                      │
│  ╔══════════════════════════════╗    │
│  ║        Continue setup        ║    │
│  ╚══════════════════════════════╝    │
│                                      │
│  [TAB BAR HIDDEN — wizard step]      │
└──────────────────────────────────────┘
```

· Typography: "Your child" Display 28sp semibold; field labels Label 14sp; input Body 16sp
· Typography: CTA "Continue setup" Body 16sp semibold, color.on-primary
· Color: CTA color.primary; dropdowns background color.surface-container, border color.outline
· Color: chevron icon color.outline; radius.sm on all fields
· Elevation: CTA overlay (8dp)
· a11y: nickname field role=text, label "Child nickname"; selectors role=spinnerPicker
· a11y: "Continue setup" contentDescription "Continue setup, step 3 of 5"
· Note: only birth_month and birth_year collected — no full birthdate (data minimisation)

Ref: UI-SPEC §Admin Onboarding Wizard (Step 3), §Spacing Scale §Child data minimisation

---

### Step 4 — Consent Screen

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│  ○ ○ ● ○ ○  (step 4 active)         │
│                                      │
│  About your data                     │
│                                      │
│  We store only your child's          │
│  nickname and approximate birth      │
│  date. No photos or full names.      │
│  Data is deleted when you close      │
│  your account.                       │
│                                      │
│  ┌──────────────────────────────┐    │
│  │ ☐  I confirm I am the parent │    │
│  │    or legal guardian of this │    │
│  │    child and am 18 years of  │    │
│  │    age or older              │    │
│  └──────────────────────────────┘    │
│                                      │
│  ╔══════════════════════════════╗    │
│  ║    I agree — continue  🔒    ║    │
│  ╚══════════════════════════════╝    │
│  (button disabled until checked)     │
│  [TAB BAR HIDDEN — wizard step]      │
└──────────────────────────────────────┘
```

· Typography: "About your data" Display 28sp semibold; data explanation Body 16sp regular, color.on-background
· Typography: checkbox label Body 16sp regular; CTA "I agree — continue" Body 16sp semibold
· Color: CTA disabled — color.primary at 38% opacity; enabled — color.primary full; color.on-primary text
· Color: checkbox border color.outline; checked state color.primary fill
· Elevation: CTA overlay (8dp); no back gesture from this step (wizard linearity)
· a11y: checkbox role=checkbox, contentDescription "Confirm parental guardian and 18+ attestation"
· a11y: CTA disabled state announced "I agree — continue, dimmed, button" when unchecked
· COMPLIANCE: exact REQ-009 copy: "I confirm I am the parent or legal guardian of this child and am 18 years of age or older"
· COMPLIANCE: T-04-06 mitigated — verbatim consent text rendered; no paraphrase permitted

Ref: UI-SPEC §Admin Onboarding Wizard (Step 4), §Copywriting Contract §Consent, REQUIREMENTS REQ-009

---

### Step 5 — Success / Completion

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│  ○ ○ ○ ● ○  (step 5 active)         │
│                                      │
│                                      │
│  You're all set!                     │
│                                      │
│  Alex is ready to track.             │
│                                      │
│  Start logging potty trips           │
│  for Alex and build your first       │
│  streak together.                    │
│                                      │
│                                      │
│                                      │
│  ╔══════════════════════════════╗    │
│  ║        Start tracking        ║    │
│  ╚══════════════════════════════╝    │
│                                      │
│  [TAB BAR HIDDEN — wizard step]      │
└──────────────────────────────────────┘
```

· Typography: "You're all set!" Display 28sp semibold; child name + body Body 16sp, color.on-background
· Typography: CTA "Start tracking" Body 16sp semibold, color.on-primary
· Color: CTA background color.primary; step dots final-step active color.primary
· Elevation: CTA overlay (8dp)
· a11y: CTA contentDescription "Start tracking"; navigates to main app (replaces nav stack root)
· Note: tapping CTA replaces navigation stack root with Tab Shell — wizard cannot be re-entered

Ref: UI-SPEC §Admin Onboarding Wizard (Step 5), §Auth → Main App Transition, §Primary CTAs

---

## Group C — Home Tab + Overlays

### Home — Single Child, No Status Chips

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│                                      │
│             Alex                     │
│      (Title 20sp — no chevron)       │
│                                      │
│               4                     │
│          events today                │
│                                      │
│                                      │
│  ╔══════════════════════════════╗    │
│  ║              Log             ║    │
│  ╚══════════════════════════════╝    │
│  (wide pill ~75% screen width, 52dp) │
│                                      │
│                                      │
├──────────────────────────────────────┤
│  Home  │ History │Progress│ Settings │
│   ●    │         │        │          │
└──────────────────────────────────────┘
```

· Typography: child name Title 20sp semibold, color.on-background (no chevron — single child)
· Typography: event count Display 28sp semibold, color.on-background; "events today" Label 14sp
· Typography: "Log" Body 16sp semibold, color.on-primary
· Color: Log button background color.primary (#7E22CE), radius.pill (100dp), elevation overlay (8dp)
· Color: tab bar background color.surface; active tab Home icon + label color.primary
· Color: inactive tabs color.on-surface 60% opacity (REQ-035)
· Elevation: Log button 8dp shadow; tab bar flat (0dp)
· a11y: Log button contentDescription "Log potty trip", role=button, 52dp height
· a11y: active tab "Home, selected, tab"; inactive tabs "[Tab], tab"
· Note: tab bar always visible on main app screens (REQ-035); chevron hidden for single child

Ref: UI-SPEC §Main App — Home Tab, §Bottom Tab Bar (component 1), §Log Button (component 2)

---

### Home — Multi-Child with Status Chips

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│                                      │
│           Alex ›                     │
│   (Title 20sp — chevron visible)     │
│                                      │
│               6                     │
│          events today                │
│                                      │
│  [2 need details] [1 syncing…]       │
│   (chips — Caption 12sp, 28dp h)    │
│                                      │
│  ╔══════════════════════════════╗    │
│  ║              Log             ║    │
│  ╚══════════════════════════════╝    │
│                                      │
├──────────────────────────────────────┤
│  Home  │ History │Progress│ Settings │
│   ●    │         │        │          │
└──────────────────────────────────────┘
```

· Typography: child name + chevron "Alex ›" Title 20sp semibold; chevron Caption size, color.outline
· Typography: status chip labels Caption 12sp, color.on-secondary; chip background color.secondary
· Color: chips radius.pill (100dp), height 28dp, elevation raised (2dp); gap between chips 8dp
· Color: tab bar as above — Home active color.primary; inactive color.on-surface 60% (REQ-035)
· a11y: child header contentDescription "Alex, switch child, button", role=button, 48dp tap target
· a11y: "2 need details" chip contentDescription "2 events need details, button"
· a11y: "1 syncing" chip contentDescription "1 event pending sync, button"
· Note: chips hidden entirely when both counts are zero; chevron visible only for multi-child families

Ref: UI-SPEC §Main App — Home Tab, §Status Chips (component 8), §Child Switcher (component 9)

---

### Toast — Post-Log with Event Type Quick-Pick Chips

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│                                      │
│           Alex ›                     │
│               6                     │
│          events today                │
│                                      │
│  ╔══════════════════════════════╗    │
│  ║              Log             ║    │
│  ╚══════════════════════════════╝    │
│                                      │
│  ┌──────────────────────────────┐    │
│  │ Logged. Add a type?          │    │
│  │                              │    │
│  │ [Pee] [Poo] [Both]          │    │
│  │ [Accident (pee)]             │    │
│  │ [Accident (poo)] [Tried]     │    │
│  └──────────────────────────────┘    │
│  (toast above tab bar, 8dp gap)      │
├──────────────────────────────────────┤
│  Home  │ History │Progress│ Settings │
│   ●    │         │        │          │
└──────────────────────────────────────┘
```

· Typography: toast body "Logged. Add a type?" Body 16sp, #FFFFFF on #1C1B1F background
· Typography: event-type chip labels Label 14sp, color.on-secondary on color.secondary background
· Color: toast background #1C1B1F (near-black, contrast in both modes), radius.lg (16dp), elevation overlay (8dp)
· Color: chip height 28dp, radius.pill; gap between chips 4dp (space-1)
· Color: tab bar visible below toast (REQ-035)
· Motion: toast enters slide-up + fade-in 200ms; exits slide-down + fade-out 150ms (D-36)
· a11y: toast liveRegion=Polite; chip labels "Pee, button", "Poo, button", etc.
· a11y: auto-dismiss after 12 seconds (D-17)
· Note: six chip labels verbatim: Pee, Poo, Both, Accident (pee), Accident (poo), Tried

Ref: UI-SPEC §Toast Post-Log (component 6), §Log Button → Toast → Bottom Sheet Flow, §Copywriting Contract

---

### Home — Empty State (New Caregiver)

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│                                      │
│           Alex                       │
│      (Title 20sp — no chevron        │
│       if single child)               │
│                                      │
│                                      │
│          No events yet               │
│   Log your first potty trip to       │
│      see it here.                    │
│                                      │
│                                      │
│  ╔══════════════════════════════╗    │
│  ║              Log             ║    │
│  ╚══════════════════════════════╝    │
│                                      │
├──────────────────────────────────────┤
│  Home  │ History │Progress│ Settings │
│   ●    │         │        │          │
└──────────────────────────────────────┘
```

· Typography: empty heading "No events yet" Headline 24sp semibold, color.on-background
· Typography: empty body Body 16sp regular, color.on-surface 70% opacity
· Typography: event count area replaced with empty state text (no integer shown)
· Color: Log button still present and active (primary focal point even in empty state)
· Color: tab bar Home active color.primary; inactive color.on-surface 60% (REQ-035)
· a11y: empty state announced on screen load via liveRegion=Polite
· Note: Log button remains fully interactive in empty state — caregiver can log immediately

Ref: UI-SPEC §Main App — Home Tab, §Empty State (component 10), §Copywriting Contract §Empty State Copy

---
