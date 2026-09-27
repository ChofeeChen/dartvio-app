package com.dartvio.app.domain.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 单应矩阵求解与映射的纯 JVM 单测。
 * 对应《M12 技术预研（PoC）计划》§11.3 的 P0 / P1：标定与映射先行验证。
 */
class HomographyTest {

    @Test
    fun `纯缩放平移映射`() {
        val src = listOf(
            Point2(0.0, 0.0), Point2(100.0, 0.0), Point2(100.0, 100.0), Point2(0.0, 100.0),
        )
        val dst = listOf(
            Point2(0.0, 0.0), Point2(340.0, 0.0), Point2(340.0, 340.0), Point2(0.0, 340.0),
        )
        val h = Homography.fromFourPoints(src, dst)
        assertNotNull(h)
        val p = h!!.map(Point2(50.0, 50.0))
        assertEquals(170.0, p.x, 1e-6)
        assertEquals(170.0, p.y, 1e-6)
    }

    @Test
    fun `透视映射往返一致`() {
        val src = listOf(
            Point2(12.0, 8.0), Point2(240.0, 20.0), Point2(230.0, 300.0), Point2(20.0, 280.0),
        )
        val dst = listOf(
            Point2(0.0, 0.0), Point2(340.0, 0.0), Point2(340.0, 340.0), Point2(0.0, 340.0),
        )
        val forward = Homography.fromFourPoints(src, dst)!!
        val backward = Homography.fromFourPoints(dst, src)!!

        val p = Point2(120.0, 90.0)
        val q = backward.map(forward.map(p))
        assertEquals(p.x, q.x, 1e-6)
        assertEquals(p.y, q.y, 1e-6)
    }

    @Test
    fun `标定残差可量化`() {
        // 图像四角 → 靶面四角（已知真值），检验标定点的反投影误差为 0
        val src = listOf(
            Point2(0.0, 0.0), Point2(300.0, 0.0), Point2(300.0, 300.0), Point2(0.0, 300.0),
        )
        val dst = listOf(
            Point2(-170.0, 170.0), Point2(170.0, 170.0), Point2(170.0, -170.0), Point2(-170.0, -170.0),
        )
        val h = Homography.fromFourPoints(src, dst)!!
        for (i in src.indices) {
            val back = h.map(src[i])
            val dx = back.x - dst[i].x
            val dy = back.y - dst[i].y
            assertEquals(0.0, kotlin.math.hypot(dx, dy), 1e-6)
        }
        // 映射后的靶面中心应落在靶心（0,0）附近
        val center = h.map(Point2(150.0, 150.0))
        assertEquals(0.0, center.x, 1e-6)
        assertEquals(0.0, center.y, 1e-6)
    }

    @Test
    fun `退化输入返回null`() {
        val degenerateSrc = listOf(
            Point2(0.0, 0.0), Point2(0.0, 0.0), Point2(0.0, 0.0), Point2(0.0, 0.0),
        )
        val dst = listOf(
            Point2(0.0, 0.0), Point2(1.0, 0.0), Point2(1.0, 1.0), Point2(0.0, 1.0),
        )
        assertNull(Homography.fromFourPoints(degenerateSrc, dst))
    }
}
