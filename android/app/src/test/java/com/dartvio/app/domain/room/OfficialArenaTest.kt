package com.dartvio.app.domain.room

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方擂台时间锚点的口径单测。
 *
 * 与 `LocalRoomRepository` 的 Mock 不同，锚点状态是**真实时钟驱动**的，
 * 因此这里必须把边界钉死：开赛前 1 分钟 / 开赛瞬间 / 结束前 1 分钟 / 结束瞬间 / 跨天。
 *
 * ## 两层分工（改时段时请照着看）
 *
 * 1. **逻辑层**：期望值全部由 [OfficialArena.START_MINUTE_OF_DAY] /
 *    [OfficialArena.END_MINUTE_OF_DAY] 派生。改时段**不需要重写**这些用例，
 *    它们继续负责抓「边界写错」这类真 bug。
 * 2. **决策层**：只有 `决策锁_擂台时段为每晚20点至22点` 硬编码了具体钟点。
 *    改时段时**只会这一组失败**——这是刻意的，它提示「你改的是一个产品决策，不是修 bug」。
 */
class OfficialArenaTest {

    private val start = OfficialArena.START_MINUTE_OF_DAY
    private val end = OfficialArena.END_MINUTE_OF_DAY

    // ===== 时段边界（期望值由常量派生）=====

    @Test
    fun `零点指向当天开赛`() {
        assertEquals(ArenaState.Upcoming(start, startsToday = true), OfficialArena.stateAt(0))
    }

    @Test
    fun `开赛前 1 分钟仍为待开始`() {
        assertEquals(
            ArenaState.Upcoming(1, startsToday = true),
            OfficialArena.stateAt(start - 1)
        )
    }

    @Test
    fun `时段首分钟立刻进入进行中`() {
        assertEquals(
            ArenaState.Live(end - start),
            OfficialArena.stateAt(start)
        )
    }

    @Test
    fun `结束前 1 分钟仍为进行中`() {
        assertEquals(ArenaState.Live(1), OfficialArena.stateAt(end - 1))
    }

    @Test
    fun `时段末分钟结束并指向次日`() {
        assertEquals(
            ArenaState.Upcoming(
                minutesUntilStart = OfficialArena.MINUTES_PER_DAY - end + start,
                startsToday = false
            ),
            OfficialArena.stateAt(end)
        )
    }

    @Test
    fun `午夜前 1 分钟指向次日`() {
        assertEquals(
            ArenaState.Upcoming(start + 1, startsToday = false),
            OfficialArena.stateAt(OfficialArena.MINUTES_PER_DAY - 1)
        )
    }

    @Test
    fun `跨天首尾相接——时段末与零点指向同一个开赛时刻`() {
        // 从时段末走到午夜花掉 (1440 - end) 分钟，剩余倒计时应正好少这么多。
        // 这条专门抓跨天分支的 off-by-N：写错常数在这里会立刻暴露。
        val atEnd = OfficialArena.stateAt(end) as ArenaState.Upcoming
        val atMidnight = OfficialArena.stateAt(0) as ArenaState.Upcoming
        assertEquals(
            atEnd.minutesUntilStart - (OfficialArena.MINUTES_PER_DAY - end),
            atMidnight.minutesUntilStart
        )
    }

    @Test
    fun `全天任意时刻都有状态且倒计时恒为正`() {
        for (minute in 0 until OfficialArena.MINUTES_PER_DAY) {
            val remaining = when (val state = OfficialArena.stateAt(minute)) {
                is ArenaState.Upcoming -> state.minutesUntilStart
                is ArenaState.Live -> state.minutesUntilEnd
            }
            assertTrue("第 $minute 分钟倒计时应为正，实际 $remaining", remaining > 0)
        }
    }

