# Phase 4: UI/UX Design - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-25
**Phase:** 4-UI/UX Design
**Areas discussed:** Visual personality, Platform approach, Core log interaction, Design token format, Animation library

---

## Visual Personality

| Option | Description | Selected |
|--------|-------------|----------|
| Warm & playful | Bright accent colors, rounded corners, friendly illustrations, maybe a mascot | |
| Calm & minimal | Soft neutrals, generous whitespace, simple line icons, no illustrations | ✓ (with nuance) |
| Energetic & celebratory | High-contrast colors, bold typography, confetti on milestones | |

**User's choice:** "Calm and minimal with maybe a bit of playful" — free-text response

**Notes:** User described a hybrid: calm/minimal as the foundation, with the playful personality showing through only in celebration moments (milestones, streak achievements). Day-to-day UI stays clean and understated.

---

| Option | Description | Selected |
|--------|-------------|----------|
| Celebrations only | Day-to-day UI clean, playfulness reserved for milestone/streak moments | ✓ |
| Everyday + celebrations | Subtle playfulness in corner radii, accent color, icon style + bigger celebration moments | |
| You decide | Claude picks based on direction | |

**User's choice:** Celebrations only

---

| Option | Description | Selected |
|--------|-------------|----------|
| Soft blue/teal | Trust-building, gender-neutral | |
| Warm purple/violet | Friendly but not childish, modern, distinctive | ✓ |
| Earthy green | Calm, organic, growth metaphor | |
| Let me describe it | Specific color in mind | |

**User's choice:** Warm purple/violet

---

| Option | Description | Selected |
|--------|-------------|----------|
| System fonts only | SF Pro (iOS), Roboto (Android), zero custom font weight | ✓ |
| One custom font for headings | Single typeface for h1/h2, body stays system | |
| You decide | Claude picks | |

**User's choice:** System fonts only

---

| Option | Description | Selected |
|--------|-------------|----------|
| Heavily rounded (16-24dp) | Large corner radii, soft and approachable | |
| Moderately rounded (8-12dp) | Balanced, safe choice for calm-minimal | |
| Minimal rounding (4-6dp) | Nearly square, crisp and utilitarian | |

**User's choice:** "Middle ground between 1 and 2" — captured as 12–16dp

---

| Option | Description | Selected |
|--------|-------------|----------|
| Outlined (stroke) icons | Lighter visual weight, less noise on dense screens | ✓ |
| Filled icons | Bolder, easier to read at small sizes | |
| Mixed: outlined inactive, filled active | Standard M3/HIG pattern | |
| You decide | Claude picks | |

**User's choice:** Outlined (stroke) icons throughout

---

| Option | Description | Selected |
|--------|-------------|----------|
| System/auto (follows OS setting) | Most user-friendly, no manual toggle | ✓ |
| Light only with dark planned later | Define tokens now, implement light in Phase 5 only | |
| You decide | Claude picks system/auto | |

**User's choice:** System/auto (follows OS setting)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Confetti + badge reveal animation | Confetti burst or particle effect on badge earn | |
| Badge glow + gentle animation | Soft glow/scale-up, no confetti — calm-celebratory | ✓ |
| Simple notification/toast | Clean toast, no animation beyond toast itself | |

**User's choice:** Badge glow + gentle animation

---

## Platform Approach

| Option | Description | Selected |
|--------|-------------|----------|
| Unified design language | One spec for both platforms, identical visual output | |
| Platform-native conventions | Android=M3, iOS=HIG — different specs | |
| Unified with platform-specific exceptions | One design language, documented exceptions | ✓ |

**User's choice:** Unified with platform-specific exceptions

---

| Option | Description | Selected |
|--------|-------------|----------|
| Both bottom (unified) | Tab bar at bottom on both platforms | ✓ |
| You decide | Claude picks both bottom | |

**User's choice:** Both bottom (unified)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Platform-native back (documented exception) | iOS NavigationStack swipe; Android system back | ✓ |
| Custom unified back button | Same branded back button both platforms | |
| You decide | Claude picks platform-native | |

**User's choice:** Platform-native back (documented exception)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Unified bottom sheet (same design both) | Rounded-top sheet, same spec on both | |
| iOS sheet vs Android bottom sheet | iOS .sheet card style; Android ModalBottomSheet | ✓ |
| You decide | Claude picks unified | |

**User's choice:** Platform-specific: iOS .sheet / Android ModalBottomSheet

---

