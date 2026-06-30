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
· Color: "Sign up" link color.on-surface (70% opacity) or color.on-background — not color.primary
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
│  │ [  ] [  ] [  ] [  ] [  ] [  ]│    │
│  └──────────────────────────────┘    │
│  ⚠ Incorrect or expired code.        │
│    Try again.                        │
└──────────────────────────────────────┘
```

· Typography: error text Label 14sp, color.error (#B91C1C light / #FCA5A5 dark)
· Typography: OTP heading Display 28sp semibold
· Color: error icon color.error; OTP digit cells background color.surface-container, border color.outline
· Color: OTP cell focus ring color.primary (radius.sm)
· Elevation: same as default state
· a11y: error message liveRegion=Polite; OTP inputs role=text, traversalIndex ordered 1–6
· a11y: contentDescription "Enter verification code digit N" per OTP cell
· Note: 6 cells shown — Clerk default email OTP is 6 digits; verify if project uses custom Clerk configuration

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
· Color: "Sign in" link color.on-surface (70% opacity) or color.on-background — not color.primary
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
· Note: tab bar visible when accessed via Settings (Settings sub-screen rule, REQ-035)

Ref: UI-SPEC §Auth Screens (Invite Caregiver), §Primary CTAs, §Copywriting Contract §Error State Copy

---

## Group B — Onboarding Wizard

### Step 2 — Family Name Input

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│  ○ ● ○ ○ ○  (step dots 1–5, dot 2   │
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
│  ○ ○ ● ○ ○  (step 3 active)         │
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
│  ○ ○ ○ ● ○  (step 4 active)         │
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
│  ○ ○ ○ ○ ●  (step 5 active)         │
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
│  │                              │    │
│  │      add details             │    │
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
· Note: "add details" text link — Label 14sp, color.on-surface; tap opens Event Detail bottom sheet without requiring chip selection first (SCREEN-FLOWS.md step 6)

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

## Group D — History Tab

### History — Heatmap with Data

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│                                      │
│       Mo  Tu  We  Th  Fr  Sa  Su    │
│  Apr  ·   ·   ░   ▒   █   ▒   ░    │
│  May  █   █   ▒   ·   █   █   ░    │
│  Jun  ·   ░   ▒   █   ▒   ·   ·    │
│  Jul  ░   ▒   █   ▒   ░   ·   ·    │
│                                      │
│  less [·] [░] [▒] [█] more          │
│       empty low  med  high           │
│                                      │
├──────────────────────────────────────┤
│  Home  │ History │Progress│ Settings │
│        │   ●     │        │          │
└──────────────────────────────────────┘
```

· Typography: month labels Caption 12sp, color.outline; week labels (Mo–Su) Label 14sp, color.on-surface 70%
· Typography: legend "less" / "more" Caption 12sp, color.outline; intensity swatch labels Caption 12sp
· Color: empty cells color.surface-container; low intensity #F3E8FF (primary-100); medium #C084FC (primary-400); high #7E22CE (primary-700) — light mode values (D-27)
· Color: cell size min 32dp square (tappable target per WCAG); radius.sm (8dp); gap 4dp between cells
· Color: tab bar History active color.primary; inactive color.on-surface 60% (REQ-035)
· Motion: tint transition 150ms ease-in-out on data load (motion.duration.short)
· a11y: non-empty cell role=button, contentDescription "[N] events on [Mon, Jan 1]" (e.g. "6 events on Thu, Jun 12, button")
· a11y: empty cell role=none (non-interactive), contentDescription "[Mon, Jan 1], no events"
· Note: tapping non-empty cell pushes History Day-Detail screen (REQ-033)

Ref: UI-SPEC §Main App — History Tab, §Heatmap Cell (component 3), §Color §Heatmap Intensity Colors

---

