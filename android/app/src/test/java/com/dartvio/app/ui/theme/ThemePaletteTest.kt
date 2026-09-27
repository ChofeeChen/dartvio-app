package com.dartvio.app.ui.theme

import org.junit.Assert.*
import org.junit.Test

class ThemePaletteTest {
    @Test fun `defaults match all seventeen user supplied roles`() {
        val expected = mapOf(
            ThemeRole.PRIMARY to "E63946", ThemeRole.ON_PRIMARY to "FFFFFF",
            ThemeRole.SECONDARY to "2A9D8F", ThemeRole.ON_SECONDARY to "FFFFFF",
            ThemeRole.TERTIARY to "4CC9F0", ThemeRole.ON_TERTIARY to "0F1115",
            ThemeRole.BACKGROUND to "0F1115", ThemeRole.ON_BACKGROUND to "E8E8EC",
            ThemeRole.SURFACE to "1A1D24", ThemeRole.ON_SURFACE to "F0F0F4",
            ThemeRole.SURFACE_VARIANT to "2A2E37", ThemeRole.ON_SURFACE_VARIANT to "9BA1AB",
            ThemeRole.OUTLINE to "3D424D", ThemeRole.ERROR to "FF6B6B",
            ThemeRole.ON_ERROR to "1A1D24", ThemeRole.WARNING to "FFB454",
            ThemeRole.ON_WARNING to "1A1D24"
        )
        expected.forEach { (role, hex) -> assertEquals(role.name, hex, ThemePalette().hex(role)) }
        assertEquals("5BC236", ThemePalette().hex(ThemeRole.SUCCESS))
        assertEquals("1A1A1A", ThemePalette().hex(ThemeRole.ON_SUCCESS))
    }

    @Test fun `accepts pasted hash and lowercase but rejects incomplete or transparent colors`() {
        assertEquals("336699", normalizeHex(" #336699 "))
        assertEquals("ABCDEF", normalizeHex("abcdef"))
        listOf("", "#123", "GGHHII", "FF336699", "12345Z", "##336699").forEach { assertNull(normalizeHex(it)) }
    }
    @Test fun `primary secondary and tertiary can be independently entered by hex`() {
        val original = ThemePalette()
        val draft = original.edit(ThemeRole.PRIMARY, "#123456")!!
            .edit(ThemeRole.SECONDARY, "abcdef")!!.edit(ThemeRole.TERTIARY, "445566")!!
        assertEquals("123456", draft.hex(ThemeRole.PRIMARY))
        assertEquals("ABCDEF", draft.hex(ThemeRole.SECONDARY))
        assertEquals("445566", draft.hex(ThemeRole.TERTIARY))
        assertEquals(ThemeRole.PRIMARY.defaultHex, original.hex(ThemeRole.PRIMARY))
        assertEquals(original.hex(ThemeRole.SURFACE), draft.hex(ThemeRole.SURFACE))
    }
    @Test fun `saved palette round trips all roles and corrupt payload cannot partially apply`() {
        val draft = ThemePalette().edit(ThemeRole.OUTLINE, "112233")!!
        assertEquals(draft, ThemePalette.decode(draft.encode()))
        assertNull(ThemePalette.decode(draft.encode().replace("OUTLINE=112233", "OUTLINE=invalid")))
        assertNull(ThemePalette.decode("PRIMARY=112233"))
        assertNull(ThemePalette.decode(null))
    }
    @Test fun `contrast detects unreadable text with known reference ratios`() {
        assertEquals(21.0, contrastRatio("FFFFFF", "000000"), 0.001)
        assertEquals(1.0, contrastRatio("336699", "336699"), 0.001)
        val low = ThemePalette().edit(ThemeRole.PRIMARY, "FFFFFF")!!.edit(ThemeRole.ON_PRIMARY, "FFFFFF")!!
        assertTrue(low.contrastWarnings().any { it.startsWith("主色与文字") })
    }
}
