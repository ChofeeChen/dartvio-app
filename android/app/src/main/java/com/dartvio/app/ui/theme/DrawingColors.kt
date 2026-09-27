package com.dartvio.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Capture at composition time, pass immutable colors to non-composable draw callbacks. */
data class DrawingColors(val Primary: Color, val Accent: Color, val Warning: Color,
    val Divider: Color, val SurfaceVariantDark: Color, val TextSecondaryDark: Color, val OnPrimary: Color, val TextPrimaryDark: Color)

@Composable
fun drawingColors() = DrawingColors(Primary, Accent, Warning, Divider, SurfaceVariantDark, TextSecondaryDark, OnPrimary, TextPrimaryDark)
