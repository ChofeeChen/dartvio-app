package com.dartvio.app.domain.rules

import com.dartvio.app.domain.model.BullMode
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.InMode
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.domain.model.Player
import com.dartvio.app.domain.model.TurnResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * X01 规则引擎测试。覆盖 PRD M2 的扣分、Bust、三组规则档位（牛眼/开局/结束）与最多轮数。
 */
class X01RulesTest {

    private val players = listOf(
        Player(id = "p1", name = "A"),
        Player(id = "p2", name = "B")
    )

    private fun newLeg(target: Int = 501, outMode: OutMode = OutMode.DOUBLE_OUT) =
        X01Rules.newLeg(
            MatchConfig(targetScore = target, outMode = outMode),
            players,
            legNumber = 1
        )

    @Test
    fun `三支镖正常扣分`() {
        val leg = newLeg()
        val (state, outcome) = X01Rules.applyTurn(
            leg,
            listOf(Dart.triple(20), Dart.single(20), Dart.single(20))
        )
        assertEquals(60 + 20 + 20, outcome.scored)
        assertEquals(501 - 100, state.players[0].remaining)
        assertEquals(TurnResult.COMPLETE, outcome.result)
        assertEquals(1, state.currentPlayerIndex)
    }

    @Test
    fun `超过剩余分判为Bust并回滚`() {
        val leg = newLeg(target = 40)
        val (state, outcome) = X01Rules.applyTurn(leg, listOf(Dart.triple(20)))
        assertEquals(TurnResult.BUST, outcome.result)
        assertEquals(40, state.players[0].remaining)
        assertEquals(0, outcome.scored)
    }

    @Test
    fun `DoubleOut时投前剩余2分打出2分单倍判为Bust`() {
        val leg = newLeg(target = 2)
        // 2 - 2 = 0，但为单倍收尾 -> Bust（Double-Out 要求）
        val (_, outcome) = X01Rules.applyTurn(leg, listOf(Dart.single(2)))
        assertEquals(TurnResult.BUST, outcome.result)
    }

    @Test
    fun `DoubleOut时剩余1分任何命中都会爆分`() {
        val leg = newLeg(target = 61)
        // 61 - 60 = 1 -> 剩余 1，Double-Out 下为 Bust
        val (_, outcome) = X01Rules.applyTurn(leg, listOf(Dart.triple(20)))
        assertEquals(TurnResult.BUST, outcome.result)
    }

    @Test
    fun `双倍收尾后不再需要第三镖`() {
        val leg = newLeg(target = 40)
        val (state, outcome) = X01Rules.applyTurn(leg, listOf(Dart.double(20)))
        assertTrue(outcome.won)
        assertTrue(state.isFinished)
    }

    @Test
    fun `DoubleOut时以单倍收尾判为Bust`() {
        val leg = newLeg(target = 21)
        val (state, outcome) = X01Rules.applyTurn(leg, listOf(Dart.single(20), Dart.single(1)))
        assertEquals(TurnResult.BUST, outcome.result)
        assertEquals(21, state.players[0].remaining)
    }

    @Test
    fun `DoubleOut时以双倍收尾获胜`() {
        val leg = newLeg(target = 40)
        val (state, outcome) = X01Rules.applyTurn(leg, listOf(Dart.double(20)))
        assertTrue(outcome.won)
        assertEquals(0, state.players[0].remaining)
        assertTrue(state.isFinished)
        assertEquals(0, state.winnerIndex)
    }

    @Test
    fun `InnerBull可作为双倍收尾`() {
        val leg = newLeg(target = 50)
        val (_, outcome) = X01Rules.applyTurn(leg, listOf(Dart.INNER_BULL))
        assertTrue(outcome.won)
    }

    @Test
    fun `直出时单倍可直接收尾`() {
        val leg = newLeg(target = 20, outMode = OutMode.STRAIGHT_OUT)
        val (_, outcome) = X01Rules.applyTurn(leg, listOf(Dart.single(20)))
        assertTrue(outcome.won)
    }

    // ===== 结束规则：大师出（Master Out）=====

    @Test
    fun `大师出时三倍收尾获胜`() {
        val leg = X01Rules.newLeg(
            MatchConfig(targetScore = 60, outMode = OutMode.MASTER_OUT),
            players,
            legNumber = 1
        )
        val (state, outcome) = X01Rules.applyTurn(leg, listOf(Dart.triple(20)))
        assertTrue("大师出允许三倍收尾", outcome.won)
        assertEquals(0, state.players[0].remaining)
    }

    @Test
    fun `大师出时单倍收尾判为Bust`() {
        val leg = X01Rules.newLeg(
            MatchConfig(targetScore = 20, outMode = OutMode.MASTER_OUT),
            players,
            legNumber = 1
        )
        val (_, outcome) = X01Rules.applyTurn(leg, listOf(Dart.single(20)))
        assertEquals(TurnResult.BUST, outcome.result)
    }

    // ===== 开局规则：双倍入 / 大师入 =====

    @Test
    fun `双倍入未开镖时单倍不计分但换手`() {
        val leg = X01Rules.newLeg(
            MatchConfig(targetScore = 501, inMode = InMode.DOUBLE_IN),
            players,
            legNumber = 1
        )
        val (state, outcome) = X01Rules.applyTurn(leg, listOf(Dart.single(20)))
        assertEquals(0, outcome.scored)
        assertEquals(501, state.players[0].remaining)
        assertFalse("未开镖前单倍不计分", state.players[0].hasOpened)
        assertEquals(1, state.currentPlayerIndex)
    }

