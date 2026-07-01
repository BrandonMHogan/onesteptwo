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