### History — Empty State

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│                                      │
│                                      │
│                                      │
│          No events yet               │
│                                      │
│   Log your first potty trip to       │
│      see it here.                    │
│                                      │
│                                      │
│                                      │
│                                      │
│                                      │
├──────────────────────────────────────┤
│  Home  │ History │Progress│ Settings │
│        │   ●     │        │          │
└──────────────────────────────────────┘
```

· Typography: "No events yet" Headline 24sp semibold, color.on-background
· Typography: body copy Body 16sp regular, color.on-surface 70% opacity
· Color: heatmap grid replaced entirely by empty-state component; no grid cells rendered
· Color: tab bar History active color.primary; inactive color.on-surface 60% (REQ-035)
· Elevation: flat — no raised elements on empty state
· a11y: empty state container announced via liveRegion=Polite on screen load
· Note: empty state shown when the child has zero logged events (no events ever)

Ref: UI-SPEC §Main App — History Tab, §Empty State (component 10), §Copywriting Contract §Empty State Copy

---

### History Day-Detail — Full Screen

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│  ← Thursday, June 12                │
│  (Headline 24sp — platform back)    │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ 08:14  Pee        ✓ synced    │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ 10:32  Poo        [add details]│  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ 14:07  Both  "good effort"    │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ 16:55  Tried      ✓ synced    │  │
│  └────────────────────────────────┘  │
│                                      │
│  [TAB BAR HIDDEN — full-screen       │
│   push navigation detail view]       │
└──────────────────────────────────────┘
```

· Typography: date header Headline 24sp semibold, color.on-background (e.g. "Thursday, June 12")
· Typography: event time Label 14sp, color.on-surface 70%; event type Body 16sp semibold
· Typography: note text Body 16sp regular, color.on-surface; sync status Caption 12sp, color.success or color.outline
· Color: event cards background color.surface, radius.md (12dp), elevation raised (2dp)
· Color: "add details" pending-details link color.primary; pending card border color.outline
· Elevation: event cards raised (2dp); screen background color.background
· a11y: back button contentDescription "Go back to History"; platform back gesture enabled (D-10)
· a11y: pending card role=button, contentDescription "[time] [type] event — tap to add details"
· Note: tab bar hidden — full-screen push navigation detail view per REQ-035; back returns to heatmap

Ref: UI-SPEC §Main App — History Day-Detail View, §Event Card (component 5), §Navigation Patterns §History Drill-Down

---

## Group E — Progress + Settings

### Progress Tab — With Data

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│                                      │
│  Alex  (Label 14sp, 70% opacity)    │
│                                      │
│           12                         │
│        day streak                    │
│   Best: 18 days  (Label 14sp)        │
│                                      │
│  This week        All time           │
│      23              147             │
│  events            events            │
│                                      │
│  ┌──────────┐  ┌──────────┐         │
│  │ 🏅 First │  │ 🔒 Acc.  │         │
│  │  trip ✓  │  │ free day │         │
│  └──────────┘  └──────────┘         │
│  ┌──────────┐  ┌──────────┐         │
│  │ 🔒 7-day │  │ 🔒 30-day│         │
│  │ streak   │  │ streak   │         │
│  └──────────┘  └──────────┘         │
├──────────────────────────────────────┤
│  Home  │ History │Progress│ Settings │
│        │         │   ●    │          │
└──────────────────────────────────────┘
```

· Typography: active child name Label 14sp, color.on-surface 70% opacity
· Typography: streak count Display 28sp semibold, color.primary (#7E22CE light); "day streak" Body 16sp
· Typography: "Best: N days" Label 14sp, color.on-surface 70%; stat numbers Title 20sp semibold
· Typography: milestone badge labels Label 14sp; 2×2 grid, badge circles 64×64dp
· Color: unlocked badge 64×64 circle, color.primary border, color.surface-container bg, radius.md
· Color: locked badge 64×64 circle, color.outline border at 38% opacity, color.surface-container bg
· Color: tab bar Progress active color.primary; inactive color.on-surface 60% (REQ-035)
· a11y: streak region contentDescription "12 day streak, current. Best: 18 days"
· a11y: unlocked badge role=none, contentDescription "[Badge name], unlocked on [date]" (e.g. "First trip milestone, unlocked on June 3")
· a11y: locked badge role=none, contentDescription "30-day streak milestone, locked"

Ref: UI-SPEC §Main App — Progress Tab, §Milestone Badge (component 4), §Streak Display

---

### Progress Tab — Empty State

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│                                      │
│  Alex  (Label 14sp, 70% opacity)    │
│                                      │
│                                      │
│   Alex is just getting started       │
│                                      │
│   Keep logging to see streaks        │
│   and milestones.                    │
│                                      │
│                                      │
│                                      │
│                                      │
│                                      │
│                                      │
├──────────────────────────────────────┤
│  Home  │ History │Progress│ Settings │
│        │         │   ●    │          │
└──────────────────────────────────────┘
```

