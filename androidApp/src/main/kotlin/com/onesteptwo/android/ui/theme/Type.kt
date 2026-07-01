@file:OptIn(ExperimentalTextApi::class)

package com.onesteptwo.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.onesteptwo.android.R

/**
 * Figtree ships only as a variable font upstream (no static instances) — resolved to the 400
 * and 600 weight instances via [FontVariation.Settings], which Compose supports from API 26+
 * (well below this app's minSdk 29), so a single bundled file is sufficient.
 */
private val FigtreeRegular = Font(
    resId = R.font.figtree,
    weight = FontWeight.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(400))
)
private val FigtreeSemiBold = Font(
    resId = R.font.figtree,
    weight = FontWeight.SemiBold,
    variationSettings = FontVariation.Settings(FontVariation.weight(600))
)

val FigtreeFamily = FontFamily(FigtreeRegular, FigtreeSemiBold)

/**
 * Six type roles from docs/DESIGN-TOKENS.md §Typography, mapped to Material3 slots per the
 * Android column of that table. Weights are SemiBold (Display/Headline/Title) or Regular
 * (Body/Label/Caption) — no other weights exist in this design system.
 */
val OneStepTwoTypography = Typography(
    headlineMedium = TextStyle( // Display
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle( // Headline
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle( // Title
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle( // Body
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    labelLarge = TextStyle( // Label
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle( // Caption
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )
)
