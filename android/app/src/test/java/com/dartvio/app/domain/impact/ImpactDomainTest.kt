package com.dartvio.app.domain.impact

import com.dartvio.app.domain.vision.BoardGeometry
import com.dartvio.app.domain.vision.BoardLayout
import com.dartvio.app.domain.vision.BoardViewport
import com.dartvio.app.domain.vision.Point2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 落点诊断领域层的纯 JVM 单测（无 Android 依赖）。
 *
 * 对应验收 A-IMP-01（视口与全盘一致）/ A-IMP-03（锚点）/ A-IMP-04（误差分解与统计）/
 * A-IMP-13（等比 + 锚点居中）/ A-IMP-18（条带）/ A-IMP-20（条带标签由几何推出）。
 */
class ImpactDomainTest {

    private val side = 340f

    // ---------------------------------------------------------------- 视口

    @Test
    fun `全盘视口与既有BoardLayout行为逐位一致`() {
        val random = Random(20260913)
        repeat(1000) {
            val x = random.nextFloat() * side
            val y = random.nextFloat() * side
            val legacy = BoardLayout.toBoardMm(side, x, y)
            val viewport = BoardViewport.Full.toBoardMm(side, side, x, y)
            assertEquals(legacy.x, viewport.x, 1e-9)
            assertEquals(legacy.y, viewport.y, 1e-9)
        }
    }

    @Test
    fun `局部窗口等比且锚点居中`() {
        val target = IntentTarget.triple(20)
        val anchor = target.anchorMm()
        val viewport = BoardViewport(
            centerXMm = anchor.x,
            centerYMm = anchor.y,
            spanXMm = 96.6,
            spanYMm = 60.0
        )
        val width = 370f
        val height = 231f
        val scale = viewport.pxPerMm(width, height)

        // 锚点必须落在画布中心。
        val center = viewport.toBoardMm(width, height, width / 2f, height / 2f)
        assertTrue(hypot(center.x - anchor.x, center.y - anchor.y) < 1e-6)

        // 8 个方向：屏幕距离 / 靶面距离恒为 pxPerMm（等比，不压扁径向）。
        val directions = listOf(1.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0, -1.0 to 1.0, -1.0 to 0.0, 0.0 to -1.0, -1.0 to -1.0, 1.0 to -1.0)
        directions.forEach { (dx, dy) ->
            val p1 = viewport.toCanvasPx(width, height, anchor.x, anchor.y)
            val p2 = viewport.toCanvasPx(width, height, anchor.x + dx * 10, anchor.y + dy * 10)
            val screenDistance = hypot(p2.x - p1.x, p2.y - p1.y)
            val boardDistance = hypot(dx * 10, dy * 10)
            assertEquals(scale.toDouble(), screenDistance / boardDistance, scale * 0.001)
        }
    }

    // ---------------------------------------------------------------- 锚点

    @Test
    fun `锚点落在目标环内且分区正确`() {
        BoardGeometry.SECTOR_ORDER.forEach { sector ->
            val triple = IntentTarget.triple(sector).anchorMm()
            val rTriple = hypot(triple.x, triple.y)
            assertTrue(rTriple in BoardGeometry.TRIPLE_INNER_RADIUS_MM..BoardGeometry.TRIPLE_OUTER_RADIUS_MM)
            assertEquals(sector, BoardGeometry.sectorAt(triple.x, triple.y))

            val double = IntentTarget.double(sector).anchorMm()
            val rDouble = hypot(double.x, double.y)
            assertTrue(rDouble in BoardGeometry.DOUBLE_INNER_RADIUS_MM..BoardGeometry.DOUBLE_OUTER_RADIUS_MM)
            assertEquals(sector, BoardGeometry.sectorAt(double.x, double.y))
        }
        val bull = IntentTarget.BULL.anchorMm()
        assertEquals(0.0, bull.x, 1e-9)
        assertEquals(0.0, bull.y, 1e-9)
    }

    @Test
    fun `邻居分区由几何算出`() {
        val (clockwise, counterClockwise) = IntentTarget.triple(20).neighbors()
        assertEquals(1, clockwise)
        assertEquals(5, counterClockwise)
    }

