package com.dartvio.app.domain.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 等待房的 5 分钟有效期（PRD A7–A9）。
 *
 * 这些规则看着简单，但每一条都对应一种**用户看得见**的状态，而且它们互相之间
 * 的边界极易写反：把「已满员」和「已过期」都写成「剩 0 秒」，界面上就是
 * 「房间满了却显示 0:00」和「房间早散了还挂在列表上」——两种都很像 bug。
 */
class RoomExpiryTest {

    private companion object {
        const val NOW = 1_000_000_000L
    }

    @Test
    fun 建房时写入的到期时刻是五分钟后() {
        assertEquals(NOW + 5 * 60_000L, RoomExpiry.deadline(NOW))
    }

    @Test
    fun 等待中且未满员时显示倒计时() {
        val left = RoomExpiry.remainingMs(expiresAt = NOW + 90_000L, now = NOW, full = false)
        assertEquals(90_000L, left)
    }

    @Test
    fun 满员后停止倒计时() {
        // 满员之后再数下去，等于给一场马上要开始的比赛倒计时。
        assertNull(RoomExpiry.remainingMs(expiresAt = NOW + 90_000L, now = NOW, full = true))
    }

    @Test
    fun 已经过期就不再显示倒计时() {
        // 「不显示」而不是「显示 0:00」：过期的卡片应当整张消失（A7）。
        assertNull(RoomExpiry.remainingMs(expiresAt = NOW - 1_000L, now = NOW, full = false))
    }

    @Test
    fun 老房间没有到期时刻就不倒计时() {
        assertNull(RoomExpiry.remainingMs(expiresAt = null, now = NOW, full = false))
    }

    @Test
    fun 剩余六十秒起进入告警档() {
        assertTrue(RoomExpiry.isWarning(60_000L))
        assertTrue(RoomExpiry.isWarning(1_000L))
        assertFalse(RoomExpiry.isWarning(61_000L))
    }

    @Test
    fun 倒计时文案向上取整() {
        // 向上取整：刚建完房显示「5:00」而不是「4:59」，后者看着像已经过了一分钟。
        assertEquals("5:00", RoomExpiry.label(300_000L))
        assertEquals("1:30", RoomExpiry.label(90_000L))
        assertEquals("0:08", RoomExpiry.label(8_000L))
        // 9.4 秒显示「0:10」：倒计时只会往下走，向上取整才不会在还差一点时先跳到 0。
        assertEquals("0:10", RoomExpiry.label(9_400L))
        assertEquals("0:01", RoomExpiry.label(1L))
    }

    @Test
    fun 到期判定不含恰好等于的边界之外的情况() {
        assertFalse(RoomExpiry.isExpired(null, NOW))
        assertFalse(RoomExpiry.isExpired(NOW + 1L, NOW))
        assertTrue(RoomExpiry.isExpired(NOW, NOW))
        assertTrue(RoomExpiry.isExpired(NOW - 1L, NOW))
    }
}
