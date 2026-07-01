# Phase 4: UI/UX Design - Context

**Gathered:** 2026-06-25
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 4 produces the complete UI/UX specification for OneStepTwo before any production UI code is written. The output is a design spec document (or set of documents) covering: all screen flows, lo-fi wireframes/annotated mockups for every screen and state, design tokens (color, typography, spacing, radii, elevation, motion), component specs for every component, and navigation/animation patterns for both Jetpack Compose (Android) and SwiftUI (iOS). Engineers in Phases 5+ implement directly from this spec — no design decisions happen during implementation.

</domain>

<decisions>
## Implementation Decisions

### Visual Personality
- **D-01:** Design language is **calm & minimal** as the base, with playfulness reserved for celebration moments only (milestone badge unlocks, streak achievements). Day-to-day UI is clean and understated.
- **D-02:** Primary accent color is **warm purple/violet** — used on the log button, tab active states, and CTAs. The supporting palette extends from primary-50 through primary-900 in purple/violet tints.
- **D-03:** Typography uses **system fonts only** — SF Pro on iOS (SwiftUI), Roboto on Android (Compose). No custom fonts.
- **D-04:** Corner radii are **12–16dp** — soft but not bubbly. A middle ground between heavily rounded and moderately rounded.
- **D-05:** Icon style is **outlined (stroke) icons** throughout — lighter visual weight, good complement to the calm/minimal aesthetic.
- **D-06:** Dark mode default is **system/auto** — the app follows the OS dark/light setting on first launch. No manual toggle in Phase 4.
- **D-07:** Milestone/celebration visual treatment is **badge glow + gentle animation** — a soft glow/scale-up animation on badge reveal, no confetti or particle effects. The playful moment is contained and calm.

### Platform Approach
- **D-08:** Design language is **unified with documented platform-specific exceptions** — one design spec for both platforms, with explicit exceptions noted where native convention is strongly preferred.
- **D-09:** Bottom tab bar is **bottom-anchored on both platforms** — standard for a 4-tab mobile app in 2025.
- **D-10:** Back navigation is a **platform-specific exception** — iOS uses NavigationStack swipe-from-left-edge gesture; Android uses system predictive back gesture. No custom back button needed.
- **D-11:** "Add event details" sheet is **platform-specific** — iOS uses the system `.sheet` modifier (card style, swipe to dismiss); Android uses `ModalBottomSheet` anchored at the bottom. Each feels native on its platform.
- **D-12:** Haptic feedback covers **key interactions only**: log button tap, milestone badge unlock, and error states.
- **D-13:** Accessibility spec requires **full a11y annotations per component**: content description, role, minimum tap target (48dp Android / 44pt iOS), focus order, and platform-specific TalkBack/VoiceOver notes.
- **D-14:** Safe area handling uses **standard safe area insets everywhere** — both platforms use their default WindowInsets (Compose) / safeAreaInset (SwiftUI) handling. No edge-to-edge custom work.

### Core Log Interaction
- **D-15:** Log button is a **wide pill button** — not a circular FAB, not full-width. Approximately 70–80% screen width, sitting above the tab bar on a non-scrollable home screen. Large, centered, unmissable.
- **D-16:** Log button tap feedback: **haptic + scale-down press animation + persistent inline toast**. Button scales to 0.95 on press, springs back on release. Haptic fires on release. Toast appears immediately.
- **D-17:** Post-log toast is **long-lived (~12s) with inline event type quick-pick chips** — the toast contains tappable chips for each event type (pee / poo / both / accident / tried — 5 values per Phase 2 D-01; accident is a single category). Selecting a chip sets the event type and dismisses the toast. Dismisses automatically after 12s.
- **D-18:** Event detail bottom sheet field order: **(1) event type selector → (2) text note field → (3) optional time adjustment → (4) Save button**. Event type selector is the primary interaction.
- **D-19:** Home screen layout (non-scrollable, top to bottom): **child name header (tappable with chevron if multiple children) → today's event count → combined status chips area → wide log button → bottom tab bar**.
- **D-20:** Combined status area shows **two small side-by-side chips** when active: `[N need details]` and `[N syncing]`. Hidden entirely when both counts are zero. This single area handles both the pending-details (REQ-032) and pending-sync (REQ-005) states.
- **D-21:** Event cards in History day-detail view are **compact**: timestamp + event type icon + note text. Dense list format, no caregiver name shown.
- **D-22:** Child switcher: **child name in home header is tappable with a chevron indicator** — tapping opens a bottom sheet listing all children. The chevron and tappable state are hidden/inactive when the family has only one child (REQ-031).

