package com.dartvio.app.domain.impact

import com.dartvio.app.domain.vision.Point2
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 落点热度栅格（KDE）。
 *
 * **纯函数**：`点列表 → FloatArray`，不持状态、不依赖 Compose。
 *
 * 输入**只接受窗内的点**（`outBand == 0`）：miss 若折算成坐标喂进来，
 * 会沿窗口边界堆出一条假高峰，直接污染「偏差方向」这个结论 —— 过滤在调用侧完成。
 *
 * 两种帧共用本函数，不写两套累加：
 * ① 局部误差帧（x = 切向、y = 径向，中心 = 瞄点）；
 * ② 绝对帧（x / y = 靶面 mm，叠在标准盘上，此时调用方传更大的 `halfSpanMm`）。
 */
object HeatmapGrid {

    /** 默认半跨（mm）：±60 覆盖 ±30 的窗口仍有余量。 */
    const val DEFAULT_HALF_SPAN_MM = 60.0

    /** 默认元胞边长（mm）：±60 / 2 ⇒ 61×61。 */
    const val DEFAULT_CELL_MM = 2.0

    /** 性能上限：超出时只取**最近** [MAX_POINTS] 个点（调用方按 hitAt 排序）。 */
    const val MAX_POINTS = 500

    /** 核带宽钳制区间（mm）。 */
    const val MIN_BANDWIDTH_MM = 3.0
    const val MAX_BANDWIDTH_MM = 10.0

    /** 核截断半径（以带宽为单位）。 */
    const val KERNEL_SIGMA = 3.0

    fun cellCount(
        halfSpanMm: Double = DEFAULT_HALF_SPAN_MM,
        cellMm: Double = DEFAULT_CELL_MM
    ): Int = (2 * halfSpanMm / cellMm).roundToInt() + 1

    /**
     * 渲染热度：每点累加单位质量高斯核，最后按最大值归一化到 `[0, 1]`。
     * 空列表返回全 0（不崩、不除零）。
     */
    fun render(
        points: List<Point2>,
        halfSpanMm: Double = DEFAULT_HALF_SPAN_MM,
        cellMm: Double = DEFAULT_CELL_MM,
        maxPoints: Int = MAX_POINTS
    ): FloatArray {
        val cells = cellCount(halfSpanMm, cellMm)
        val grid = FloatArray(cells * cells)
        if (points.isEmpty()) return grid

        val used = if (points.size > maxPoints) points.takeLast(maxPoints) else points
        val bandwidthX = bandwidth(used.map { it.x }, used.size)
        val bandwidthY = bandwidth(used.map { it.y }, used.size)
        val reach = (KERNEL_SIGMA * maxOf(bandwidthX, bandwidthY) / cellMm).roundToInt().coerceAtLeast(1)

        used.forEach { point ->
            val col = ((point.x + halfSpanMm) / cellMm).roundToInt()
            val row = ((point.y + halfSpanMm) / cellMm).roundToInt()
            for (r in (row - reach)..(row + reach)) {
                if (r !in 0 until cells) continue
                val dy = (r * cellMm - halfSpanMm - point.y) / bandwidthY
                for (c in (col - reach)..(col + reach)) {
                    if (c !in 0 until cells) continue
                    val dx = (c * cellMm - halfSpanMm - point.x) / bandwidthX
                    grid[r * cells + c] += exp(-0.5 * (dx * dx + dy * dy)).toFloat()
                }
            }
        }
        normalize(grid)
        return grid
    }

    /** 最大值所在的元胞下标（用于断言峰值位置）。 */
    fun indexOfMax(grid: FloatArray): Int {
        var best = 0
        for (i in grid.indices) if (grid[i] > grid[best]) best = i
        return best
    }

    /** 元胞下标 → 该元胞中心的 mm 坐标。 */
    fun mmAt(
        index: Int,
        halfSpanMm: Double = DEFAULT_HALF_SPAN_MM,
        cellMm: Double = DEFAULT_CELL_MM
    ): Point2 {
        val cells = cellCount(halfSpanMm, cellMm)
        val row = index / cells
        val col = index % cells
        return Point2(col * cellMm - halfSpanMm, row * cellMm - halfSpanMm)
    }

    /** Silverman 分轴带宽：`1.06 · σ · n^(−1/5)`，钳制到 [MIN_BANDWIDTH_MM, MAX_BANDWIDTH_MM]。 */
    private fun bandwidth(values: List<Double>, n: Int): Double {
        if (n <= 0) return MIN_BANDWIDTH_MM
        val mean = values.sum() / n
        val sd = if (n < 2) 0.0 else sqrt(values.sumOf { (it - mean) * (it - mean) } / (n - 1))
        val raw = 1.06 * sd * n.toDouble().pow(-0.2)
        return raw.coerceIn(MIN_BANDWIDTH_MM, MAX_BANDWIDTH_MM)
    }

    private fun normalize(grid: FloatArray) {
        val max = grid.maxOrNull() ?: 0f
        if (max <= 0f) return
        for (i in grid.indices) grid[i] = grid[i] / max
    }
}