    @Test
    fun `双倍入命中双倍后开始计分`() {
        val leg = X01Rules.newLeg(
            MatchConfig(targetScore = 501, inMode = InMode.DOUBLE_IN),
            players,
            legNumber = 1
        )
        val (state, outcome) = X01Rules.applyTurn(
            leg,
            listOf(Dart.single(20), Dart.double(20), Dart.single(20))
        )
        // 第一支单倍不计，第二支 D20 开镖（40），第三支 S20 计分
        assertEquals(60, outcome.scored)
        assertEquals(501 - 60, state.players[0].remaining)
        assertTrue(state.players[0].hasOpened)
    }

    @Test
    fun `大师入时三倍可开镖而单倍不可`() {
        val leg = X01Rules.newLeg(
            MatchConfig(targetScore = 501, inMode = InMode.MASTER_IN),
            players,
            legNumber = 1
        )
        val (unopened, _) = X01Rules.applyTurn(leg, listOf(Dart.single(20)))
        assertFalse("大师入下单倍不能开镖", unopened.players[0].hasOpened)

        val (opened, outcome) = X01Rules.applyTurn(leg, listOf(Dart.triple(20)))
        assertTrue("大师入下三倍可以开镖", opened.players[0].hasOpened)
        assertEquals(60, outcome.scored)
    }

    // ===== 牛眼规则：50/50 =====

    @Test
    fun `5050牛眼规则下外牛眼按50分计`() {
        val leg = X01Rules.newLeg(
            MatchConfig(targetScore = 501, inMode = InMode.STRAIGHT_IN, bullMode = BullMode.BULL_50_50),
            players,
            legNumber = 1
        )
        val (state, outcome) = X01Rules.applyTurn(leg, listOf(Dart.OUTER_BULL))
        assertEquals("外牛眼在 50/50 下计 50 分", 50, outcome.scored)
        assertEquals(451, state.players[0].remaining)
    }

    @Test
    fun `标准牛眼规则下外牛眼仍按25分计`() {
        val leg = newLeg(target = 501)
        val (_, outcome) = X01Rules.applyTurn(leg, listOf(Dart.OUTER_BULL))
        assertEquals(25, outcome.scored)
    }

    // ===== 最多轮数（Max Rounds）=====

    @Test
    fun `打满最多轮数后剩余分最低者获胜`() {
        // 2 人局、最多 1 轮：每人各投一次后即终局。
        val leg = X01Rules.newLeg(
            MatchConfig(targetScore = 501, maxRounds = 1, outMode = OutMode.STRAIGHT_OUT),
            players,
            legNumber = 1
        )
        val (afterFirst, firstOutcome) = X01Rules.applyTurn(leg, listOf(Dart.triple(20)))
        assertFalse("第一位投完还没到轮数上限", firstOutcome.won)

        val (state, outcome) = X01Rules.applyTurn(afterFirst, listOf(Dart.single(20)))
        assertTrue("两人各投一轮后终局", outcome.won)
        assertTrue(outcome.endedByRoundLimit)
        assertEquals(0, state.winnerIndex)
        assertEquals("MAX ROUNDS", outcome.message)
    }

    @Test
    fun `未设置最多轮数时不会因轮次终局`() {
        val leg = X01Rules.newLeg(
            MatchConfig(targetScore = 501, maxRounds = 0, outMode = OutMode.STRAIGHT_OUT),
            players,
            legNumber = 1
        )
        val (_, outcome) = X01Rules.applyTurn(leg, listOf(Dart.single(20)))
        assertFalse(outcome.won)
        assertFalse(outcome.endedByRoundLimit)
    }

    // ===== 档位解析的宽容性（落库 / 协议共用）=====

    @Test
    fun `未知档位字符串宽容回落缺省档而不抛异常`() {
        assertEquals(OutMode.STRAIGHT_OUT, OutMode.fromKey("MASTER_OUT_V2"))
        assertEquals(OutMode.STRAIGHT_OUT, OutMode.fromKey(null))
        assertEquals(InMode.STRAIGHT_IN, InMode.fromKey("???"))
        assertEquals(BullMode.STANDARD_25_50, BullMode.fromKey("BULL_70_30"))
        // 认识的值必须原样返回，否则宽容就变成了「永远忽略用户选择」。
        assertEquals(OutMode.MASTER_OUT, OutMode.fromKey("MASTER_OUT"))
        assertEquals(InMode.MASTER_IN, InMode.fromKey("MASTER_IN"))
        assertEquals(BullMode.BULL_50_50, BullMode.fromKey("BULL_50_50"))
    }

    @Test
    fun `MISS不计分`() {
        val leg = newLeg()
        val (_, outcome) = X01Rules.applyTurn(leg, listOf(Dart.MISS, Dart.MISS, Dart.MISS))
        assertEquals(0, outcome.scored)
        assertEquals(TurnResult.NO_SCORE, outcome.result)
    }

    @Test
    fun `爆分后回合切换`() {
        val leg = newLeg(target = 40)
        val (state, _) = X01Rules.applyTurn(leg, listOf(Dart.triple(20)))
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(40, state.players[0].remaining)
        assertFalse(state.isFinished)
    }
}
