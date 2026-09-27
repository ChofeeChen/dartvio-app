package com.dartvio.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dartvio.app.data.theme.ThemeStore
import com.dartvio.app.ui.theme.ThemePalette
import com.dartvio.app.ui.theme.ThemeRole
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThemeNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun profileEntryAppliesAndActivityRecreationRetainsLocalPalette() {
        val prefs = compose.activity.getSharedPreferences(ThemeStore.PREFS, Context.MODE_PRIVATE)
        val before = prefs.getString(ThemeStore.KEY, null)
        try {
            compose.onNodeWithText("我的").performClick()
            compose.onNodeWithText("设置").performScrollTo().performClick()
            compose.onNodeWithText("外观与主题").performClick()
            compose.onNodeWithText("主题调试面板").performClick()
            compose.onNodeWithTag("theme-hex").performTextReplacement("#336699")
            compose.onNodeWithTag("theme-apply").performClick()
            compose.waitUntil(10_000) {
                ThemePalette.decode(prefs.getString(ThemeStore.KEY, null))?.hex(ThemeRole.PRIMARY) == "336699"
            }
            compose.waitForIdle()
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            assertEquals("336699", ThemeStore(prefs).palette.value?.hex(ThemeRole.PRIMARY))
            compose.onNodeWithTag("theme-hex").assertTextContains("336699", substring = true)
            compose.onNodeWithTag("theme-cancel").performClick()
            compose.onNodeWithTag("settings-theme-reset").performClick()
            compose.waitUntil(10_000) {
                ThemePalette.decode(prefs.getString(ThemeStore.KEY, null)) == ThemePalette()
            }
            compose.waitForIdle()
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            assertEquals(ThemePalette(), ThemeStore(prefs).palette.value)
            compose.onNodeWithText("主题调试面板").performClick()
            compose.onNodeWithTag("theme-hex").assertTextContains("E63946", substring = true)
        } finally {
            val editor = prefs.edit()
            if (before == null) editor.remove(ThemeStore.KEY) else editor.putString(ThemeStore.KEY, before)
            check(editor.commit())
        }
    }
}
