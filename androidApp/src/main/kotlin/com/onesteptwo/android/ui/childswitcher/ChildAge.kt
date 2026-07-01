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
