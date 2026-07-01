# Persistent Cross-Tab Child Switcher + Swipe Gesture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Home-only, tap-only child switcher header with a persistent `ChildSwitcherBanner` shared across Home, History, and Progress, plus a whole-screen swipe gesture (via `HorizontalPager`) that cycles the active child with a live "card" transition, alongside the existing tap-to-open-sheet path.

**Architecture:** A new shared `ChildSwitcherPagerHost` composable wraps each tab root's content. For multi-child families it renders the `ChildSwitcherBanner` (name + age + page dots) above a `HorizontalPager` where each page is bound to one specific child and constructs its own screen ViewModel scoped to that fixed child (via a constant `MutableStateFlow(child)`) — this is what makes the adjacent page's real content visible while dragging, not just the "currently active" child's content. Swiping updates the shared `ChildSelectionViewModel.activeChild`; tapping the banner still opens the existing `ChildSwitcherSheet`. Single-child families skip the pager entirely and render a non-interactive banner. Two small pure-Kotlin modules (`ChildAge`, `ChildPagerMath`) carry the age-formatting and modulo/wraparound arithmetic and get real unit tests — the first ones in this Android module.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 + `androidx.compose.foundation.pager.HorizontalPager`), existing `ChildSelectionViewModel`/`ChildrenRepository`, JUnit4 (new, module-local, unit-test-only).

## Global Constraints

- No new UI dependencies: `HorizontalPager` ships in `androidx.compose.foundation`, already pulled in transitively via `libs.compose.material3` — do not add a new Gradle dependency for it (04-UI-SPEC.md D-30: Compose built-in APIs only, no third-party).
- Animation must be `HorizontalPager`'s built-in default fling/snap — do not add a custom `tween`/`spring` animation spec to the pager (04-UI-SPEC.md §Motion Tokens, "Child switcher swipe/page (D-12)": *Pager default*).
- Banner/pager renders **only** on the three tab roots (Home, History, Progress). Never on History Day-Detail or Settings sub-screens — those screens are unaffected by this plan; do not touch `DayDetailScreen.kt`, `SettingsScreen.kt`, `AddChildScreen.kt`, or `EditChildScreen.kt`.
- Single-child families: banner renders name + age, centered, but **no** page dots, **no** tap handler, **no** pager (04-UI-SPEC.md §Component 9, revised D-12).
- Child order everywhere is `children` as already supplied by `ChildSelectionViewModel.children` (backed by `ChildrenRepository.observeAll()` → `Children.sq` `selectAll` → `ORDER BY created_at ASC`). Never re-sort it.
- This codebase has **no existing automated test suite** (zero files under any `src/test` or `src/androidTest` directory in `androidApp`, and no test dependency declared). Match that convention for all Compose UI code in this plan — verify by compiling + manually running the app on an emulator/device, not by adding Compose UI tests. The two pure-Kotlin math/formatting files are the deliberate exception (see Task 1 rationale) — keep the new test surface limited to exactly those two files; do not add Robolectric, Compose UI testing, or instrumented tests as part of this plan.
- Typography/color mapping already established in `OneStepTwoTypography`/`MaterialTheme.colorScheme`: Title 20sp → `titleLarge`, Caption 12sp → `bodySmall`, `color.on-surface` 70% → `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)`, `color.outline` 38% → `MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)`, `color.primary` → `MaterialTheme.colorScheme.primary`. Reuse these exactly; do not invent new tokens.

---

## File Structure

New package `androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/`:
- `ChildAge.kt` — pure function formatting a child's age as "Xy Ym"
- `ChildPagerMath.kt` — pure functions for the infinite-loop page↔index math
- `ChildSwitcherBanner.kt` — the shared banner composable (name, age, page dots, tap target)
- `ChildSwitcherPagerHost.kt` — the shared pager/banner/sheet wrapper each tab root delegates to
- `ChildSwitcherSheet.kt` — **moved** from `ui/home` (unchanged content, new package + one call-site import fixed)

New test files under `androidApp/src/test/kotlin/com/onesteptwo/android/ui/childswitcher/` (new source set — see Task 1):
- `ChildAgeTest.kt`
- `ChildPagerMathTest.kt`

Modified:
- `androidApp/build.gradle.kts` — add `testImplementation(libs.junit)`
- `gradle/libs.versions.toml` — add `junit` version + library alias
- `androidApp/src/main/kotlin/com/onesteptwo/android/ui/home/HomeScreen.kt` — delegate to `ChildSwitcherPagerHost`, extract per-child body into private `HomeContent`
- `androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/HistoryScreen.kt` — same shape of change, extract `HistoryContent`
- `androidApp/src/main/kotlin/com/onesteptwo/android/ui/progress/ProgressScreen.kt` — same shape of change, extract `ProgressContent`

