package com.dartvio.app.data.theme

import android.content.SharedPreferences
import com.dartvio.app.ui.theme.ThemePalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dedicated local preferences; never changes match schemas, identity or cloud data. */
class ThemeStore(private val preferences: SharedPreferences) {
    private val state = MutableStateFlow(read())
    val palette = state.asStateFlow()

    private fun read(): ThemePalette? = try {
        ThemePalette.decode(preferences.getString(KEY, null))
    } catch (_: ClassCastException) { null }

    /** Called off the main thread. Publish only after a successful durable write. */
    fun apply(palette: ThemePalette): Boolean {
        val valid = ThemePalette.decode(palette.encode()) ?: return false
        if (!preferences.edit().putString(KEY, valid.encode()).commit()) return false
        state.value = valid
        return true
    }

    companion object {
        const val PREFS = "dartvio_theme_debug"
        const val KEY = "light_palette_v1"
    }
}
