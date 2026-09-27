package com.dartvio.app.domain.versus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对抗练习六个玩法的规则单测。
 *
 * 覆盖的是**引擎判定本身**（怎么算分 / 什么时候前进 / 什么时候终局 / 认输与中止的语义），
 * 因为这一层是纯 JVM：不碰 Android、不碰数据库，所以每条规则分支都能在这里逐条钉死，
 * 不需要真机点一遍。
 *
 * 落库链路（「中止后已录轮次还在不在」）由 `ImpactV14WiringTest` 同款的装配测试负责，
 * 这里只保证**状态层**保留了历史 —— 两者叠起来才是「中途退出不丢数据」。
 */
class VersusRuleTest {

    private val names = listOf("玩家1", "玩家2")

    // ---------------------------------------------------------------- Bull 之争

    @Test
    fun `bull 之争 外牛1分 内牛2分`() {
        val state = BullBattleRule.newState(
            BattleConfig(modeKey = VersusModes.BULL_BATTLE, targetScore = 20, splitBull = true),
            names,
        )
        var s = BullBattleRule.onDart(state, BoardHit.OUTER_BULL).first
        assertEquals(1, s.players[0].score)
        s = BullBattleRule.onDart(s, BoardHit.INNER_BULL).first
        // 1（外牛）+ 2（内牛）：not 3 是巧合，这里钉的是「内外不同分」
        assertEquals(3, s.players[0].score)
    }

    @Test
    fun `bull 之争 不区分内外时都是1分`() {
        val state = BullBattleRule.newState(
            BattleConfig(modeKey = VersusModes.BULL_BATTLE, targetScore = 20, splitBull = false),
            names,
        )
        var s = BullBattleRule.onDart(state, BoardHit.INNER_BULL).first
        assertEquals(1, s.players[0].score)
        s = BullBattleRule.onDart(s, BoardHit.OUTER_BULL).first
        assertEquals(2, s.players[0].score)
    }

    @Test
    fun `bull 之争 非牛眼计0分`() {
        val state = BullBattleRule.newState(
            BattleConfig(modeKey = VersusModes.BULL_BATTLE, targetScore = 20, splitBull = true),
            names,
        )
        val s = BullBattleRule.onDart(state, BoardHit.triple(20)).first
        assertEquals(0, s.players[0].score)
    }

    @Test
    fun `bull 之争 先达目标分即胜`() {
        val state = BullBattleRule.newState(
            BattleConfig(modeKey = VersusModes.BULL_BATTLE, targetScore = 3, splitBull = true),
            names,
        )
        var s = BullBattleRule.onDart(state, BoardHit.INNER_BULL).first
        assertEquals(2, s.players[0].score)
        assertFalse("达到 2 分但目标 3 分，不应终局", s.finished)

        val (next, event) = BullBattleRule.onDart(s, BoardHit.INNER_BULL)
        assertTrue(next.finished)
        assertEquals(0, next.winnerIndex)
        assertEquals(BattleEndReason.NORMAL, next.endReason)
        assertTrue(event is DartEvent.Win)
    }

    // ---------------------------------------------------------------- 倍区竞赛

    @Test
    fun `倍区竞赛 只有目标分区计分`() {
        val state = RingRaceRule.newState(
            BattleConfig(modeKey = VersusModes.RING_RACE, targetSector = 20, targetScore = 20),
            names,
        )
        var s = RingRaceRule.onDart(state, BoardHit.triple(20)).first
        assertEquals(3, s.players[0].score)
        s = RingRaceRule.onDart(s, BoardHit.single(5)).first
        assertEquals("打错分区计 0 分", 3, s.players[0].score)
        s = RingRaceRule.onDart(s, BoardHit.double(16)).first
        assertEquals(3, s.players[0].score)
        assertTrue(s.isRoundComplete)
    }

    @Test
    fun `倍区竞赛 单双三倍分别记1到3分`() {
        val state = RingRaceRule.newState(
            BattleConfig(modeKey = VersusModes.RING_RACE, targetSector = 19, targetScore = 100),
            names,
        )
        var s = RingRaceRule.onDart(state, BoardHit.single(19)).first
        assertEquals(1, s.players[0].score)
        s = RingRaceRule.onDart(s, BoardHit.double(19)).first
        assertEquals(3, s.players[0].score)
        s = RingRaceRule.onDart(s, BoardHit.triple(19)).first
        assertEquals(6, s.players[0].score)
    }

