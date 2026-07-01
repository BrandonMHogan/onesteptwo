package com.onesteptwo.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corner radius tokens — docs/DESIGN-TOKENS.md §Corner Radii. */
object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val pill = 100.dp
}

val ShapeSm = RoundedCornerShape(Radius.sm)
val ShapeMd = RoundedCornerShape(Radius.md)
val ShapeLg = RoundedCornerShape(Radius.lg)
val ShapePill = RoundedCornerShape(Radius.pill)
