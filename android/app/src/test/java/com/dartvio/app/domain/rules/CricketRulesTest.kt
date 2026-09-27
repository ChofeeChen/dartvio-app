package com.dartvio.app.domain.rules

import com.dartvio.app.domain.model.ClaimedDart
import com.dartvio.app.domain.model.CricketHitResult
import com.dartvio.app.domain.model.CricketMarks
import com.dartvio.app.domain.model.CricketPlayerState
import com.dartvio.app.domain.model.CricketTarget
import com.dartvio.app.domain.model.CricketVariant
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.DartClaim
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.Player
import com.dartvio.app.domain.model.TargetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cricket 规则引擎测试。覆盖 PRD M2 的标记、关闭、计分与获胜，以及二期 2C 的
 * 归属裁决（§4.9.2）、死区豁免（§4.9.3③）、Overkill（§4.9.9）与轮数上限（§4.9.8）。
 *
 * 2C 纪律：`claim = NUMBER` + `overkillEnabled = false` 时，一期全部断言必须逐条不变
 * （P1① 行为零变更）—— 所以「一期用例」的写法只做了签名适配，期望值一个没动。
 */
class CricketRulesTest {

    private val players = listOf(
        Player(id = "p1", name = "A"),
        Player(id = "p2", name = "B")
    )

    private fun newLeg() = CricketRules.newLeg(MatchConfig.CRICKET, players, 1)

    /** 一期签名的适配糖：把裸镖包成缺省裁决（NUMBER）。 */
    private fun darts(vararg d: Dart): List<ClaimedDart> = d.map { ClaimedDart(it) }

    private fun claimed(dart: Dart, claim: DartClaim) = ClaimedDart(dart, claim)

    /**
     * 默认目标集的号位。
     *
     * 断言用号位、而不是把 `CricketTarget.Number(20)` 写满全篇：这些用例校验的是**玩法数值**
     * （几标记、几分），号位写法更贴近板面。标识本身的行为另有 `CricketTargetTest` 覆盖。
     */
    private val defaultNumbers: List<Int>
        get() = MatchConfig.CRICKET.cricketTargets.map { (it as CricketTarget.Number).value }

    private fun target(number: Int): CricketTarget = CricketTarget.Number(number)

    @Test
    fun `三倍20得3标记`() {
        val leg = newLeg()
        val (state, hits, _) = CricketRules.applyTurn(leg, darts(Dart.triple(20)))
        assertEquals(3, state.players[0].marks.marksOf(target(20)))
        assertTrue(state.players[0].marks.isClosed(target(20)))
        assertEquals(3, hits.first().marksGained)
    }

    @Test
    fun `单倍20得1标记`() {
        val leg = newLeg()
        val (state, _, _) = CricketRules.applyTurn(leg, darts(Dart.single(20)))
        assertEquals(1, state.players[0].marks.marksOf(target(20)))
        assertFalse(state.players[0].marks.isClosed(target(20)))
    }

    @Test
    fun `关闭后再击中可得分`() {
        val leg = newLeg()
        // 第一回合关闭 20
        val (s1, _, _) = CricketRules.applyTurn(leg, darts(Dart.triple(20)))
        // 切到 p2，投 miss
        val (s2, _, _) = CricketRules.applyTurn(s1, darts(Dart.MISS))
        // p1 再打 T20：已关闭，对手未关闭 -> 得 60 分
        val (s3, hits, _) = CricketRules.applyTurn(s2, darts(Dart.triple(20)))
        assertEquals(60, s3.players[0].score)
        assertEquals(60, hits.first().scoreGained)
    }

    @Test
    fun `对手关闭后不能再得分`() {
        val leg = newLeg()
        // p1 关闭 20
        val (s1, _, _) = CricketRules.applyTurn(leg, darts(Dart.triple(20)))
        // p2 也关闭 20
        val (s2, _, _) = CricketRules.applyTurn(s1, darts(Dart.triple(20)))
        // p1 再打 T20：对手已关闭 -> 不得分
        val (s3, hits, _) = CricketRules.applyTurn(s2, darts(Dart.triple(20)))
        assertEquals(0, s3.players[0].score)
        assertEquals(0, hits.first().scoreGained)
    }

    @Test
    fun `Inner Bull得2标记`() {
        val leg = newLeg()
        val (state, _, _) = CricketRules.applyTurn(leg, darts(Dart.INNER_BULL))
        assertEquals(2, state.players[0].marks.marksOf(CricketTarget.BULL))
    }

