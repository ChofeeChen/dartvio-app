package com.dartvio.app.domain.room

import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.OutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 房间纯规则的边界单测。
 *
 * 联网后这些判定由服务端权威执行，所以一旦写错，症状是「两端各执一词」：
 * A 看到房间满、B 看到可以加入；或房主点了「开始比赛」而服务端拒绝。
 * 因此这里的重点不是覆盖分支，而是**把顺序与不变式钉死**。
 */
class RoomRulesTest {

    private val hostId = "wire-host"
    private val guestId = "wire-guest"

    private fun hostMember() = RoomMember(hostId, "房主", "HUMAN_1", isCreator = true, isReady = true)

    private fun guestMember(id: String = guestId, ready: Boolean = false) =
        RoomMember(id, "客人$id", "HUMAN_2", isReady = ready)

    private fun room(
        members: List<RoomMember> = listOf(hostMember()),
        status: RoomStatus = RoomStatus.WAITING,
        visibility: RoomVisibility = RoomVisibility.PUBLIC,
        createdAt: Long = 0L,
        id: String = "384712"
    ) = Room(
        id = id,
        name = "测试房",
        creatorId = hostId,
        config = MatchConfig(),
        visibility = visibility,
        status = status,
        members = members,
        createdAt = createdAt
    )

    // ===== 加入判定顺序 =====

    @Test
    fun `房号不存在时报房间不存在`() {
        assertEquals(JoinResult.RoomNotFound, RoomRules.join(null, guestMember()))
    }

    @Test
    fun `已在房内优先于其它条件`() {
        val playing = room(members = listOf(hostMember(), guestMember()), status = RoomStatus.PLAYING)

        assertEquals(
            "已在房内时不该报「对局已开始」",
            JoinResult.AlreadyJoined,
            RoomRules.join(playing, guestMember())
        )
    }

    @Test
    fun `已开局优先于已满`() {
        val full = room(
            members = List(Room.MAX_MEMBERS) { index ->
                if (index == 0) hostMember() else guestMember("wire-g$index")
            },
            status = RoomStatus.PLAYING
        )

        assertTrue("前置：房间确实已满", full.isFull)
        assertEquals(
            "同时满员且已开局时，应报更接近真实原因的「已开始」",
            JoinResult.MatchStarted,
            RoomRules.join(full, guestMember())
        )
    }

    @Test
    fun `满员时拒绝加入`() {
        val full = room(
            members = List(Room.MAX_MEMBERS) { index ->
                if (index == 0) hostMember() else guestMember("wire-g$index")
            }
        )

        assertEquals(JoinResult.RoomFull, RoomRules.join(full, guestMember()))
    }

    @Test
    fun `差一人的满员边界仍可加入`() {
        val almostFull = room(
            members = List(Room.MAX_MEMBERS - 1) { index ->
                if (index == 0) hostMember() else guestMember("wire-g$index")
            }
        )

        val result = RoomRules.join(almostFull, guestMember())

        assertTrue("差一人时应当加入成功", result is JoinResult.Success)
        assertEquals(Room.MAX_MEMBERS, (result as JoinResult.Success).room.members.size)
    }

    @Test
    fun `加入成功后新成员排在末尾且未准备`() {
        val result = RoomRules.join(room(), guestMember()) as JoinResult.Success

        assertEquals(guestId, result.room.members.last().id)
        assertFalse("新加入的成员不应自动准备", result.room.members.last().isReady)
        assertEquals("加入不得改变房主", hostId, result.room.creatorId)
    }

    // ===== 离开 / 解散 =====

    @Test
    fun `房主离开必然解散房间`() {
        assertNull(
            "withoutMember 返回 null 就是解散语义",
            RoomRules.withoutMember(room(listOf(hostMember(), guestMember())), hostId)
        )
    }

    @Test
    fun `成员离开后房主仍在房内`() {
        val updated = RoomRules.withoutMember(room(listOf(hostMember(), guestMember())), guestId)

        assertEquals(1, updated!!.members.size)
        assertTrue("不变式：creatorId 必须始终能在 members 里找到", updated.contains(updated.creatorId))
    }

    @Test
    fun `踢人等价于非房主离开`() {
        val before = room(listOf(hostMember(), guestMember(), guestMember("wire-other")))

        val viaKick = RoomRules.withMember(before, guestMember())
            .let { RoomRules.withoutMember(it, guestId) }
        val expected = RoomRules.withoutMember(before, guestId)

        assertEquals(expected, viaKick)
    }

    // ===== 开局门禁 =====

    @Test
    fun `单人时不能开局`() {
        assertEquals(
            StartBlocker.NOT_ENOUGH_PLAYERS,
            RoomRules.startBlocker(room(), hostId)
        )
    }

    @Test
    fun `人数不足时 allReady 恒为 false`() {
        assertFalse("1 人时不该出现「全员准备」", room().allReady)
        assertFalse("0 人时不该出现「全员准备」", room(members = emptyList()).allReady)
    }

    @Test
    fun `有人未准备时不能开局`() {
        val twoReady = room(listOf(hostMember(), guestMember(ready = false)))

        assertEquals(StartBlocker.MEMBERS_NOT_READY, RoomRules.startBlocker(twoReady, hostId))
    }

    @Test
    fun `全员准备且人数足够时放行`() {
        val ready = room(listOf(hostMember(), guestMember(ready = true)))

        assertNull(RoomRules.startBlocker(ready, hostId))
    }

