package com.dartvio.app.domain.impact

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.vision.BoardGeometry
import com.dartvio.app.domain.vision.Point2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 意图的环类型。 */
enum class IntentKind(val label: String) {
    TRIPLE("三倍"),
    DOUBLE("双倍"),
    SINGLE_OUTER("外单倍"),
    BULL("牛眼")
}

/**
 * 瞄准意图 = 「这一组想打哪里」。
 *
 * 没有意图就只能画「绝对落点图」（你常往哪打），画不出**系统偏移**与**跨目标合并的散布画像** ——
 * 而这两者才是落点诊断要回答的问题，所以意图列是本功能能否成立的分水岭。
 *
 * 唯一几何来源 = [BoardGeometry]：锚点半径取各环**中线**（把「环内偏移」留给随机散布，
 * 不让它混进系统偏移），角度由 `SECTOR_ORDER` 反查索引算出（禁查表硬写角度）。
 */
data class IntentTarget(val kind: IntentKind, val sector: Int = 25) {

    /** 锚点半径（mm）。 */
    fun radiusMm(): Double = when (kind) {
        IntentKind.TRIPLE -> (BoardGeometry.TRIPLE_INNER_RADIUS_MM + BoardGeometry.TRIPLE_OUTER_RADIUS_MM) / 2.0
        IntentKind.DOUBLE -> (BoardGeometry.DOUBLE_INNER_RADIUS_MM + BoardGeometry.DOUBLE_OUTER_RADIUS_MM) / 2.0
        IntentKind.SINGLE_OUTER -> (BoardGeometry.TRIPLE_OUTER_RADIUS_MM + BoardGeometry.DOUBLE_INNER_RADIUS_MM) / 2.0
        IntentKind.BULL -> 0.0
    }

    /** 分区在几何顺序中的索引；`-1` = 未知分区。 */
    fun sectorIndex(): Int =
        if (kind == IntentKind.BULL) -1 else BoardGeometry.SECTOR_ORDER.indexOf(sector)

    /** 锚点（靶面 mm）。 */
    fun anchorMm(): Point2 {
        if (kind == IntentKind.BULL) return Point2(0.0, 0.0)
        val index = sectorIndex()
        require(index >= 0) { "未知分区：$sector" }
        val rad = index * BoardGeometry.SECTOR_ANGLE_DEG * PI / 180.0
        return Point2(x = sin(rad) * radiusMm(), y = cos(rad) * radiusMm())
    }

    /** 命中判据（BULL 只看是否牛眼，内外牛眼都算命中）。 */
    fun isHit(dart: Dart): Boolean = when (kind) {
        IntentKind.BULL -> dart.isBull
        IntentKind.TRIPLE -> dart.number == sector && dart.multiplier == 3
        IntentKind.DOUBLE -> dart.number == sector && dart.multiplier == 2
        IntentKind.SINGLE_OUTER -> dart.number == sector && dart.multiplier == 1 && dart.number != 25
    }

    /**
     * 左右相邻分区（顺时针侧, 逆时针侧）—— **由几何顺序算，不得硬编码「20 的邻居」**
     * （分区顺序若调整，这里必须跟着变）。
     */
    fun neighbors(): Pair<Int, Int> {
        if (kind == IntentKind.BULL) return 25 to 25
        val count = BoardGeometry.SECTOR_COUNT
        val index = sectorIndex()
        val cw = BoardGeometry.SECTOR_ORDER[(index + 1) % count]
        val ccw = BoardGeometry.SECTOR_ORDER[(index - 1 + count) % count]
        return cw to ccw
    }

    val label: String
        get() = when (kind) {
            IntentKind.TRIPLE -> "T$sector"
            IntentKind.DOUBLE -> "D$sector"
            IntentKind.SINGLE_OUTER -> "S$sector(外)"
            IntentKind.BULL -> "BULL"
        }

    /** 落库形式：`(intentNumber, intentMultiplier)`；无意图在库里写 `(0, 0)`。 */
    fun toStored(): Pair<Int, Int> = when (kind) {
        IntentKind.BULL -> 25 to 2
        IntentKind.TRIPLE -> sector to 3
        IntentKind.DOUBLE -> sector to 2
        IntentKind.SINGLE_OUTER -> sector to 1
    }

    companion object {
        fun triple(sector: Int) = IntentTarget(IntentKind.TRIPLE, sector)
        fun double(sector: Int) = IntentTarget(IntentKind.DOUBLE, sector)
        fun singleOuter(sector: Int) = IntentTarget(IntentKind.SINGLE_OUTER, sector)
        val BULL: IntentTarget = IntentTarget(IntentKind.BULL, 25)

        /** 由落库字段还原；`(0, 0)` 或非法组合 → `null`（= 无意图）。 */
        fun fromStored(number: Int, multiplier: Int): IntentTarget? = when {
            number <= 0 || multiplier <= 0 -> null
            number == 25 -> BULL
            multiplier == 3 -> triple(number)
            multiplier == 2 -> double(number)
            multiplier == 1 -> singleOuter(number)
            else -> null
        }

        /** 首屏主项（进阶玩家最常练的）。 */
        val PRIMARY_CHIPS: List<IntentTarget> =
            listOf(triple(20), triple(19), triple(18), triple(17))

        /** 结镖双倍区。 */
        val CHECKOUT_CHIPS: List<IntentTarget> =
            listOf(double(20), double(16), double(12), double(8))

        /** 其它。 */
        val OTHER_CHIPS: List<IntentTarget> =
            listOf(BULL, triple(16), singleOuter(20))
    }
}
