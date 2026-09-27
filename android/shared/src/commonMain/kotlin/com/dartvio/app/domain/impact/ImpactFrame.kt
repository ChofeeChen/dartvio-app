package com.dartvio.app.domain.impact

import com.dartvio.app.domain.vision.Point2
import kotlin.math.hypot

/**
 * 误差分解结果：把「落点 − 锚点」拆成**径向 / 切向**两个正交分量。
 *
 * 径向的「外」= 远离靶心，**不是**「上」—— 这点必须由几何说话：
 * 同一个 `eRad > 0`，在 12 点方向是「偏上」，在 6 点方向却是「偏下」，
 * 所以 UI 文案一律用**偏内 / 偏外**，禁止把 `eRad` 直接写成"偏高/偏低"。
 */
data class ImpactFrame(val eRad: Double, val eTan: Double) {
    /** 该镖偏离锚点的距离（mm）。 */
    val length: Double get() = hypot(eRad, eTan)
}

/** 误差分解与「屏幕方向 ↔ 靶面语义」的映射。 */
object ImpactFrames {

    /** 沿半径向外的单位向量；锚点在靶心时退化为 12 点方向。 */
    fun radialUnit(target: IntentTarget): Point2 {
        val anchor = target.anchorMm()
        val len = hypot(anchor.x, anchor.y)
        return if (len < 1e-9) Point2(0.0, 1.0) else Point2(anchor.x / len, anchor.y / len)
    }

    /** 切向单位向量 = 径向顺时针旋转 90°（x 右 / y 上坐标系下即 `(r.y, −r.x)`）。 */
    fun tangentUnit(target: IntentTarget): Point2 {
        val r = radialUnit(target)
        return Point2(r.y, -r.x)
    }

    /** 由落点与意图算出误差分解。 */
    fun of(target: IntentTarget, xMm: Double, yMm: Double): ImpactFrame {
        val anchor = target.anchorMm()
        val r = radialUnit(target)
        val t = tangentUnit(target)
        val ex = xMm - anchor.x
        val ey = yMm - anchor.y
        return ImpactFrame(
            eRad = ex * r.x + ey * r.y,
            eTan = ex * t.x + ey * t.y
        )
    }

    /** 靶面语义。 */
    enum class BandSemantic(val label: String) {
        OUTWARD("偏外"),
        INWARD("偏内"),
        TANGENT_CW("切向顺时针侧"),
        TANGENT_CCW("切向逆时针侧")
    }

    /**
     * 把「屏幕四边」翻译成靶面语义。
     *
     * 窗口**不旋转**（与对局页一致，用户抬头看靶、低头点屏方向感一致），
     * 因此「哪条边是偏外」由锚点径向在**屏幕**上的指向决定：
     * 屏幕坐标 y 向下，故 mm 的 `+y` 对应屏幕的「上」。
     */
    fun semanticOf(target: IntentTarget, outBand: Int): BandSemantic {
        val r = radialUnit(target)
        val sx = r.x
        val sy = -r.y      // mm → 屏幕
        fun dot(bx: Double, by: Double) = sx * bx + sy * by
        // 四条边的方向（屏幕系，y 向下）：上 / 右 / 下 / 左
        val dirs = listOf(1 to (0.0 to -1.0), 2 to (1.0 to 0.0), 3 to (0.0 to 1.0), 4 to (-1.0 to 0.0))
        val outwardBand = dirs.maxByOrNull { dot(it.second.first, it.second.second) }!!.first
        val oppositeBand = when (outwardBand) {
            1 -> 3
            2 -> 4
            3 -> 1
            else -> 2
        }
        return when (outBand) {
            outwardBand -> BandSemantic.OUTWARD
            oppositeBand -> BandSemantic.INWARD
            else -> {
                // 切向两侧：屏幕系顺时针 = (x, y) → (−y, x)
                val cwX = -sy
                val cwY = sx
                val bandDir = dirs.first { it.first == outBand }.second
                if (bandDir.first * cwX + bandDir.second * cwY > 0.0) {
                    BandSemantic.TANGENT_CW
                } else {
                    BandSemantic.TANGENT_CCW
                }
            }
        }
    }
}