    @Test
    fun `关闭所有分数且分数不低于对手时获胜`() {
        val leg = newLeg()
        val numbers = defaultNumbers
        // 让 p1 逐步关闭所有分数（每回合 3 支 T）
        var state = leg
        for (n in numbers) {
            val (s, _, _) = CricketRules.applyTurn(state, darts(Dart.triple(n)))
            state = s
            if (n != numbers.last()) {
                // 切换回 p1：让 p2 miss
                val (s2, _, _) = CricketRules.applyTurn(state, darts(Dart.MISS))
                state = s2
            }
        }
        assertTrue(state.isFinished)
        assertEquals(0, state.winnerIndex)
    }

    @Test
    fun `无效分数不产生标记`() {
        val leg = newLeg()
        val (state, hits, _) = CricketRules.applyTurn(leg, darts(Dart.triple(1)))
        assertTrue(hits.isEmpty())
        assertEquals(0, state.players[0].marks.marksOf(target(1)))
    }

    @Test
    fun `目标集之外的分区即使合法也不记标记`() {
        // 2C 会裁剪目标集（tactics / random）。规则层判「在不在目标集里」，
        // 而不是判「是不是 15-20 或 Bull」—— 否则裁剪后的局会照旧记分。
        val config = MatchConfig.CRICKET.copy(
            cricketTargets = listOf(CricketTarget.Number(20), CricketTarget.Number(19))
        )
        val leg = CricketRules.newLeg(config, players, 1)

        val (state, hits, _) = CricketRules.applyTurn(leg, darts(Dart.triple(18)))

        assertTrue("18 不在目标集里，不该产生结算明细", hits.isEmpty())
        assertEquals(0, state.players[0].marks.marksOf(CricketTarget.Number(18)))
    }

    // ------------------------------------------------- M2 §4.8 玩法变体

    private fun newLeg(variant: CricketVariant) =
        CricketRules.newLeg(MatchConfig.CRICKET.copy(cricketVariant = variant), players, 1)

    /** 造一个「已关满全部分区」的玩家，用于直接验证 [CricketRules.isWinningLeg]。 */
    private fun closedAll(id: String, score: Int, turnsPlayed: Int = 0): CricketPlayerState =
        CricketPlayerState(
            playerId = id,
            marks = MatchConfig.CRICKET.cricketTargets.fold(CricketMarks()) { m, t -> m.add(t, 3) },
            score = score,
            turnsPlayed = turnsPlayed,
        )

    @Test
    fun `no_score 任何命中都不计分 双方分数恒为 0`() {
        val leg = newLeg(CricketVariant.NO_SCORE)
        val (s1, _, _) = CricketRules.applyTurn(leg, darts(Dart.triple(20)))   // p1 关 20
        val (s2, _, _) = CricketRules.applyTurn(s1, darts(Dart.MISS))          // p2 miss
        val (s3, hits, _) = CricketRules.applyTurn(s2, darts(Dart.triple(20))) // p1 再打 T20

        assertEquals(0, s3.players[0].score)
        assertEquals(0, s3.players[1].score)
        assertEquals(0, hits.first().scoreGained)
        assertTrue(hits.first().scoreOwnerIds.isEmpty())
        assertFalse(hits.first().isScoredForOpponent("p1"))
    }

    @Test
    fun `cut_throat 得分记到对手账上 投掷者自己仍是 0`() {
        val leg = newLeg(CricketVariant.CUT_THROAT)
        val (s1, _, _) = CricketRules.applyTurn(leg, darts(Dart.triple(20)))   // p1 关 20
        val (s2, _, _) = CricketRules.applyTurn(s1, darts(Dart.MISS))          // p2 miss
        val (s3, hits, _) = CricketRules.applyTurn(s2, darts(Dart.triple(20))) // p1 再打 T20

        assertEquals(0, s3.players[0].score)
        assertEquals(60, s3.players[1].score)
        assertEquals(60, hits.first().scoreGained)
        assertEquals(listOf("p2"), hits.first().scoreOwnerIds)
        assertTrue(hits.first().isScoredForOpponent("p1"))
    }

    @Test
    fun `standard 关满后分数不低于所有对手才获胜`() {
        val config = MatchConfig.CRICKET
        val me = closedAll("p1", score = 40)

        assertTrue(CricketRules.isWinningLeg(listOf(me, CricketPlayerState("p2", score = 30)), 0, config))
        // 等号也算胜（高分领先，含等号）
        assertTrue(CricketRules.isWinningLeg(listOf(me, CricketPlayerState("p2", score = 40)), 0, config))
        assertFalse(CricketRules.isWinningLeg(listOf(me, CricketPlayerState("p2", score = 50)), 0, config))
    }

