package com.dartvio.app.data.theme

import android.content.SharedPreferences
import com.dartvio.app.data.achievement.InMemorySharedPreferences
import com.dartvio.app.ui.theme.ThemePalette
import com.dartvio.app.ui.theme.ThemeRole
import org.junit.Assert.*
import org.junit.Test

class ThemeStoreTest {
    @Test fun `draft cancel has no persistence and apply survives a new store instance`() {
        val prefs = InMemorySharedPreferences()
        val store = ThemeStore(prefs)
        val draft = ThemePalette().edit(ThemeRole.PRIMARY, "123456")!!
        assertNull(store.palette.value)
        assertNull(ThemeStore(prefs).palette.value)
        assertTrue(store.apply(draft))
        assertEquals(draft, ThemeStore(prefs).palette.value)
    }
    @Test fun `saving defaults replaces custom palette and preserves unrelated preferences`() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString("unrelated", "keep").apply()
        val store = ThemeStore(prefs)
        val custom = ThemePalette().edit(ThemeRole.SECONDARY, "123456")!!
        store.apply(custom)
        val restoredDraft = ThemePalette()
        assertEquals(custom, store.palette.value)
        assertTrue(store.apply(restoredDraft))
        assertEquals(restoredDraft, ThemeStore(prefs).palette.value)
        assertEquals("keep", prefs.getString("unrelated", null))
    }
    @Test fun `corrupt preference falls back without overwriting it`() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString(ThemeStore.KEY, "bad").apply()
        assertNull(ThemeStore(prefs).palette.value)
        assertEquals("bad", prefs.getString(ThemeStore.KEY, null))
    }
    @Test fun `failed durable write does not publish new palette`() {
        val backing = InMemorySharedPreferences()
        val failing = object : SharedPreferences by backing {
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor by backing.edit() {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor = this
                override fun commit(): Boolean = false
            }
        }
        val store = ThemeStore(failing)
        assertFalse(store.apply(ThemePalette()))
        assertNull(store.palette.value)
        assertNull(ThemeStore(backing).palette.value)
    }
}
