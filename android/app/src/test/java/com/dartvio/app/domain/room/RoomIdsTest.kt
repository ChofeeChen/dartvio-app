package com.dartvio.app.domain.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 6 位房间号生成的边界单测。
 *
 * 房间号是唯一的加入凭证：**重复 = 加进别人的房间**，属于不崩溃的静默错误。
 * 因此这里把三件事钉死：格式、避让、退化随机源下必须抛错而不是死循环。
 */
class RoomIdsTest {

    // ===== 格式 =====

    @Test
    fun `六位纯数字是合法房间号`() {
        assertTrue(RoomIds.isValid("384712"))
        assertTrue(RoomIds.isValid("100000"))
        assertTrue(RoomIds.isValid("999999"))
    }

    @Test
    fun `位数不对或含非数字都非法`() {
        assertFalse("5 位", RoomIds.isValid("38471"))
        assertFalse("7 位", RoomIds.isValid("3847123"))
        assertFalse("空串", RoomIds.isValid(""))
        assertFalse("含字母", RoomIds.isValid("38471a"))
        assertFalse("含空格", RoomIds.isValid("384 71"))
        assertFalse("全角数字", RoomIds.isValid("３８４７１２"))
    }

    // ===== 取值范围 =====

    @Test
    fun `生成范围恒为六位且不含前导零`() {
        var from = -1
        var until = -1
        val id = RoomIds.newRoomId(emptySet()) { f, u ->
            from = f
            until = u
            100_000
        }

        assertEquals("下界必须是 100000，否则会出现前导零导致房间号不足 6 位", 100_000, from)
        assertEquals("上界必须不含 1000000（那是 7 位）", 1_000_000, until)
        assertEquals("100000", id)
        assertEquals("生成了非 6 位的房间号", RoomIds.LENGTH, id.length)
        assertTrue(RoomIds.isValid(id))
    }

    // ===== 避让 =====

    @Test
    fun `生成时跳过已占用的房间号`() {
        val occupied = setOf("100000")
        val source = ArrayDeque(listOf(100_000, 100_001))

        val generated = RoomIds.newRoomId(occupied) { _, _ -> source.removeFirst() }

        assertEquals("必须跳过已占用号码", "100001", generated)
        assertTrue(source.isEmpty())
    }

    @Test
    fun `连续冲突时仍能在上限内找回未占用号码`() {
        // 占用 100000..100019（20 个），随机源从 100000 起逐次 +1：
        // 前 20 次全撞、第 21 次命中 100020，仍在上限内。
        val occupied = (100_000 until 100_020).map(Int::toString).toSet()
        var cursor = 100_000

        val generated = RoomIds.newRoomId(occupied) { _, _ -> cursor++ }

        assertFalse("生成结果落在已占用集合里", generated in occupied)
        assertEquals("100020", generated)
        assertEquals("说明重试没有提前放弃", 100_020, cursor - 1)
        assertTrue(RoomIds.isValid(generated))
    }

    @Test
    fun `重试上限小于极端占用规模时宁可直接报错`() {
        // 边界意识：MAX_ATTEMPTS 是「防御退化随机源」的护栏，不是「保证一定能生成」的承诺。
        // 号码池被占用到撞满 32 次时抛错是正确的（此时房源空间也确实接近枯竭）。
        val occupied = (100_000 until 100_000 + RoomIds.MAX_ATTEMPTS).map(Int::toString).toSet()
        var cursor = 100_000

        assertThrows(IllegalStateException::class.java) {
            RoomIds.newRoomId(occupied) { _, _ -> cursor++ }
        }
    }

    // ===== 退化随机源 =====

    @Test
    fun `随机源持续冲突时到达上限即抛错而不是死循环`() {
        val occupied = setOf("384712")
        var calls = 0

        val error = assertThrows(IllegalStateException::class.java) {
            RoomIds.newRoomId(occupied) { _, _ ->
                calls++
                384_712
            }
        }

        assertEquals("重试次数必须等于上限" + RoomIds.MAX_ATTEMPTS, RoomIds.MAX_ATTEMPTS, calls)
        assertTrue(
            "异常信息需要包含重试上限，便于线上定位：${error.message}",
            error.message.orEmpty().contains(RoomIds.MAX_ATTEMPTS.toString())
        )
    }

    @Test
    fun `上限内命中即可返回`() {
        var calls = 0
        val generated = RoomIds.newRoomId(setOf("100000")) { _, _ ->
            calls++
            if (calls < RoomIds.MAX_ATTEMPTS) 100_000 else 200_000
        }

        assertEquals("200000", generated)
        assertEquals(RoomIds.MAX_ATTEMPTS, calls)
    }
}