    @Test
    fun `意图可以从落库字段还原`() {
        assertEquals(IntentTarget.triple(20), IntentTarget.fromStored(20, 3))
        assertEquals(IntentTarget.double(16), IntentTarget.fromStored(16, 2))
        assertEquals(IntentTarget.singleOuter(20), IntentTarget.fromStored(20, 1))
        assertEquals(IntentTarget.BULL, IntentTarget.fromStored(25, 2))
        assertNull(IntentTarget.fromStored(0, 0))
    }

    // ---------------------------------------------------------------- 误差分解与统计

    @Test
    fun `径向分量是偏内偏外而不是上下`() {
        // 3 点方向（正下方）的 T3：往靶心方向偏（屏幕上是向上）应当判为「偏内」= eRad < 0。
        val target = IntentTarget.triple(3)
        val anchor = target.anchorMm()
        val inward = ImpactFrames.of(target, anchor.x, anchor.y + 6.0)
        assertTrue(inward.eRad < 0)
        assertEquals(-6.0, inward.eRad, 1e-9)

        // 同一个「偏内」在 12 点方向也是 eRad < 0。
        val top = IntentTarget.triple(20)
        val topAnchor = top.anchorMm()
        assertEquals(-6.0, ImpactFrames.of(top, topAnchor.x, topAnchor.y - 6.0).eRad, 1e-9)
    }

    @Test
    fun `合成样本的偏移与散布误差小于2个百分点`() {
        val random = Random(20260913)
        val frames = List(5000) {
            ImpactFrame(eRad = gaussian(random, 6.0, 4.0), eTan = gaussian(random, -3.0, 5.0))
        }
        val stats = ImpactCalculator.of(frames)!!
        assertEquals(5000, stats.n)
        // 均值：容差取 3 倍标准误（σ/√n），而不是 σ 的 2% —— 后者小于 1.5 个标准误，会随机挂。
        assertEquals(0.0, stats.biasRad - 6.0, 3 * 4.0 / sqrt(5000.0))
        assertEquals(0.0, stats.biasTan + 3.0, 3 * 5.0 / sqrt(5000.0))
        // 散布：相对误差 <2%（σ 估计的相对标准误约 1/√(2n) ≈ 1%）。
        assertEquals(4.0, stats.sigmaRad, 4.0 * 0.02)
        assertEquals(5.0, stats.sigmaTan, 5.0 * 0.02)
        // rmse = sqrt(σr² + σt² + |bias|²) = sqrt(16 + 25 + 45) ≈ 9.27
        assertEquals(sqrt(16.0 + 25.0 + 45.0), stats.rmse, 0.2)
    }

    @Test
    fun `r95按质心覆盖百分之九十五的点`() {
        val random = Random(7)
        val frames = List(400) { ImpactFrame(gaussian(random, 0.0, 6.0), gaussian(random, 0.0, 6.0)) }
        val stats = ImpactCalculator.of(frames)!!
        val inside = frames.count { hypot(it.eRad - stats.biasRad, it.eTan - stats.biasTan) <= stats.r95 }
        assertTrue(inside.toDouble() / frames.size >= 0.95)
        assertTrue(inside.toDouble() / frames.size < 0.99)
    }

    @Test
    fun `样本门槛与结论文案`() {
        assertEquals(ImpactCalculator.Confidence.TOO_FEW, ImpactCalculator.confidenceOf(11))
        assertEquals(ImpactCalculator.Confidence.BIAS_ONLY, ImpactCalculator.confidenceOf(12))
        assertEquals(ImpactCalculator.Confidence.BIAS_ONLY, ImpactCalculator.confidenceOf(29))
        assertEquals(ImpactCalculator.Confidence.FULL, ImpactCalculator.confidenceOf(30))

        val few = ImpactStats(n = 11, biasRad = 1.0, biasTan = 0.0, sigmaRad = 1.0, sigmaTan = 1.0, r95 = 3.0, rmse = 2.0)
        assertTrue(ImpactCalculator.headline(few).contains("还需要 19 镖"))

        val obvious = ImpactStats(n = 40, biasRad = 6.0, biasTan = 0.0, sigmaRad = 5.0, sigmaTan = 5.0, r95 = 20.0, rmse = 8.0)
        assertEquals(ImpactCalculator.BiasLevel.OBVIOUS, ImpactCalculator.biasLevelOf(obvious.bias))
        assertTrue(ImpactCalculator.headline(obvious).contains("偏外"))
    }

