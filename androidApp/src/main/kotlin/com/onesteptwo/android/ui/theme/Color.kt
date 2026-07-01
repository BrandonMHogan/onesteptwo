package com.onesteptwo.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Token values transcribed verbatim from docs/DESIGN-TOKENS.md — do not edit here without
 * updating that file first.
 */

// Purple scale
val Purple50 = Color(0xFFFAF5FF)
val Purple100 = Color(0xFFF3E8FF)
val Purple200 = Color(0xFFE9D5FF)
val Purple300 = Color(0xFFD8B4FE)
val Purple400 = Color(0xFFC084FC)
val Purple500 = Color(0xFFA855F7)
val Purple600 = Color(0xFF9333EA)
val Purple700 = Color(0xFF7E22CE)
val Purple800 = Color(0xFF6B21A8)
val Purple900 = Color(0xFF581C87)

// Light scheme semantic tokens
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFAF5FF)
val LightSurfaceContainer = Color(0xFFF3E8FF)
val LightPrimary = Color(0xFF7E22CE)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightSecondary = Color(0xFFF3E8FF)
val LightOnSecondary = Color(0xFF581C87)
val LightOnBackground = Color(0xFF1C1B1F)
val LightOnSurface = Color(0xFF1C1B1F)
val LightOutline = Color(0xFFCDC7D8)
val LightError = Color(0xFFB91C1C)
val LightOnError = Color(0xFFFFFFFF)
val LightSuccess = Color(0xFF15803D)
val LightOnSuccess = Color(0xFFFFFFFF)

// Dark scheme semantic tokens
val DarkBackground = Color(0xFF0E0A14)
val DarkSurface = Color(0xFF1A1427)
val DarkSurfaceContainer = Color(0xFF25183A)
val DarkPrimary = Color(0xFFD8B4FE)
val DarkOnPrimary = Color(0xFF3B0764)
val DarkSecondary = Color(0xFF2D1B4A)
val DarkOnSecondary = Color(0xFFE9D5FF)
val DarkOnBackground = Color(0xFFECE8F4)
val DarkOnSurface = Color(0xFFECE8F4)
val DarkOutline = Color(0xFF534870)
val DarkError = Color(0xFFFCA5A5)
val DarkOnError = Color(0xFF7F1D1D)
val DarkSuccess = Color(0xFF86EFAC)
val DarkOnSuccess = Color(0xFF14532D)

// Heatmap intensity (light / dark)
val HeatmapLowLight = Purple100
val HeatmapMediumLight = Purple400
val HeatmapHighLight = Purple700
val HeatmapLowDark = Purple800
val HeatmapMediumDark = Purple500
val HeatmapHighDark = Purple300

// Fixed regardless of theme (Toast background per Component 6)
val ToastBackground = Color(0xFF1C1B1F)
val ToastOnBackground = Color(0xFFFFFFFF)