    @Test
    fun `no_score 关满即胜 完全不读分数`() {
        val config = MatchConfig.CRICKET.copy(cricketVariant = CricketVariant.NO_SCORE)
        val me = closedAll("p1", score = 0)

        assertTrue(CricketRules.isWinningLeg(listOf(me, CricketPlayerState("p2", score = 999)), 0, config))
    }

    @Test
    fun `cut_throat 低分领先 分数不高于所有对手才获胜`() {
        val config = MatchConfig.CRICKET.copy(cricketVariant = CricketVariant.CUT_THROAT)
        val me = closedAll("p1", score = 20)

        assertTrue(CricketRules.isWinningLeg(listOf(me, CricketPlayerState("p2", score = 40)), 0, config))
        // 含等号
        assertTrue(CricketRules.isWinningLeg(listOf(me, CricketPlayerState("p2", score = 20)), 0, config))
        assertFalse(CricketRules.isWinningLeg(listOf(me, CricketPlayerState("p2", score = 10)), 0, config))
    }

    @Test
    fun `cut_throat 三人局必须低过每一个对手才算领先`() {
        val config = MatchConfig.CRICKET.copy(cricketVariant = CricketVariant.CUT_THROAT)
        val me = closedAll("p1", score = 20)
        val low = CricketPlayerState("p2", score = 10)
        val high = CricketPlayerState("p3", score = 30)

        // 只要还有一个对手比自己低，就不算领先 —— 否则多人局会被判成同时获胜。
        assertFalse(CricketRules.isWinningLeg(listOf(me, low, high), 0, config))
        assertTrue(CricketRules.isWinningLeg(listOf(me, high, high.copy(playerId = "p4")), 0, config))
    }

    @Test
    fun `未关满全部分区时任何变体都不算获胜`() {
        CricketVariant.entries.forEach { variant ->
            val config = MatchConfig.CRICKET.copy(cricketVariant = variant)
            val notClosed = CricketPlayerState("p1", score = 0)
            assertFalse(
                "变体 $variant 未关满分区却判胜",
                CricketRules.isWinningLeg(listOf(notClosed, CricketPlayerState("p2")), 0, config),
            )
        }
    }

    // ============================================ 二期 2C（M2 §4.9）

    /** Tactics 局：9 档目标集（20→15 + 双倍档 + 三倍档 + Bull），变体仍是 STANDARD（Q7）。 */
    private val tacticsConfig = MatchConfig.CRICKET.copy(cricketTargets = CricketTarget.TACTICS_TARGETS)

    private val doubleCategory = CricketTarget.Category(TargetCategory.DOUBLES)
    private val tripleCategory = CricketTarget.Category(TargetCategory.TRIPLES)

    private fun tacticsLeg() = CricketRules.newLeg(tacticsConfig, players, 1)

    private fun player(
        id: String,
        marks: Map<CricketTarget, Int> = emptyMap(),
        score: Int = 0,
        turnsPlayed: Int = 0,
    ): CricketPlayerState = CricketPlayerState(
        playerId = id,
        marks = marks.entries.fold(CricketMarks()) { m, (t, c) -> m.add(t, c) },
        score = score,
        turnsPlayed = turnsPlayed,
    )

    /** 「已关满这些目标」的标记表糖。 */
    private fun closed(vararg t: CricketTarget): Map<CricketTarget, Int> = t.associateWith { 3 }

    /** 直接打规则层：默认走 Tactics 配置与它自己的 Overkill 开关。 */
    private fun eval(
        dart: Dart,
        claim: DartClaim,
        shooter: CricketPlayerState,
        opponents: List<CricketPlayerState>,
        config: MatchConfig = tacticsConfig,
        overkill: Boolean = config.overkillEnabled,
    ): CricketHitResult? = CricketRules.evaluateDart(
        dart, claim, overkill, shooter, opponents, config.cricketTargets, config.cricketVariant,
    )

    // ------------------------------------------- N1–N12 结算判据表（§8.4）

    @Test
    fun `N1 记数字时已关号位按面值得分`() {
        val shooter = player("p1", closed(CricketTarget.Number(20)))
        val hit = eval(Dart.triple(20), DartClaim.NUMBER, shooter, listOf(player("p2")))

        assertNotNull(hit)
        assertEquals(CricketTarget.Number(20), hit!!.target)
        assertEquals(0, hit.marksGained)
        assertEquals(60, hit.scoreGained)
        assertFalse(hit.deadZoneExemptionApplied)
    }