    @Test
    fun `倍区竞赛 三倍独尊让分 只认三倍环`() {
        val config = BattleConfig(
            modeKey = VersusModes.RING_RACE,
            targetSector = 20,
            targetScore = 20,
            tripleOnlySeats = listOf(0),
        )
        val state = RingRaceRule.newState(config, names)
        assertTrue(config.handicapped(0))
        assertFalse(config.handicapped(1))

        var s = RingRaceRule.onDart(state, BoardHit.double(20)).first
        assertEquals("让分方打双倍环不计分", 0, s.players[0].score)
        s = RingRaceRule.onDart(s, BoardHit.triple(20)).first
        assertEquals(3, s.players[0].score)
    }

    // ---------------------------------------------------------------- 环游三镖

    @Test
    fun `环游三镖 核心规则 三镖全中才前进`() {
        val target = ClockTripleRule.ORDER.first()
        val state = ClockTripleRule.newState(
            BattleConfig(modeKey = VersusModes.CLOCK_TRIPLE, singleHitAdvance = false),
            names,
        )
        assertEquals(target, state.players[0].targetSector)
        assertEquals(0, state.players[0].step)

        var s = state
        repeat(VERSUS_DARTS_PER_ROUND) { s = ClockTripleRule.onDart(s, BoardHit.single(target)).first }
        assertTrue(s.isRoundComplete)
        assertEquals("核心规则下 onDart 不推进", 0, s.players[0].step)

        s = ClockTripleRule.onRoundEnd(s).first
        assertEquals(1, s.players[0].step)
        assertEquals(1, s.players[0].history.size)
        assertEquals(3, s.players[0].history[0].score)
        assertEquals(1, s.currentPlayerIndex)
    }

    @Test
    fun `环游三镖 核心规则 少中一镖不前进`() {
        val target = ClockTripleRule.ORDER.first()
        // 一定不是当前目标的另一个分区（靶盘上 1..20 里挑一个不等于 target 的）
        val other = ClockTripleRule.ORDER.first { it != target }
        val state = ClockTripleRule.newState(
            BattleConfig(modeKey = VersusModes.CLOCK_TRIPLE, singleHitAdvance = false),
            names,
        )
        var s = state
        repeat(2) { s = ClockTripleRule.onDart(s, BoardHit.single(target)).first }
        s = ClockTripleRule.onDart(s, BoardHit.single(other)).first
        assertTrue(s.isRoundComplete)

        s = ClockTripleRule.onRoundEnd(s).first
        assertEquals("3 镖只中 2 镖 ⇒ 不推进", 0, s.players[0].step)
        assertEquals(2, s.players[0].history[0].score)
    }

    @Test
    fun `环游三镖 经典版 一镖中即前进`() {
        val target = ClockTripleRule.ORDER.first()
        val state = ClockTripleRule.newState(
            BattleConfig(modeKey = VersusModes.CLOCK_TRIPLE, singleHitAdvance = true),
            names,
        )
        val (next, event) = ClockTripleRule.onDart(state, BoardHit.single(target))
        assertEquals(1, next.players[0].step)
        assertTrue(event is DartEvent.Advance)
    }

    @Test
    fun `环游三镖 让分 强者起点靠后`() {
        val state = ClockTripleRule.newState(
            BattleConfig(modeKey = VersusModes.CLOCK_TRIPLE, startSteps = listOf(0, 3)),
            names,
        )
        assertEquals(0, state.players[0].step)
        assertEquals(3, state.players[1].step)
        assertEquals(ClockTripleRule.ORDER[3], state.players[1].targetSector)
    }

    // ------------------------------------------- 2026-09-27 新开放：双倍环游 / 上海 / 减半

    @Test
    fun `双倍环游 中一镖走一格`() {
        val state = DoublesClockRule.newState(DoublesClockRule.defaultConfig(), names)
        assertEquals(1, state.players[0].targetSector)

        val (next, event) = DoublesClockRule.onDart(state, BoardHit(1, Ring.DOUBLE))
        assertEquals(2, next.players[0].targetSector)
        assertTrue(event is DartEvent.Advance)
    }

    @Test
    fun `双倍环游 单倍不算命中`() {
        // 与环游三镖最关键的差别：本模式只认**双倍环**，打中同一个分区的单倍不算。
        val state = DoublesClockRule.newState(DoublesClockRule.defaultConfig(), names)
        val next = DoublesClockRule.onDart(state, BoardHit.single(1)).first
        assertEquals(1, next.players[0].targetSector)
        assertEquals(0, next.players[0].step)
    }