· Typography: "[Child name] is just getting started" Headline 24sp semibold, color.on-background
· Typography: "Keep logging to see streaks and milestones." Body 16sp regular, color.on-surface 70%
· Color: streak and stats sections replaced by empty-state component; milestone grid hidden
· Color: tab bar Progress active color.primary; inactive color.on-surface 60% (REQ-035)
· a11y: empty state liveRegion=Polite on screen load; child name label is non-interactive
· Note: empty state fires when child has zero logged events (REQ-034)

Ref: UI-SPEC §Main App — Progress Tab, §Empty State (component 10), §Copywriting Contract §Empty State Copy

---

### Settings Tab — Admin View

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│  Settings                            │
│                                      │
│  Family ──────────────────────────   │
│  Brandon  · Admin                    │
│  Jamie    · Caregiver         [✕]   │
│  Invite caregiver             [›]   │
│                                      │
│  Children ─────────────────────────  │
│  Alex  (Jun 2022)             [›]   │
│  Add child                    [›]   │
│                                      │
│  Notifications ────────────────────  │
│  Notify me for Alex           [⬜]  │
│                                      │
│  Account ──────────────────────────  │
│  brandon@example.com                 │
│  Sign out                     [›]   │
│  Delete my data               [›]   │
│  (color.error — 2 taps from Settings)│
├──────────────────────────────────────┤
│  Home  │ History │Progress│ Settings │
│        │         │        │    ●     │
└──────────────────────────────────────┘
```

· Typography: section labels Label 14sp semibold, color.on-surface 70%; row text Body 16sp
· Typography: "Delete my data" Body 16sp semibold, color.error (#B91C1C light / #FCA5A5 dark)
· Color: section dividers color.outline; list row background color.surface, radius.md (12dp)
· Color: "Delete my data" label color.error — visually distinct destructive action (REQ-014, T-04-07)
· Color: tab bar Settings active color.primary; inactive color.on-surface 60% (REQ-035)
· Elevation: rows flat (0dp); confirmation modal elevated (8dp) when delete is triggered
· a11y: "Delete my data" role=button, contentDescription "Delete my data, destructive action"
· a11y: remove-caregiver [✕] role=button, contentDescription "Remove Jamie, destructive"
· a11y: notification toggle role=switch, contentDescription "Notify me for Alex, [on/off]"
· COMPLIANCE: REQ-014 — "Delete my data" reachable in two taps from Settings tab (tap Settings → tap Delete my data)
· Note: Admin sees all 4 sections: Family, Children, Notifications, Account

Ref: UI-SPEC §Main App — Settings Tab (Admin), §Copywriting Contract §Destructive Action Confirmations, REQUIREMENTS REQ-014

---

### Settings Tab — Caregiver View

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│  Settings                            │
│                                      │
│  [Family section — HIDDEN]           │
│  [Children section — HIDDEN]         │
│  (role gate: org:caregiver)          │
│                                      │
│  Notifications ────────────────────  │
│  Notify me for Alex           [⬜]  │
│                                      │
│  Account ──────────────────────────  │
│  jamie@example.com                   │
│  Sign out                     [›]   │
│  Delete my data               [›]   │
│  (color.error — own account only)    │
│                                      │
│                                      │
├──────────────────────────────────────┤
│  Home  │ History │Progress│ Settings │
│        │         │        │    ●     │
└──────────────────────────────────────┘
```

· Typography: section labels Label 14sp semibold; row text Body 16sp, color.on-background
· Color: Family and Children sections not rendered (removed from view tree — not visibility:hidden)
· Color: tab bar Settings active color.primary; inactive color.on-surface 60% (REQ-035)
· a11y: screen reader traverses 2 sections only (Notifications, Account); hidden sections inaccessible
· SECURITY: role gate (org:caregiver) at view layer mirrors API-level enforcement (T-04-09 mitigated)
· Note: Caregiver sees only 2 sections: Notifications and Account; Family and Children sections are hidden

Ref: UI-SPEC §Main App — Settings Tab (Caregiver), §Access Control (V4), REQUIREMENTS REQ-014

---

## Group F — Sheets + States

