package com.dartvio.app.domain.vision

import kotlin.math.abs

/** 平面二维点（图像像素或靶面毫米，语义由调用方约定）。 */
data class Point2(val x: Double, val y: Double)

/**
 * 3×3 单应矩阵（row-major），用于「图像平面 ↔ 靶面平面」的透视映射。
 *
 * PRD M12 §3.2 / §5.1：机位标定的产物就是本矩阵。求得 H 之后，
 * 图像上任意镖尖像素点即可映射为靶面 mm 坐标，再交给 [BoardGeometry] 判分。
 *
 * 该实现为纯 Kotlin、无 Android 依赖，可在 JVM 单测中直接验证 ——
 * 对应《M12 技术预研（PoC）计划》§11.3 的「P0 几何验证（零训练）」。
 */
class Homography private constructor(private val m: DoubleArray) {

    /** 将点 p 从源平面映射到目标平面；退化（w≈0）时抛错。 */
    fun map(p: Point2): Point2 {
        val w = m[6] * p.x + m[7] * p.y + m[8]
        require(abs(w) > EPS) { "单应变换退化（w≈0）" }
        return Point2(
            (m[0] * p.x + m[1] * p.y + m[2]) / w,
            (m[3] * p.x + m[4] * p.y + m[5]) / w,
        )
    }

    companion object {
        private const val EPS = 1e-12

        /**
         * 由 4 组对应点求解单应矩阵（DLT + Gauss–Jordan 消元）。
         * 四点退化（共线 / 重合）时返回 null。
         */
        fun fromFourPoints(src: List<Point2>, dst: List<Point2>): Homography? {
            require(src.size == 4 && dst.size == 4) { "需要恰好 4 组对应点" }
            val a = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val s = src[i]
                val d = dst[i]
                a[2 * i] = doubleArrayOf(
                    s.x, s.y, 1.0, 0.0, 0.0, 0.0, -d.x * s.x, -d.x * s.y, d.x,
                )
                a[2 * i + 1] = doubleArrayOf(
                    0.0, 0.0, 0.0, s.x, s.y, 1.0, -d.y * s.x, -d.y * s.y, d.y,
                )
            }
            val h = solve(a) ?: return null
            return Homography(h)
        }

        /** Gauss–Jordan 消元（列主元）；未知量 8 个，系数奇异时返回 null。 */
        private fun solve(a: Array<DoubleArray>): DoubleArray? {
            val n = 8
            for (col in 0 until n) {
                var pivot = col
                for (r in col + 1 until n) {
                    if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
                }
                if (abs(a[pivot][col]) < EPS) return null
                if (pivot != col) {
                    val t = a[pivot]
                    a[pivot] = a[col]
                    a[col] = t
                }
                val pv = a[col][col]
                for (c in col..n) a[col][c] /= pv
                for (r in 0 until n) {
                    if (r == col) continue
                    val f = a[r][col]
                    if (abs(f) < EPS) continue
                    for (c in col..n) a[r][c] -= f * a[col][c]
                }
            }
            val h = DoubleArray(9)
            for (i in 0 until n) h[i] = a[i][n]
            h[8] = 1.0
            return h
        }
    }
}
