package com.dartvio.app.net.online

import com.dartvio.app.domain.room.RoomJoinSource
import com.dartvio.app.domain.room.RoomLiveState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 大厅快照的编解码。
 *
 * 这里守的是**兼容性**：表是追加式的，里面混着新旧两端写进来的行。缺字段的行
 * 必须仍能解出一条快照（取默认值），否则某个房间会「突然没有比分」——
 * 看起来像随机 bug，实际是旧端少写了两个字段。
 */
class RoomStateCodecTest {

    @Test
    fun `encode 后 decode 回到原值`() {
        val state = RoomLiveState(
            roomId = "123456",
            seq = 7,
            hostId = "dv_host",
            hostName = "ABC",
            guestId = "dv_guest",
            guestName = "XYZ",
            legsHost = 1,
            legsGuest = 2,
            scoreHost = 320,
            scoreGuest = 60,
            round = 9,
            legsToWin = 3,
            playing = true
        )
        val decoded = RoomStateCodec.decodeRow(RoomStateCodec.encodeRow(state))
        assertEquals(state, decoded)
    }

    @Test
    fun `缺字段的行取默认值而不是整条丢弃`() {
        val row = buildJsonObject {
            put("room_id", "123456")
            put("seq", 1)
            put("state", buildJsonObject { put("host_name", "ABC") })
        }
        val decoded = RoomStateCodec.decodeRow(row)
        assertEquals(RoomLiveState(roomId = "123456", seq = 1, hostName = "ABC"), decoded)
    }

    @Test
    fun `结构不对的行返回 null`() {
        assertNull(RoomStateCodec.decodeRow(buildJsonObject { put("seq", 1) }))
    }

    @Test
    fun `局制文案随 legsToWin 变化`() {
        assertEquals("1 局定胜负", RoomLiveState(roomId = "1", seq = 0, legsToWin = 1).legsLabel)
        assertEquals("先到 3 局", RoomLiveState(roomId = "1", seq = 0, legsToWin = 3).legsLabel)
    }

    @Test
    fun `来源字段写了就带回来，老端没写就是空`() {
        val encoded = RoomStateCodec.encodeRow(
            RoomLiveState(roomId = "1", seq = 1, source = RoomJoinSource.LOBBY.key)
        )
        assertEquals("lobby", RoomStateCodec.decodeRow(encoded)!!.source)

        val legacy = RoomStateCodec.decodeRow(
            buildJsonObject {
                put("room_id", "1")
                put("seq", 1)
                put("state", buildJsonObject { put("host_name", "甲") })
            }
        )!!
        // 空串 = 老端写的行，按房间号处理（不猜）。
        assertEquals("", legacy.source)
        assertEquals(RoomJoinSource.CODE, RoomJoinSource.fromKey(legacy.source))
    }

    @Test
    fun `行数组只保留每个房间最新的一条`() {
        // 表是 append-only 的：同一个房间会有多条，取 seq 最大的一条。
        val rows = JsonArray(
            listOf(
                RoomStateCodec.encodeRow(RoomLiveState(roomId = "1", seq = 1, scoreHost = 100)),
                RoomStateCodec.encodeRow(RoomLiveState(roomId = "1", seq = 3, scoreHost = 40)),
                RoomStateCodec.encodeRow(RoomLiveState(roomId = "2", seq = 1, scoreHost = 501))
            )
        )
        val latest = LinkedHashMap<String, RoomLiveState>()
        rows.mapNotNull { RoomStateCodec.decodeRow(it) }.forEach { state ->
            val current = latest[state.roomId]
            if (current == null || state.seq > current.seq) latest[state.roomId] = state
        }
        assertEquals(40, latest["1"]?.scoreHost)
        assertEquals(501, latest["2"]?.scoreHost)
    }
}
