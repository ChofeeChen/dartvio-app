package com.dartvio.app.domain.vision

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 全盘 UI 布局的**兼容门面**：正方形画布 ↔ 靶面 mm 的换算。
 *
 * 换算本体已抽到 [BoardViewport]（全盘 = [BoardViewport.Full]），本对象只保留
 * 「正方形边长入参」的旧签名，保证既有调用方（`BoardTapPad`、单测）行为**逐位不变**；
 * 落点诊断的局部放大窗直接用 [BoardViewport]，不再经过这里。
 *
 * 正方形边长里，最外圈留给 1-20 的读数（[NUMBER_RING_MM]），
 * 真正的 170mm 计分区因此居中、两侧自然留出间隙 —— 数字不侵占点击区，读数也不会被切掉。
 */
object BoardLayout {

    /** 数字环宽度（mm）：170mm 计分区之外，留给 1-20 的读数。 */
    const val NUMBER_RING_MM = 25.0

    /** 数字中心所在半径（mm）。 */
    const val NUMBER_RADIUS_MM = 182.0

    /** 数字字号（mm，随靶盘等比缩放）。 */
    const val NUMBER_FONT_MM = 17.0

    /** 正方形边长要覆盖到的最外半径（mm）= 计分区 + 数字环。 */
    fun outerMm(): Double = BoardGeometry.DOUBLE_OUTER_RADIUS_MM + NUMBER_RING_MM

    /** mm → px 比例（正方形全盘）。 */
    fun pxPerMm(squareSidePx: Float): Float = BoardViewport.Full.pxPerMm(squareSidePx, squareSidePx)

    /** 画布内偏移（左上原点，y 向下）→ 靶面 mm。 */
    fun toBoardMm(squareSidePx: Float, offsetX: Float, offsetY: Float): Point2 =
        BoardViewport.Full.toBoardMm(squareSidePx, squareSidePx, offsetX, offsetY)

    /** 靶面 mm → 画布内偏移（左上原点，y 向下）。 */
    fun toCanvasPx(squareSidePx: Float, xMm: Double, yMm: Double): Point2 =
        BoardViewport.Full.toCanvasPx(squareSidePx, squareSidePx, xMm, yMm)

    /** 第 [sectorIndex] 个分区（自 12 点方向起顺时针）的数字标签锚点，画布坐标。 */
    fun numberAnchorPx(squareSidePx: Float, sectorIndex: Int): Point2 {
        val rad = sectorIndex * BoardGeometry.SECTOR_ANGLE_DEG * PI / 180.0
        return toCanvasPx(squareSidePx, sin(rad) * NUMBER_RADIUS_MM, cos(rad) * NUMBER_RADIUS_MM)
    }
}