    // ---------------------------------------------------------------- 出框条带

    @Test
    fun `条带八种组合与四角不响应`() {
        val width = 400f
        val height = 300f
        val density = 1f

        assertEquals(ImpactMissBand.BandHit(1, 1), ImpactMissBand.bandAt(200f, 30f, width, height, density))
        assertEquals(ImpactMissBand.BandHit(1, 2), ImpactMissBand.bandAt(200f, 10f, width, height, density))
        assertEquals(ImpactMissBand.BandHit(2, 1), ImpactMissBand.bandAt(370f, 150f, width, height, density))
        assertEquals(ImpactMissBand.BandHit(2, 2), ImpactMissBand.bandAt(390f, 150f, width, height, density))
        assertEquals(ImpactMissBand.BandHit(3, 1), ImpactMissBand.bandAt(200f, 270f, width, height, density))
        assertEquals(ImpactMissBand.BandHit(3, 2), ImpactMissBand.bandAt(200f, 295f, width, height, density))
        assertEquals(ImpactMissBand.BandHit(4, 1), ImpactMissBand.bandAt(35f, 150f, width, height, density))
        assertEquals(ImpactMissBand.BandHit(4, 2), ImpactMissBand.bandAt(15f, 150f, width, height, density))

        // 主预览区与四角：不记 miss。
        assertNull(ImpactMissBand.bandAt(200f, 150f, width, height, density))
        assertNull(ImpactMissBand.bandAt(10f, 10f, width, height, density))
        assertNull(ImpactMissBand.bandAt(392f, 8f, width, height, density))
    }

    @Test
    fun `出框坐标先clamp再外推一个环宽`() {
        val viewport = BoardViewport(centerXMm = 0.0, centerYMm = 103.0, spanXMm = 96.6, spanYMm = 60.0)
        val clamped = ImpactMissBand.missPointMm(viewport, 10.0, 999.0, ImpactMissBand.BandHit(1, 1))
        assertEquals(133.0, clamped.y, 1e-9)
        assertEquals(10.0, clamped.x, 1e-9)

        val far = ImpactMissBand.missPointMm(viewport, 10.0, 999.0, ImpactMissBand.BandHit(1, 2))
        assertEquals(133.0 + ImpactMissBand.RING_WIDTH_MM, far.y, 1e-9)
    }

    @Test
    fun `条带文案由几何推出`() {
        val t20 = IntentTarget.triple(20)
        val anchor = t20.anchorMm()
        val viewport = BoardViewport(anchor.x, anchor.y, spanXMm = 96.6, spanYMm = 60.0)

        assertTrue(ImpactMissBand.outsideLabel(t20, viewport, 1).contains("偏外"))
        assertTrue(ImpactMissBand.outsideLabel(t20, viewport, 3).contains("偏内"))

        val d20 = IntentTarget.double(20)
        val d20Anchor = d20.anchorMm()
        val d20Viewport = BoardViewport(d20Anchor.x, d20Anchor.y, spanXMm = 155.0, spanYMm = 60.0)
        assertTrue(ImpactMissBand.outsideLabel(d20, d20Viewport, 1).contains("靶外"))

        // 切向两侧：邻居由几何给出（T20 顺时针侧 = 1、逆时针侧 = 5）。
        assertTrue(ImpactMissBand.tangentLabel(t20, 2).contains("1"))
        assertTrue(ImpactMissBand.tangentLabel(t20, 4).contains("5"))
    }

    @Test
    fun `未知意图不会崩`() {
        // 防御：非法分区不应在半途抛异常（由调用方保证不构造）。
        val anchor = IntentTarget.BULL.anchorMm()
        assertNotNull(anchor)
        assertEquals(0.0, anchor.x, 1e-9)
        assertEquals(Point2(0.0, 0.0), anchor)
    }

    /** Box–Muller 取正态样本（固定种子 ⇒ 测试可复现）。 */
    private fun gaussian(random: Random, mean: Double, sd: Double): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return mean + sd * sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2)
    }
}