    @Test
    fun `双倍环游 地狱模式 三镖同中才进`() {
        val state = DoublesClockRule.newState(
            DoublesClockRule.defaultConfig().copy(singleHitAdvance = false),
            names,
        )
        var s = state
        repeat(VERSUS_DARTS_PER_ROUND) { s = DoublesClockRule.onDart(s, BoardHit(1, Ring.DOUBLE)).first }
        assertEquals("地狱模式下 onDart 不推进", 0, s.players[0].step)

        s = DoublesClockRule.onRoundEnd(s).first
        assertEquals(1, s.players[0].step)
    }

    @Test
    fun `双倍环游 收尾Bull 命中牛眼即胜`() {
        // 让分到最后一格（D20），省掉 19 次推进
        val config = DoublesClockRule.defaultConfig()
            .copy(finishOnBull = true, startSteps = listOf(19, 0))
        val state = DoublesClockRule.newState(config, names)
        assertEquals(20, state.players[0].targetSector)

        var s = DoublesClockRule.onDart(state, BoardHit(20, Ring.DOUBLE)).first
        assertEquals(DoublesClockRule.BULL_STEP, s.players[0].targetSector)

        s = DoublesClockRule.onDart(s, BoardHit.INNER_BULL).first
        assertTrue(s.finished)
        assertEquals(0, s.winnerIndex)
    }

    @Test
    fun `上海 同轮SDT秒杀`() {
        val state = ShanghaiRule.newState(ShanghaiRule.defaultConfig(), names)
        var s = ShanghaiRule.onDart(state, BoardHit.single(1)).first
        s = ShanghaiRule.onDart(s, BoardHit(1, Ring.DOUBLE)).first
        s = ShanghaiRule.onDart(s, BoardHit(1, Ring.TRIPLE)).first
        // 秒杀在**换手结算**时判定（不是第三镖落下那一刻）：
        // 「同一轮」这个前提要等这一轮真的录满才成立，半轮就宣布获胜会让玩家无法反悔。
        val (win, event) = ShanghaiRule.onRoundEnd(s)

        assertTrue(win.finished)
        assertEquals(0, win.winnerIndex)
        assertEquals(BattleEndReason.SHANGHAI, win.endReason)
        assertTrue(event is DartEvent.Shanghai)
    }

    @Test
    fun `上海 打错分区记0分`() {
        val state = ShanghaiRule.newState(ShanghaiRule.defaultConfig(), names)
        // 第 1 轮打 1 分区；这里打 20 分区 —— 必须记 0 分，而不是「按命中算」
        val s = ShanghaiRule.onDart(state, BoardHit.triple(20)).first
        assertEquals(0, s.players[0].score)
    }

    @Test
    fun `减半 本轮全0分 总分减半`() {
        val config = HalveItRule.defaultConfig()
        var s = HalveItRule.newState(config, names)

        // 第 1 轮（20 分区）：三支单倍 20 = 60 分
        repeat(VERSUS_DARTS_PER_ROUND) { s = HalveItRule.onDart(s, BoardHit.single(20)).first }
        s = HalveItRule.onRoundEnd(s).first
        assertEquals(60, s.players[0].score)
        assertEquals(1, s.currentPlayerIndex)

        // 对手一轮全脱靶：0 的一半还是 0（不该变成负数或空）
        repeat(VERSUS_DARTS_PER_ROUND) { s = HalveItRule.onDart(s, BoardHit.MISS).first }
        s = HalveItRule.onRoundEnd(s).first
        assertEquals(0, s.players[1].score)
        assertEquals(0, s.currentPlayerIndex)

        // 我第 2 轮全脱靶 → 60 减半为 30
        repeat(VERSUS_DARTS_PER_ROUND) { s = HalveItRule.onDart(s, BoardHit.MISS).first }
        val (after, event) = HalveItRule.onRoundEnd(s)
        assertEquals(30, after.players[0].score)
        assertTrue(event is DartEvent.Halve)
    }

    // ---------------------------------------------------------------- 通用规则

    @Test
    fun `认输 判对手胜且标注RESIGN`() {
        val state = BullBattleRule.newState(
            BattleConfig(modeKey = VersusModes.BULL_BATTLE, targetScore = 50, splitBull = true),
            names,
        )
        val next = BullBattleRule.resign(state, 0)
        assertTrue(next.finished)
        assertEquals(1, next.winnerIndex)
        assertEquals(BattleEndReason.RESIGN, next.endReason)
    }

