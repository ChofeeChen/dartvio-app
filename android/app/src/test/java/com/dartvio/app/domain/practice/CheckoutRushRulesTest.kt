package com.dartvio.app.domain.practice

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.DartSource
import com.dartvio.app.domain.model.InMode
import com.dartvio.app.domain.model.OutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 极速结镖规则层的测试 —— 对应 PRD §十一验收的第 3~10 项。
 *
 * 这里断言的每一条结果都应该由 X01 逐镖规则推导出来，而不是本文件内部再算一遍：
 * 断言值写的是**期望的现实结果**（例如「40 分打 D20 就是一镖结镖」），
 * 因此如果将来有人在结算侧另起一套评分实现，这些用例会立刻红的。
 */
class CheckoutRushRulesTest {

    // ----------------------------------------------------------------------------------
    // PRD §十一.3-6：逐镖结果与 Bust 原因
    // ----------------------------------------------------------------------------------

    @Test
    fun `40 分投中 D20 判定为 Checkout 且用镖数为 1`() {
        val outcome = CheckoutRushRules.evaluate(40, listOf(Dart.double(20)))
        assertEquals(RushResult.CHECKOUT, outcome.result)
        assertEquals(1, outcome.dartsUsed)
        assertEquals(0, outcome.remainingAfter)
        assertNull(outcome.bustReason)
    }

    @Test
    fun `20 分投中 S20 归零但最后一镖不是双区判定为 Bust`() {
        val outcome = CheckoutRushRules.evaluate(20, listOf(Dart.single(20)))
        assertEquals(RushResult.BUST, outcome.result)
        assertEquals(BustReason.NOT_DOUBLE, outcome.bustReason)
        // 爆分按 X01 语义回滚到目标分 —— 不留下「0 分但没结掉」的假象。
        assertEquals(20, outcome.remainingAfter)
    }

    @Test
    fun `50 分投中 T20 超出剩余分判定为 Bust 且原因为超分`() {
        val outcome = CheckoutRushRules.evaluate(50, listOf(Dart.triple(20)))
        assertEquals(RushResult.BUST, outcome.result)
        assertEquals(BustReason.OVER, outcome.bustReason)
        assertEquals(50, outcome.remainingAfter)
    }

    @Test
    fun `20 分投中 S19 留下 1 分判定为 Bust`() {
        val outcome = CheckoutRushRules.evaluate(20, listOf(Dart.single(19)))
        assertEquals(RushResult.BUST, outcome.result)
        assertEquals(BustReason.LEFT_1, outcome.bustReason)
    }

    @Test
    fun `40 分先 S20 再 D10 两镖结镖`() {
        val outcome = CheckoutRushRules.evaluate(40, listOf(Dart.single(20), Dart.double(10)))
        assertEquals(RushResult.CHECKOUT, outcome.result)
        assertEquals(2, outcome.dartsUsed)
        assertEquals(0, outcome.remainingAfter)
    }

    // ----------------------------------------------------------------------------------
    // PRD §十一.7：已终局后不再接受镖
    // ----------------------------------------------------------------------------------

    @Test
    fun `两镖已经结镖时第三镖不被接受`() {
        val two = listOf(Dart.single(20), Dart.double(10))
        val three = two + Dart.single(5)
        val outcome = CheckoutRushRules.evaluate(40, three)
        assertEquals(RushResult.CHECKOUT, outcome.result)
        // 用镖数仍是 2：第 3 镖既不计分，也不改变已产生的结果。
        assertEquals(2, outcome.dartsUsed)
    }

    @Test
    fun `第一镖结镖即为终局`() {
        assertTrue(CheckoutRushRules.isTerminal(40, listOf(Dart.double(20))))
        assertTrue(CheckoutRushRules.isTerminal(20, listOf(Dart.single(20))))
        assertFalse(CheckoutRushRules.isTerminal(40, listOf(Dart.single(20))))
    }

    // ----------------------------------------------------------------------------------
    // PRD §十一.8：键盘与靶面点选两种输入必须落到同一种结果
    // ----------------------------------------------------------------------------------

    @Test
    fun `键盘逐镖与靶面点选产生相同的镖序结果一致`() {
        // 两条输入路径最终都收敛成同一份 `List<Dart>`；这里是刻意把「它们是同一种东西」钉成断言：
        // 只要 evaluate() 只认 List<Dart>，就不可能出现「同一种镖有两种结果」。
        val fromKeypad = listOf(Dart.triple(20), Dart.double(20)) // 100 = T20 + D20
        val fromBoardTap = listOf(Dart.triple(20), Dart.double(20))
        val a = CheckoutRushRules.evaluate(100, fromKeypad)
        val b = CheckoutRushRules.evaluate(100, fromBoardTap)
        assertEquals(a.result, b.result)
        assertEquals(a.remainingAfter, b.remainingAfter)
        assertEquals(a.dartsUsed, b.dartsUsed)
        assertEquals(RushResult.CHECKOUT, a.result)
        assertEquals(RushResult.CHECKOUT, b.result)
    }

