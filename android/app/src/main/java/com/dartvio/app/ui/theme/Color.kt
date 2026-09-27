package com.dartvio.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme

/** Historical working palette. These values are not approved final branding. */
internal object BaselineColors {
    val Primary = Color(0xFFEE9756)
    val PrimaryDark = Color(0xFFC97840)
    val PrimaryLight = Color(0xFFF6C29A)
    val PrimaryContainer = Color(0xFF4A2E17)
    val OnPrimary = Color(0xFF1A1108)
    val Secondary = Color(0xFF4EC9C4)
    val SecondaryDark = Color(0xFF2E9A96)
    val SecondaryLight = Color(0xFF9BE3E0)
    val OnSecondary = Color(0xFF04201F)
    val Accent = Color(0xFFE8C468)
    val AccentDark = Color(0xFFB99A3F)
    val OnAccent = Color(0xFF241B04)
    val BackgroundDark = Color(0xFF121212)
    val SurfaceDark = Color(0xFF1E1E1E)
    val SurfaceVariantDark = Color(0xFF2A2A2A)
    val SurfaceElevated = Color(0xFF333333)
    val Divider = Color(0xFF3D3D3D)
    val BackgroundLight = Color(0xFFF7F5F2)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceVariantLight = Color(0xFFEFEBE6)
    val DividerLight = Color(0xFFDDD8D1)
    val Success = Color(0xFF5BC236)
    val Warning = Color(0xFFF5A623)
    val Error = Color(0xFFE5484D)
    val Info = Color(0xFF5B9BD5)
    val ScorePositive = Color(0xFF5BC236)
    val Bust = Color(0xFFE5484D)
    val GameShot = Color(0xFFE8C468)
    val Checkout = Color(0xFF4EC9C4)
    val BoardBlack = Color(0xFF1A1A1A)
    val BoardCream = Color(0xFFE8DCC8)
    val BoardRed = Color(0xFFC8322B)
    val BoardGreen = Color(0xFF2E8B57)
    val BoardWire = Color(0xFF8A8A8A)
    val HeatCold = Color(0xFF5B9BD5)
    val HeatMid = Color(0xFFF5A623)
    val HeatHot = Color(0xFFE5484D)
    val TextPrimaryDark = Color(0xFFF2F2F2)
    val TextSecondaryDark = Color(0xFFB0B0B0)
    val TextDisabledDark = Color(0xFF6B6B6B)
    val TextPrimaryLight = Color(0xFF1A1A1A)
    val TextSecondaryLight = Color(0xFF5A5A5A)
}

// Compatibility names now resolve semantic roles from the current local Theme.
val Primary: Color @Composable get() = MaterialTheme.colorScheme.primary
val PrimaryDark: Color @Composable get() = MaterialTheme.colorScheme.primary
val PrimaryLight: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val PrimaryContainer: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val OnPrimary: Color @Composable get() = MaterialTheme.colorScheme.onPrimary
val Secondary: Color @Composable get() = MaterialTheme.colorScheme.secondary
val SecondaryDark: Color @Composable get() = MaterialTheme.colorScheme.secondary
val SecondaryLight: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
val OnSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSecondary
val Accent: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val AccentDark: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val OnAccent: Color @Composable get() = MaterialTheme.colorScheme.onTertiary
val BackgroundDark: Color @Composable get() = MaterialTheme.colorScheme.background
val SurfaceDark: Color @Composable get() = MaterialTheme.colorScheme.surface
val SurfaceVariantDark: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val SurfaceElevated: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
val Divider: Color @Composable get() = MaterialTheme.colorScheme.outline
val BackgroundLight: Color @Composable get() = MaterialTheme.colorScheme.background
val SurfaceLight: Color @Composable get() = MaterialTheme.colorScheme.surface
val SurfaceVariantLight: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val DividerLight: Color @Composable get() = MaterialTheme.colorScheme.outline
val Success: Color @Composable get() = LocalStatusColors.current.success
val Warning: Color @Composable get() = LocalStatusColors.current.warning
val Error: Color @Composable get() = MaterialTheme.colorScheme.error
val Info: Color = BaselineColors.Info
val ScorePositive: Color @Composable get() = LocalStatusColors.current.success
val Bust: Color @Composable get() = MaterialTheme.colorScheme.error
val GameShot: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val Checkout: Color @Composable get() = MaterialTheme.colorScheme.secondary
val BoardBlack: Color = BaselineColors.BoardBlack
val BoardCream: Color = BaselineColors.BoardCream
val BoardRed: Color = BaselineColors.BoardRed
val BoardGreen: Color = BaselineColors.BoardGreen
val BoardWire: Color = BaselineColors.BoardWire
val HeatCold: Color = BaselineColors.HeatCold
val HeatMid: Color = BaselineColors.HeatMid
val HeatHot: Color = BaselineColors.HeatHot
val TextPrimaryDark: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val TextSecondaryDark: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TextDisabledDark: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
val TextPrimaryLight: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val TextSecondaryLight: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

// Board-specific drawing roles stay independent from application branding.
val BoardLabel = Color(0xFFF2F2F2)
val BoardMarker = Color(0xFFFFFFFF)
val BoardLabelBackground = Color(0xCC000000)