    @Test
    fun `N2 记三倍档只加一个标记且不产分`() {
        val hit = eval(Dart.triple(20), DartClaim.CATEGORY, player("p1"), listOf(player("p2")))

        assertNotNull(hit)
        assertEquals(tripleCategory, hit!!.target)
        assertEquals(1, hit.marksGained)
        assertEquals(0, hit.scoreGained)
    }

    @Test
    fun `N3 死区豁免 号位已全员关闭仍按倍数得分`() {
        val shooter = player("p1", closed(CricketTarget.Number(19), tripleCategory))
        val opponent = player("p2", closed(CricketTarget.Number(19)))
        val hit = eval(Dart.triple(19), DartClaim.NUMBER, shooter, listOf(opponent))

        assertNotNull(hit)
        assertEquals(0, hit!!.marksGained)
        assertEquals(57, hit.scoreGained)
        assertTrue(hit.deadZoneExemptionApplied)
    }

    @Test
    fun `N4 本方未关对应类别档时死区不豁免`() {
        val shooter = player("p1", closed(CricketTarget.Number(19)))
        val opponent = player("p2", closed(CricketTarget.Number(19)))
        val hit = eval(Dart.triple(19), DartClaim.NUMBER, shooter, listOf(opponent))

        assertNotNull(hit)
        assertEquals(0, hit!!.scoreGained)
        assertFalse(hit.deadZoneExemptionApplied)
    }

    @Test
    fun `N5 类别档已全员关闭时死区不豁免`() {
        val shooter = player("p1", closed(CricketTarget.Number(19), tripleCategory))
        val opponent = player("p2", closed(CricketTarget.Number(19), tripleCategory))
        val hit = eval(Dart.triple(19), DartClaim.NUMBER, shooter, listOf(opponent))

        assertNotNull(hit)
        assertEquals(0, hit!!.scoreGained)
        assertFalse(hit.deadZoneExemptionApplied)
    }

    @Test
    fun `N6 本方未关号位时照常补关且不产分`() {
        val shooter = player("p1")
        val opponent = player("p2", closed(CricketTarget.Number(19)))
        val hit = eval(Dart.triple(19), DartClaim.NUMBER, shooter, listOf(opponent))

        assertNotNull(hit)
        assertEquals(3, hit!!.marksGained)
        assertEquals(0, hit.scoreGained)
    }

    @Test
    fun `N7 双方都未关时记录标记`() {
        val hit = eval(Dart.triple(19), DartClaim.NUMBER, player("p1"), listOf(player("p2")))

        assertNotNull(hit)
        assertEquals(3, hit!!.marksGained)
        assertEquals(0, hit.scoreGained)
    }

    @Test
    fun `N8 类别档已关闭时二选一也不产分`() {
        val hit = eval(Dart.triple(19), DartClaim.CATEGORY, player("p1", closed(tripleCategory)), listOf(player("p2")))

        assertNotNull(hit)
        assertEquals(0, hit!!.marksGained)
        assertEquals(0, hit.scoreGained)
    }

    @Test
    fun `N9 死区豁免对双倍档对称生效`() {
        val shooter = player("p1", closed(CricketTarget.Number(19), doubleCategory))
        val opponent = player("p2", closed(CricketTarget.Number(19)))
        val hit = eval(Dart.double(19), DartClaim.NUMBER, shooter, listOf(opponent))

        assertNotNull(hit)
        assertEquals(0, hit!!.marksGained)
        assertEquals(38, hit.scoreGained)
        assertTrue(hit.deadZoneExemptionApplied)
    }

    @Test
    fun `N10 单倍镖裁决为类别档时回落数字不吞镖`() {
        val hit = eval(Dart.single(20), DartClaim.CATEGORY, player("p1"), listOf(player("p2")))

        assertNotNull(hit)
        assertEquals(CricketTarget.Number(20), hit!!.target)
        assertEquals(1, hit.marksGained)
        assertEquals(0, hit.scoreGained)
    }

    @Test
    fun `N11 目标集外的号位裁决为类别档仍是未命中`() {
        assertNull(eval(Dart.double(3), DartClaim.CATEGORY, player("p1"), listOf(player("p2"))))
    }

