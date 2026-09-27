package com.dartvio.app.domain.vision

import kotlin.math.min

/**
 * 靶面视口：把一块画布矩形映射到靶面 mm 上的一个**窗口**。
 *
 * 全盘 = [Full]（窗口 = 170mm 计分区 + 两侧数字环，共 390mm）；
 * 落点诊断的局部放大窗 = 任意 `(centerXMm, centerYMm, spanXMm, spanYMm)`。
 *
 * **等比是硬红线**：两轴共用同一个 `pxPerMm`，所以实际可点区 = 窗口与画布的交集，
 * 不足处**居中留边**（既不拉满也不拉伸）。压缩径向会把「偏内 / 偏外」变成一个
 * 系统性缩放错误（比随机噪声危险得多），详见落点诊断设计 §2.1.1。
 *
 * 坐标约定与 [BoardGeometry] 一致：原点靶心、x 右 / y 上、单位 mm；
 * 画布 y 轴向下，翻转只在本类发生一次。
 */
data class BoardViewport(
    val centerXMm: Double,
    val centerYMm: Double,
    val spanXMm: Double,
    val spanYMm: Double
) {
    init {
        require(spanXMm > 0.0 && spanYMm > 0.0) { "窗口跨度必须为正：$spanXMm × $spanYMm" }
    }

    /** mm → px 比例（两轴同一个值，等比）。 */
    fun pxPerMm(canvasWidthPx: Float, canvasHeightPx: Float): Float =
        min(canvasWidthPx / spanXMm.toFloat(), canvasHeightPx / spanYMm.toFloat())

    /** 画布内偏移（左上原点、y 向下）→ 靶面 mm。 */
    fun toBoardMm(canvasWidthPx: Float, canvasHeightPx: Float, offsetX: Float, offsetY: Float): Point2 {
        val scale = pxPerMm(canvasWidthPx, canvasHeightPx)
        val cx = canvasWidthPx / 2f
        val cy = canvasHeightPx / 2f
        return Point2(
            x = centerXMm + ((offsetX - cx) / scale).toDouble(),
            y = centerYMm - ((offsetY - cy) / scale).toDouble()
        )
    }

    /** 靶面 mm → 画布内偏移（左上原点、y 向下）。 */
    fun toCanvasPx(canvasWidthPx: Float, canvasHeightPx: Float, xMm: Double, yMm: Double): Point2 {
        val scale = pxPerMm(canvasWidthPx, canvasHeightPx)
        val cx = canvasWidthPx / 2f
        val cy = canvasHeightPx / 2f
        return Point2(
            x = (cx + (xMm - centerXMm) * scale).toDouble(),
            y = (cy - (yMm - centerYMm) * scale).toDouble()
        )
    }

    companion object {
        /**
         * 全盘视口。用 `by lazy` 而非直接初始化：跨度依赖 [BoardLayout] 的数字环常量，
         * 而 [BoardLayout] 又反向引用本对象 —— 懒初始化是唯一不产生初始化环的写法。
         */
        val Full: BoardViewport by lazy {
            val span = BoardLayout.outerMm() * 2
            BoardViewport(0.0, 0.0, span, span)
        }
    }
}
