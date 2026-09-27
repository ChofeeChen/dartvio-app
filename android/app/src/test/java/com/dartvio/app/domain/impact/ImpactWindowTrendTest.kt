package com.dartvio.app.domain.impact

import com.dartvio.app.domain.vision.BoardGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * 窗口档位换算 + 轮间趋势判定的纯 JVM 单测（无 Android 依赖）。
 *
 * 对应验收 A-IMP-02（档位换算由几何推出）/ A-IMP-10（趋势不显著不写「改善」）/
 * A-IMP-11（出框只进计数）/ A-IMP-12（命中率升而 R95 未动要单独给文案）。
 */
class ImpactWindowTrendTest {

    // ---------------------------------------------------------------- 窗口档位

    @Test
    fun `BULL 窗口是方形且等于档位`() {
        assertEquals(60.0, ImpactWindow.spanX(IntentTarget.BULL, 60.0), 1e-9)
        assertEquals(60.0, ImpactWindow.spanY(60.0), 1e-9)
        assertEquals(120.0, ImpactWindow.spanY(120.0), 1e-9)
    }

    @Test
    fun `外层目标的切向跨度覆盖左右各一个邻居且不浪费窗口`() {
        val target = IntentTarget.triple(20)
        // 弧宽 = 2·r·sin(9°)：T20 半径取三倍环中线，故半径来自 BoardGeometry 而非字面量。
        val radius = (BoardGeometry.TRIPLE_INNER_RADIUS_MM + BoardGeometry.TRIPLE_OUTER_RADIUS_MM) / 2.0
        val arcWidth = 2.0 * radius * sin(Math.toRadians(BoardGeometry.SECTOR_ANGLE_DEG / 2.0))
        val spanX = ImpactWindow.spanX(target, ImpactWindow.STANDARD_SPAN_MM)

        // 至少覆盖「左邻居 + 目标 + 右邻居」三个分区。
        assertTrue(spanX >= 3 * arcWidth - 1e-9)
        // 但不超过四个分区，否则等于没放大。
        assertTrue(spanX < 4 * arcWidth)

        // 半径越大同样 18° 张角对应的弧越长 ⇒ 双倍环的窗口必须比三倍环宽。
        assertTrue(ImpactWindow.spanX(IntentTarget.double(20), 60.0) > spanX)
    }

    @Test
    fun `只有两档且宽松档是标准档的两倍`() {
        assertEquals(
            listOf(ImpactWindow.STANDARD_SPAN_MM, ImpactWindow.WIDE_SPAN_MM),
            ImpactWindow.SPAN_PRESETS
        )
        assertEquals(2.0, ImpactWindow.WIDE_SPAN_MM / ImpactWindow.STANDARD_SPAN_MM, 1e-9)
        assertTrue(ImpactWindow.labelOf(ImpactWindow.WIDE_SPAN_MM).contains("宽松"))
        assertTrue(ImpactWindow.labelOf(ImpactWindow.STANDARD_SPAN_MM).contains("标准"))
    }

    @Test
    fun `视口中心就是瞄点锚点`() {
        IntentTarget.PRIMARY_CHIPS.forEach { target ->
            val anchor = target.anchorMm()
            val viewport = ImpactWindow.viewportOf(target, ImpactWindow.STANDARD_SPAN_MM)
            assertEquals(anchor.x, viewport.centerXMm, 1e-9)
            assertEquals(anchor.y, viewport.centerYMm, 1e-9)
            assertEquals(ImpactWindow.STANDARD_SPAN_MM, viewport.spanYMm, 1e-9)
            assertEquals(
                ImpactWindow.spanX(target, ImpactWindow.STANDARD_SPAN_MM),
                viewport.spanXMm,
                1e-9
            )
        }
    }

    // ---------------------------------------------------------------- 按 session 聚合为轮

    @Test
    fun `按 session 聚合为轮且按时间升序并忽略空 sessionId`() {
        val samples = listOf(
            sample("b", 300L, 4.0),
            sample("a", 100L, 4.0),
            sample("", 200L, 4.0),
            sample("a", 150L, 4.0)
        )
        val rounds = ImpactTrend.rounds(samples)
        assertEquals(listOf("a", "b"), rounds.map { it.sessionId })
        assertEquals(100L, rounds.first().firstHitAt)
        assertEquals(2, rounds.first().dartCount)
    }

    @Test
    fun `出框镖只进计数不进统计`() {
        val samples = windowRound("s", 100L, offsetMm = 5.0, dartCount = 12, hitCount = 6) +
            List(3) { ImpactSample("s", 100L + it, frame = null, hit = false) }
        val round = ImpactTrend.rounds(samples).single()

        assertEquals(15, round.dartCount)
        assertEquals(12, round.stats?.n)
        assertEquals(6, round.hitCount)
        // 分母是全部录入（含出框），否则「出框多」会被算成命中率高。
        assertEquals(6.0 / 15.0, round.hitRate, 1e-9)
    }

    // ---------------------------------------------------------------- 趋势判定

    @Test
    fun `有效轮不足时不给结论`() {
        val rounds = ImpactTrend.rounds(
            (1..4).flatMap { windowRound("s$it", it * 1000L, offsetMm = 6.0, dartCount = 12) }
        )
        val trend = ImpactTrend.compare(rounds)
        assertEquals(TrendDirection.NOT_ENOUGH, trend.direction)
        assertTrue(trend.summary.contains("还需要 1 轮"))
    }