    @Test
    fun `N12 红心裁决为类别档时回落数字 不计入双倍档`() {
        val hit = eval(Dart.INNER_BULL, DartClaim.CATEGORY, player("p1"), listOf(player("p2")))

        assertNotNull(hit)
        assertEquals(CricketTarget.BULL, hit!!.target)
        assertEquals(2, hit.marksGained)
    }

    // ----------------------------------------------------------- 类别档标记

    @Test
    fun `三倍镖记类别档只加一个标记 不会一镖关闭`() {
        // 红线 §10.2：类别裁决的 marksToAdd 必须恒为 1，写成 dart.multiplier 会一镖关掉三倍档。
        val (state, _) = CricketRules.applySingleDart(
            tacticsLeg(), claimed(Dart.triple(20), DartClaim.CATEGORY)
        )

        assertEquals(1, state.players[0].marks.marksOf(tripleCategory))
        assertFalse(state.players[0].marks.isClosed(tripleCategory))
        // 也不该顺手在数字 20 上记标记。
        assertEquals(0, state.players[0].marks.marksOf(CricketTarget.Number(20)))
    }

    // ------------------------------------------------------ 改判（§4.9.2⑤）

    @Test
    fun `改判后与一开始就那么打完全一致`() {
        val base = tacticsLeg()
        val (asNumber, _) = CricketRules.applySingleDart(base, claimed(Dart.triple(20), DartClaim.NUMBER))

        val (redecided, hits) = CricketRules.redeclare(asNumber, 0, DartClaim.CATEGORY)
        val (direct, directHit) = CricketRules.applySingleDart(base, claimed(Dart.triple(20), DartClaim.CATEGORY))

        assertEquals(direct.players, redecided.players)
        assertEquals(directHit, hits.single())
        assertEquals(1, redecided.players[0].marks.marksOf(tripleCategory))
        assertEquals(0, redecided.players[0].marks.marksOf(CricketTarget.Number(20)))
    }

    @Test
    fun `改判会重放整个回合序列`() {
        val base = tacticsLeg()
        val (s1, _) = CricketRules.applySingleDart(base, claimed(Dart.triple(20), DartClaim.NUMBER))
        val (s2, _) = CricketRules.applySingleDart(s1, claimed(Dart.triple(19), DartClaim.NUMBER))

        // 把第 1 镖改记三倍档：第 2 镖仍记数字，且必须在「20 未关」的新基线上重算。
        val (replayed, hits) = CricketRules.redeclare(s2, 0, DartClaim.CATEGORY)

        assertEquals(2, hits.size)
        assertEquals(0, replayed.players[0].marks.marksOf(CricketTarget.Number(20)))
        assertEquals(1, replayed.players[0].marks.marksOf(tripleCategory))
        assertEquals(3, replayed.players[0].marks.marksOf(CricketTarget.Number(19)))
    }

    // ------------------------------------------------------ Overkill（§4.9.9）

    @Test
    fun `Overkill 开启且领先 200 分时只加标记不计分`() {
        val shooter = player("p1", marks = mapOf(CricketTarget.Number(20) to 2), score = 300)
        val opponent = player("p2", score = 50)

        val hit = eval(Dart.triple(20), DartClaim.NUMBER, shooter, listOf(opponent), overkill = true)

        assertNotNull(hit)
        assertEquals(1, hit!!.marksGained)
        assertEquals(0, hit.scoreGained)
        assertTrue(hit.scoreSuppressedByOverkill)
    }

    @Test
    fun `Overkill 领先不足 200 分照常计分`() {
        val shooter = player("p1", marks = mapOf(CricketTarget.Number(20) to 2), score = 249)
        val opponent = player("p2", score = 50)

        val hit = eval(Dart.triple(20), DartClaim.NUMBER, shooter, listOf(opponent), overkill = true)

        assertNotNull(hit)
        assertEquals(40, hit!!.scoreGained)
        assertFalse(hit.scoreSuppressedByOverkill)
    }

    @Test
    fun `Overkill 缺省关闭时行为与一期一致`() {
        val shooter = player("p1", marks = mapOf(CricketTarget.Number(20) to 2), score = 300)
        val opponent = player("p2", score = 50)

        // 不显式传 overkill ⇒ 取 config.overkillEnabled = false（缺省）。
        val hit = eval(Dart.triple(20), DartClaim.NUMBER, shooter, listOf(opponent))

        assertNotNull(hit)
        assertEquals(40, hit!!.scoreGained)
        assertFalse(hit.scoreSuppressedByOverkill)
    }

