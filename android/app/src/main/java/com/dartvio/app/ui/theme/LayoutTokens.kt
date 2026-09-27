package com.dartvio.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Adjustable working scales, not final design values. */
object ThemeSpacing { val small = 8.dp; val medium = 16.dp; val large = 24.dp }
object ThemeSize { val swatch = 48.dp; val previewMinimum = 48.dp }
object ThemeElevation { val card = 1.dp; val dialog = 6.dp }
val DartVioShapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp))
