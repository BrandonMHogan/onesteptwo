# Design Tokens — OneStepTwo

> Source: `.planning/phases/04-ui-ux-design/04-UI-SPEC.md` (approved 2026-06-29)
> Values must not be altered — all modifications require UI-SPEC.md to be updated first.

---

## Color Tokens

### Semantic Tokens

Tab bar active state: `color.primary` (REQ-035). Inactive icon + label: `color.on-surface` at 60% opacity.

| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| `color.background` | `#FFFFFF` | `#0E0A14` | Screen background |
| `color.surface` | `#FAF5FF` | `#1A1427` | Cards, tab bar bg, nav bg, bottom sheet bg |
| `color.surface-container` | `#F3E8FF` | `#25183A` | Input field backgrounds, heatmap empty cells |
| `color.primary` | `#7E22CE` | `#D8B4FE` | Accent — reserved for log button, tab active, primary CTAs, streak callout, heatmap high, badge unlocked, focus ring |
| `color.on-primary` | `#FFFFFF` | `#3B0764` | Text/icons on primary-colored surfaces |
| `color.secondary` | `#F3E8FF` | `#2D1B4A` | Secondary containers, status chip background |
| `color.on-secondary` | `#581C87` | `#E9D5FF` | Text on secondary surfaces |
| `color.on-background` | `#1C1B1F` | `#ECE8F4` | Body text, primary content |
| `color.on-surface` | `#1C1B1F` | `#ECE8F4` | Text on cards and surface elements |
| `color.outline` | `#CDC7D8` | `#534870` | Dividers, field borders, inactive separators |
| `color.error` | `#B91C1C` | `#FCA5A5` | Error text, destructive button fill |
| `color.on-error` | `#FFFFFF` | `#7F1D1D` | Text on error-colored surfaces |
| `color.success` | `#15803D` | `#86EFAC` | Sync confirmed, milestone unlocked accent |
| `color.on-success` | `#FFFFFF` | `#14532D` | Text on success-colored surfaces |

### Primary Purple Scale

| Token | Hex | Usage |
|-------|-----|-------|
| `primary-50` | `#FAF5FF` | Surface (secondary 30% light mode) |
| `primary-100` | `#F3E8FF` | Secondary surface container, input field backgrounds (light) |
| `primary-200` | `#E9D5FF` | On-secondary in dark mode |
| `primary-300` | `#D8B4FE` | Primary accent in dark mode; heatmap high (dark) |
| `primary-400` | `#C084FC` | Heatmap medium intensity (light) |
| `primary-500` | `#A855F7` | Anchor reference (not used directly in semantic tokens) |
| `primary-600` | `#9333EA` | Heatmap low-to-medium boundary reference |
| `primary-700` | `#7E22CE` | Primary accent in light mode (Dominant accent 10%) |
| `primary-800` | `#6B21A8` | Heatmap high intensity (dark mode) |
| `primary-900` | `#581C87` | On-secondary in light mode |

### Heatmap Intensity Colors

| Intensity | Light | Dark | Condition |
|-----------|-------|------|-----------|
| Empty | `color.surface-container` | `color.surface-container` | 0 events |
| Low | `#F3E8FF` (primary-100) | `#6B21A8` (primary-800) | 1–2 events |
| Medium | `#C084FC` (primary-400) | `#A855F7` (primary-500) | 3–5 events |
| High | `#7E22CE` (primary-700) | `#D8B4FE` (primary-300) | 6+ events |

---

## Typography Tokens

Six levels per D-25. Weights limited to regular (400) and semibold (600).

| Role | Size | Weight | Line Height | Letter Spacing | Android TextStyle | iOS Font |
|------|------|--------|-------------|----------------|-------------------|----------|
| Display | 28sp / 28pt | Semibold (600) | 34sp / 34pt (1.21×) | 0sp / 0pt | `headlineMedium` (custom weight) | `.title` + `.semibold` |
| Headline | 24sp / 24pt | Semibold (600) | 30sp / 30pt (1.25×) | 0sp / 0pt | `headlineSmall` (custom weight) | `.system(size: 24, weight: .semibold)` |
| Title | 20sp / 20pt | Semibold (600) | 26sp / 26pt (1.30×) | 0sp / 0pt | `titleLarge` (custom weight) | `.title3` + `.semibold` |
| Body | 16sp / 16pt | Regular (400) | 24sp / 24pt (1.50×) | 0.5sp / 0pt | `bodyLarge` | `.callout` |
| Label | 14sp / 14pt | Regular (400) | 20sp / 20pt (1.43×) | 0.1sp / 0pt | `labelLarge` | `.system(size: 14, weight: .regular)` |
| Caption | 12sp / 12pt | Regular (400) | 16sp / 16pt (1.33×) | 0.4sp / 0pt | `bodySmall` | `.caption` |

---

## Spacing Tokens