    @Test
    fun `Overkill 不作用于 cut_throat`() {
        val config = MatchConfig.CRICKET.copy(
            cricketVariant = CricketVariant.CUT_THROAT,
            overkillEnabled = true,
        )
        val shooter = player("p1", marks = mapOf(CricketTarget.Number(20) to 2), score = 300)
        val opponent = player("p2", score = 50)

        val hit = eval(Dart.triple(20), DartClaim.NUMBER, shooter, listOf(opponent), config = config)

        assertNotNull(hit)
        assertEquals(40, hit!!.scoreGained)
        assertEquals(listOf("p2"), hit.scoreOwnerIds)
        assertFalse(hit.scoreSuppressedByOverkill)
    }

    // ------------------------------------------------ 轮数上限终局（§4.9.8）

    @Test
    fun `轮数上限打满且无人获胜时总分最高者胜`() {
        val config = MatchConfig.CRICKET.copy(maxRounds = 3)
        val leg = CricketRules.newLeg(config, players, 1).copy(
            players = listOf(
                player("p1", score = 100, turnsPlayed = 2),
                player("p2", score = 50, turnsPlayed = 3),
            )
        )

        // p1 打完这一回合后双方都满 3 轮 → 超时终局。
        val (state, _, won) = CricketRules.applyTurn(leg, darts(Dart.MISS))

        assertTrue(won)
        assertTrue(state.isFinished)
        assertEquals(0, state.winnerIndex)
        assertTrue("超时终局必须落 endedByRoundLimit", state.endedByRoundLimit)
    }

    @Test
    fun `未全员打满轮数时不判超时`() {
        val config = MatchConfig.CRICKET.copy(maxRounds = 3)
        val leg = CricketRules.newLeg(config, players, 1).copy(
            players = listOf(
                player("p1", score = 100, turnsPlayed = 2),
                player("p2", score = 50, turnsPlayed = 1),
            )
        )

        val (state, _, won) = CricketRules.applyTurn(leg, darts(Dart.MISS))

        assertFalse(won)
        assertFalse(state.endedByRoundLimit)
    }

    @Test
    fun `maxRounds 为 0 时没有超时终局`() {
        // 缺省 0 = 无上限：即使两边回合数极大也不能凭空判胜（一期行为零变更）。
        val leg = newLeg().copy(
            players = listOf(
                player("p1", score = 100, turnsPlayed = 999),
                player("p2", score = 50, turnsPlayed = 999),
            )
        )

        val (_, _, won) = CricketRules.applyTurn(leg, darts(Dart.MISS))

        assertFalse(won)
    }

    @Test
    fun `有人正常关满时超时终局不接管`() {
        val config = MatchConfig.CRICKET.copy(maxRounds = 3)
        val leg = CricketRules.newLeg(config, players, 1).copy(
            players = listOf(
                closedAll("p1", score = 100, turnsPlayed = 3),
                player("p2", score = 50, turnsPlayed = 3),
            )
        )

        val (state, _, won) = CricketRules.applyTurn(leg, darts(Dart.MISS))

        assertTrue(won)
        // 这是常规获胜，不是超时 —— 落库标记不能被误置。
        assertFalse(state.endedByRoundLimit)
    }

    @Test
    fun `roundLimitReached 要求全员打满`() {
        val config = MatchConfig.CRICKET.copy(maxRounds = 2)
        val leg = CricketRules.newLeg(config, players, 1).copy(
            players = listOf(
                player("p1", turnsPlayed = 2),
                player("p2", turnsPlayed = 1),
            )
        )

        assertFalse(leg.roundLimitReached)
        assertTrue(leg.copy(players = leg.players.map { it.copy(turnsPlayed = 2) }).roundLimitReached)
    }

    // ------------------------------------------------------------ 回合计数

    @Test
    fun `endTurn 把回合数计到当前玩家头上`() {
        val ended = CricketRules.endTurn(newLeg())

        assertEquals(1, ended.players[0].turnsPlayed)
        assertEquals(0, ended.players[1].turnsPlayed)
        assertTrue(ended.currentTurnDarts.isEmpty())
        assertNull(ended.turnStartPlayers)
    }

    @Test
    fun `整回合结算会推进回合计数`() {
        val (state, _, _) = CricketRules.applyTurn(newLeg(), darts(Dart.MISS))

        assertEquals(1, state.players[0].turnsPlayed)
        assertEquals(0, state.players[1].turnsPlayed)
    }
}
