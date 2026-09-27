package com.dartvio.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dartvio.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThemeDebugScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hexDraftDoesNotApplyUntilConfirmedAndCancelDoesNotSave() {
        var applied: ThemePalette? = null
        var cancelled = false
        compose.setContent { ThemeDebugScreen(ThemePalette(), { applied = it; true }, { cancelled = true }) }
        compose.onNodeWithTag("theme-hex").performTextReplacement("#336699")
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithTag("theme-apply").performClick()
        compose.runOnIdle { assertEquals("336699", applied!!.hex(ThemeRole.PRIMARY)) }
        compose.onNodeWithTag("theme-hex").performTextReplacement("112233")
        compose.onNodeWithTag("theme-cancel").performClick()
        compose.runOnIdle { assertTrue(cancelled); assertEquals("336699", applied!!.hex(ThemeRole.PRIMARY)) }
    }

    @Test fun invalidHexDisablesApplyButRestoreImmediatelySavesDefaults() {
        var applied: ThemePalette? = null
        val initial = ThemePalette().edit(ThemeRole.PRIMARY, "336699")!!
        compose.setContent { ThemeDebugScreen(initial, { applied = it; true }, {}) }
        compose.onNodeWithTag("theme-hex").performTextReplacement("oops")
        compose.onNodeWithTag("theme-apply").assertIsNotEnabled()
        compose.onNodeWithTag("theme-reset").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ThemePalette(), applied) }
    }

    @Test fun defaultThemeUsesConfirmedPaletteWithoutOverride() {
        compose.setContent {
            DartVioTheme {
                assertEquals(0xFFE63946.toInt(), MaterialTheme.colorScheme.primary.toArgb())
                assertEquals(0xFF0F1115.toInt(), MaterialTheme.colorScheme.background.toArgb())
                assertEquals(0xFFFFB454.toInt(), LocalStatusColors.current.warning.toArgb())
            }
        }
    }

    @Test fun failedRestoreKeepsDraftAndReportsFailure() {
        compose.setContent { ThemeDebugScreen(ThemePalette().edit(ThemeRole.PRIMARY, "336699")!!, { false }, {}) }
        compose.onNodeWithTag("theme-reset").performScrollTo().performClick()
        compose.onNodeWithTag("theme-message").assertTextContains("保存失败", substring = true)
        compose.onNodeWithTag("theme-hex").assertTextContains("336699", substring = true)
    }

    @Test fun legacyTokensAndMaterialSchemeUpdateTogether() {
        var palette by mutableStateOf(ThemePalette())
        var legacy = 0
        var material = 0
        compose.setContent {
            DartVioTheme(paletteOverride = palette) {
                legacy = Primary.toArgb()
                material = MaterialTheme.colorScheme.primary.toArgb()
                Text("preview")
            }
        }
        compose.runOnIdle { palette = palette.edit(ThemeRole.PRIMARY, "336699")!! }
        compose.runOnIdle { assertEquals(0xFF336699.toInt(), legacy); assertEquals(legacy, material) }
    }
}