    @Test
    fun `全天任意时刻的倒计时都不超过一天的分钟数`() {
        for (minute in 0 until OfficialArena.MINUTES_PER_DAY) {
            val remaining = when (val state = OfficialArena.stateAt(minute)) {
                is ArenaState.Upcoming -> state.minutesUntilStart
                is ArenaState.Live -> state.minutesUntilEnd
            }
            assertTrue(
                "第 $minute 分钟倒计时不应超过一天，实际 $remaining",
                remaining <= OfficialArena.MINUTES_PER_DAY
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `非法分钟数直接失败而不是静默兜底`() {
        OfficialArena.stateAt(OfficialArena.MINUTES_PER_DAY)
    }

    // ===== 与真实时钟的衔接 =====

    @Test
    fun `按时间戳取当天分钟数与本地时间一致`() {
        val millis = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.SEPTEMBER, 11, 20, 30, 0)
        }.timeInMillis

        // 只断言时钟换算本身，不掺杂具体时段状态（那是决策）。
        assertEquals(20 * 60 + 30, OfficialArena.minuteOfDayOf(millis))
    }

    @Test
    fun `时间戳判定与分钟判定完全一致`() {
        val millis = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.SEPTEMBER, 11, OfficialArena.START_HOUR, 30, 0)
        }.timeInMillis