| Option | Description | Selected |
|--------|-------------|----------|
| Log button only | Haptic only on log button | |
| Key interactions only | Log button + milestone + error states | ✓ |
| Platform defaults everywhere | Let platforms handle haptics automatically | |

**User's choice:** Key interactions only

---

| Option | Description | Selected |
|--------|-------------|----------|
| Spec content descriptions and tap targets only | Minimum a11y annotations | |
| Full a11y annotations per component | Content desc, role, tap target, focus order, TalkBack/VoiceOver notes | ✓ |
| You decide | Claude picks lightweight | |

**User's choice:** Full a11y annotations per component

---

| Option | Description | Selected |
|--------|-------------|----------|
| Standard safe area insets everywhere | Default WindowInsets/safeAreaInset handling | ✓ |
| Spec edge-to-edge with custom insets | Immersive design, background behind status bar | |
| You decide | Claude picks standard | |

**User's choice:** Standard safe area insets everywhere

---

## Core Log Interaction

| Option | Description | Selected |
|--------|-------------|----------|
| FAB above tab bar | Circular/rounded-square floating action button | |
| Full-width bottom bar button | Edge-to-edge pill above tab bar | |
| Centered in content area | Large button embedded in content, scrolls away | |

**User's choice:** "Not a FAB, not full-width. The home page doesn't scroll so everything fits. Button is above bottom nav, large and easily clicked, but not full width." — captured as **wide pill button (~70-80% screen width)**

---

| Option | Description | Selected |
|--------|-------------|----------|
| Haptic + button press animation + persistent toast | Scale-down, haptic, long-lived toast | ✓ |
| Haptic + full-screen flash/ripple + toast | Dramatic ripple across screen | |
| Haptic + count increment animation + toast | Day count increments visibly | |
| You decide | Claude picks haptic+press+toast | |

**User's choice:** Haptic + button press animation + persistent toast

---

| Option | Description | Selected |
|--------|-------------|----------|
| Long-lived (8-12s) with 'Add details' action button | Toast with CTA to open detail sheet | |
| Long-lived with event type quick-pick inline | Toast contains type chips directly | ✓ |
| Short toast (2-3s) with separate pending banner | Short confirmation + banner handles rest | |

**User's choice:** Long-lived with inline event type quick-pick chips (~12s)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Event type first, then notes, then time edit | Type selector top, notes, time adjustment | ✓ |
| Time first, then event type, then notes | Time shown first | |
| You decide | Claude picks type first | |

**User's choice:** Event type first, then notes, then time edit

---

| Option | Description | Selected |
|--------|-------------|----------|
| Child name + today's count + pending banner + log button | Focused home layout | ✓ |
| Child name + streak + today's count + log button | Adds streak to home | |
| Child name + log button only (minimal) | Stripped down | |
| You decide | Claude picks option 1 | |

**User's choice:** Child name + today's count + combined status area + log button

---

Offline/pending-details status area — **user-initiated combination**: User specified both the pending-details indicator and the offline sync indicator should share one small banner area, shown as two separate side-by-side chips: `[N need details]` `[N syncing]`. Hidden when both counts are zero.

| Option | Description | Selected |
|--------|-------------|----------|
| Two lines in the same card | One card, two lines | |
| Two separate small chips side by side | Pill-shaped chips in a row | ✓ |
| You decide | Claude picks two-line card | |

**User's choice:** Two separate small chips side by side

---

| Option | Description | Selected |
|--------|-------------|----------|
| Time + event type icon + notes (compact) | Dense compact list | ✓ |
| Time + event type label + caregiver name + notes (full) | Full context cards | |
| You decide | Claude picks compact | |

**User's choice:** Compact (time + icon + notes)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Child name tappable with chevron/dropdown | Name in header opens bottom sheet | ✓ |
| Tab bar or header chip row | Avatar chips always visible | |
| You decide | Claude picks tappable name | |

**User's choice:** Child name tappable with chevron indicator

---

## Design Token Format

| Option | Description | Selected |
|--------|-------------|----------|
| Single table: token name + Compose + SwiftUI value | Cross-platform parity table | ✓ |
| Separate platform sections | Compose and SwiftUI as separate blocks | |
| You decide | Claude picks single table | |

**User's choice:** Single table format in DESIGN-TOKENS.md

---

| Option | Description | Selected |
|--------|-------------|----------|
| Semantic tokens only | ~10-15 semantic tokens | |
| Semantic + supporting palette | Semantic + primary purple shades primary-50 to primary-900 | ✓ |
| You decide | Claude picks semantic + partial supporting | |

**User's choice:** Semantic + supporting palette