Unchanged (do not modify): `ChildSelectionViewModel.kt`, `MainTabNavigation.kt`, `HomeViewModel.kt`, `HistoryViewModel.kt`, `DayDetailScreen.kt`, all Settings screens.

---

### Task 1: Age formatting (`ChildAge.kt`) + JUnit test infra

**Files:**
- Create: `gradle/libs.versions.toml` (edit — add `junit` entries)
- Create: `androidApp/build.gradle.kts` (edit — add test dependency)
- Create: `androidApp/src/test/kotlin/com/onesteptwo/android/ui/childswitcher/ChildAgeTest.kt`
- Create: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildAge.kt`

**Interfaces:**
- Produces: `fun formatChildAge(birthMonth: Int, birthYear: Int, today: LocalDate = LocalDate.now()): String` — later used by `ChildSwitcherBanner.kt` (Task 3)

- [ ] **Step 1: Add the JUnit version catalog entries**

Edit `gradle/libs.versions.toml`. In the `[versions]` block, add a line after `lifecycle = "2.8.7"`:

```toml
lifecycle = "2.8.7"
junit = "4.13.2"
```

In the `[libraries]` block, add a line after `lifecycle-runtime-compose = ...`:

```toml
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
```

- [ ] **Step 2: Add the test dependency to the app module**

Edit `androidApp/build.gradle.kts`. In the `dependencies { ... }` block, add as the last line:

```kotlin
    implementation(libs.lifecycle.runtime.compose)
    testImplementation(libs.junit)
