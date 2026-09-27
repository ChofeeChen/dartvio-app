package com.dartvio.app.ui.theme

import kotlin.math.pow

/** Editable opaque sRGB roles. Defaults confirmed by the user on 2026-09-15; success roles retain prior values. */
enum class ThemeRole(val label: String, val defaultHex: String) {
    PRIMARY("主色 Primary", "E63946"), ON_PRIMARY("主色文字 OnPrimary", "FFFFFF"),
    SECONDARY("辅助色 Secondary", "2A9D8F"), ON_SECONDARY("辅助色文字 OnSecondary", "FFFFFF"),
    TERTIARY("强调色 Tertiary", "4CC9F0"), ON_TERTIARY("强调色文字 OnTertiary", "0F1115"),
    BACKGROUND("页面背景 Background", "0F1115"), ON_BACKGROUND("背景文字 OnBackground", "E8E8EC"),
    SURFACE("容器 Surface", "1A1D24"), ON_SURFACE("容器文字 OnSurface", "F0F0F4"),
    SURFACE_VARIANT("次级容器 SurfaceVariant", "2A2E37"), ON_SURFACE_VARIANT("次级文字 OnSurfaceVariant", "9BA1AB"),
    OUTLINE("边框 Outline", "3D424D"),
    ERROR("错误 Error", "FF6B6B"), ON_ERROR("错误文字 OnError", "1A1D24"),
    WARNING("警告 Warning", "FFB454"), ON_WARNING("警告文字 OnWarning", "1A1D24"),
    SUCCESS("成功 Success", "5BC236"), ON_SUCCESS("成功文字 OnSuccess", "1A1A1A");
}

data class ThemePalette(val values: Map<ThemeRole, String> = ThemeRole.entries.associateWith { it.defaultHex }) {
    fun hex(role: ThemeRole): String = values[role] ?: role.defaultHex
    fun edit(role: ThemeRole, raw: String): ThemePalette? = normalizeHex(raw)?.let { copy(values = values + (role to it)) }
    fun encode(): String = ThemeRole.entries.joinToString(";") { "${it.name}=${hex(it)}" }

    companion object {
        fun decode(raw: String?): ThemePalette? {
            if (raw == null) return null
            val parts = raw.split(';').map { it.split('=') }
            if (parts.size != ThemeRole.entries.size || parts.any { it.size != 2 }) return null
            val map = parts.associate { it[0] to it[1] }
            if (map.keys != ThemeRole.entries.map { it.name }.toSet()) return null
            return ThemePalette(ThemeRole.entries.associateWith { normalizeHex(map[it.name]!!) ?: return null })
        }
    }
}

fun normalizeHex(raw: String): String? = raw.trim().removePrefix("#").uppercase().takeIf { it.matches(Regex("[0-9A-F]{6}")) }

fun contrastRatio(first: String, second: String): Double {
    fun luminance(hex: String): Double {
        val rgb = normalizeHex(hex) ?: return 0.0
        val channels = listOf(0, 2, 4).map { offset ->
            val value = rgb.substring(offset, offset + 2).toInt(16) / 255.0
            if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
    }
    val a = luminance(first); val b = luminance(second)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

fun ThemePalette.contrastWarnings(): List<String> = listOf(
    ThemeRole.PRIMARY to ThemeRole.ON_PRIMARY, ThemeRole.SECONDARY to ThemeRole.ON_SECONDARY,
    ThemeRole.TERTIARY to ThemeRole.ON_TERTIARY, ThemeRole.BACKGROUND to ThemeRole.ON_BACKGROUND,
    ThemeRole.SURFACE to ThemeRole.ON_SURFACE, ThemeRole.SURFACE_VARIANT to ThemeRole.ON_SURFACE_VARIANT,
    ThemeRole.ERROR to ThemeRole.ON_ERROR, ThemeRole.WARNING to ThemeRole.ON_WARNING,
    ThemeRole.SUCCESS to ThemeRole.ON_SUCCESS,
).mapNotNull { (background, foreground) ->
    val ratio = contrastRatio(hex(background), hex(foreground))
    if (ratio < 4.5) "${background.label.substringBefore(' ')}与文字：%.2f:1，低于正文参考值 4.5:1".format(java.util.Locale.ROOT, ratio) else null
}