Base scale (all multiples of 4):

| Token | dp / pt | Usage |
|-------|---------|-------|
| `space-1` | 4 | Icon-to-label gap, tight inline padding, chip internal gap |
| `space-2` | 8 | Compact element spacing, field-to-field gap, error message gap |
| `space-4` | 16 | Default horizontal screen padding, standard element spacing |
| `space-6` | 24 | Section padding, group separation, vertical section gap |
| `space-8` | 32 | Layout gap, heading-to-content gap |
| `space-12` | 48 | Major section breaks |
| `space-16` | 64 | Top/bottom screen breathing room on auth and onboarding screens |

Exceptions:

| Context | Value | Reason |
|---------|-------|--------|
| Minimum tap target (Android) | 48dp height/width | WCAG 2.5.5 / Material3 accessibility |
| Minimum tap target (iOS) | 44pt height/width | Apple HIG |
| Log button height | 52dp / 52pt | Wide pill — comfortably above 48dp minimum |
| Bottom tab bar height (Android) | 56dp + system bottom inset | Material3 NavigationBar spec |
| Status chip height | 28dp / 28pt | Compact pill, not interactive (read-only) |
| Heatmap cell minimum size | 32dp / 32pt | Tappable target (day-detail drill-down) |

---

## Corner Radius Tokens

| Token | Value | Usage |
|-------|-------|-------|
| `radius.sm` | 8dp / 8pt | Input fields, OTP input, password toggle container |
| `radius.md` | 12dp / 12pt | Event cards, settings list rows, milestone badge frame |
| `radius.lg` | 16dp / 16pt | Bottom sheet top corners, toast, child switcher sheet, onboarding wizard step cards |
| `radius.pill` | 100dp / 100pt | Log button, status chips, event type quick-pick chips in toast, tab active indicator |

---

## Elevation Tokens

| Level | Android | iOS | Usage |
|-------|---------|-----|-------|
| Flat | `elevation = 0.dp`, no shadow | No shadow modifier | Backgrounds, tab bar fill, form fields |
| Raised | `elevation = 2.dp` (via `Card`) | `.shadow(radius: 2, y: 1)` | Event cards, status chips |
| Overlay | `elevation = 8.dp` | `.shadow(radius: 8, y: 4)` | Bottom sheets, log button, toast |

---

## Motion Tokens

| Token | Value | Android API | SwiftUI API |
|-------|-------|-------------|-------------|
| `motion.duration.short` | 150ms | `tween(150)` | `.easeInOut(duration: 0.15)` |
| `motion.duration.medium` | 300ms | `tween(300)` | `.easeInOut(duration: 0.30)` |
| `motion.duration.long` | 400ms | `tween(400)` | `.easeInOut(duration: 0.40)` |
| `motion.easing.standard` | ease-in-out | `FastOutSlowInEasing` | `.easeInOut` |
| `motion.easing.spring` | spring | `spring(stiffness=300f, dampingRatio=0.6f)` | `.spring(response: 0.35, dampingFraction: 0.7)` |

Android animations use Compose built-in APIs only — no Lottie, no third-party (D-30).
iOS animations use SwiftUI built-in APIs only — no Lottie, no third-party (D-31).

### Specific Animation Specs

| Interaction | Duration | Easing | Android Compose | SwiftUI |
|-------------|----------|--------|-----------------|---------|
| Tab switch cross-dissolve (D-32) | 120ms | ease-in-out | `AnimatedContent` with `fadeIn(tween(120)) + fadeOut(tween(120))` | `.transition(.opacity)` with `withAnimation(.easeInOut(duration: 0.12))` |
| Log button press scale (D-33) | press: 100ms, release: 200ms | spring | `animateFloatAsState(0.95f, spring(stiffness=500f, dampingRatio=0.7f))` | `.scaleEffect(pressed ? 0.95 : 1.0).animation(.spring(response: 0.10, dampingFraction: 0.7))` |
| Badge glow + scale (D-34) | ~400ms total | spring | `animateFloatAsState(scale 0.7→1.0) + animateFloatAsState(glow alpha 0→0.6→0)` | `withAnimation(.spring(response: 0.4, dampingFraction: 0.6)) { scale = 1.0 }` |
| Toast enter (D-36) | 200ms | ease-out | `AnimatedVisibility(slideInVertically { it } + fadeIn(tween(200)))` | `.transition(.asymmetric(insertion: .move(edge: .bottom).combined(with: .opacity)))` |
| Toast exit (D-36) | 150ms | ease-in | `AnimatedVisibility(slideOutVertically { it } + fadeOut(tween(150)))` | `.transition(.asymmetric(removal: .move(edge: .bottom).combined(with: .opacity)))` |
| Bottom sheet open/close (D-35) | platform default | platform default | `ModalBottomSheet` default (~300ms) | `.sheet` modifier default (natural spring) |
