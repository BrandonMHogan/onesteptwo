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
