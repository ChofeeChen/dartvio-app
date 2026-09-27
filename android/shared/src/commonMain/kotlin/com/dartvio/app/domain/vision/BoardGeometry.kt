package com.dartvio.app.domain.vision

import com.dartvio.app.domain.model.Dart
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * 靶面平面几何 → 分值（PRD M12 §5.2 落点映射规则）。
 *
 * 坐标约定：以靶心为原点，x 向右、y 向上，单位 **毫米（mm）**。
 *
 * 本映射是纯数学、不依赖任何模型 —— 这正是 M12 把「算分」从「识别」中解耦的原因：
 * 模型只需给出镖尖落点（亚像素），分区与倍数由本对象精确判定，
 * 从而把 8 mm 环宽的精度问题留给几何，而非让分类器去硬扛。
 *
 * 尺寸采用 WDF / BDO 标准靶（见 M12 §3.1）：靶面直径 340 mm，双倍 / 三倍环宽 8 mm。
 */
object BoardGeometry {

    /** 内牛眼（Double Bull，50 分）半径。 */
    const val INNER_BULL_RADIUS_MM = 6.35

    /** 外牛眼（Outer Bull，25 分）半径。 */
    const val OUTER_BULL_RADIUS_MM = 15.9

    /** 三倍环内径。 */
    const val TRIPLE_INNER_RADIUS_MM = 99.0

    /** 三倍环外径（环宽 8 mm）。 */
    const val TRIPLE_OUTER_RADIUS_MM = 107.0

    /** 双倍环内径。 */
    const val DOUBLE_INNER_RADIUS_MM = 162.0

    /** 双倍环外径（= 靶面半径 170 mm，环宽 8 mm）。 */
    const val DOUBLE_OUTER_RADIUS_MM = 170.0

    /** 分区数。 */
    const val SECTOR_COUNT = 20

    /** 单个分区圆心角（度）。 */
    const val SECTOR_ANGLE_DEG = 360.0 / SECTOR_COUNT

    /** 顺时针分区顺序；索引 0 = 12 点方向的 20 分区。 */
    val SECTOR_ORDER = intArrayOf(
        20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5,
    )

    /**
     * 由靶面坐标（mm）计算落点分值。
     * 落在靶面之外（半径 > 170 mm）判定为 [Dart.MISS]。
     */
    fun dartAt(xMm: Double, yMm: Double): Dart {
        if (!xMm.isFinite() || !yMm.isFinite()) return Dart.MISS
        val r = hypot(xMm, yMm)
        return when {
            r <= INNER_BULL_RADIUS_MM -> Dart.INNER_BULL
            r <= OUTER_BULL_RADIUS_MM -> Dart.OUTER_BULL
            r > DOUBLE_OUTER_RADIUS_MM -> Dart.MISS
            r >= TRIPLE_INNER_RADIUS_MM && r < TRIPLE_OUTER_RADIUS_MM -> Dart(sectorAt(xMm, yMm), 3)
            r >= DOUBLE_INNER_RADIUS_MM -> Dart(sectorAt(xMm, yMm), 2)
            else -> Dart(sectorAt(xMm, yMm), 1)
        }
    }

    /** 便捷重载：接受 [Point2]（语义为靶面 mm 坐标）。 */
    fun dartAt(p: Point2): Dart = dartAt(p.x, p.y)

    /**
     * 由靶面坐标（mm）计算所属分区号（1..20）。
     * 角度自 12 点方向起、顺时针度量；每个分区以该分区号为中心、张角 18°。
     */
    fun sectorAt(xMm: Double, yMm: Double): Int {
        var deg = atan2(xMm, yMm) * 180.0 / PI
        if (deg < 0) deg += 360.0
        val index = ((deg + SECTOR_ANGLE_DEG / 2.0) / SECTOR_ANGLE_DEG).toInt() % SECTOR_COUNT
        return SECTOR_ORDER[index]
    }
}