    @Test
    fun `已录入的镖不被自动补全为 MISS`() {
        val outcome = CheckoutRushRules.evaluate(40, listOf(Dart.single(20)))
        assertEquals(1, outcome.darts.size)
        assertFalse(outcome.darts.any { it.isMiss })
        assertEquals(RushResult.NOT_FINISHED, outcome.result)
        assertEquals(20, outcome.remainingAfter)
    }

    // ----------------------------------------------------------------------------------
    // PRD §十一.9-10：不经 quick_total / checkoutDartCount 路径；场景配置固定
    // ----------------------------------------------------------------------------------

    @Test
    fun `MVP 只允许逐镖与靶面点选两种录入方式`() {
        assertTrue(CheckoutRushRules.SUPPORTED_INPUT_MODES.contains(DartSource.DART_BY_DART))
        assertTrue(CheckoutRushRules.SUPPORTED_INPUT_MODES.contains(DartSource.BOARD_TAP))
        assertFalse(CheckoutRushRules.SUPPORTED_INPUT_MODES.contains(DartSource.QUICK_TOTAL))
        assertEquals(2, CheckoutRushRules.SUPPORTED_INPUT_MODES.size)
    }

    @Test
    fun `单题配置固定为 Double Out 加标准牛眼且已开分`() {
        val config = CheckoutRushRules.rushConfig(100)
        assertEquals(OutMode.DOUBLE_OUT, config.outMode)
        // 「不作 Double In 首镖检查」= 用直入而不是新增标志位；等价于已开分。
        assertEquals(InMode.STRAIGHT_IN, config.inMode)
        assertEquals(100, config.targetScore)
        assertTrue(CheckoutRushRules.rushLegState(100).players.first().hasOpened)
    }

    @Test
    fun `第一镖不会因为未开分而被跳过`() {
        // 直入 + hasOpened=true ⇒ 第一镖必须正常计分（90 → 90 - 60 = 30）。
        val outcome = CheckoutRushRules.evaluate(90, listOf(Dart.triple(20)))
        assertEquals(30, outcome.remainingAfter)
        assertEquals(RushResult.NOT_FINISHED, outcome.result)
    }

    // ----------------------------------------------------------------------------------
    // 选题
    // ----------------------------------------------------------------------------------

    @Test
    fun `难度分类依据最短镖数与分数高低`() {
        assertEquals(RushDifficulty.ROOKIE, CheckoutTargetFactory.difficultyOf(40)) // D20 一镖
        assertEquals(RushDifficulty.ROOKIE, CheckoutTargetFactory.difficultyOf(2)) // D1 一镖
        assertEquals(RushDifficulty.ROOKIE, CheckoutTargetFactory.difficultyOf(50)) // 50 = Bull 一镖
        assertEquals(RushDifficulty.ADVANCED, CheckoutTargetFactory.difficultyOf(100)) // T20 + D20
        assertEquals(RushDifficulty.CHALLENGE, CheckoutTargetFactory.difficultyOf(170)) // T20 T20 BULL
        assertNull(CheckoutTargetFactory.difficultyOf(1)) // 无路线
    }

    @Test
    fun `出题结果一定带得动路线且落在合法难度`() {
        repeat(60) {
            val target = CheckoutTargetFactory.nextTarget(RushDifficulty.MIXED)
            assertTrue(target.score in 2..170)
            assertTrue("目标 ${target.score} 必须有路线", target.routes.isNotEmpty())
            assertNotNull(CheckoutTargetFactory.difficultyOf(target.score))
            assertTrue(target.preferredRoute.isNotEmpty())
            assertEquals(target.score, target.preferredRoute.sumOf { it.score })
        }
    }

    @Test
    fun `挑战档只出至少三镖且高于六十分的目标`() {
        repeat(20) {
            val target = CheckoutTargetFactory.nextTarget(RushDifficulty.CHALLENGE)
            assertEquals(RushDifficulty.CHALLENGE, CheckoutTargetFactory.difficultyOf(target.score))
            assertTrue(target.score > 60)
            assertTrue(target.preferredRoute.size >= 3)
        }
    }

    @Test
    fun `十题会话尽量不重复出题`() {
        val recent = mutableSetOf<Int>()
        repeat(10) {
            val target = CheckoutTargetFactory.nextTarget(RushDifficulty.MIXED, recent)
            assertTrue("重复出题：$target", target.score !in recent)
            recent.add(target.score)
        }
    }

    @Test
    fun `某一档题库被抽完时允许重复而不是抛错`() {
        val used = CheckoutTargetFactory.candidates()
            .filter { CheckoutTargetFactory.difficultyOf(it) == RushDifficulty.CHALLENGE }
            .toSet()
        val target = CheckoutTargetFactory.nextTarget(RushDifficulty.CHALLENGE, used)
        assertTrue(used.contains(target.score))
    }
}