    @Test
    fun `中止 保留已录轮次`() {
        val state = BullBattleRule.newState(
            BattleConfig(modeKey = VersusModes.BULL_BATTLE, targetScore = 50, splitBull = true),
            names,
        )
        var s = state
        repeat(VERSUS_DARTS_PER_ROUND) { s = BullBattleRule.onDart(s, BoardHit.OUTER_BULL).first }
        s = BullBattleRule.onRoundEnd(s).first
        assertEquals(1, s.players[0].history.size)

        val aborted = BullBattleRule.abort(s)
        assertTrue(aborted.finished)
        assertNull("中止没有胜者", aborted.winnerIndex)
        assertEquals(BattleEndReason.ABORT, aborted.endReason)
        assertEquals("已录轮次必须保留", 1, aborted.players[0].history.size)
    }

    @Test
    fun `本轮录满后 多余的镖被忽略`() {
        val state = RingRaceRule.newState(
            BattleConfig(modeKey = VersusModes.RING_RACE, targetSector = 20, targetScore = 100),
            names,
        )
        var s = state
        repeat(VERSUS_DARTS_PER_ROUND) { s = RingRaceRule.onDart(s, BoardHit.triple(20)).first }
        assertEquals(9, s.players[0].score)

        val after = RingRaceRule.onDart(s, BoardHit.triple(20)).first
        assertEquals("第 4 镖不应被记入", VERSUS_DARTS_PER_ROUND, after.dartsInRound.size)
        assertEquals(9, after.players[0].score)
    }

    // ---------------------------------------------------------------- 注册表与配置

    @Test
    fun `未知玩法 返回null而不是抛异常`() {
        assertNull(VersusModes.ruleOf("not_a_mode"))
        assertNull(VersusModes.infoOf("not_a_mode"))
    }

    /**
     * 注册表：**凡是标了 `available` 的模式都必须有引擎**（2026-09-27 起是六个）。
     *
     * 此前这里写死三个 key —— 于是「把某张卡点亮」和「给它补引擎」成了两件独立的事，
     * 而它们其实是同一件事：卡片点亮 = 承诺这一局能打完。现在从 [VersusModes.ALL] 反查，
     * 谁被点亮却没有引擎，这里当场失败。
     */
    @Test
    fun `注册表 所有可用模式都能取到引擎`() {
        val available = VersusModes.ALL.filter { it.available }.map { it.modeKey }
        assertTrue("可用模式应随开卡而增长，当前：$available", available.size >= 6)
        available.forEach { key ->
            val rule = VersusModes.ruleOf(key)
            assertTrue("$key 已开卡却取不到引擎", rule != null)
            assertEquals(key, rule!!.modeKey)
        }
    }

    @Test
    fun `配置快照 同配置同文本 异配置异文本`() {
        val a = BattleConfig(
            modeKey = VersusModes.RING_RACE,
            targetSector = 19,
            targetScore = 30,
            tripleOnlySeats = listOf(1),
        )
        assertEquals(a.toStorageString(), a.copy().toStorageString())
        assertNotEquals(a.toStorageString(), a.copy(targetScore = 50).toStorageString())
        assertNotEquals(a.toStorageString(), a.copy(targetSector = 20).toStorageString())
        assertTrue(a.toStorageString().contains("mode=${VersusModes.RING_RACE}"))
    }

    @Test
    fun `目标分按席位取值 不同则各取各的`() {
        val same = BattleConfig(modeKey = VersusModes.BULL_BATTLE, targetScore = 20, targetScores = emptyList())
        assertEquals(20, same.targetFor(0))
        assertEquals(20, same.targetFor(1))
        // 双方同分 ⇒ 谁都不是「被让分」的一方（否则两个席位都会挂上让分标记，等于没说）。
        assertFalse(same.handicapped(0))
        assertFalse(same.handicapped(1))

        val handicap = BattleConfig(
            modeKey = VersusModes.BULL_BATTLE,
            targetScore = 20,
            targetScores = listOf(20, 10),
        )
        assertEquals(20, handicap.targetFor(0))
        assertEquals(10, handicap.targetFor(1))
        assertTrue("目标更低的一席才是被让分方", handicap.handicapped(1))
        assertFalse(handicap.handicapped(0))
    }
}