---

| Option | Description | Selected |
|--------|-------------|----------|
| 6 levels: Display, Headline, Title, Body, Label, Caption | M3-aligned full scale | ✓ |
| 4 levels: Large heading, Section heading, Body, Small/caption | Simplified scale | |
| You decide | Claude picks 6 levels | |

**User's choice:** 6 levels (M3-aligned)

---

| Option | Description | Selected |
|--------|-------------|----------|
| 4dp base (4, 8, 12, 16, 24, 32, 48, 64) | Fine-grained standard Material grid | |
| 8dp base (8, 16, 24, 32, 48, 64) | Simpler scale | ✓ |
| You decide | Claude picks 4dp base | |

**User's choice:** 8dp base

---

| Option | Description | Selected |
|--------|-------------|----------|
| Named semantic color token per event type | color.event.pee etc. | |
| Heatmap uses intensity levels only (no type-specific colors) | Count-based intensity, type colors in list view only | ✓ |
| You decide | Claude picks named tokens | |

**User's choice:** Heatmap intensity levels only (purple tints)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Purple tints (matching primary palette) | Brand-coherent intensity | ✓ |
| Green tints (conventional progress/GitHub style) | Universally readable as activity | |
| You decide | Claude picks purple | |

**User's choice:** Purple tints (primary-100/400/700)

---

| Option | Description | Selected |
|--------|-------------|----------|
| 3 levels: flat (0dp), raised (2dp), overlay (8dp) | Clean 3-level system | ✓ |
| You decide | Claude picks 3 levels | |

**User's choice:** 3 elevation levels

---

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — include duration + easing as tokens | Motion tokens in DESIGN-TOKENS.md | ✓ |
| No — animation values in animation spec only | Keep tokens visual-only | |
| You decide | Claude picks yes | |

**User's choice:** Yes — include motion tokens

---

## Animation Library

| Option | Description | Selected |
|--------|-------------|----------|
| Compose built-in only | animate*AsState, AnimatedVisibility, animateContentSize | ✓ |
| Lottie for celebrations only | Lottie for badge glow, Compose for everything else | |
| You decide | Claude picks Lottie for celebrations | |

**User's choice:** Compose built-in only

---

| Option | Description | Selected |
|--------|-------------|----------|
| SwiftUI built-in only | withAnimation, .animation, matchedGeometryEffect | ✓ |
| LottieSwiftUI for celebrations | Shared JSON asset with Android | |
| You decide | Claude picks SwiftUI built-in | |

**User's choice:** SwiftUI built-in only

---

| Option | Description | Selected |
|--------|-------------|----------|
| No transition (instant switch) | Fastest, cleanest | |
| Fade cross-dissolve (100-150ms) | Subtle polish without feeling slow | ✓ |
| You decide | Claude picks instant | |

**User's choice:** Fade cross-dissolve (100-150ms)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Scale down on press (0.95), spring back on release | Classic press-and-spring | ✓ |
| Color flash on tap | Brief background brightening | |
| You decide | Claude picks scale-down spring | |

**User's choice:** Scale to 0.95 on press, spring back on release (~100ms press, ~200ms spring)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Scale up from 0.7 + glow overlay fade-in, 400ms spring | Scale + concurrent glow | ✓ |
| Ripple expand from badge center + badge reveal, 500ms | Outward ripple + fade-in | |
| You decide | Claude picks scale + glow | |

**User's choice:** Scale from 0.7x to 1.0x + glow overlay, 400ms spring curve

---

| Option | Description | Selected |
|--------|-------------|----------|
| Platform default (Android ~300ms, iOS spring) | Let platform handle timing | ✓ |
| Explicit motion token values (300ms both) | Override to motion.duration.medium | |
| You decide | Claude picks platform default | |

**User's choice:** Platform default

---

| Option | Description | Selected |
|--------|-------------|----------|
| Slide up from bottom edge + fade in / slide down + fade out | Directional + fade | ✓ |
| Fade in / fade out only | Calm and minimal, no slide | |
| You decide | Claude picks slide-up | |

**User's choice:** Slide up + fade in (200ms); slide down + fade out (150ms)

---

## Claude's Discretion

- Icon library/set selection (which specific outlined icon set to use)
- Exact primary purple/violet hex anchor value (primary-500)
- Precise line-height and letter-spacing values per typography level
- Event type icon designs (visual representation of each of pee/poo/both/accident types)
- Empty state copy and illustration (text-only approach, no illustrations)

## Deferred Ideas

None — discussion stayed within Phase 4 scope.