```

- [ ] **Step 3: Write the failing test**

Create `androidApp/src/test/kotlin/com/onesteptwo/android/ui/childswitcher/ChildAgeTest.kt`:

```kotlin
package com.onesteptwo.android.ui.childswitcher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ChildAgeTest {

    @Test
    fun `formats years and months from a birth month and year`() {
        val today = LocalDate.of(2026, 7, 1)
        assertEquals("2y 3m", formatChildAge(birthMonth = 4, birthYear = 2024, today = today))
    }

    @Test
    fun `formats zero years when born this same calendar month`() {
        val today = LocalDate.of(2026, 7, 15)
        assertEquals("0y 0m", formatChildAge(birthMonth = 7, birthYear = 2026, today = today))
    }

    @Test
    fun `rolls over to a full year at the 12 month boundary`() {
        val today = LocalDate.of(2026, 7, 1)
        assertEquals("1y 0m", formatChildAge(birthMonth = 7, birthYear = 2025, today = today))
    }

    @Test
    fun `clamps to zero for an implausible future birth date instead of going negative`() {
        val today = LocalDate.of(2026, 7, 1)
        assertEquals("0y 0m", formatChildAge(birthMonth = 1, birthYear = 2027, today = today))
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.onesteptwo.android.ui.childswitcher.ChildAgeTest"`
Expected: FAIL — compilation error, `formatChildAge` is unresolved (the file doesn't exist yet).

- [ ] **Step 5: Write the implementation**

Create `androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildAge.kt`:

```kotlin
package com.onesteptwo.android.ui.childswitcher

import java.time.LocalDate

/**
 * Formats a child's age as "Xy Ym" for the Child Switcher Banner (04-UI-SPEC.md §Component 9,
 * revised D-12). Clamped at zero — an implausible future birth date never renders as negative.
 */
fun formatChildAge(birthMonth: Int, birthYear: Int, today: LocalDate = LocalDate.now()): String {
    val totalMonths = ((today.year - birthYear) * 12 + (today.monthValue - birthMonth))
        .coerceAtLeast(0)
    val years = totalMonths / 12
    val months = totalMonths % 12
    return "${years}y ${months}m"
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.onesteptwo.android.ui.childswitcher.ChildAgeTest"`
Expected: PASS (4 tests, 0 failures)

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml androidApp/build.gradle.kts androidApp/src/test/kotlin/com/onesteptwo/android/ui/childswitcher/ChildAgeTest.kt androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildAge.kt
git commit -m "feat(child-switcher): add age formatting with unit tests"
```

---

### Task 2: Infinite-pager index math (`ChildPagerMath.kt`)

**Files:**
- Create: `androidApp/src/test/kotlin/com/onesteptwo/android/ui/childswitcher/ChildPagerMathTest.kt`
- Create: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildPagerMath.kt`

**Interfaces:**
- Consumes: nothing (pure, no dependency on Task 1)
- Produces:
  - `fun realIndex(page: Int, count: Int): Int`
  - `fun initialPageFor(activeIndex: Int, count: Int, anchor: Int = Int.MAX_VALUE / 2): Int`
  - `fun nearestPageFor(currentPage: Int, currentIndex: Int, targetIndex: Int, count: Int): Int`
  - All three consumed by `ChildSwitcherPagerHost.kt` (Task 5)

- [ ] **Step 1: Write the failing tests**

Create `androidApp/src/test/kotlin/com/onesteptwo/android/ui/childswitcher/ChildPagerMathTest.kt`:

```kotlin
package com.onesteptwo.android.ui.childswitcher

import org.junit.Assert.assertEquals
import org.junit.Test

class ChildPagerMathTest {

    @Test
    fun `realIndex wraps forward past the end of the list`() {
        assertEquals(0, realIndex(page = 3, count = 3))
        assertEquals(1, realIndex(page = 4, count = 3))
    }

    @Test
    fun `realIndex wraps backward for negative pages`() {
        assertEquals(2, realIndex(page = -1, count = 3))
    }

    @Test
    fun `initialPageFor lands on a page whose modulo equals the active index`() {
        val count = 3
        val activeIndex = 2
        val page = initialPageFor(activeIndex, count, anchor = 100)
        assertEquals(activeIndex, realIndex(page, count))
    }

    @Test
    fun `nearestPageFor picks the shorter wraparound direction`() {
        // count=3, currently on index 0 at page 10, target index 2 is one step *backward*
        // (0 -> 2 wraps around), which is shorter than two steps forward (0 -> 1 -> 2).
        val page = nearestPageFor(currentPage = 10, currentIndex = 0, targetIndex = 2, count = 3)
        assertEquals(9, page)
        assertEquals(2, realIndex(page, count = 3))
    }

    @Test
    fun `nearestPageFor steps forward when that is the shorter direction`() {
        // count=4, index 0 -> index 1 is one step forward, shorter than three steps backward.
        val page = nearestPageFor(currentPage = 20, currentIndex = 0, targetIndex = 1, count = 4)
        assertEquals(21, page)
        assertEquals(1, realIndex(page, count = 4))
    }

    @Test
    fun `nearestPageFor is a no-op when already on the target index`() {
        val page = nearestPageFor(currentPage = 7, currentIndex = 1, targetIndex = 1, count = 3)
        assertEquals(7, page)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.onesteptwo.android.ui.childswitcher.ChildPagerMathTest"`
Expected: FAIL — compilation error, `realIndex`/`initialPageFor`/`nearestPageFor` unresolved.

- [ ] **Step 3: Write the implementation**

Create `androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildPagerMath.kt`:

```kotlin
package com.onesteptwo.android.ui.childswitcher

/**
 * Index/page arithmetic for the "infinite loop" HorizontalPager trick used by
 * [ChildSwitcherPagerHost] (04-UI-SPEC.md §Component 9, revised D-12): the pager is given a
 * practically-infinite page count, and these functions map back and forth between a raw page
 * number and the real 0-until-[count] child index, so swiping past the last child wraps to the
 * first (D-12 wrap-around decision) without the pager needing native loop support (it has none).
 */

/** Maps a raw pager page number to a real child-list index in `0 until count`. */
fun realIndex(page: Int, count: Int): Int {
    require(count > 0) { "count must be positive" }
    return ((page % count) + count) % count
}

/** The initial page to hand to `rememberPagerState` so it opens already showing [activeIndex]. */
fun initialPageFor(activeIndex: Int, count: Int, anchor: Int = Int.MAX_VALUE / 2): Int {
    require(count > 0) { "count must be positive" }
    return anchor - (anchor % count) + activeIndex
}

/**
 * The page closest to [currentPage] whose real index is [targetIndex] — used to programmatically
 * scroll the pager (e.g. after a tap-path selection in the Child Switcher sheet, or when another
 * tab changed the shared active child) via the *shorter* wraparound direction rather than always
 * animating forward.
 */
fun nearestPageFor(currentPage: Int, currentIndex: Int, targetIndex: Int, count: Int): Int {
    require(count > 0) { "count must be positive" }
    val rawDelta = targetIndex - currentIndex
    val half = count / 2
    val normalizedDelta = ((rawDelta + half + count) % count) - half
    return currentPage + normalizedDelta
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.onesteptwo.android.ui.childswitcher.ChildPagerMathTest"`
Expected: PASS (6 tests, 0 failures)

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/test/kotlin/com/onesteptwo/android/ui/childswitcher/ChildPagerMathTest.kt androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildPagerMath.kt
git commit -m "feat(child-switcher): add infinite-pager index math with unit tests"
```

---

### Task 3: Move `ChildSwitcherSheet` into the shared package

**Files:**
- Delete: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/home/ChildSwitcherSheet.kt`
- Create: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildSwitcherSheet.kt`
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/home/HomeScreen.kt:1-247` (only the package-level import changes in this task — the bigger `HomeScreen` rewrite is Task 6)

**Interfaces:**
- Produces (unchanged signature, new package): `com.onesteptwo.android.ui.childswitcher.ChildSwitcherSheet(children: List<Children>, activeChildId: String?, onSelect: (Children) -> Unit, onDismiss: () -> Unit)` — consumed by `ChildSwitcherPagerHost.kt` (Task 5) and by `HomeScreen.kt` until Task 6 removes the direct call there

This is a pure move — no behavior change. It is its own task because both History (Task 7) and Progress (Task 8) need to call this composable from outside the `ui.home` package, and moving it now (before `ChildSwitcherPagerHost` is written) keeps the later tasks from having to depend on a not-yet-relocated file.

- [ ] **Step 1: Move the file**

```bash
git mv androidApp/src/main/kotlin/com/onesteptwo/android/ui/home/ChildSwitcherSheet.kt androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildSwitcherSheet.kt
```

- [ ] **Step 2: Update its package declaration**

In `androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildSwitcherSheet.kt`, change line 1:

```kotlin
package com.onesteptwo.android.ui.home
```

to:

```kotlin
package com.onesteptwo.android.ui.childswitcher
```

- [ ] **Step 3: Fix the only call site's import**

In `androidApp/src/main/kotlin/com/onesteptwo/android/ui/home/HomeScreen.kt`, since `ChildSwitcherSheet` is no longer in the same package (`ui.home`), add an import. Find this existing import block near the top of the file:

```kotlin
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.ui.theme.Radius
import com.onesteptwo.android.viewmodel.ChildSelectionViewModel
```

Replace it with:

```kotlin
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.ui.childswitcher.ChildSwitcherSheet
import com.onesteptwo.android.ui.theme.Radius
import com.onesteptwo.android.viewmodel.ChildSelectionViewModel
```

- [ ] **Step 4: Compile-check**

Run: `./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — no unresolved-reference errors for `ChildSwitcherSheet`.

- [ ] **Step 5: Commit**

```bash
git add -A androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildSwitcherSheet.kt androidApp/src/main/kotlin/com/onesteptwo/android/ui/home/HomeScreen.kt
git commit -m "refactor(child-switcher): move ChildSwitcherSheet into the shared childswitcher package"
```

---

### Task 4: `ChildSwitcherBanner` composable

**Files:**
- Create: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildSwitcherBanner.kt`

**Interfaces:**
- Consumes: `formatChildAge` (Task 1)
- Produces: `@Composable fun ChildSwitcherBanner(activeChild: Children?, isInteractive: Boolean, dotCount: Int, activeDotIndex: Int, onTap: () -> Unit, modifier: Modifier = Modifier)` — consumed by `ChildSwitcherPagerHost.kt` (Task 5)

No automated test for this file (Compose UI, no test infra in this repo per Global Constraints) — verified manually as part of Task 9's end-to-end pass.

- [ ] **Step 1: Write the composable**

Create `androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildSwitcherBanner.kt`:

```kotlin
package com.onesteptwo.android.ui.childswitcher

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onesteptwo.db.Children

/**
 * Persistent Child Switcher Banner (04-UI-SPEC.md §Component 9, revised D-12) shared by Home,
 * History, and Progress tab roots. Centered name + age; page dots and tap affordance only render
 * for multi-child families ([isInteractive]) — single-child families get name + age only, per
 * REQ-031's "shows no [interactive] switcher" for one child.
 */
@Composable
fun ChildSwitcherBanner(
    activeChild: Children?,
    isInteractive: Boolean,
    dotCount: Int,
    activeDotIndex: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = activeChild?.nickname ?: ""
    val age = activeChild?.let { formatChildAge(it.birth_month.toInt(), it.birth_year.toInt()) } ?: ""
    val description = if (isInteractive) {
        "$name, active child. Double tap to open child list, swipe left or right to switch."
    } else {
        "$name, active child"
    }

    val interactionModifier = if (isInteractive) {
        Modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .pointerInput(Unit) {
                detectTapGestures { onTap() }
            }
    } else {
        Modifier.semantics { contentDescription = description }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(interactionModifier)
    ) {
        Text(text = name, style = MaterialTheme.typography.titleLarge)
        Text(
            text = age,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        if (isInteractive && dotCount > 1) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(dotCount) { i ->
                    val dotColor = if (i == activeDotIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(color = dotColor, shape = CircleShape)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildSwitcherBanner.kt
git commit -m "feat(child-switcher): add shared ChildSwitcherBanner composable"
```

---

### Task 5: `ChildSwitcherPagerHost` composable

**Files:**
- Create: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildSwitcherPagerHost.kt`

**Interfaces:**
- Consumes: `ChildSwitcherBanner` (Task 4), `ChildSwitcherSheet` (Task 3), `realIndex`/`initialPageFor`/`nearestPageFor` (Task 2)
- Produces: `@Composable fun ChildSwitcherPagerHost(children: List<Children>, activeChild: Children?, onSelectChild: (Children) -> Unit, content: @Composable (Children) -> Unit)` — consumed by `HomeScreen.kt` (Task 6), `HistoryScreen.kt` (Task 7), `ProgressScreen.kt` (Task 8)

This is the core new piece: it owns the `HorizontalPager`, the loop math, the two-way sync with `ChildSelectionViewModel` (via the `activeChild`/`onSelectChild` parameters the caller already gets from that ViewModel), and the tap-path sheet.

- [ ] **Step 1: Write the composable**

Create `androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildSwitcherPagerHost.kt`:

```kotlin
package com.onesteptwo.android.ui.childswitcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.onesteptwo.db.Children

/**
 * Shared cross-tab child switcher (04-UI-SPEC.md §Component 9, revised D-12): a persistent
 * [ChildSwitcherBanner] above a whole-screen [HorizontalPager] cycling through [children], one
 * page per child. Each page invokes [content] with *that specific child* (not necessarily the
 * globally active one) so screens can bind their own per-child ViewModel and show real data while
 * the user is mid-drag between two children — not just a banner-only animation.
 *
 * Single-child families skip the pager entirely: [content] is invoked once with the only child,
 * and the banner renders non-interactively (no dots, no tap, no swipe — REQ-031).
 *
 * Swiping to settle on a different page calls [onSelectChild], which callers wire to
 * [com.onesteptwo.android.viewmodel.ChildSelectionViewModel.selectChild] so the change propagates
 * to the other tabs. Conversely, if [activeChild] changes for a reason other than a local swipe
 * (the tap-path sheet below, or another tab's swipe, once the caller remounts on this tab), the
 * pager reconciles itself to match, animating via the shorter wraparound direction.
 */
@Composable
fun ChildSwitcherPagerHost(
    children: List<Children>,
    activeChild: Children?,
    onSelectChild: (Children) -> Unit,
    content: @Composable (Children) -> Unit
) {
    if (children.isEmpty()) return

    if (children.size == 1) {
        val onlyChild = children.first()
        Column(modifier = Modifier.fillMaxSize()) {
            ChildSwitcherBanner(
                activeChild = onlyChild,
                isInteractive = false,
                dotCount = 0,
                activeDotIndex = 0,
                onTap = {}
            )
            content(onlyChild)
        }
        return
    }

    var showSheet by remember { mutableStateOf(false) }
    val activeIndex = children.indexOfFirst { it.id == activeChild?.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPageFor(activeIndex, children.size)
    ) { Int.MAX_VALUE }

    // Local swipe settles on a new page -> propagate to the shared active-child context.
    LaunchedEffect(pagerState, children) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val settledChild = children.getOrNull(realIndex(page, children.size)) ?: return@collect
            if (settledChild.id != activeChild?.id) onSelectChild(settledChild)
        }
    }

    // Active child changed some other way (tap-path sheet below, or a fresh mount landing on a
    // stale remembered page after another tab changed the active child) -> reconcile the pager.
    LaunchedEffect(activeChild?.id, children) {
        val targetIndex = children.indexOfFirst { it.id == activeChild?.id }
        if (targetIndex < 0) return@LaunchedEffect
        val currentIndex = realIndex(pagerState.currentPage, children.size)
        if (currentIndex != targetIndex) {
            val targetPage = nearestPageFor(pagerState.currentPage, currentIndex, targetIndex, children.size)
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChildSwitcherBanner(
            activeChild = activeChild,
            isInteractive = true,
            dotCount = children.size,
            activeDotIndex = realIndex(pagerState.currentPage, children.size),
            onTap = { showSheet = true },
            modifier = Modifier.fillMaxWidth()
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            content(children[realIndex(page, children.size)])
        }
    }

    if (showSheet) {
        ChildSwitcherSheet(
            children = children,
            activeChildId = activeChild?.id,
            onSelect = { selected ->
                onSelectChild(selected)
                showSheet = false
            },
            onDismiss = { showSheet = false }
        )
    }
}
```

Note: the `HorizontalPager` is given `Modifier.fillMaxWidth()`, not `Modifier.weight(1f)` — it is not inside a `Column` that also needs to reserve space for something below it here, so it naturally fills remaining height as the last child of the outer `fillMaxSize()` `Column` combined with each screen's own content filling its page. (If a future page's content is shorter than the available height, that is that screen's own layout concern, matching how `HomeContent`/`HistoryContent`/`ProgressContent` already size themselves with `fillMaxSize()`.)

- [ ] **Step 2: Compile-check**

Run: `./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add androidApp/src/main/kotlin/com/onesteptwo/android/ui/childswitcher/ChildSwitcherPagerHost.kt
git commit -m "feat(child-switcher): add ChildSwitcherPagerHost with loop-aware swipe sync"
```

---

### Task 6: Wire Home tab to the pager host

**Files:**
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/home/HomeScreen.kt` (full rewrite of the file body; `StatusChip` at the bottom is unchanged)

**Interfaces:**
- Consumes: `ChildSwitcherPagerHost` (Task 5)
- No change to `HomeScreen`'s own public signature: `@Composable fun HomeScreen(container: AppContainer, childSelectionViewModel: ChildSelectionViewModel)` — `MainTabNavigation.kt` calls it exactly as before, no changes needed there.

- [ ] **Step 1: Rewrite the file**

Replace the full contents of `androidApp/src/main/kotlin/com/onesteptwo/android/ui/home/HomeScreen.kt`:

```kotlin
package com.onesteptwo.android.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.ui.childswitcher.ChildSwitcherPagerHost
import com.onesteptwo.android.ui.theme.Radius
import com.onesteptwo.android.viewmodel.ChildSelectionViewModel
import com.onesteptwo.db.Children
import kotlinx.coroutines.flow.MutableStateFlow

/** Home tab (04-UI-SPEC.md Screen Inventory): persistent Child Switcher Banner (D-12) -> per-child
 * content (count/status/Log button, M6). */
@Composable
fun HomeScreen(container: AppContainer, childSelectionViewModel: ChildSelectionViewModel) {
    val children by childSelectionViewModel.children.collectAsState()
    val activeChild by childSelectionViewModel.activeChild.collectAsState()

    ChildSwitcherPagerHost(
        children = children,
        activeChild = activeChild,
        onSelectChild = childSelectionViewModel::selectChild
    ) { child ->
        HomeContent(container = container, child = child)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(container: AppContainer, child: Children) {
    val activeChildFlow = remember(child.id) { MutableStateFlow<Children?>(child) }
    val homeViewModel: HomeViewModel = viewModel(
        key = child.id,
        factory = HomeViewModelFactory(container.pottyEventsRepository, activeChildFlow)
    )
    val state by homeViewModel.state.collectAsState()
    val selectedEvent by homeViewModel.selectedEventForSheet.collectAsState()
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Event count / empty state.
            if (state.todayEventCount == 0) {
                Text(text = "No events yet", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Log your first potty trip to see it here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            } else {
                Text(text = "${state.todayEventCount}", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "events today",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status chips — hidden entirely when both counts are zero.
            if (state.pendingDetailsCount > 0 || state.pendingSyncCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.pendingDetailsCount > 0) {
                        StatusChip(
                            text = "${state.pendingDetailsCount} need details",
                            contentDescription = "${state.pendingDetailsCount} events need details",
                            onClick = homeViewModel::openFirstPendingDetailsEvent
                        )
                    }
                    if (state.pendingSyncCount > 0) {
                        StatusChip(
                            text = "${state.pendingSyncCount} syncing…",
                            contentDescription = "${state.pendingSyncCount} events pending sync",
                            onClick = {}
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Log button — wide pill, spring press scale, haptic on release.
            val scale by animateFloatAsState(
                targetValue = if (state.pressed) 0.95f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.7f),
                label = "logButtonScale"
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(Radius.pill),
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(52.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .semantics {
                            contentDescription = "Log potty trip"
                            role = Role.Button
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    homeViewModel.onPress(true)
                                    val released = tryAwaitRelease()
                                    homeViewModel.onPress(false)
                                    if (released) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        homeViewModel.logEvent()
                                    }
                                }
                            )
                        }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Log",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Post-log toast — overlays above the Log button.
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 84.dp), contentAlignment = Alignment.BottomCenter) {
            LogToast(
                visible = state.toastVisible,
                onChipTap = { homeViewModel.pickEventType(it.lowercase()) },
                onAddDetails = homeViewModel::openAddDetails,
                onDismiss = homeViewModel::dismissToast
            )
        }
    }

    selectedEvent?.let { event ->
        EventDetailSheet(
            event = event,
            onSave = { type, notes -> homeViewModel.saveEventDetails(type, notes, event.occurred_at) },
            onDismiss = homeViewModel::dismissEventDetail
        )
    }
}

@Composable
private fun StatusChip(text: String, contentDescription: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondary,
        shape = RoundedCornerShape(Radius.pill),
        onClick = onClick,
        modifier = Modifier
            .height(28.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondary
            )
        }
    }
}
```

This removes the old inline header Row (name + chevron + tap-to-open-sheet) and the `ChildSwitcherSheet`/`showChildSwitcher` state entirely from `HomeScreen` — both now live in `ChildSwitcherPagerHost`. `HomeViewModel`/`HomeViewModelFactory` are unchanged; each page just supplies a constant per-child flow instead of the shared reactive one, which is exactly the type `HomeViewModelFactory` already expects (`StateFlow<Children?>`).

- [ ] **Step 2: Compile-check**

Run: `./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual verification (build + run)**

Run: `./gradlew :androidApp:installDebug` with an emulator/device attached, or run from Android Studio.

Check, using a test account with **one** child, then a test account with **two or more** children (Settings → Children → Add child to create more if needed):
- One-child account: Home shows the centered name + age banner, no dots, tapping it does nothing, swiping does nothing.
- Multi-child account: Home shows name + age + page dots; tapping opens the "Switch child" sheet exactly as before; swiping left/right cycles through children, log button/event count/status chips update to match whichever child is currently on screen, and dragging partway visibly shows the adjacent child's real data (not blank/placeholder) sliding in.
- Logging an event, then swiping to another child and back: the toast/event count for the first child is exactly as you left it (per-child ViewModel state survives the swipe).

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/main/kotlin/com/onesteptwo/android/ui/home/HomeScreen.kt
git commit -m "feat(child-switcher): wire Home tab to the persistent banner + swipe pager"
```

---

### Task 7: Wire History tab to the pager host

**Files:**
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/HistoryScreen.kt`

**Interfaces:**
- Consumes: `ChildSwitcherPagerHost` (Task 5)
- No change to `HistoryScreen`'s public signature: `@Composable fun HistoryScreen(container: AppContainer, childSelectionViewModel: ChildSelectionViewModel, onDayClick: (LocalDate) -> Unit)`

- [ ] **Step 1: Rewrite the file**

Replace the full contents of `androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/HistoryScreen.kt`:

```kotlin
package com.onesteptwo.android.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.ui.childswitcher.ChildSwitcherPagerHost
import com.onesteptwo.android.viewmodel.ChildSelectionViewModel
import com.onesteptwo.db.Children
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/** History tab (04-UI-SPEC.md §Main App — History Tab): persistent Child Switcher Banner (D-12)
 * above the rolling heatmap, or empty state if that page's child has never logged an event. */
@Composable
fun HistoryScreen(
    container: AppContainer,
    childSelectionViewModel: ChildSelectionViewModel,
    onDayClick: (LocalDate) -> Unit
) {
    val children by childSelectionViewModel.children.collectAsState()
    val activeChild by childSelectionViewModel.activeChild.collectAsState()

    ChildSwitcherPagerHost(
        children = children,
        activeChild = activeChild,
        onSelectChild = childSelectionViewModel::selectChild
    ) { child ->
        HistoryContent(container = container, child = child, onDayClick = onDayClick)
    }
}

@Composable
private fun HistoryContent(container: AppContainer, child: Children, onDayClick: (LocalDate) -> Unit) {
    val activeChildFlow = remember(child.id) { MutableStateFlow<Children?>(child) }
    val historyViewModel: HistoryViewModel = viewModel(
        key = child.id,
        factory = HistoryViewModelFactory(container.pottyEventsRepository, activeChildFlow)
    )
    val state by historyViewModel.state.collectAsState()

    if (!state.hasEverLogged) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "No events yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Log your first potty trip to see it here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            HeatmapView(weeks = state.weeks, onDayClick = onDayClick)
        }
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual verification (build + run)**

Same install as Task 6 (`./gradlew :androidApp:installDebug`). On the History tab:
- One-child account: banner shows name + age, no dots; heatmap or empty state exactly as before.
- Multi-child account: banner + dots at top, swiping cycles children and the heatmap grid updates to that child's data as you drag; tapping a day cell still pushes History Day-Detail (confirm the banner does **not** appear on that pushed screen); back-navigating returns to History with the same child still active.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/main/kotlin/com/onesteptwo/android/ui/history/HistoryScreen.kt
git commit -m "feat(child-switcher): wire History tab to the persistent banner + swipe pager"
```

---

### Task 8: Wire Progress tab to the pager host

**Files:**
- Modify: `androidApp/src/main/kotlin/com/onesteptwo/android/ui/progress/ProgressScreen.kt`

**Interfaces:**
- Consumes: `ChildSwitcherPagerHost` (Task 5)
- No change to `ProgressScreen`'s public signature: `@Composable fun ProgressScreen(childSelectionViewModel: ChildSelectionViewModel)`

- [ ] **Step 1: Rewrite the file**

Replace the full contents of `androidApp/src/main/kotlin/com/onesteptwo/android/ui/progress/ProgressScreen.kt`:

```kotlin
package com.onesteptwo.android.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onesteptwo.android.ui.childswitcher.ChildSwitcherPagerHost
import com.onesteptwo.android.viewmodel.ChildSelectionViewModel
import com.onesteptwo.db.Children

/**
 * Phase 5 only needs the persistent Child Switcher Banner + swipe pager here (REQ-031, revised
 * D-12) — full streak/milestone UI is Phase 7 (REQ-034). Each page is bound to one specific child
 * ([ProgressContent]) rather than reading a shared "active child," consistent with Home/History.
 */
@Composable
fun ProgressScreen(childSelectionViewModel: ChildSelectionViewModel) {
    val children by childSelectionViewModel.children.collectAsState()
    val activeChild by childSelectionViewModel.activeChild.collectAsState()

    ChildSwitcherPagerHost(
        children = children,
        activeChild = activeChild,
        onSelectChild = childSelectionViewModel::selectChild
    ) { child ->
        ProgressContent(child = child)
    }
}

@Composable
private fun ProgressContent(child: Children) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Progress", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Streaks and milestones for ${child.nickname} are coming soon.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual verification (build + run)**

Same install as Task 6. On the Progress tab:
- One-child account: banner shows name + age, no dots; placeholder text below unchanged.
- Multi-child account: banner + dots at top; swiping cycles children and the placeholder text's child name updates to match; tapping the banner opens the switcher sheet.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/main/kotlin/com/onesteptwo/android/ui/progress/ProgressScreen.kt
git commit -m "feat(child-switcher): wire Progress tab to the persistent banner + swipe pager"
```

---

### Task 9: Full cross-tab manual verification pass

**Files:** none (verification only)

This task exists because the individual per-tab checks in Tasks 6-8 can't catch *cross-tab* desync — the scenario 05-CONTEXT.md D-12 and this plan's `ChildSwitcherPagerHost` reconciliation logic specifically exist to prevent.

- [ ] **Step 1: Full build**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Cross-tab consistency check**

Using a multi-child (3+, to exercise wraparound meaningfully) test account, install and run the app, then walk through:
1. On Home, swipe to the 2nd child. Switch to the History tab — it must open already showing the 2nd child (not the 1st), with the banner name/dots and heatmap matching.
2. On History, tap the banner and pick the 3rd child from the sheet. Switch to Progress — it must show the 3rd child.
3. On Progress, swipe backward (right) past the 1st child — it must wrap to the last child (loop, D-12), not stop/bounce.
4. Swipe forward (left) past the last child — it must wrap to the first child.
5. With TalkBack enabled (Settings → Accessibility → TalkBack on the test device/emulator), navigate to the banner on any tab: for a multi-child account it should announce "[Name], active child. Double tap to open child list, swipe left or right to switch." and double-tapping must open the sheet (the sheet's own rows must remain independently operable via TalkBack, unchanged from before this plan).
6. Confirm History Day-Detail (tap any non-empty heatmap cell) and Settings → Add child / Edit child / Invite caregiver show **no** Child Switcher Banner at all.

- [ ] **Step 3: Record the result**

If all six checks pass, this plan is complete. If any fails, fix the specific file responsible (most likely `ChildSwitcherPagerHost.kt`'s reconciliation `LaunchedEffect`) and re-run this task's checklist from Step 1 — do not partially re-verify.

- [ ] **Step 4: Final commit (if any fixes were needed in Step 3)**

```bash
git add -A
git commit -m "fix(child-switcher): address cross-tab verification findings"
```

(If Step 3 required no fixes, there is nothing to commit for this task.)

---

## Self-Review

**Spec coverage** (against 05-CONTEXT.md D-12):
- Placement (banner replaces per-screen name elements) → Tasks 6, 7, 8
- Single-child non-interactive banner → `ChildSwitcherPagerHost` size==1 branch (Task 5), verified in Tasks 6-8 Step 3
- Banner visual design (centered name/age/dots, no chevron, no letter-circle) → Task 4
- Whole-screen pager, per-child real content while dragging → Task 5's per-page `content(children[...])` binding + Tasks 6-8's per-page ViewModel construction
- Wrap-around loop → `ChildPagerMath.kt` (Task 2), verified in Task 9 Step 2.3-2.4
- Order = `created_at ASC` → never re-sorted anywhere in this plan; `children` passed straight through
- Accessibility (tap fallback + contentDescription) → Task 4, verified in Task 9 Step 2.5
- Scope limit (no banner on Day-Detail/Settings) → untouched files, verified in Task 9 Step 2.6
- Animation = Pager default → Task 5 uses `pagerState.animateScrollToPage` with no custom `AnimationSpec`, and swipe itself uses `HorizontalPager`'s own default fling
- REQ numbering (amend REQ-031 in place) → already done during the spec-writing pass before this plan existed

**Placeholder scan:** no TBD/TODO, no "similar to Task N" — Tasks 6, 7, 8 each contain the full file contents even though they share a lot of structure, because each task's implementer may work from a fresh context.

**Type consistency:** `ChildSwitcherPagerHost(children: List<Children>, activeChild: Children?, onSelectChild: (Children) -> Unit, content: @Composable (Children) -> Unit)` is the exact signature used identically in Tasks 6, 7, 8. `formatChildAge(birthMonth: Int, birthYear: Int, today: LocalDate = LocalDate.now()): String` matches its Task 1 definition and its one call site in `ChildSwitcherBanner.kt` (Task 4). `realIndex`/`initialPageFor`/`nearestPageFor` signatures match between Task 2's definitions and Task 5's usage.
