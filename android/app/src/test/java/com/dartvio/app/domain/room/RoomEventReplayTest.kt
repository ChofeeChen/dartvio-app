package com.dartvio.app.domain.room

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.MatchConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 在线对战的**正确性核心**：同一份事件，必须重放出同一份状态。
 *
 * 这些用例不是为了「覆盖代码」，而是钉住几条一旦破掉就再也说不清的规则：
 * - 顺序由 seq 决定，与到达先后无关（否则两台手机会各自看到不同的比分）；
 * - 非法事件被忽略，而不是让房间打不开（事件是别人写进数据库的，本端拦不住）；
 * - 撤销只回退「最后一手」，且必须是自己投的。
 */
class RoomEventReplayTest {

    private companion object {
        const val ROOM = "123456"
        const val HOST = "wire_host"
        const val GUEST = "wire_guest"

        val T20 = Dart(number = 20, multiplier = 1)
        val TRIPLE_20 = listOf(
            Dart(number = 20, multiplier = 3),
            Dart(number = 20, multiplier = 3),
            Dart(number = 20, multiplier = 3)
        )
    }

    private fun created() = RoomEvent(
        roomId = ROOM,
        seq = 1,
        actorId = HOST,
        type = RoomEventType.ROOM_CREATED,
        payload = RoomEventPayload.RoomCreated(
            name = "房间",
            config = MatchConfig(),
            visibility = RoomVisibility.PUBLIC,
            allowSpectators = true,
            creatorName = "房主",
            creatorAvatar = "HUMAN_1"
        )
    )

    private fun joined(seq: Int = 2) = RoomEvent(
        roomId = ROOM,
        seq = seq,
        actorId = GUEST,
        type = RoomEventType.MEMBER_JOINED,
        payload = RoomEventPayload.MemberJoined("客人", "HUMAN_2")
    )

    private fun started(seq: Int = 3) = RoomEvent(
        roomId = ROOM,
        seq = seq,
        actorId = HOST,
        type = RoomEventType.MATCH_STARTED,
        payload = RoomEventPayload.Empty
    )

    private fun turn(seq: Int, actor: String, darts: List<Dart> = TRIPLE_20) = RoomEvent(
        roomId = ROOM,
        seq = seq,
        actorId = actor,
        type = RoomEventType.TURN_SUBMITTED,
        payload = RoomEventPayload.TurnSubmitted(darts, "turn-$seq")
    )

    private fun undo(seq: Int, actor: String) = RoomEvent(
        roomId = ROOM,
        seq = seq,
        actorId = actor,
        type = RoomEventType.TURN_UNDONE,
        payload = RoomEventPayload.Empty
    )

    private fun scoresOf(state: RoomState): List<Int> =
        RoomMatchRules.snapshot(state.match!!).players.map { it.score }

    // ===== 房间 =====

    @Test
    fun 建房后房主是创建者且已准备() {
        val room = RoomEventReplay.replay(listOf(created())).room!!
        assertEquals(HOST, room.creatorId)
        assertEquals(1, room.members.size)
        assertTrue(room.members.first().isReady)
        assertEquals(RoomStatus.WAITING, room.status)
    }

    @Test
    fun 同一个人重复加入不会变成两个人() {
        val room = RoomEventReplay.replay(listOf(created(), joined(), joined(seq = 3))).room!!
        assertEquals(2, room.members.size)
    }

    @Test
    fun 对局开始后不再接受新成员() {
        // 房间只有房主一人时开局不合法（人数不足），这里用「已开局」状态验证门禁本身：
        // 顺序是先开局再有人来 —— 迟到的人不该被塞进一局已经打起来的比赛。
        val state = RoomEventReplay.replay(listOf(created(), joined(), started(), joined(seq = 4)))
        assertEquals(2, state.room!!.members.size)
    }

    @Test
    fun 房主离开则房间解散() {
        val state = RoomEventReplay.replay(
            listOf(created(), joined(), RoomEvent(ROOM, 3, HOST, RoomEventType.MEMBER_LEFT, RoomEventPayload.Empty))
        )
        assertNull(state.room)
        assertNull(state.match)
    }

