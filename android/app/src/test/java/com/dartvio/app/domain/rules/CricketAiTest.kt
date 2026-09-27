package com.dartvio.app.domain.rules

import com.dartvio.app.domain.model.AiDifficulty
import com.dartvio.app.domain.model.ClaimedDart
import com.dartvio.app.domain.model.CricketLegState
import com.dartvio.app.domain.model.CricketPlayerState
import com.dartvio.app.domain.model.CricketMarks
import com.dartvio.app.domain.model.CricketTarget
import com.dartvio.app.domain.model.CricketVariant
import com.dartvio.app.domain.model.DartClaim
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.Player
import com.dartvio.app.domain.model.TargetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Cricket AI 出镖测试（M4 §6.6）。
 *
 * 重点是那条「兜底不得硬编码 20」：目标集被裁剪后（2C 的 tactics / random，或任何非默认集），
 * 20 可能根本不在集内 —— 硬编码会直接产出**目标集之外**的镖，规则层当未命中丢弃，
 * AI 于是每回合空转。这类缺陷在一期看不出来（集恒为 7 分区），必须靠裁剪后的用例钉住。
 */
class CricketAiTest {

    private val players = listOf(
        Player(id = "p1", name = "AI"),
        Player(id = "p2", name = "B"),
    )

    /** 恒定值的随机源：用来把「命中 / 未命中」两条分支各自钉死，不靠 seed 碰运气。 */
    private class FixedDouble(private val value: Double) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = value
    }

    private val alwaysHit = FixedDouble(0.0)
    private val alwaysMiss = FixedDouble(1.0)

    /**
     * 二期 2C：`generateTurn` 的产物从裸 [com.dartvio.app.domain.model.Dart] 变成带归属裁决的
     * [ClaimedDart]。这些用例校验的是**落点**，所以剥一层看裸镖；裁决本身另有规则层用例覆盖。
     */
    private fun List<ClaimedDart>.numbers(): List<Int> = map { it.dart.number }
    private fun List<ClaimedDart>.multipliers(): List<Int> = map { it.dart.multiplier }

    private fun legWith(config: MatchConfig, aiClosed: List<CricketTarget>): CricketLegState {
        val leg = CricketRules.newLeg(config, players, 1)
        return leg.copy(
            players = leg.players.mapIndexed { index, p ->
                if (index != 0) p else p.copy(
                    marks = aiClosed.fold(p.marks) { m, t -> m.add(t, 3) }
                )
            }
        )
    }

    // ===== 兜底：目标集之外的值一个都不许出现（§3.3 指定用例）=====

    @Test
    fun `目标集不含 20 时 AI 兜底不会出 20`() {
        // AI 已关满全部目标集 → 走到兜底；no_score 下没有对手分支，必然命中兜底值。
        // 兜底若写死 20，这三支镖就全打在目标集之外（规则层当未命中丢弃）。
        val config = MatchConfig.CRICKET.copy(
            cricketTargets = listOf(CricketTarget.Number(17), CricketTarget.Number(16)),
            cricketVariant = CricketVariant.NO_SCORE,
        )
        val leg = legWith(config, listOf(CricketTarget.Number(17), CricketTarget.Number(16)))

        val darts = CricketAi.generateTurn(leg, AiDifficulty.INTERMEDIATE, alwaysHit)

        assertEquals(3, darts.size)
        assertTrue(
            "兜底值必须取自目标集，实际出镖：${darts.numbers()}",
            darts.all { it.dart.number in setOf(16, 17) }
        )
    }

    @Test
    fun `目标集不含 20 时未命中分支同样不会出 20`() {
        // 未命中会在「有效落点池」里随机 —— 这个池子同样必须来自目标集。
        val config = MatchConfig.CRICKET.copy(
            cricketTargets = listOf(CricketTarget.Number(17), CricketTarget.Number(16)),
            cricketVariant = CricketVariant.NO_SCORE,
        )
        val leg = legWith(config, emptyList())

        val darts = CricketAi.generateTurn(leg, AiDifficulty.INTERMEDIATE, alwaysMiss)

        assertTrue(
            "随机落点必须来自目标集，实际出镖：${darts.numbers()}",
            darts.all { it.dart.number in setOf(16, 17) }
        )
    }

    @Test
    fun `standard 下对手全关时兜底同样落在目标集内`() {
        val config = MatchConfig.CRICKET.copy(
            cricketTargets = listOf(CricketTarget.Number(18), CricketTarget.Number(17)),
        )
        val leg = legWith(
            config,
            listOf(CricketTarget.Number(18), CricketTarget.Number(17))
        ).let { l ->
            // 对手也关满 → 没有「对手未关的最高分区」可打，落到兜底。
            l.copy(
                players = l.players.map { p ->
                    p.copy(
                        marks = listOf(CricketTarget.Number(18), CricketTarget.Number(17))
                            .fold(p.marks) { m, t -> m.add(t, 3) }
                    )
                }
            )
        }

        val darts = CricketAi.generateTurn(leg, AiDifficulty.INTERMEDIATE, alwaysHit)

        assertTrue(darts.all { it.dart.number in setOf(17, 18) })
    }

    // ===== 目标集语义 =====

    @Test
    fun `默认目标集优先关闭自己最高的未关分区`() {
        val leg = legWith(MatchConfig.CRICKET, listOf(CricketTarget.Number(20)))

        val darts = CricketAi.generateTurn(leg, AiDifficulty.INTERMEDIATE, alwaysHit)

        // 20 已关 → 下一个最高的是 19；命中打三倍区
        assertEquals(listOf(19, 19, 19), darts.numbers())
        assertEquals(listOf(3, 3, 3), darts.multipliers())
    }

    @Test
    fun `Bull 命中降为双倍`() {
        // 目标集只剩 Bull：非 Bull 的候选全空，兜底只能回 Bull；
        // 而 Bull 没有三倍区，必须降成双倍而不是发出 Dart(25, 3) 这种非法镖。
        val config = MatchConfig.CRICKET.copy(
            cricketTargets = listOf(CricketTarget.BULL),
            cricketVariant = CricketVariant.NO_SCORE,
        )
        val leg = legWith(config, emptyList())

        val darts = CricketAi.generateTurn(leg, AiDifficulty.INTERMEDIATE, alwaysHit)

        assertEquals(listOf(25, 25, 25), darts.numbers())
        assertEquals(listOf(2, 2, 2), darts.multipliers())
    }

    @Test
    fun `类别档不参与出镖选择`() {
        // 2C 未解禁：「类别档怎么出镖」的口径尚未冻结（提示词 §5.1），
        // 所以 2A 只从数字目标里选点，不发任何含义未定的镖。
        val config = MatchConfig.CRICKET.copy(
            cricketTargets = listOf(CricketTarget.Category(TargetCategory.TRIPLES), CricketTarget.Number(19)),
        )
        val leg = legWith(config, emptyList())

        val darts = CricketAi.generateTurn(leg, AiDifficulty.INTERMEDIATE, alwaysHit)

        assertEquals(listOf(19, 19, 19), darts.numbers())
    }

    @Test
    fun `已关满时也不会因空候选抛异常`() {
        // 空目标集是解析层的兜底要避免的形态，但生成器本身也不该在这里崩。
        val config = MatchConfig.CRICKET.copy(cricketTargets = emptyList())
        val leg = legWith(config, emptyList())

        val darts = CricketAi.generateTurn(leg, AiDifficulty.INTERMEDIATE, alwaysHit)

        assertEquals(3, darts.size)
    }

    // ===== 二期 2C：归属裁决（§4.9.2⑥）与 Overkill 感知（§8.6.3）=====

    /** 双方各自的已关目标与分数都可控的局，用于裁决 / Overkill 用例。 */
    private fun legWithMarks(
        config: MatchConfig,
        aiClosed: List<CricketTarget>,
        aiScore: Int = 0,
        oppClosed: List<CricketTarget> = emptyList(),
        oppScore: Int = 0,
    ): CricketLegState {
        val leg = CricketRules.newLeg(config, players, 1)
        fun apply(p: CricketPlayerState, closed: List<CricketTarget>, score: Int) = p.copy(
            marks = closed.fold(p.marks) { m, t -> m.add(t, 3) },
            score = score,
        )
        return leg.copy(
            players = listOf(
                apply(leg.players[0], aiClosed, aiScore),
                apply(leg.players[1], oppClosed, oppScore),
            )
        )
    }

    private val tactics = MatchConfig.CRICKET.copy(cricketTargets = CricketTarget.TACTICS_TARGETS)

    /** Tactics 目标集里的**数字**（不含双倍 / 三倍档）—— 裁决用例要的「数字全关」。 */
    private val tacticsNumbers = CricketTarget.TACTICS_TARGETS.filterIsInstance<CricketTarget.Number>()

    @Test
    fun `Tactics 局本方数字目标全关后 AI 把镖改记类别档`() {
        // 数字全关 ⇒ 打数字只会在死区里空刷；此时该把 Z20 转记三倍档（+1 有效标记）。
        val leg = legWithMarks(tactics, aiClosed = tacticsNumbers)

        val darts = CricketAi.generateTurn(leg, AiDifficulty.INTERMEDIATE, alwaysHit)

        assertEquals(3, darts.size)
        assertTrue("数字目标全关时应当改记类别档", darts.all { it.claim == DartClaim.CATEGORY })
    }

    @Test
    fun `对应类别档已关时 AI 不做无意义改判`() {
        // 三倍档也关满了，再「改记三倍档」拿不到任何标记 ⇒ 必须回落数字，不得静默吞镖。
        val leg = legWithMarks(
            tactics,
            aiClosed = tacticsNumbers + CricketTarget.Category(TargetCategory.TRIPLES),
        )

        val darts = CricketAi.generateTurn(leg, AiDifficulty.INTERMEDIATE, alwaysHit)

        assertTrue("无效改判必须回落数字", darts.all { it.claim == DartClaim.NUMBER })
    }

    @Test
    fun `Overkill 领先时不再去刷必然被抑制的对手分区`() {
        // 两个局只差 Overkill 开关：关闭时会去打对手未关的 19（刷分），
        // 开启且领先 ≥200 时该分必然被压掉 ⇒ 退回合法落点 20，而不是产出「必然 0 分」的选点。
        val withoutOverkill = legWithMarks(
            tactics,
            aiClosed = tacticsNumbers,
            aiScore = 300,
            oppClosed = listOf(CricketTarget.Number(20)),
        )
        val withOverkill = withoutOverkill.copy(
            config = tactics.copy(overkillEnabled = true),
        )

        assertEquals(
            listOf(19, 19, 19),
            CricketAi.generateTurn(withoutOverkill, AiDifficulty.INTERMEDIATE, alwaysHit).numbers(),
        )
        assertEquals(
            listOf(20, 20, 20),
            CricketAi.generateTurn(withOverkill, AiDifficulty.INTERMEDIATE, alwaysHit).numbers(),
        )
    }

    @Test
    fun `标记表以目标位为键`() {
        // 顺带钉住 CricketMarks 的 key 语义：号位数字不再能当键用。
        val marks = CricketMarks().add(CricketTarget.Number(20), 3)

        assertTrue(marks.isClosed(CricketTarget.Number(20)))
        assertTrue(!marks.isClosed(CricketTarget.BULL))
    }
}
