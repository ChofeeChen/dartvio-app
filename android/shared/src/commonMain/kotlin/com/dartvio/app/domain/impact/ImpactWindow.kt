package com.dartvio.app.domain.impact

import com.dartvio.app.domain.vision.BoardGeometry
import com.dartvio.app.domain.vision.BoardViewport
import kotlin.math.PI
import kotlin.math.sin

/**
 * 局部放大窗的档位与换算。
 *
 * 窗口是**等比**的（两轴共用一个 `pxPerMm`，见 [BoardViewport]），所以「窗口多高」这一个数
 * 就决定了毫米分辨率。档位只提供 [STANDARD_SPAN_MM] / [WIDE_SPAN_MM] 两档：
 * 再多档位就会把「同一意图的历史成绩」切碎成互不可比的小样本，
 * 而 `windowSpanMm` 已随每镖落库 —— 跨档位合并统计是被显式禁止的。
 */
object ImpactWindow {

    /** 标准档：径向 ±30 mm（比 8 mm 环宽多一个数量级的余量）。 */
    const val STANDARD_SPAN_MM = 60.0

    /** 宽松档：径向 ±60 mm（给散布大或刚入门的样本用，门槛问题交给样本量而不是窗口）。 */
    const val WIDE_SPAN_MM = 120.0

    /** 可选档位（升序）。 */
    val SPAN_PRESETS: List<Double> = listOf(STANDARD_SPAN_MM, WIDE_SPAN_MM)

    /** 窗口按档位命名，让用户知道「换档后成绩不可比」。 */
    fun labelOf(spanMm: Double): String = when (spanMm) {
        WIDE_SPAN_MM -> "宽松（径向 ±${(WIDE_SPAN_MM / 2).toInt()} mm）"
        else -> "标准（径向 ±${(STANDARD_SPAN_MM / 2).toInt()} mm）"
    }

    /** 径向跨度 = 档位本身。 */
    fun spanY(spanMm: Double): Double = spanMm

    /**
     * 切向跨度：至少覆盖目标分区**左右各一个**邻居。
     *
     * 用弧宽（`2·r·sin(9°)`）而不是弦长的近似或固定毫米数 —— 半径越大，同样 18° 张角对应的
     * 弧越长（T20 半径 103 mm 上 1 个分区 ≈ 32 mm，牛眼附近则接近于 0），
     * 写死毫米数会让外层目标的邻居被切掉、内层目标浪费窗口。
     */
    fun spanX(target: IntentTarget, spanMm: Double): Double {
        if (target.kind == IntentKind.BULL) return spanMm
        val arcWidth = 2.0 * target.radiusMm() *
            sin(BoardGeometry.SECTOR_ANGLE_DEG / 2.0 * PI / 180.0)
        return 3.0 * arcWidth
    }

    /** 该意图在档位下的视口（窗口中心 = 瞄点锚点）。 */
    fun viewportOf(target: IntentTarget, spanMm: Double): BoardViewport {
        val anchor = target.anchorMm()
        return BoardViewport(
            centerXMm = anchor.x,
            centerYMm = anchor.y,
            spanXMm = spanX(target, spanMm),
            spanYMm = spanY(spanMm)
        )
    }
}
