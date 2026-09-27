package com.dartvio.app.domain.impact

import com.dartvio.app.domain.vision.Point2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/** 热度栅格的纯函数性质（A-IMP-19 的「miss 不进热力图」由调用侧过滤保证，这里只测函数本身）。 */
class HeatmapGridTest {

    @Test
    fun `空列表返回全零且不崩`() {
        val grid = HeatmapGrid.render(emptyList())
        assertEquals(0, grid.count { it != 0f })
    }

    @Test
    fun `单点不除零且峰值在点上`() {
        val grid = HeatmapGrid.render(listOf(Point2(0.0, 0.0)))
        val peak = HeatmapGrid.mmAt(HeatmapGrid.indexOfMax(grid))
        assertTrue(hypot(peak.x, peak.y) <= HeatmapGrid.DEFAULT_CELL_MM)
        assertEquals(1f, grid[HeatmapGrid.indexOfMax(grid)], 1e-6f)
    }

    @Test
    fun `单簇峰值落在簇心`() {
        val random = Random(99)
        val cluster = List(300) {
            Point2(gaussian(random, 12.0, 3.0), gaussian(random, -8.0, 3.0))
        }
        val peak = HeatmapGrid.mmAt(HeatmapGrid.indexOfMax(HeatmapGrid.render(cluster)))
        assertTrue(hypot(peak.x - 12.0, peak.y + 8.0) <= 2 * HeatmapGrid.DEFAULT_CELL_MM)
    }

    @Test
    fun `整体平移后热区随之平移`() {
        val random = Random(3)
        val base = List(200) { Point2(gaussian(random, 0.0, 2.0), gaussian(random, 0.0, 2.0)) }
        val shifted = base.map { Point2(it.x + 20.0, it.y + 10.0) }
        val basePeak = HeatmapGrid.mmAt(HeatmapGrid.indexOfMax(HeatmapGrid.render(base)))
        val shiftedPeak = HeatmapGrid.mmAt(HeatmapGrid.indexOfMax(HeatmapGrid.render(shifted)))
        assertEquals(20.0, shiftedPeak.x - basePeak.x, 2 * HeatmapGrid.DEFAULT_CELL_MM)
        assertEquals(10.0, shiftedPeak.y - basePeak.y, 2 * HeatmapGrid.DEFAULT_CELL_MM)
    }

    @Test
    fun `左右对称簇的热区也对称`() {
        val random = Random(11)
        val left = List(150) { Point2(gaussian(random, -15.0, 2.0), gaussian(random, 0.0, 2.0)) }
        val right = left.map { Point2(-it.x, it.y) }
        val grid = HeatmapGrid.render(left + right)
        val cells = HeatmapGrid.cellCount()
        // 关于中轴（col = (cells-1)/2 = 30）镜像后逐元胞相等。
        for (row in 0 until cells) {
            for (col in 0 until cells) {
                assertEquals(grid[row * cells + col], grid[row * cells + (cells - 1 - col)], 1e-5f)
            }
        }
    }

    @Test
    fun `元胞下标与毫米坐标互相对应`() {
        val cells = HeatmapGrid.cellCount()
        assertEquals(61, cells)
        val center = HeatmapGrid.mmAt(30 * cells + 30)
        assertEquals(0.0, center.x, 1e-9)
        assertEquals(0.0, center.y, 1e-9)
    }

    private fun gaussian(random: Random, mean: Double, sd: Double): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return mean + sd * kotlin.math.sqrt(-2.0 * Math.log(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }
}
