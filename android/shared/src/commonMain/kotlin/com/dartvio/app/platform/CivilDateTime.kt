package com.dartvio.app.platform

/**
 * 公历运算（纯 Kotlin，不依赖任何平台 API）。
 *
 * ## 为什么自己算
 *
 * 两端共用口径的前提是**同一段代码**。平台只提供「当前时刻」与「时区偏移」两个数字
 * （见 [PlatformTime]），从这里往上的日历换算全部在这份文件里完成，因此：
 *
 * - Android 与 iOS 算出来的「今天」必然是同一个今天；
 * - 这批逻辑可以被 JVM 单测直接覆盖，不需要真机、不需要 Robolectric。
 *
 * 日序号算法用 Howard Hinnant 的 `civil_from_days`（纯整数运算，无查表、无时区）。
 */
object CivilDateTime {

    const val MS_PER_DAY = 86_400_000L
    private const val MS_PER_MINUTE = 60_000L

    /** 本地自然日的序号（1970-01-01 当地 = 0，更早为负）。 */
    fun dayNumber(millis: Long, offsetMillis: Long): Int =
        (millis + offsetMillis).floorDiv(MS_PER_DAY).toInt()

    /** 本地当天已过的分钟数（0..1439）。 */
    fun minuteOfDay(millis: Long, offsetMillis: Long): Int =
        ((millis + offsetMillis).mod(MS_PER_DAY) / MS_PER_MINUTE).toInt()

    /** `20:30`。 */
    fun clockLabel(millis: Long, offsetMillis: Long): String {
        val minute = minuteOfDay(millis, offsetMillis)
        val hour = minute / 60
        val rest = minute % 60
        return "${hour.toString().padStart(2, '0')}:${rest.toString().padStart(2, '0')}"
    }

    /** `9月27日 `（尾空格，供「日期 + 时刻」直接拼接）。 */
    fun monthDayLabel(millis: Long, offsetMillis: Long): String {
        val (_, month, day) = civilFromDays(dayNumber(millis, offsetMillis))
        return "${month}月${day}日 "
    }

    /** 自然日键 `yyyy-MM-dd`。 */
    fun dateKey(millis: Long, offsetMillis: Long): String = dateKeyOfDayNumber(dayNumber(millis, offsetMillis))

    /**
     * [millis] 之前 [days] 个自然日的键（[days] = 1 即「昨天」）。
     *
     * 走**日序号减法**而不是「减 24 小时再取偏移」：后者在夏令时切换日会差一小时，
     * 恰好落在午夜附近就会算错一天。
     */
    fun dateKeyDaysBefore(millis: Long, offsetMillis: Long, days: Int): String =
        dateKeyOfDayNumber(dayNumber(millis, offsetMillis) - days)

    /** 日序号 → 自然日键。 */
    fun dateKeyOfDayNumber(day: Int): String {
        val (year, month, dayOfMonth) = civilFromDays(day)
        return buildString {
            append(year.toString().padStart(4, '0'))
            append('-')
            append(month.toString().padStart(2, '0'))
            append('-')
            append(dayOfMonth.toString().padStart(2, '0'))
        }
    }

    /**
     * 日序号 → 公历年月日（Hinnant `civil_from_days`）。
     *
     * 这里的整数除法是**向零取整**（Kotlin `Int / Int` 的语义），与原算法一致；
     * 改成向下取整会算错 1970 年之前的日期。
     */
    fun civilFromDays(day: Int): Triple<Int, Int, Int> {
        val z = day + 719_468
        val era = if (z >= 0) z else z - 146_096
        val doe = era - era / 146_097 * 146_097                            // [0, 146096]
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365  // [0, 399]
        val y = yoe + era / 146_097 * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)                  // [0, 365]
        val mp = (5 * doy + 2) / 153                                       // [0, 11]
        val dayOfMonth = doy - (153 * mp + 2) / 5 + 1                      // [1, 31]
        val month = mp + if (mp < 10) 3 else -9                            // [1, 12]
        return Triple(if (month <= 2) y + 1 else y, month, dayOfMonth)
    }
}
