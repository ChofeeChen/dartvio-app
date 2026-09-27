package com.dartvio.app.domain.room

import com.dartvio.app.domain.model.MatchConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「房间名 / 玩家昵称 / PPR」这三件事各走各的路（2026-09-27 真机反馈）。
 *
 * 真机上的表现是：房间名叫「茄子的房间1」，而玩家昵称也叫「茄子」——
 * 卡片上两个名字挤在一起，读的人分不清「这一局叫什么」和「谁开的」；
 * 更要紧的是**同一个人**在自己的手机上与在别人手机上显示成两个名字
 * （本机档案与大档档案各存一份），而这类 bug 不报错、只是安静地错。
 *
 * 这里钉的是**数据分工**：房间名在 `Room.name`、昵称在 `RoomMember.name`、
 * 实力在 `RoomMember.ppr` —— 三者互不越界，谁也别去顶替谁。
 */
class RoomNamePprTest {

    private val names = listOf("茄子", "大蒜头")

    private fun created(roomName: String = "茄子的房间1", creatorPpr: Double = 0.0): RoomEvent =
        RoomEvent(
            roomId = "room-1",
            seq = 1,
            actorId = "wire-host",
            type = RoomEventType.ROOM_CREATED,
            payload = RoomEventPayload.RoomCreated(
                name = roomName,
                config = MatchConfig(),
                visibility = RoomVisibility.PUBLIC,
                allowSpectators = true,
                creatorName = names[0],
                creatorAvatar = "HUMAN_1",
                creatorPpr = creatorPpr,
            ),
        )

    private fun joined(ppr: Double): RoomEvent =
        RoomEvent(
            roomId = "room-1",
            seq = 2,
            actorId = "wire-guest",
            type = RoomEventType.MEMBER_JOINED,
            payload = RoomEventPayload.MemberJoined(name = names[1], avatar = "HUMAN_2", ppr = ppr),
        )

    @Test
    fun 房间名与房主昵称是两个字段互不顶替() {
        val state = RoomEventReplay.replay(listOf(created()))
        val room = state.room!!

        assertEquals("茄子的房间1", room.name)
        assertEquals("茄子", room.members.single().name)
        // 房间名里恰好夹着房主昵称，但两者**各自独立**：改昵称不该动房间名，反之亦然。
        assertTrue(room.name.contains(room.members.single().name))
        assertEquals("茄子的房间1", room.copy(members = room.members.map { it.copy(name = "西瓜") }).name)
    }

    @Test
    fun 加入者带来的PPR挂到成员上() {
        val state = RoomEventReplay.replay(listOf(created(), joined(ppr = 42.5)))
        val guest = state.room!!.members.last { !it.isCreator }

        assertEquals("大蒜头", guest.name)
        assertEquals(42.5, guest.ppr!!, 0.001)
        // 房主自己建房时事件里不带 PPR（那一份走索引行的 host_ppr），因此是 null 而不是 0。
        assertNull(state.room!!.members.first().ppr)
    }

    @Test
    fun 老端事件没有PPR时读成null而不是0() {
        // 老端写下的 `member_joined` 没有 ppr 字段 → 反序列化取默认值 0.0。
        // 0 必须被读成「没有」，否则卡片上会显示「PPR 0.0」—— 那会被读成「这个人很菜」。
        val legacy = RoomEvent(
            roomId = "room-1",
            seq = 2,
            actorId = "wire-guest",
            type = RoomEventType.MEMBER_JOINED,
            payload = RoomEventPayload.MemberJoined(name = "大蒜头", avatar = "HUMAN_2"),
        )
        val state = RoomEventReplay.replay(listOf(created(), legacy))
        assertNull(state.room!!.members.last { !it.isCreator }.ppr)
    }

    @Test
    fun 对局卡的PPR按线上id注入身份投影之前() {
        val snapshot = SpectatorSnapshot(
            roomId = "room-1",
            roomName = "茄子的房间1",
            configName = "X01 - 501",
            leg = 1,
            legsToWin = 3,
            players = listOf(
                SpectatorPlayer("wire-host", "茄子", "HUMAN_1", legsWon = 0, score = 501),
                SpectatorPlayer("wire-guest", "大蒜头", "HUMAN_2", legsWon = 1, score = 360),
            ),
            turns = emptyList(),
        )

        val filled = snapshot.withPpr(mapOf("wire-host" to 51.2, "wire-guest" to 42.5))
        assertEquals(51.2, filled.players[0].ppr!!, 0.001)
        assertEquals(42.5, filled.players[1].ppr!!, 0.001)
        // 昵称不动：这张卡要区分「这局几分」与「这个人什么水平」，不能互相覆盖。
        assertEquals("茄子", filled.players[0].name)
        assertEquals(1, filled.players[1].legsWon)

        // 匹配不上 / 非正数 → 保持原样（null 或已有值），不写 0。
        assertEquals(snapshot, snapshot.withPpr(emptyMap()))
        assertEquals(snapshot, snapshot.withPpr(mapOf("别的人" to 30.0)))
        assertEquals(snapshot, snapshot.withPpr(mapOf("wire-host" to 0.0)))
    }
}
