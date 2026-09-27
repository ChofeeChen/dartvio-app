package com.dartvio.app.domain.impact

import com.dartvio.app.domain.vision.BoardGeometry
import com.dartvio.app.domain.vision.BoardViewport
import com.dartvio.app.domain.vision.Point2
import kotlin.math.hypot

/**
 * 出框（miss）录入：预览矩形**四周的条带状区域**。
 *
 * 为什么必须有它：局部窗口是**截断采样**，落点一旦超出可点范围就只能放弃记录 ⇒
 * 分布被截尾、σ / R95 偏小，而且不同目标的截尾程度不同（T20 先截外侧、D16 先截内侧）
 * ⇒ 不记出框，趋势图会骗人。
 *
 * 两档程度用**条带内部位置**表达，不增加操作：
 * 内半圈 = 轻微出框（`outLevel = 1`）、外半圈 = 远出框 / 靶外（`outLevel = 2`）。
 * 四角留 [CORNER_GAP_DP]×[CORNER_GAP_DP] dp 不响应，避免斜向歧义。
 */
object ImpactMissBand {

    /** 条带最小宽度（dp）：不小于手指可靠触控下限。 */
    const val BAND_MIN_DP = 40f

    /** 四角非响应区（dp）。 */
    const val CORNER_GAP_DP = 20f

    /** 外推 1 个环宽用的环宽（mm）。 */
    const val RING_WIDTH_MM = ImpactCalculator.RING_WIDTH_MM

    /** 条带命中结果：屏幕方向 `1 上 / 2 右 / 3 下 / 4 左` + 程度 `1 轻微 / 2 远`。 */
    data class BandHit(val outBand: Int, val outLevel: Int)

    /**
     * 条带命中检测（纯几何，不依赖 Compose / Context）。
     *
     * @return `null` = 落在主预览区或四角角隙（不记 miss）。
     */
    fun bandAt(
        xPx: Float,
        yPx: Float,
        widthPx: Float,
        heightPx: Float,
        density: Float
    ): BandHit? {
        val band = BAND_MIN_DP * density
        val corner = CORNER_GAP_DP * density
        val nearLeft = xPx < band
        val nearRight = xPx > widthPx - band
        val nearTop = yPx < band
        val nearBottom = yPx > heightPx - band
        val horizontalEdge = nearLeft || nearRight
        val verticalEdge = nearTop || nearBottom
        if (!horizontalEdge && !verticalEdge) return null
        if (horizontalEdge && verticalEdge) return null
        // 角隙只裁掉最外侧的 [0, corner] 段：四角 20×20dp 内不响应。
        if (nearLeft && yPx < corner) return null
        if (nearRight && yPx < corner) return null
        if (nearLeft && yPx > heightPx - corner) return null
        if (nearRight && yPx > heightPx - corner) return null
        if (nearTop && xPx < corner) return null
        if (nearTop && xPx > widthPx - corner) return null
        if (nearBottom && xPx < corner) return null
        if (nearBottom && xPx > widthPx - corner) return null
        return when {
            nearTop -> BandHit(1, if (yPx < band / 2f) 2 else 1)
            nearBottom -> BandHit(3, if (yPx > heightPx - band / 2f) 2 else 1)
            nearLeft -> BandHit(4, if (xPx < band / 2f) 2 else 1)
            else -> BandHit(2, if (xPx > widthPx - band / 2f) 2 else 1)
        }
    }

    /**
     * 出框点折算成靶面 mm：先 clamp 到窗口边界，`outLevel == 2` 再沿该屏幕方向外推 1 个环宽。
     *
     * 这样 miss 不只是一个枚举 —— 沿条带的位置仍然提供**一维有效信息**（切向分量）。
     */
    fun missPointMm(viewport: BoardViewport, xMm: Double, yMm: Double, hit: BandHit): Point2 {
        val left = viewport.centerXMm - viewport.spanXMm / 2.0
        val right = viewport.centerXMm + viewport.spanXMm / 2.0
        val bottom = viewport.centerYMm - viewport.spanYMm / 2.0
        val top = viewport.centerYMm + viewport.spanYMm / 2.0
        val clamped = when (hit.outBand) {
            1 -> Point2(xMm.coerceIn(left, right), top)
            3 -> Point2(xMm.coerceIn(left, right), bottom)
            4 -> Point2(left, yMm.coerceIn(bottom, top))
            else -> Point2(right, yMm.coerceIn(bottom, top))
        }
        if (hit.outLevel != 2) return clamped
        return when (hit.outBand) {
            1 -> Point2(clamped.x, clamped.y + RING_WIDTH_MM)
            3 -> Point2(clamped.x, clamped.y - RING_WIDTH_MM)
            4 -> Point2(clamped.x - RING_WIDTH_MM, clamped.y)
            else -> Point2(clamped.x + RING_WIDTH_MM, clamped.y)
        }
    }

    /**
     * 该方向「窗外是什么」的文案：取窗口边再往外 1 个环宽的采样点判分得到。
     *
     * **全部由几何推出**：改 `BoardGeometry` 的常量，文案随之变化 ⇒ 证明没有硬编码。
     */
    fun outsideLabel(target: IntentTarget, viewport: BoardViewport, outBand: Int): String {
        val edge = missPointMm(viewport, viewport.centerXMm, viewport.centerYMm, BandHit(outBand, 1))
        val beyond = missPointMm(viewport, edge.x, edge.y, BandHit(outBand, 2))
        val r = hypot(beyond.x, beyond.y)
        val semantic = ImpactFrames.semanticOf(target, outBand).label
        val what = if (r > BoardGeometry.DOUBLE_OUTER_RADIUS_MM) {
            "靶外"
        } else {
            BoardGeometry.dartAt(beyond.x, beyond.y).label()
        }
        return "$semantic（$what）"
    }

    /** 切向两侧的文案：邻居分区由几何算出，禁硬编码「20 的邻居」。 */
    fun tangentLabel(target: IntentTarget, outBand: Int): String {
        val (clockwise, counterClockwise) = target.neighbors()
        return when (ImpactFrames.semanticOf(target, outBand)) {
            ImpactFrames.BandSemantic.TANGENT_CW -> "偏 $clockwise 侧"
            ImpactFrames.BandSemantic.TANGENT_CCW -> "偏 $counterClockwise 侧"
            else -> ""
        }
    }
}