        assertEquals(
            OfficialArena.stateAt(OfficialArena.START_MINUTE_OF_DAY + 30),
            OfficialArena.stateAt(millis)
        )
    }

    // ===== 文案口径 =====
    // 文案是 ArenaState 的纯函数，**直接构造状态**而不要绕经时钟。
    // 绕经 stateAt 会让文案测试隐含依赖具体时段取值（第一版就踩了这个坑）。

    @Test
    fun `待开始文案写出固定钟点与立刻可做的动作`() {
        val state = ArenaState.Upcoming(minutesUntilStart = 11 * 60, startsToday = true)
        assertEquals(
            "官方擂台 · 今晚 ${OfficialArena.START_HOUR}:00 开始",
            state.headline
        )
        assertEquals("还有 11 小时开始 · 先去练几镖", state.subline)
    }

    @Test
    fun `次日待开始文案明确是明晚`() {
        assertEquals(
            "官方擂台 · 明晚 ${OfficialArena.START_HOUR}:00 开始",
            ArenaState.Upcoming(minutesUntilStart = 60, startsToday = false).headline
        )
    }

    @Test
    fun `次日待开始文案不再重复倒计时而是给出可做的动作`() {
        assertEquals(
            "现在先去练几镖，或开个房间等镖友",
            ArenaState.Upcoming(minutesUntilStart = 60, startsToday = false).subline
        )
    }

    @Test
    fun `不足 1 小时时文案不出现 0 小时`() {
        assertEquals(
            "还有 45 分钟开始 · 先去练几镖",
            ArenaState.Upcoming(minutesUntilStart = 45, startsToday = true).subline
        )
    }

    @Test
    fun `进行中文案给出剩余时间与此刻收益最高的动作`() {
        val state = ArenaState.Live(minutesUntilEnd = 72)
        assertEquals("官方擂台 · 进行中", state.headline)
        assertEquals("还剩 1 小时 12 分 · 现在加入最容易配上", state.subline)
    }

    /**
     * 不变式：待开始态**永远**写出固定钟点。
     *
     * 这是「时间锚点」不被写成「预约」的硬约束——只承诺时间，不承诺对手。
     * 一旦有人把文案改成「即将开始」，档位的可承诺性就丢了。
     */
    @Test
    fun `待开始态全天都必须写出固定钟点`() {
        for (minute in 0 until start) {
            val state = OfficialArena.stateAt(minute) as ArenaState.Upcoming
            assertTrue(
                "缺少固定钟点：${state.headline}",
                state.headline.contains("${OfficialArena.START_HOUR}:00")
            )
        }
    }

    // ===== 开赛跳变（大厅开赛横幅的唯一触发条件） =====
    // 横幅是一次性副作用，触发口径必须只有一处：OfficialArena.isKickoff。
    // 这里锁的是「什么时候算开赛」，而不是横幅长什么样（那是 UI 的事）。

    @Test
    fun `待开始推进到进行中算开赛`() {
        assertTrue(
            OfficialArena.isKickoff(
                previous = ArenaState.Upcoming(minutesUntilStart = 1, startsToday = true),
                next = ArenaState.Live(minutesUntilEnd = end - start)
            )
        )
    }

    @Test
    fun `已在进行中不重复算开赛`() {
        assertFalse(
            OfficialArena.isKickoff(
                previous = ArenaState.Live(minutesUntilEnd = 90),
                next = ArenaState.Live(minutesUntilEnd = 89)
            )
        )
    }

    @Test
    fun `进行中推进到待开始不算开赛`() {
        assertFalse(
            OfficialArena.isKickoff(
                previous = ArenaState.Live(minutesUntilEnd = 1),
                next = ArenaState.Upcoming(minutesUntilStart = 22 * 60, startsToday = false)
            )
        )
    }

    @Test
    fun `待开始推进到待开始不算开赛`() {
        assertFalse(
            OfficialArena.isKickoff(
                previous = ArenaState.Upcoming(minutesUntilStart = 10, startsToday = true),
                next = ArenaState.Upcoming(minutesUntilStart = 9, startsToday = true)
            )
        )
    }

    /**
     * 不变式：一整天逐分钟推进，「开赛跳变」恰好发生一次。
     *
     * 多于一次 = 横幅会重复播放（从「召回」变「噪音」）；零次 = 横幅永远不会出现。
     * 这条同时覆盖了「App 打开时已在时段内不触发」——那种情况下没有上一帧，
     * 初始状态即为 Live，天然不构成跳变。
     */
    @Test
    fun `一天内开赛跳变恰好发生一次`() {
        var kickoffs = 0
        for (minute in 1 until OfficialArena.MINUTES_PER_DAY) {
            val previous = OfficialArena.stateAt(minute - 1)
            val next = OfficialArena.stateAt(minute)
            if (OfficialArena.isKickoff(previous, next)) kickoffs++
        }
        assertEquals("一天内开赛跳变次数", 1, kickoffs)
    }

    // ===== 时段配置守卫 =====

    @Test
    fun `当前时段配置合法`() {
        requireValidWindow(OfficialArena.START_HOUR, OfficialArena.END_HOUR)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `跨零点时段直接失败而不是静默算错`() {
        // 若不拦，00:30 会被判成「尚未开赛」并显示「还有 22 小时开始」——
        // 不崩溃，只是安静地说谎。这是本组守卫存在的唯一理由。
        requireValidWindow(23, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `零长度时段直接失败`() {
        requireValidWindow(20, 20)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `起始小时越界直接失败`() {
        requireValidWindow(24, 25)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `结束小时到 24 直接失败以保持时段落在同一天内`() {
        requireValidWindow(20, 24)
    }

    // ===== 决策锁 =====
    // 这一组锁的是「决策」而不是「逻辑」。改 START_HOUR / END_HOUR 时**只有这里会失败**，
    // 因为上面的期望值全部由常量派生。
    //
    // 请把失败当成一次确认：如果新时段仍在同一天内，改完这组断言即可；
    // 如果落在跨零点（如 23:00–01:00），requireValidWindow 那一组会一并失败——
    // 那说明这不是一次「改两个常量」的调整，需要先改造 stateAt。

    @Test
    fun `决策锁_擂台时段为每晚20点至22点`() {
        assertEquals("擂台时段为待校准的占位决策，改动需同步本断言", 20, OfficialArena.START_HOUR)
        assertEquals("擂台时段为待校准的占位决策，改动需同步本断言", 22, OfficialArena.END_HOUR)
        assertEquals("每晚 20:00 - 22:00", OfficialArena.WINDOW_LABEL)
    }
}