### Event Detail — Bottom Sheet

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│  (background screen — scrim overlay) │
│                                      │
│ ─── bottom sheet ── (radius.lg) ─── │
│                                      │
│  Add details                  [×]   │
│                                      │
│  Event type                          │
│  [Pee] [Poo] [Both]                 │
│  [Accident (pee)] [Accident (poo)]  │
│  [Tried]                             │
│                                      │
│  Note                                │
│  ┌──────────────────────────────┐    │
│  │ Add a note (optional)        │    │
│  └──────────────────────────────┘    │
│                                      │
│  ┌──────────────────────────────┐    │
│  │ Logged at [HH:mm]        [›]│    │
│  └──────────────────────────────┘    │
│                                      │
│  ╔══════════════════════════════╗    │
│  ║         Save details         ║    │
│  ╚══════════════════════════════╝    │
└──────────────────────────────────────┘
```

· Typography: "Add details" Title 20sp semibold, color.on-background; dismiss [×] icon 24dp
· Typography: section label "Event type" / "Note" Label 14sp; event type chips Label 14sp
· Typography: note placeholder "Add a note (optional)" Body 16sp, color.outline; time row Body 16sp
· Typography: "Save details" CTA Body 16sp semibold, color.on-primary
· Color: sheet background color.surface; top corners radius.lg (16dp); scrim #00000066
· Color: CTA background color.primary; event type chips color.secondary bg, color.on-secondary text, radius.pill
· Color: note field background color.surface-container, border color.outline, radius.sm
· Elevation: sheet overlay (8dp); platform default open/close animation (D-35, ~300ms)
· a11y: sheet role=bottomSheet; dismiss button contentDescription "Close, dismiss sheet"
· a11y: event type chips role=radio within a radioGroup; "Save details" role=button
· Note: fields in order — event type → note → time adjustment → save CTA (per UI-SPEC §Log Button flow)

Ref: UI-SPEC §Event Detail Bottom Sheet (component 7), §Log Button → Toast → Bottom Sheet Flow, §Copywriting Contract

---

### Child Switcher — Bottom Sheet

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│  (background screen — scrim overlay) │
│                                      │
│ ─── bottom sheet ── (radius.lg) ─── │
│                                      │
│  Switch child                        │
│                                      │
│  ┌──────────────────────────────┐    │
│  │ Alex                    [✓] │    │
│  └──────────────────────────────┘    │
│  ┌──────────────────────────────┐    │
│  │ Sam                         │    │
│  └──────────────────────────────┘    │
│  ┌──────────────────────────────┐    │
│  │ Jordan                      │    │
│  └──────────────────────────────┘    │
│                                      │
│                                      │
│                                      │
│                                      │
└──────────────────────────────────────┘
```

· Typography: "Switch child" Title 20sp semibold, color.on-background
· Typography: child name rows Title 20sp semibold, color.on-background; active checkmark color.primary
· Color: sheet background color.surface; top corners radius.lg (16dp); row height 48dp
· Color: active row checkmark color.primary; inactive rows no indicator
· Color: scrim #00000066 behind sheet; sheet elevation overlay (8dp)
· a11y: sheet role=bottomSheet; rows role=radio within radioGroup "Switch child" (REQ-031)
· a11y: active row contentDescription "[child name], selected"; inactive "[child name]"
· a11y: selecting a child dismisses sheet and updates active child context
· Note: sheet triggered by tapping child name header (chevron "›") in Home tab multi-child view

Ref: UI-SPEC §Child Switcher (component 9), §Main App — Home Tab, REQUIREMENTS REQ-031

---

### Loading State and Error State

```
┌──────────────────────────────────────┐
│ STATUS BAR (system insets)           │
├──────────────────────────────────────┤
│                                      │
│                                      │
│  [LOADING STATE]                     │
│                                      │
│              ◌                       │
│     (CircularProgressIndicator /     │
│      ProgressView — centered)        │
│                                      │
│  [ERROR STATE]                       │
│                                      │
│   Couldn't load data.                │
│   Pull down to refresh.              │
│                                      │
│                                      │
│                                      │
│                                      │
└──────────────────────────────────────┘
```

· Typography: loading spinner — no label (spinner is self-explanatory); error text Body 16sp, color.error
· Typography: error copy "Couldn't load data. Pull down to refresh." Body 16sp, color.error
· Color: spinner (◌) color.primary (#7E22CE light / #D8B4FE dark); centered on screen
· Color: error text color.error (#B91C1C light / #FCA5A5 dark); background color.background
· Color: no tab bar shown — generic pattern applies to any screen or content zone
· Elevation: flat — loading and error states use no elevation
· a11y: spinner contentDescription "Loading"; liveRegion=Polite to announce loading start
· a11y: error message liveRegion=Polite; pull-to-refresh accessible via accessibility action
· Note: generic pattern — used for initial page loads and data-fetch failures across all tabs

Ref: UI-SPEC §Loading State (component 12), §Error State (component 11), §Copywriting Contract §Error State Copy

---
