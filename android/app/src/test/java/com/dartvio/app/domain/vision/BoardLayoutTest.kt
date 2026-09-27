package com.dartvio.app.domain.vision

import com.dartvio.app.domain.model.Dart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画布 ↔ 靶面换算的纯 JVM 单测（无 Android 依赖）。
 *
 * 这里覆盖的是 [BoardGeometryTest] **没有覆盖**的那一层：
 * 判分（mm → Dart）已经测过，而「点在盘的上方到底是不是 20 区」取决于这里的坐标轴方向 ——
 * y 轴一旦写反，上方会判成 3 区，屏幕上看不出错，数据却全反了。
 */
class BoardLayoutTest {

    private val side = 340f

    @Test
    fun `画布中心是靶心`() {
        assertEquals(Dart.INNER_BULL, BoardGeometry.dartAt(BoardLayout.toBoardMm(side, 170f, 170f)))
    }

    @Test
    fun `点正上方得到20分区`() {
        // 画布 y 轴向下 ⇒ 越靠上 offsetY 越小；坐标轴写反的话，这里会判成 3 区。
        val top = BoardLayout.toBoardMm(side, 170f, 40f)
        assertTrue(top.y > 0)
        assertEquals(20, BoardGeometry.sectorAt(top.x, top.y))
    }

    @Test
    fun `20区三倍环上的点判为T20`() {
        // 170 - 103mm × 0.872 px/mm ≈ 80px，取 80 正好落在三倍环（99~107mm）内。
        assertEquals(Dart(20, 3), BoardGeometry.dartAt(BoardLayout.toBoardMm(side, 170f, 80f)))
    }

    @Test
    fun `点正下方得到3分区`() {
        val bottom = BoardLayout.toBoardMm(side, 170f, 300f)
        assertTrue(bottom.y < 0)
        assertEquals(3, BoardGeometry.sectorAt(bottom.x, bottom.y))
    }

    @Test
    fun `点右上得到1分区`() {
        // 自 12 点方向顺时针第一个分区。
        val rightUp = BoardLayout.toBoardMm(side, 210f, 90f)
        assertTrue(rightUp.x > 0 && rightUp.y > 0)
        assertEquals(1, BoardGeometry.sectorAt(rightUp.x, rightUp.y))
    }

    @Test
    fun `点出圆环判MISS`() {
        val corner = BoardLayout.toBoardMm(side, 335f, 5f)
        assertEquals(Dart.MISS, BoardGeometry.dartAt(corner))
    }

    @Test
    fun `mm与画布坐标可往返`() {
        val source = Point2(48.0, -103.0)
        val px = BoardLayout.toCanvasPx(side, source.x, source.y)
        val back = BoardLayout.toBoardMm(side, px.x.toFloat(), px.y.toFloat())
        assertEquals(source.x, back.x, 0.01)
        assertEquals(source.y, back.y, 0.01)
    }

    @Test
    fun `数字环不侵占计分区`() {
        assertTrue(BoardLayout.NUMBER_RADIUS_MM > BoardGeometry.DOUBLE_OUTER_RADIUS_MM)
        assertTrue(BoardLayout.NUMBER_RADIUS_MM < BoardLayout.outerMm())
    }
}
