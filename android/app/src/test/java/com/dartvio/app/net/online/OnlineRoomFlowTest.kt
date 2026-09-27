package com.dartvio.app.net.online

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.room.RoomEvent
import com.dartvio.app.domain.room.RoomEventPayload
import com.dartvio.app.domain.room.RoomEventReplay
import com.dartvio.app.domain.room.RoomEventType
import com.dartvio.app.domain.room.RoomMatchRules
import com.dartvio.app.domain.room.RoomVisibility
import kotlinx.coroutines.runBlocking
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * 在线链路的**端到端**验证：真的连一次后端，模拟两台手机打完一局。
 *
 * 它存在的理由很具体：`RoomEventReplayTest` 证明的是「给一堆事件，能算出正确的状态」，
 * 但**事件能不能真的写进去、能不能原样读回来**，那条链路谁都没走 ——
 * 而 Postgres 的 jsonb、PostgREST 的筛选语法、RLS 策略，任何一处写错都会让它静默失效
 * （不报错，只是「什么都收不到」），这种错在手机上是查不出来的。
 *
 * 房间用 PRIVATE：它不该出现在用户的大厅列表里。
 */
class OnlineRoomFlowTest {

    private companion object {
        val TRIPLE_20 = listOf(
            Dart(number = 20, multiplier = 3),
            Dart(number = 20, multiplier = 3),
            Dart(number = 20, multiplier = 3)
        )
    }

    @Test
    fun 两台手机打完一回合并撤销() = runBlocking {
        // 没配后端时跳过而不是失败：这个测试验证的是「配好之后能不能通」，
        // 不该让「没配」变成一条红灯。
        Assume.assumeTrue("未配置 Supabase，跳过在线链路验证", OnlineConfig.isConfigured)

        val api = OnlineRoomApi()

        // 配好 ≠ 通。这台机器可能在防火墙后、可能被中间设备掐掉 TLS —— 那时每一个
        // 请求都是 "Connection reset"，而这个测试会把它报成产品缺陷。
        // 网络不可达是**环境问题**：跳过（黄条）而不是失败（红灯），
        // 免得一次网络波动把联机链路永久标记成坏的。
        Assume.assumeTrue(
            "云端不可达（lastFailure=${api.lastFailure}），跳过在线链路验证",
            api.ping()
        )

        val stamp = System.currentTimeMillis()
        val host = "test_host_$stamp"
        val guest = "test_guest_$stamp"

        // 房间号必须随机：早先用「时间戳取模」生成，两次运行相隔整百秒就会撞到同一个号，
        // 而被 unique 挡在门外的表现是「房间索引写不进去」—— 与真的网络故障长一模一样。
        // 撞号在本测试里也可能发生（号空间有限），因此允许换号重试。
        var roomId = newTestRoomId()
        var created = false
        // 失败原因必须留在断言里：只写一句「房间索引写不进去」，
        // 跟「没配后端」「被 RLS 拒绝」「房间号格式不符」长得分毫不差，而三者修法完全不同。
        var lastResult: WriteResult = WriteResult.Failed("未尝试")
        repeat(3) {
            val result = api.insertRoom(
                roomId, "联调测试房", host, MatchConfig(), RoomVisibility.PRIVATE, true
            )
            if (result is WriteResult.Ok) {
                created = true
                return@repeat
            }
            lastResult = result
            roomId = newTestRoomId()
        }
        assertTrue(
            "房间索引写不进去：$lastResult / lastFailure=${api.lastFailure}",
            created
        )
        append(
            api,
            RoomEvent(
                roomId = roomId,
                seq = 1,
                actorId = host,
                type = RoomEventType.ROOM_CREATED,
                payload = RoomEventPayload.RoomCreated(
                    name = "联调测试房",
                    config = MatchConfig(),
                    visibility = RoomVisibility.PRIVATE,
                    allowSpectators = true,
                    creatorName = "房主",
                    creatorAvatar = "HUMAN_1"
                )
            )
        )

        // ===== 第二个人加入 =====
        append(
            api,
            RoomEvent(roomId, 2, guest, RoomEventType.MEMBER_JOINED, RoomEventPayload.MemberJoined("客人", "HUMAN_2"))
        )

        // ===== 开局 =====
        append(api, RoomEvent(roomId, 3, host, RoomEventType.MATCH_STARTED, RoomEventPayload.Empty))

        var state = RoomEventReplay.replay(eventsOf(api, roomId))
        assertEquals("房间里应当有两个人", 2, state.room!!.members.size)
        assertNotNull("对局应当已经建立", state.match)

        // ===== 投一手 =====
        val first = state.match!!.currentPlayerId
        append(
            api,
            RoomEvent(
                roomId,
                4,
                first,
                RoomEventType.TURN_SUBMITTED,
                RoomEventPayload.TurnSubmitted(TRIPLE_20, "turn-1")
            )
        )

        state = RoomEventReplay.replay(eventsOf(api, roomId))
        assertEquals(
            "读回来的事件重放后应当是 501-180",
            501 - 180,
            RoomMatchRules.snapshot(state.match!!).players.first { it.id == first }.score
        )

        // ===== 撤回 =====
        append(api, RoomEvent(roomId, 5, first, RoomEventType.TURN_UNDONE, RoomEventPayload.Empty))
        state = RoomEventReplay.replay(eventsOf(api, roomId))
        assertEquals(
            "撤回之后应当回到 501",
            501,
            RoomMatchRules.snapshot(state.match!!).players.first { it.id == first }.score
        )
    }

    private fun newTestRoomId(): String =
        "t" + UUID.randomUUID().toString().replace("-", "").take(5)

    private suspend fun eventsOf(api: OnlineRoomApi, roomId: String): List<RoomEvent> {
        val events = api.fetchEvents(roomId)
        assertNotNull("拉取事件失败：${api.lastFailure}", events)
        return events!!
    }

    private suspend fun append(api: OnlineRoomApi, event: RoomEvent) {
        // 重试：这个测试走的是真实网络，偶发超时不该让「联调」变成一条红灯。
        // 生产代码里同样的重试在 OnlineRoomRepository.write 中。
        var result: WriteResult = WriteResult.Failed("未尝试")
        repeat(3) { attempt ->
            result = api.insertEvent(event)
            if (result is WriteResult.Ok) return
        }
        assertTrue(
            "事件写不进去：${if (result is WriteResult.Failed) result.message else result}",
            result is WriteResult.Ok
        )
    }
}
