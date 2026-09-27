package com.dartvio.app.domain.room

import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.X01LegState
import com.dartvio.app.domain.model.X01PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 房间事实 → 大厅快照（M2：大厅实时比分）。
 *
 * 这里守的是「大厅上那个数字**是什么意思**」：
 * 「剩余 501」代表还没开局，不是「他打得很差」；局数取跨局累计，不是本局内的计数。
 * 这些取值错了不会崩溃，只会让大厅看起来像有人的房间其实没人打。
 */
class RoomLobbyStateTest {

    private val config = MatchConfig(targetScore = 501, legsToWin = 3)

    private fun room(status: RoomStatus = RoomStatus.WAITING) = Room(
        id = "123456",
        name = "甲的房间",
        creatorId = "dv_host",
        config = config,
        status = status,
        members = listOf(
            RoomMember("dv_host", "甲", "HUMAN_1", isCreator = true),
            RoomMember("dv_guest", "乙", "HUMAN_2")
        )
    )

    private fun match(
        room: Room,
        remainingHost: Int = 501,
        remainingGuest: Int = 501,
        roundsCompleted: Int = 0,
        legsWon: Map<String, Int> = emptyMap(),
        finished: Boolean = false
    ) = RoomMatch(
        roomId = room.id,
        roomName = room.name,
        config = config,
        members = room.members,
        leg = X01LegState(
            config = config,
            players = listOf(
                X01PlayerState("dv_host", remainingHost),
                X01PlayerState("dv_guest", remainingGuest)
            ),
            currentPlayerIndex = 0,
            roundsCompleted = roundsCompleted
        ),
        legsWon = legsWon,
        isFinished = finished
    )

    @Test
    fun `没开局时显示起始分，轮数为 0`() {
        val state = RoomState(room = room(), match = null)
        val live = RoomLobbyState.of(state, seq = 1)!!
        assertEquals(501, live.scoreHost)
        assertEquals(501, live.scoreGuest)
        assertEquals(0, live.round)
        assertTrue(!live.playing)
    }

    @Test
    fun `对局中显示本局剩余分与当前轮数`() {
        val r = room(status = RoomStatus.PLAYING)
        val state = RoomState(
            room = r,
            match = match(r, remainingHost = 320, remainingGuest = 260, roundsCompleted = 4)
        )
        val live = RoomLobbyState.of(state, seq = 9)!!
        assertEquals(320, live.scoreHost)
        assertEquals(260, live.scoreGuest)
        // 已完成 4 轮 ⇒ 现在打的是第 5 轮。
        assertEquals(5, live.round)
        assertTrue(live.playing)
    }

    @Test
    fun `局数取跨局累计，不取本局内的计数`() {
        val r = room(status = RoomStatus.PLAYING)
        val state = RoomState(
            room = r,
            match = match(r, legsWon = mapOf("dv_host" to 2, "dv_guest" to 1))
        )
        val live = RoomLobbyState.of(state, seq = 5)!!
        assertEquals(2, live.legsHost)
        assertEquals(1, live.legsGuest)
    }

    @Test
    fun `结束的对局不再算作进行中，轮数归零`() {
        val r = room(status = RoomStatus.PLAYING)
        val state = RoomState(
            room = r,
            match = match(r, roundsCompleted = 7, finished = true)
        )
        val live = RoomLobbyState.of(state, seq = 20)!!
        assertTrue(!live.playing)
        assertEquals(0, live.round)
    }

    @Test
    fun `没有对手时客人侧留空`() {
        val r = room().copy(members = listOf(RoomMember("dv_host", "甲", "HUMAN_1", isCreator = true)))
        val live = RoomLobbyState.of(RoomState(room = r), seq = 1)!!
        assertEquals("dv_host", live.hostId)
        assertEquals("甲", live.hostName)
        assertEquals("", live.guestId)
        assertEquals("", live.guestName)
    }

    @Test
    fun `来源落进快照，缺省按房间号`() {
        val r = room()
        val live = RoomLobbyState.of(
            RoomState(room = r),
            seq = 1,
            source = RoomJoinSource.LOBBY
        )!!
        assertEquals(RoomJoinSource.LOBBY, RoomJoinSource.fromKey(live.source))
        assertEquals(RoomJoinSource.CODE, RoomJoinSource.fromKey(""))
    }

    @Test
    fun `房间还不成立时不产出快照`() {
        assertNull(RoomLobbyState.of(RoomState(), seq = 1))
    }
}
