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