    @Test
    fun 客人离开房间仍在() {
        val state = RoomEventReplay.replay(
            listOf(created(), joined(), RoomEvent(ROOM, 3, GUEST, RoomEventType.MEMBER_LEFT, RoomEventPayload.Empty))
        )
        assertNotNull(state.room)
        assertEquals(1, state.room!!.members.size)
    }

    @Test
    fun 非房主开局无效() {
        val state = RoomEventReplay.replay(
            listOf(created(), joined(), RoomEvent(ROOM, 3, GUEST, RoomEventType.MATCH_STARTED, RoomEventPayload.Empty))
        )
        assertEquals(RoomStatus.WAITING, state.room!!.status)
        assertNull(state.match)
    }

    // ===== 对局 =====

    @Test
    fun 乱序到达的事件重放出相同结果() {
        val events = listOf(created(), joined(), started(), turn(4, HOST))
        val first = RoomEventReplay.replay(listOf(created(), joined(), started())).match!!.currentPlayerId
        val ordered = RoomEventReplay.replay(events)
        val shuffled = RoomEventReplay.replay(
            listOf(turn(4, first), started(), joined(), created())
        )

        assertEquals(ordered.match!!.version, shuffled.match!!.version)
        assertEquals(scoresOf(ordered), scoresOf(shuffled))
    }

    @Test
    fun 投镖推进比分并轮转到下一个人() {
        val base = RoomEventReplay.replay(listOf(created(), joined(), started()))
        val first = base.match!!.currentPlayerId

        val after = RoomEventReplay.replay(listOf(created(), joined(), started(), turn(4, first)))
        val snapshot = RoomMatchRules.snapshot(after.match!!)

        assertEquals(501 - 180, snapshot.players.first { it.id == first }.score)
        assertEquals(1, after.match!!.turns.size)
        // 轮到另一个人：这是「下一位投镖」的唯一依据。
        assertTrue(snapshot.players.any { it.isActive && it.id != first })
    }

    @Test
    fun 不该自己投的时候提交被忽略() {
        val base = RoomEventReplay.replay(listOf(created(), joined(), started()))
        val first = base.match!!.currentPlayerId
        val other = if (first == HOST) GUEST else HOST

        val after = RoomEventReplay.replay(listOf(created(), joined(), started(), turn(4, other)))

        // 忽略而不是报错：整局仍然可用，只是那一手没生效。
        assertEquals(0, after.match!!.turns.size)
        assertEquals(501, RoomMatchRules.snapshot(after.match!!).players.first { it.id == other }.score)
    }

    @Test
    fun 撤回自己刚投的那一手会回到原分() {
        val first = RoomEventReplay.replay(listOf(created(), joined(), started())).match!!.currentPlayerId

        val after = RoomEventReplay.replay(
            listOf(created(), joined(), started(), turn(4, first), undo(5, first))
        )

        assertEquals(501, RoomMatchRules.snapshot(after.match!!).players.first { it.id == first }.score)
        assertEquals(0, after.match!!.turns.size)
    }

    @Test
    fun 对手已经投过之后就撤不回上一手() {
        val base = RoomEventReplay.replay(listOf(created(), joined(), started()))
        val first = base.match!!.currentPlayerId
        val second = if (first == HOST) GUEST else HOST

        val after = RoomEventReplay.replay(
            listOf(created(), joined(), started(), turn(4, first), turn(5, second), undo(6, first))
        )

        // 回退点已经被第二手覆盖：此时再撤第一手会让两个人的比分对不上，必须忽略。
        assertEquals(2, after.match!!.turns.size)
    }

    @Test
    fun 重复收到同一条事件不会重复结算() {
        val first = RoomEventReplay.replay(listOf(created(), joined(), started())).match!!.currentPlayerId

        val after = RoomEventReplay.replay(
            listOf(created(), joined(), started(), turn(4, first), turn(4, first))
        )

        // 「写成功后本地先并入」与「随后收到推送」是同一条事件，只能算一次。
        assertEquals(1, after.match!!.turns.size)
    }
}
