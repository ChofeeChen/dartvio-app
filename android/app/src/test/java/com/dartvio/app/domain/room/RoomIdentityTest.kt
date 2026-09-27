package com.dartvio.app.domain.room

import com.dartvio.app.domain.model.MatchConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「我是谁」投影的接口契约单测。
 *
 * 这一层如果写错，表现是**不崩溃但显示错误**：房主看到自己是客人、两个成员都标着「（你）」、
 * 或者点「准备」改到了别人身上。因此这里全部用**不变式**断言，而不是逐个字段比对。
 */
class RoomIdentityTest {

    private val hostWire = "wire-host-4f2a"
    private val guestWire = "wire-guest-91bd"
    private val absentWire = "wire-absent-0000"

    private fun room() = Room(
        id = "384712",
        name = "测试房",
        creatorId = hostWire,
        config = MatchConfig(),
        members = listOf(
            RoomMember(hostWire, "房主", "HUMAN_1", isCreator = true, isReady = true),
            RoomMember(guestWire, "客人", "HUMAN_2")
        ),
        createdAt = 0L
    )

    /** 不变式 1：房间里至多出现 1 个「我」。 */
    private fun assertAtMostOneSelf(room: Room) {
        assertTrue(
            "房间里出现了多份「我」，说明线上身份与 UI 别名撞了：${room.members.map { it.id }}",
            room.members.count { it.id == LocalUser.ID } <= 1
        )
    }

    /** 不变式 3：除我自己外，没有人可能叫 [LocalUser].`ID`。 */
    private fun assertNoImpostorSelf(room: Room) {
        val selfIndex = room.members.indexOfFirst { it.id == LocalUser.ID }
        if (selfIndex < 0) return
        val others = room.members.filterIndexed { index, _ -> index != selfIndex }
        assertTrue(
            "别人的 id 不应等于 UI 别名：${others.map { it.id }}",
            others.none { it.id == LocalUser.ID }
        )
    }

    // ===== 房主视角 =====

    @Test
    fun `房主视角投影后自己是房主`() {
        val projected = room().withLocalIdentity(hostWire)

        assertEquals(LocalUser.ID, projected.creatorId)
        assertTrue("UI 判定房主用的是 creatorId == LocalUser.ID", projected.creatorId == LocalUser.ID)
        assertAtMostOneSelf(projected)
        assertNoImpostorSelf(projected)
    }

    @Test
    fun `房主视角下客人保持线上身份`() {
        val projected = room().withLocalIdentity(hostWire)

        val guest = projected.members.first { it.name == "客人" }
        assertEquals("客人不是我，id 必须原样保留", guestWire, guest.id)
    }

    // ===== 客人视角 =====

    @Test
    fun `客人视角投影后不是房主但房间里恰好有一个我`() {
        val projected = room().withLocalIdentity(guestWire)

        assertFalse("客人不应被判成房主", projected.creatorId == LocalUser.ID)
        assertEquals("房主仍是线上身份", hostWire, projected.creatorId)
        assertEquals(1, projected.members.count { it.id == LocalUser.ID })
        assertAtMostOneSelf(projected)
        assertNoImpostorSelf(projected)
    }

    @Test
    fun `客人视角下房主名下的成员条数不变`() {
        val projected = room().withLocalIdentity(guestWire)

        assertEquals(room().members.size, projected.members.size)
        assertEquals("改名不该动昵称", listOf("房主", "客人"), projected.members.map { it.name })
    }

    // ===== 边界 =====

    @Test
    fun `我不在房间里时原样返回`() {
        val original = room()

        val projected = original.withLocalIdentity(absentWire)

        assertEquals("房主浏览大厅列表时不该发生任何改写", original, projected)
        assertTrue(projected === original)
    }

    @Test
    fun `投影是幂等的`() {
        val once = room().withLocalIdentity(guestWire)

        assertEquals(once, once.withLocalIdentity(guestWire))
    }

    @Test
    fun `把 UI 别名当成线上身份时立刻报错`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            room().withLocalIdentity(LocalUser.ID)
        }

        assertTrue(
            "异常信息要指出这是线上身份与 UI 别名混用：${error.message}",
            error.message.orEmpty().contains(LocalUser.ID)
        )
    }

    // ===== 出站方向 =====

    @Test
    fun `出站映射把 UI 别名翻译回线上身份`() {
        assertEquals(absentWire, toWireMemberId(LocalUser.ID, absentWire))
        assertEquals("别人的 id 原样透传", guestWire, toWireMemberId(guestWire, absentWire))
    }

    @Test
    fun `出站映射与投影互逆`() {
        val wire = room()
        val projected = wire.withLocalIdentity(guestWire)

        val restored = projected.copy(
            creatorId = toWireMemberId(projected.creatorId, guestWire),
            members = projected.members.map { it.copy(id = toWireMemberId(it.id, guestWire)) }
        )

        assertEquals("投影 → 出站映射必须回到原始房间", wire, restored)
    }
}
