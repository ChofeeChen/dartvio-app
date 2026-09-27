package com.dartvio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

val LocalThemeStore = androidx.compose.runtime.staticCompositionLocalOf<com.dartvio.app.data.theme.ThemeStore> {
    error("ThemeStore must be provided by the application")
}

private val DartVioDarkColorScheme = darkColorScheme(
    primary = BaselineColors.Primary,
    onPrimary = BaselineColors.OnPrimary,
    primaryContainer = BaselineColors.PrimaryContainer,
    onPrimaryContainer = BaselineColors.PrimaryLight,
    secondary = BaselineColors.Secondary,
    onSecondary = BaselineColors.OnSecondary,
    secondaryContainer = BaselineColors.SecondaryDark,
    onSecondaryContainer = BaselineColors.SecondaryLight,
    tertiary = BaselineColors.Accent,
    onTertiary = BaselineColors.OnAccent,
    background = BaselineColors.BackgroundDark,
    onBackground = BaselineColors.TextPrimaryDark,
    surface = BaselineColors.SurfaceDark,
    onSurface = BaselineColors.TextPrimaryDark,
    surfaceVariant = BaselineColors.SurfaceVariantDark,
    onSurfaceVariant = BaselineColors.TextSecondaryDark,
    outline = BaselineColors.Divider,
    error = BaselineColors.Error,
    onError = BaselineColors.TextPrimaryDark
)

private val DartVioLightColorScheme = lightColorScheme(
    primary = BaselineColors.Primary,
    onPrimary = BaselineColors.OnPrimary,
    primaryContainer = BaselineColors.PrimaryLight,
    onPrimaryContainer = BaselineColors.PrimaryContainer,
    secondary = BaselineColors.SecondaryDark,
    onSecondary = BaselineColors.OnSecondary,
    secondaryContainer = BaselineColors.SecondaryLight,
    onSecondaryContainer = BaselineColors.SecondaryDark,
    tertiary = BaselineColors.AccentDark,
    onTertiary = BaselineColors.OnAccent,
    background = BaselineColors.BackgroundLight,
    onBackground = BaselineColors.TextPrimaryLight,
    surface = BaselineColors.SurfaceLight,
    onSurface = BaselineColors.TextPrimaryLight,
    surfaceVariant = BaselineColors.SurfaceVariantLight,
    onSurfaceVariant = BaselineColors.TextSecondaryLight,
    outline = BaselineColors.DividerLight,
    error = BaselineColors.Error,
    onError = BaselineColors.SurfaceLight
)

/** Centralized application defaults with an optional saved custom palette. */
data class StatusColors(val warning: androidx.compose.ui.graphics.Color, val onWarning: androidx.compose.ui.graphics.Color,
    val success: androidx.compose.ui.graphics.Color, val onSuccess: androidx.compose.ui.graphics.Color)

val LocalStatusColors = androidx.compose.runtime.staticCompositionLocalOf {
    StatusColors(BaselineColors.Warning, BaselineColors.TextPrimaryLight, BaselineColors.Success, BaselineColors.TextPrimaryLight)
}

fun ThemePalette.color(role: ThemeRole) = androidx.compose.ui.graphics.Color(0xFF000000L or hex(role).toLong(16))

fun paletteScheme(palette: ThemePalette): androidx.compose.material3.ColorScheme {
    fun c(role: ThemeRole) = palette.color(role)
    return DartVioDarkColorScheme.copy(
        primary=c(ThemeRole.PRIMARY), onPrimary=c(ThemeRole.ON_PRIMARY),
        primaryContainer=c(ThemeRole.SURFACE_VARIANT), onPrimaryContainer=c(ThemeRole.ON_SURFACE_VARIANT),
        secondary=c(ThemeRole.SECONDARY), onSecondary=c(ThemeRole.ON_SECONDARY),
        secondaryContainer=c(ThemeRole.SURFACE_VARIANT), onSecondaryContainer=c(ThemeRole.ON_SURFACE_VARIANT),
        tertiary=c(ThemeRole.TERTIARY), onTertiary=c(ThemeRole.ON_TERTIARY),
        tertiaryContainer=c(ThemeRole.SURFACE_VARIANT), onTertiaryContainer=c(ThemeRole.ON_SURFACE_VARIANT),
        background=c(ThemeRole.BACKGROUND), onBackground=c(ThemeRole.ON_BACKGROUND),
        surface=c(ThemeRole.SURFACE), onSurface=c(ThemeRole.ON_SURFACE),
        surfaceVariant=c(ThemeRole.SURFACE_VARIANT), onSurfaceVariant=c(ThemeRole.ON_SURFACE_VARIANT),
        surfaceContainer=c(ThemeRole.SURFACE), surfaceContainerLow=c(ThemeRole.SURFACE),
        surfaceContainerLowest=c(ThemeRole.BACKGROUND), surfaceContainerHigh=c(ThemeRole.SURFACE_VARIANT),
        surfaceContainerHighest=c(ThemeRole.SURFACE_VARIANT), surfaceBright=c(ThemeRole.SURFACE),
        surfaceDim=c(ThemeRole.SURFACE_VARIANT), surfaceTint=c(ThemeRole.PRIMARY),
        outline=c(ThemeRole.OUTLINE), outlineVariant=c(ThemeRole.OUTLINE),
        error=c(ThemeRole.ERROR), onError=c(ThemeRole.ON_ERROR),
        errorContainer=c(ThemeRole.ERROR), onErrorContainer=c(ThemeRole.ON_ERROR),
        inverseSurface=c(ThemeRole.ON_SURFACE), inverseOnSurface=c(ThemeRole.SURFACE), inversePrimary=c(ThemeRole.PRIMARY),
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DartVioTheme(
    darkTheme: Boolean = true,
    paletteOverride: ThemePalette? = null,
    content: @Composable () -> Unit
) {
    val palette = paletteOverride ?: ThemePalette()
    val scheme = if (!darkTheme && paletteOverride == null) DartVioLightColorScheme else paletteScheme(palette)
    val status = StatusColors(palette.color(ThemeRole.WARNING), palette.color(ThemeRole.ON_WARNING),
        palette.color(ThemeRole.SUCCESS), palette.color(ThemeRole.ON_SUCCESS))
    androidx.compose.runtime.CompositionLocalProvider(
        LocalStatusColors provides status,
        // 关掉 Android 12+ 的滚动拉伸（overscroll stretch）：列表滚到底就停住，
        // 卡片不该像果冻一样被拉长 —— 那会让人误以为卡片本身在变形。
        androidx.compose.foundation.LocalOverscrollConfiguration provides null,
    ) {
        MaterialTheme(colorScheme = scheme, typography = DartVioTypography,
            shapes = DartVioShapes, content = content)
    }
}
