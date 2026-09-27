package com.dartvio.app.domain.practice

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.DartSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 极速结镖会话口径的测试 —— 对应 PRD §十一验收的第 11~13 项。
 *
 * 三条必须永远成立：
 * ① 看过提示的成绩不得刷新**无提示**个人最佳；
 * ② SKIPPED / ABORTED 不进成功率分母；
 * ③ 这条学习/训练数据不写 `match_records`（这里由「会话统计只认 AttemptRecord」在结构上保证，
 *    仓库层不接触对局表 —— 见 `CheckoutRushRepository` 的注释）。
 */
class CheckoutRushSessionTest {

    private fun attempt(
        id: Long = 0,
        target: Int = 40,
        result: RushResult = RushResult.CHECKOUT,
        throwMs: Long = 3_000,
        inputMs: Long = 2_000,
        darts: List<Dart> = listOf(Dart.double(20)),
        hinted: Boolean = false,
        difficulty: RushDifficulty = RushDifficulty.MIXED,
        invalidated: Boolean = false,
        source: DartSource = DartSource.DART_BY_DART,
        retryOf: Long = 0,
        bustReason: BustReason? = null,
    ) = RushAttemptRecord(
        id = id,
        sessionId = "s1",
        createdAt = id,
        target = target,
        difficulty = difficulty,
        darts = darts,
        inputMode = source,
        throwElapsedMs = throwMs,
        inputElapsedMs = inputMs,
        routeHintUsed = hinted,
        result = result,
        remainingAfter = if (result == RushResult.CHECKOUT) 0 else target,
        bustReason = bustReason,
        retryOfAttemptId = retryOf,
        timingInvalidated = invalidated,
    )

    // ----------------------------------------------------------------------------------
    // PRD §十一.12：跳过不计入成功率分母
    // ----------------------------------------------------------------------------------

    @Test
    fun `跳过的题不进成功率分母`() {
        val records = listOf(
            attempt(id = 1),
            attempt(id = 2, result = RushResult.BUST),
            attempt(id = 3, result = RushResult.SKIPPED),
            attempt(id = 4, result = RushResult.ABORTED),
        )
        val stats = CheckoutRushStatistics.of(records)
        assertEquals(2, stats.scoredAttempts)
        assertEquals(50, stats.successRatePct)
        assertEquals(1, stats.skippedCount)
        assertEquals(1, stats.abortedCount)
    }

    @Test
    fun `跳过 streak 会打断连击`() {
        val records = listOf(
            attempt(id = 1),
            attempt(id = 2),
            attempt(id = 3, result = RushResult.SKIPPED),
            attempt(id = 4),
        )
        assertEquals(2, CheckoutRushStatistics.of(records).longestSuccessStreak)
    }

    // ----------------------------------------------------------------------------------
    // PRD §十一.11：有提示 / 无提示分开聚合
    // ----------------------------------------------------------------------------------

    @Test
    fun `有提示与无提示的成功率分开统计`() {
        val records = listOf(
            attempt(id = 1, result = RushResult.CHECKOUT, hinted = true),
            attempt(id = 2, result = RushResult.CHECKOUT, hinted = true),
            attempt(id = 3, result = RushResult.CHECKOUT, hinted = false),
            attempt(id = 4, result = RushResult.BUST, hinted = false),
        )
        val stats = CheckoutRushStatistics.of(records)
        assertEquals(4, stats.scoredAttempts)
        assertEquals(75, stats.successRatePct)
        assertEquals(2, stats.unhintedAttempts)
        assertEquals(1, stats.unhintedSuccessCount)
        assertEquals(50, stats.unhintedSuccessRatePct)
        assertEquals(2, stats.hintedAttempts)
        assertEquals(100, stats.hintedSuccessRatePct)
    }

    @Test
    fun `看过提示的成绩不刷新无提示个人最佳`() {
        val history = listOf(
            attempt(id = 1, difficulty = RushDifficulty.ROOKIE, throwMs = 900, hinted = true),
            attempt(id = 2, difficulty = RushDifficulty.ROOKIE, throwMs = 5_000, hinted = false),
        )
        // 真实的无提示最佳是 5000ms；那次 900ms 是辅助成绩，不得把最佳刷成 900ms。
        assertEquals(5_000L, bestUnhintedThrowMs(history, RushDifficulty.ROOKIE))
    }

    @Test
    fun `切后台失效的成绩不参与个人最佳`() {
        val history = listOf(
            attempt(id = 1, difficulty = RushDifficulty.ROOKIE, throwMs = 100, invalidated = true),
            attempt(id = 2, difficulty = RushDifficulty.ROOKIE, throwMs = 4_000),
        )
        assertEquals(4_000L, bestUnhintedThrowMs(history, RushDifficulty.ROOKIE))
    }