    @Test
    fun `综合偏离显著变小才写改善`() {
        // 此前 3 轮 rmse ≈ 18/19/20，最近 3 轮 ≈ 4/5/6：差 14 mm，远大于噪声阈值。
        val rounds = ImpactTrend.rounds(
            listOf(18.0, 19.0, 20.0).mapIndexed { i, rmse ->
                windowRound("old$i", 1000L + i, offsetMm = rmse)
            }.flatten() +
                listOf(4.0, 5.0, 6.0).mapIndexed { i, rmse ->
                    windowRound("new$i", 5000L + i, offsetMm = rmse)
                }.flatten()
        )
        val trend = ImpactTrend.compare(rounds)

        assertEquals(TrendDirection.IMPROVED, trend.direction)
        assertNotNull(trend.previousRmse)
        assertNotNull(trend.recentRmse)
        assertTrue(trend.previousRmse!! > trend.recentRmse!!)
        assertTrue(trend.summary.contains("改善是真的"))
        assertTrue(trend.nextAction.contains("收窄"))
    }

    @Test
    fun `差异落在噪声内不写改善`() {
        // 10.0 → 10.5 mm，轮间方差为 0 ⇒ 连阈值都算不出来，只能说「还说不上改善」。
        val rounds = ImpactTrend.rounds(
            (1..3).flatMap { windowRound("old$it", it * 1000L, offsetMm = 10.0) } +
                (1..3).flatMap { windowRound("new$it", 10_000L + it * 1000L, offsetMm = 10.5) }
        )
        val trend = ImpactTrend.compare(rounds)

        assertEquals(TrendDirection.NOISE, trend.direction)
        assertTrue(trend.summary.contains("没超过噪声阈值"))
        assertFalse(trend.summary.contains("改善是真的"))
    }

    @Test
    fun `命中率升而散布未动时点明是瞄点修正`() {
        // 六轮的 rmse / r95 逐位相同（散布没动），只有命中率从 50% 涨到 ≈98%。
        val rounds = ImpactTrend.rounds(
            (1..3).flatMap { windowRound("old$it", it * 1000L, offsetMm = 10.0, hitCount = 10) } +
                listOf(20, 20, 19).mapIndexed { i, hit ->
                    windowRound("new$i", 10_000L + i * 1000L, offsetMm = 10.0, hitCount = hit)
                }.flatten()
        )
        val trend = ImpactTrend.compare(rounds)

        assertEquals(TrendDirection.NOISE, trend.direction)
        assertTrue(trend.summary.contains("瞄点修正"))
        assertFalse(trend.summary.contains("改善是真的"))
    }

    @Test
    fun `显著性判据在组数不足时不成立`() {
        // 每侧只有 1 轮样本 ⇒ 没有轮间方差，任何差异都算不出来。
        assertNull(ImpactTrend.thresholdOf(sigma1 = 0.0, n1 = 1, n2 = 1, sigma2 = 0.0))
        assertFalse(ImpactTrend.isSignificant(50.0, 1.0, 1, 1, 1.0))
        // 方差为 0 时不写结论（se = 0 是「无法判断」，不是「显著」）。
        assertFalse(ImpactTrend.isSignificant(50.0, 0.0, 3, 3, 0.0))
        // 正常情形：差 2 mm、每侧 σ=0.5、n=3 ⇒ 阈值 ≈ 0.8 mm ⇒ 显著。
        val threshold = ImpactTrend.thresholdOf(0.5, 3, 3, 0.5)!!
        assertTrue(threshold < 1.0)
        assertTrue(ImpactTrend.isSignificant(2.0, 0.5, 3, 3, 0.5))
    }

    // ---------------------------------------------------------------- 构造工具

    private fun sample(session: String, at: Long, offsetMm: Double): ImpactSample =
        ImpactSample(session, at, ImpactFrame(offsetMm, 0.0), hit = false)

    /**
     * 一轮全部落在窗内的样本：径向偏移 [offsetMm]、切向 ±0.6 抖动。
     *
     * 抖动模式在**轮内**固定，所以同一 [offsetMm] 的两轮 rmse 逐位相同 ⇒ 轮间方差为 0；
     * 不同 [offsetMm] 之间才有轮间方差。趋势测试正是靠这一点区分「有变化」与「没变化」。
     */
    private fun windowRound(
        session: String,
        at: Long,
        offsetMm: Double,
        dartCount: Int = 20,
        hitCount: Int = 0
    ): List<ImpactSample> = List(dartCount) { i ->
        ImpactSample(
            sessionId = session,
            hitAt = at,
            frame = ImpactFrame(eRad = offsetMm, eTan = if (i % 2 == 0) 0.6 else -0.6),
            hit = i < hitCount
        )
    }

    @Test
    fun `标准档窗口远宽于环宽`() {
        // 窗口不能窄到和 8 mm 环宽同量级：那样每镖几乎必然出框。
        val spanY = ImpactWindow.spanY(ImpactWindow.STANDARD_SPAN_MM)
        assertTrue(spanY > 4 * ImpactCalculator.RING_WIDTH_MM)
    }
}
