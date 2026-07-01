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
        // count=3, currently on index 0 at page 9, target index 2 is one step *backward*
        // (0 -> 2 wraps around), which is shorter than two steps forward (0 -> 1 -> 2).
        val page = nearestPageFor(currentPage = 9, currentIndex = 0, targetIndex = 2, count = 3)
        assertEquals(8, page)
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