### Design Token Format
- **D-23:** Tokens live in a **single DESIGN-TOKENS.md** with a table per category. Each row: `token name | Compose value | SwiftUI value`. Categories: Color, Typography, Spacing, Corner Radius, Elevation, Motion.
- **D-24:** Color palette = **semantic tokens + supporting purple/violet scale** (primary-50 through primary-900). Semantic tokens: primary, on-primary, secondary, on-secondary, background, surface, error, on-error, success, on-success — each with light and dark variants.
- **D-25:** Typography scale = **6 levels**: Display, Headline, Title, Body, Label, Caption. Each level documents: size, weight, line-height, Compose TextStyle name, SwiftUI Font equivalent.
- **D-26:** Spacing grid = **8dp base**: 8, 16, 24, 32, 48, 64.
- **D-27:** Heatmap intensity colors use **purple tints** (not type-specific per event): empty = surface/neutral, low = primary-100, medium = primary-400, high = primary-700.
- **D-28:** Elevation = **3 levels**: flat (0dp shadow), raised (2dp shadow — cards, chips), overlay (8dp shadow — bottom sheets, log button).
- **D-29:** Motion tokens are included in DESIGN-TOKENS.md: `motion.duration.short` (150ms), `motion.duration.medium` (300ms), `motion.duration.long` (400ms), `motion.easing.standard` (ease-in-out), `motion.easing.spring` (spring curve).

### Animation Library
- **D-30:** Android uses **Compose built-in animation APIs only** — `animate*AsState`, `AnimatedVisibility`, `animateContentSize`, `spring()` specs. No Lottie or third-party library.
- **D-31:** iOS uses **SwiftUI built-in animation only** — `withAnimation`, `.animation` modifier, `matchedGeometryEffect`. No Lottie or third-party library.
- **D-32:** Tab switch transition: **fade cross-dissolve, 100–150ms** on both platforms.
- **D-33:** Log button press: **scale to 0.95 on press, spring back on release** (~100ms press, ~200ms spring). Consistent on both platforms.
- **D-34:** Badge glow animation: **scale from 0.7x to 1.0x with spring curve + semi-transparent colored glow overlay that fades in then out**. Total duration ~400ms with spring easing.
- **D-35:** Bottom sheet open/close uses **platform defaults** — Android ModalBottomSheet (~300ms), iOS .sheet (natural spring). No custom timing override.
- **D-36:** Toast enter/exit: **slide up from bottom + fade in (200ms) on appear; slide down + fade out (150ms) on dismiss**.

### Claude's Discretion
- Icon set selection (which specific icon library/set to use — any outlined stroke icon set consistent with the aesthetic)
- Exact purple/violet hex values within the warm purple direction (primary-500 anchor value)
- Precise line-height and letter-spacing values for each typography level
- Event type icon designs (what each of pee/poo/both/accident types looks like as an icon)
- Empty state copy and illustration approach (text-only since no illustrations in calm/minimal direction)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Requirements
- `.planning/REQUIREMENTS.md` — Full requirements; REQ-035 (4-tab navigation) is the primary Phase 4 requirement; REQ-031 (child switcher), REQ-032 (pending banner), REQ-033 (heatmap history), REQ-034 (progress tab), REQ-036 (onboarding wizard) define the screens this spec must cover
- `.planning/PROJECT.md` — Stack decisions (Jetpack Compose, SwiftUI, SKIE), locked decisions, non-goals

### Architecture & System
- `docs/03-system-architecture.md` — API contract shape relevant for loading/offline states in UI
- `docs/07-sync-and-notifications.md` — Offline sync model, pending queue behavior, notification content format (REQ-024) — informs the offline indicator and toast spec

### Auth & Roles
- `docs/06-auth.md` — Clerk roles (org:admin vs org:caregiver) affect which Settings screens and actions are visible to which users

### Privacy & Compliance
- `docs/05-privacy.md` — Consent screen requirements (REQ-009, REQ-036), erasure flow reachability (REQ-014) — affects onboarding wizard spec and Settings screen layout

### Roadmap Phase Goal
- `.planning/ROADMAP.md` §Phase 4 — Success criteria enumerate every required artifact (screen flows, wireframes, token doc, component specs, animation patterns)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- None — the codebase is scaffolded but all app directories (androidApp/, iosApp/, shared/) are currently empty. Phase 4 produces the spec that Phase 5 implements from scratch.

### Established Patterns
- Kotlin Multiplatform with SKIE interop is the architecture — the UI spec should assume KMP-delivered data and not spec any direct networking in UI components
- SQLDelight is the source of truth for displayed data — the UI spec should account for the offline-first read model (data comes from local DB, not API responses directly)

### Integration Points
- The log button triggers a local SQLDelight write immediately — the UI feedback spec (haptic + animation + toast) must not depend on network confirmation
- The pending sync count displayed in the status chips comes from a SQLDelight query (count of rows where sync_status = 'pending')
- The 4-tab bar is hidden during onboarding wizard and full-screen detail flows (per REQ-035)

</code_context>

<specifics>
## Specific Ideas

- The home screen is **non-scrollable** — all content fits without scrolling. The layout design must ensure this constraint is met.
- The log button should feel like the **single most important element on the screen** — large, centered, unmissable, but not aggressive.
- The combined status area (chips for pending details + pending sync) should be **unobtrusive but easily readable** — it should not pull the eye when both counts are low, but should be visible at a glance.
- Celebration moments (badge glow, milestone unlock) are the **only place where playfulness appears** — all other UI interactions should feel calm and deliberate.
- The heatmap calendar should use **purple intensity tints** to reinforce brand identity rather than the GitHub green convention.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 4-UI/UX Design*
*Context gathered: 2026-06-25*
