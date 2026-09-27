package com.dartvio.app.domain.achievement

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 练习打卡口径单测（决策⑤：自然日 + 连续/最高双指标）。
 */
class PracticeCheckInTest {

    @Test
    fun `首日打卡从 1 开始`() {
        val after = PracticeCheckIn().afterPractice(
            todayKey = "2026-09-11",
            yesterdayKey = "2026-09-10",
        )
        assertEquals("2026-09-11", after.lastDateKey)
        assertEquals(1, after.streakCurrent)
        assertEquals(1, after.streakBest)
        assertEquals(1, after.daysTotal)
    }

    @Test
    fun `同日重复练习不重复计数也不打断连续`() {
        val before = PracticeCheckIn(lastDateKey = "2026-09-11", streakCurrent = 3, streakBest = 5, daysTotal = 7)
        val after = before.afterPractice(todayKey = "2026-09-11", yesterdayKey = "2026-09-10")
        assertEquals(before, after)
    }

    @Test
    fun `昨天练过则连续天数加一`() {
        val after = PracticeCheckIn(lastDateKey = "2026-09-10", streakCurrent = 2, streakBest = 2, daysTotal = 2)
            .afterPractice(todayKey = "2026-09-11", yesterdayKey = "2026-09-10")
        assertEquals(3, after.streakCurrent)
        assertEquals(3, after.streakBest)
        assertEquals(3, after.daysTotal)
    }

    @Test
    fun `断签时当前连续重置为 1，但最高纪录保留`() {
        val after = PracticeCheckIn(lastDateKey = "2026-09-01", streakCurrent = 5, streakBest = 5, daysTotal = 5)
            .afterPractice(todayKey = "2026-09-11", yesterdayKey = "2026-09-10")
        assertEquals(1, after.streakCurrent)
        assertEquals("最高纪录不得因断签而下调（决策⑤）", 5, after.streakBest)
        assertEquals(6, after.daysTotal)
    }

    @Test
    fun `自然日键跨月与跨年正确回退`() {
        assertEquals("2026-09-11", LocalDateKey.of(millisOf(2026, 9, 11, 12)))
        // 2026 非闰年：3/1 的昨天是 2/28
        assertEquals("2026-02-28", LocalDateKey.daysBefore(millisOf(2026, 3, 1, 12), 1))
        // 跨年
        assertEquals("2025-12-31", LocalDateKey.daysBefore(millisOf(2026, 1, 1, 12), 1))
    }

    /** 用 Calendar 构造本地时间，避免把时区写进断言。 */
    private fun millisOf(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis
}