    @Test
    fun `非房主不能开局`() {
        val ready = room(listOf(hostMember(), guestMember(ready = true)))

        assertEquals(StartBlocker.NOT_HOST, RoomRules.startBlocker(ready, guestId))
    }

    @Test
    fun `已开局后不能重复开局`() {
        val playing = room(
            members = listOf(hostMember(), guestMember(ready = true)),
            status = RoomStatus.PLAYING
        )

        assertEquals(StartBlocker.ALREADY_PLAYING, RoomRules.startBlocker(playing, hostId))
    }

    @Test
    fun `门禁与等候页按钮条件同构`() {
        val cases = listOf(
            room(listOf(hostMember(), guestMember(ready = true))) to true,
            room(listOf(hostMember(), guestMember(ready = false))) to false,
            room() to false
        )

        cases.forEach { (subject, buttonEnabled) ->
            assertEquals(
                "按钮可用性与服务端门禁必须一致，否则会出现「按钮亮着但点了没反应」",
                buttonEnabled,
                RoomRules.startBlocker(subject, hostId) == null
            )
        }
    }

    @Test
    fun `开局后回合流水号归零`() {
        val started = RoomRules.started(room(listOf(hostMember(), guestMember(ready = true))).copy(turnSeq = 42))

        assertEquals(RoomStatus.PLAYING, started.status)
        assertEquals(0, started.turnSeq)
    }

    // ===== 观战首帧 =====

    @Test
    fun `观战首帧是首局零回合且双方按目标分`() {
        val config = MatchConfig(matchType = MatchType.X01, targetScore = 501, mode = MatchMode.MULTI_LEG, legsToWin = 3)
        val started = RoomRules.started(
            room(listOf(hostMember(), guestMember(ready = true))).copy(config = config)
        )

        val snapshot = RoomRules.initialSpectatorSnapshot(started)

        assertEquals(1, snapshot.leg)
        assertEquals(3, snapshot.legsToWin)
        assertTrue("本切片只同步首帧，不预留假回合", snapshot.turns.isEmpty())
        assertEquals(listOf(501, 501), snapshot.players.map { it.score })
        assertEquals(listOf(true, false), snapshot.players.map { it.isActive })
        assertEquals("X01 - 501", snapshot.configName)
        assertFalse("双方都是 0 局时不该判成已结束", snapshot.isFinished)
    }

    @Test
    fun `Cricket 观战首帧分数从零开始`() {
        val cricket = room(
            members = listOf(hostMember(), guestMember(ready = true))
        ).copy(config = MatchConfig(matchType = MatchType.CRICKET, outMode = OutMode.STRAIGHT_OUT))

        val snapshot = RoomRules.initialSpectatorSnapshot(cricket)

        assertEquals(listOf(0, 0), snapshot.players.map { it.score })
        assertEquals("Cricket", snapshot.configName)
    }

    // ===== 规范化 =====

    @Test
    fun `空白昵称使用默认名`() {
        assertEquals("${LocalUser.DEFAULT_NAME}1", RoomRules.normalizeDisplayName(""))
        assertEquals("${LocalUser.DEFAULT_NAME}1", RoomRules.normalizeDisplayName("   "))
        assertEquals("镖友", RoomRules.normalizeDisplayName("  镖友  "))
    }

    @Test
    fun `未命名房间用房主名兜底`() {
        assertEquals("阿镖 的房间", RoomRules.normalizeRoomName("", "阿镖"))
        assertEquals("周末 501 局", RoomRules.normalizeRoomName(" 周末 501 局 ", "阿镖"))
    }

    @Test
    fun `房主成员默认已准备`() {
        val host = RoomRules.newMember(hostId, "房主", "HUMAN_1", isCreator = true)
        val guest = RoomRules.newMember(guestId, "客人", "HUMAN_2", isCreator = false)

        assertTrue("房主视为常备，否则 UI 没有给房主的准备按钮", host.isReady)
        assertTrue(host.isCreator)
        assertFalse(guest.isReady)
        assertFalse(guest.isCreator)
    }

    // ===== 大厅列表 =====

    @Test
    fun `大厅只列公开房间`() {
        val rooms = mapOf(
            "111111" to room(id = "111111"),
            "222222" to room(id = "222222", visibility = RoomVisibility.PRIVATE)
        )

        assertEquals(listOf("111111"), RoomRules.publicRoomsOf(rooms).map { it.id })
    }

    @Test
    fun `等候中优先于对局中`() {
        val rooms = mapOf(
            "111111" to room(id = "111111", status = RoomStatus.PLAYING, createdAt = 999L),
            "222222" to room(id = "222222", status = RoomStatus.WAITING, createdAt = 1L)
        )

        assertEquals(listOf("222222", "111111"), RoomRules.publicRoomsOf(rooms).map { it.id })
    }

    @Test
    fun `同等条件下新建优先且顺序可复现`() {
        val rooms = mapOf(
            "333333" to room(id = "333333", createdAt = 100L),
            "111111" to room(id = "111111", createdAt = 100L),
            "222222" to room(id = "222222", createdAt = 200L)
        )

        val first = RoomRules.publicRoomsOf(rooms).map { it.id }
        val second = RoomRules.publicRoomsOf(rooms.entries.reversed().associate { it.toPair() }).map { it.id }

        assertEquals(listOf("222222", "111111", "333333"), first)
        assertEquals("createdAt 相同时必须按 id 兜底，否则列表会无端抖动", first, second)
    }
}
