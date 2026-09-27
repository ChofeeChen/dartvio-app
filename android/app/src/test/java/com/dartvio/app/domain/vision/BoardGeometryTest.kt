package com.dartvio.app.domain.vision

import com.dartvio.app.domain.model.Dart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 靶面几何映射的纯 JVM 单测（无 Android 依赖）。
 * 对应《M12 技术预研（PoC）计划》§11.3 的 P0：几何先行验证。
 */
class BoardGeometryTest {

    @Test
    fun `靶心与牛眼`() {
        assertEquals(Dart.INNER_BULL, BoardGeometry.dartAt(0.0, 0.0))
        assertEquals(Dart.OUTER_BULL, BoardGeometry.dartAt(0.0, 10.0))
    }

    @Test
    fun `12点方向为20分区`() {
        assertEquals(Dart(20, 1), BoardGeometry.dartAt(0.0, 50.0))
        assertEquals(Dart(20, 3), BoardGeometry.dartAt(0.0, 103.0))
        assertEquals(Dart(20, 2), BoardGeometry.dartAt(0.0, 166.0))
        assertEquals(20, BoardGeometry.sectorAt(0.0, 50.0))
    }

    @Test
    fun `6点方向为3分区`() {
        assertEquals(3, BoardGeometry.sectorAt(0.0, -50.0))
    }

    @Test
    fun `顺时针18度为1分区`() {
        val rad = Math.toRadians(18.0)
        val x = 50.0 * kotlin.math.sin(rad)
        val y = 50.0 * kotlin.math.cos(rad)
        assertEquals(1, BoardGeometry.sectorAt(x, y))
    }

    @Test
    fun `三倍环与双倍环判定`() {
        assertEquals(3, BoardGeometry.dartAt(0.0, 103.0).multiplier)
        assertEquals(2, BoardGeometry.dartAt(0.0, 166.0).multiplier)
        assertEquals(1, BoardGeometry.dartAt(0.0, 130.0).multiplier)
        // 环外沿（170 mm）仍算双倍，超出即 MISS
        assertEquals(Dart(20, 2), BoardGeometry.dartAt(0.0, 170.0))
        assertEquals(Dart.MISS, BoardGeometry.dartAt(0.0, 170.5))
    }

    @Test
    fun `出靶为MISS`() {
        assertEquals(Dart.MISS, BoardGeometry.dartAt(0.0, 200.0))
        assertEquals(Dart.MISS, BoardGeometry.dartAt(0.0, -171.0))
        assertEquals(Dart.MISS, BoardGeometry.dartAt(Double.NaN, 0.0))
    }

    @Test
    fun `分区序列覆盖1到20且不重复`() {
        val set = BoardGeometry.SECTOR_ORDER.toSet()
        assertEquals(20, set.size)
        assertTrue((1..20).all { it in set })
    }
}