    @Test
    fun `个人最佳按难度分开`() {
        val history = listOf(
            attempt(id = 1, difficulty = RushDifficulty.ROOKIE, throwMs = 1_000),
            attempt(id = 2, difficulty = RushDifficulty.CHALLENGE, throwMs = 9_000),
        )
        assertEquals(1_000L, bestUnhintedThrowMs(history, RushDifficulty.ROOKIE))
        assertEquals(9_000L, bestUnhintedThrowMs(history, RushDifficulty.CHALLENGE))
        assertNull(bestUnhintedThrowMs(history, RushDifficulty.ADVANCED))
    }

    @Test
    fun `没有记录时统计退化为零值`() {
        val stats = CheckoutRushStatistics.of(emptyList())
        assertEquals(0, stats.scoredAttempts)
        assertEquals(0, stats.successRatePct)
        assertNull(stats.avgThrowMs)
        assertNull(stats.mostFailedTarget)
    }

    // ----------------------------------------------------------------------------------
    // 分布类口径
    // ----------------------------------------------------------------------------------

    @Test
    fun `用镖分布只统计成功的题`() {
        val records = listOf(
            attempt(id = 1, darts = listOf(Dart.double(20))),
            attempt(id = 2, darts = listOf(Dart.single(20), Dart.double(10))),
            attempt(id = 3, result = RushResult.NOT_FINISHED, darts = listOf(Dart.single(5), Dart.single(5), Dart.single(5))),
        )
        val distribution = CheckoutRushStatistics.of(records).checkoutDartDistribution
        assertEquals(1, distribution[1])
        assertEquals(1, distribution[2])
        assertNull(distribution[3])
    }

    @Test
    fun `中位与均值都排除跳过与中断`() {
        val records = listOf(
            attempt(id = 1, throwMs = 1_000),
            attempt(id = 2, throwMs = 3_000),
            attempt(id = 3, throwMs = 5_000),
            attempt(id = 4, throwMs = 999_999, result = RushResult.SKIPPED),
        )
        val stats = CheckoutRushStatistics.of(records)
        assertEquals(3_000L, stats.avgThrowMs)
        assertEquals(3_000L, stats.medianThrowMs)
    }

    @Test
    fun `爆分原因分布与最常失败目标`() {
        val records = listOf(
            attempt(id = 1, result = RushResult.BUST, bustReason = BustReason.OVER, target = 100),
            attempt(id = 2, result = RushResult.BUST, bustReason = BustReason.OVER, target = 100),
            attempt(id = 3, result = RushResult.BUST, bustReason = BustReason.LEFT_1, target = 20),
            attempt(id = 4, result = RushResult.CHECKOUT, target = 40),
        )
        val stats = CheckoutRushStatistics.of(records)
        assertEquals(3, stats.bustCount)
        assertEquals(2, stats.bustReasons[BustReason.OVER])
        assertEquals(1, stats.bustReasons[BustReason.LEFT_1])
        assertEquals(100, stats.mostFailedTarget)
    }

    @Test
    fun `重试保留与原记录的关联且不再算新分母`() {
        val records = listOf(
            attempt(id = 7, result = RushResult.NOT_FINISHED, target = 100),
            attempt(id = 8, result = RushResult.NOT_FINISHED, target = 100, retryOf = 7),
        )
        assertEquals(7, records.last().retryOfAttemptId)
        // 重试是一条新的尝试记录：不重复计数原记录，但也不把它从分母里抹掉。
        assertEquals(2, CheckoutRushStatistics.of(records).scoredAttempts)
    }

    @Test
    fun `录入用时仅作交互分析且按来源区分`() {
        val records = listOf(
            attempt(id = 1, inputMs = 1_000, source = DartSource.DART_BY_DART),
            attempt(id = 2, inputMs = 3_000, source = DartSource.BOARD_TAP),
        )
        val stats = CheckoutRushStatistics.of(records)
        assertEquals(2_000L, stats.avgInputMs)
        assertEquals(1_000L, stats.avgInputMsBySource[DartSource.DART_BY_DART])
        assertEquals(3_000L, stats.avgInputMsBySource[DartSource.BOARD_TAP])
    }

    @Test
    fun `跳过与中断不算成功`() {
        assertFalse(RushResult.SKIPPED.countsTowardsSuccessRate)
        assertFalse(RushResult.ABORTED.countsTowardsSuccessRate)
        assertTrue(RushResult.CHECKOUT.countsTowardsSuccessRate)
        assertTrue(RushResult.BUST.countsTowardsSuccessRate)
        assertTrue(RushResult.NOT_FINISHED.countsTowardsSuccessRate)
    }
}
