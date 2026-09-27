package com.dartvio.app.domain.room

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.InMode
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
 * 房间**权威对局**的纯规则单测（M5 实时同步）。
 *
 * 这一层是「主机算的分」唯一的真相来源：客户端不复制规则（见 `RoomMatchScreen`），
 * 观战页看到的每一格比分都从这里的 [RoomMatchRules.snapshot] 出。
 * 因此这里的重点不是覆盖分支，而是把几件**错了只有真机对拍才发现**的事钉死：
 *
 * 1. 裁定顺序（已结束 → 没轮到 → 镖非法），报错话术要指向用户真正该做的事；
 * 2. 换局/终局边界（`legsToWin` 与休闲模式 `<= 0` 的编码）；
 * 3. 帧派生（选手顺序、`isActive`、`legsWon`、越界兜底）与权威状态严格同源。
 */
class RoomMatchRulesTest {

    private val hostId = "wire-host"
    private val guestId = "wire-guest"

    private fun host() = RoomMember(hostId, "房主", "HUMAN_1", isCreator = true, isReady = true)

    private fun guest() = RoomMember(guestId, "客人", "HUMAN_2", isReady = true)

    private fun room(
        config: MatchConfig = MatchConfig(),
        members: List<RoomMember> = listOf(host(), guest())
    ) = Room(
        id = "384712",
        name = "测试房",
        creatorId = hostId,
        config = config,
        status = RoomStatus.PLAYING,
        members = members
    )

    /** 直接开一局，省掉每个用例里的 `requireNotNull`。 */
    private fun started(
        config: MatchConfig = MatchConfig(),
        members: List<RoomMember> = listOf(host(), guest())
    ): RoomMatch = requireNotNull(RoomMatchRules.start(room(config, members)))

    /** 一镖收局（D20 打 40 分）的配置，用来在三行内走到「赢一局 / 赢整场」。 */
    private fun checkoutConfig(legsToWin: Int, mode: MatchMode = MatchMode.MULTI_LEG) =
        MatchConfig(targetScore = 40, mode = mode, legsToWin = legsToWin, outMode = OutMode.DOUBLE_OUT)

    private fun applied(result: TurnSubmit): TurnSubmit.Applied {
        assertTrue("期望提交生效，实际为 $result", result is TurnSubmit.Applied)
        return result as TurnSubmit.Applied
    }

    private fun checkout(match: RoomMatch, memberId: String): RoomMatch =
        applied(RoomMatchRules.submit(match, memberId, listOf(Dart.double(20)))).match

    // ===== 玩法名单 =====

    @Test
    fun `本切片只为 X01 维护权威对局`() {
        assertTrue(RoomMatchRules.supportsLiveMatch(MatchType.X01))

        MatchType.entries.filterNot { it == MatchType.X01 }.forEach { type ->
            assertFalse("$type 尚无权威对局模型，必须显式为 false", RoomMatchRules.supportsLiveMatch(type))
            assertNull(
                "不支持的玩法必须回 null，调用方据此走静态观战分支",
                RoomMatchRules.start(room(MatchConfig(matchType = type)))
            )
        }
    }

    @Test
    fun `空成员房间不建局`() {
        assertNull(
            "无成员时 currentPlayerId 会越界，必须在入口挡住",
            RoomMatchRules.start(room(members = emptyList()))
        )
    }

    // ===== 开局 =====

    @Test
    fun `开局是首局零回合且双方按目标分`() {
        val match = started(MatchConfig(targetScore = 501, legsToWin = 3))

        assertEquals(1, match.leg.legNumber)
        assertEquals(0, match.turnCount)
        assertTrue(match.turns.isEmpty())
        assertFalse(match.isFinished)
        assertNull(match.winnerId)
        assertEquals(hostId, match.currentPlayerId)
        assertEquals(listOf(501, 501), match.leg.players.map { it.remaining })
    }

    @Test
    fun `出手顺序与房间成员顺序一致`() {
        val reversed = listOf(guest(), host())
        val match = started(members = reversed)

        assertEquals(guestId, match.currentPlayerId)
        assertEquals(listOf(guestId, hostId), RoomMatchRules.snapshot(match).players.map { it.id })
    }

    // ===== 裁定顺序 =====

    @Test
    fun `已结束优先于没轮到`() {
        val finished = checkout(started(checkoutConfig(legsToWin = 1)), hostId)
        assertTrue("前置：这一局已经打完", finished.isFinished)

        assertEquals(
            "终局后应从先手继续提交，报「已结束」而不是「没轮到你」",
            TurnSubmit.Finished,
            RoomMatchRules.submit(finished, guestId, listOf(Dart.single(20)))
        )
    }

    @Test
    fun `没轮到优先于镖非法`() {
        val match = started()

        val result = RoomMatchRules.submit(
            match,
            guestId,
            listOf(Dart(25, 3), Dart(99, 9), Dart.single(20), Dart.single(20))
        )

        assertEquals(
            "顺序反了会把越权提交报成「镖非法」，用户会去改镖而不是等自己回合",
            TurnSubmit.NotYourTurn,
            result
        )
    }

    @Test
    fun `空镖数组被拒且文案指明数量下限`() {
        val result = RoomMatchRules.submit(started(), hostId, emptyList())

        assertTrue(result is TurnSubmit.InvalidDarts)
        assertEquals("一个回合至少需要 1 支镖", (result as TurnSubmit.InvalidDarts).message)
    }

    @Test
    fun `超过三镖被拒且文案带实际上限与收到数`() {
        val four = List(4) { Dart.single(20) }

        val result = RoomMatchRules.submit(started(), hostId, four)

        assertTrue(result is TurnSubmit.InvalidDarts)
        assertEquals("一个回合最多 3 支镖，收到 4 支", (result as TurnSubmit.InvalidDarts).message)
    }

    @Test
    fun `落点非法被拒且文案指出具体那一镖`() {
        // 25 号没有三倍区；21 号不在盘面上。
        listOf(Dart(25, 3), Dart(21, 1)).forEach { bad ->
            val result = RoomMatchRules.submit(started(), hostId, listOf(bad))

            assertTrue("$bad 应被判非法", result is TurnSubmit.InvalidDarts)
            assertEquals(
                "落点非法：${bad.number} x ${bad.multiplier}",
                (result as TurnSubmit.InvalidDarts).message
            )
        }
    }

    @Test
    fun `MISS 是合法落点`() {
        val match = applied(RoomMatchRules.submit(started(), hostId, listOf(Dart.MISS))).match

        assertEquals("打空不进分但回合照常过去", 501, match.leg.players[0].remaining)
        assertEquals(guestId, match.currentPlayerId)
    }

    // ===== 计分与推进 =====

    @Test
    fun `一次提交推进一回合并换手`() {
        val applied = applied(
            RoomMatchRules.submit(started(), hostId, listOf(Dart.triple(20), Dart.triple(20), Dart.triple(20)))
        )
        val match = applied.match

        assertEquals(501 - 180, match.leg.players[0].remaining)
        assertEquals("换手到下一个成员", guestId, match.currentPlayerId)
        assertEquals(1, match.turnCount)
        assertEquals(1, match.turns.size)
    }

    @Test
    fun `回合流水与本地对局同源`() {
        val applied = applied(
            RoomMatchRules.submit(started(), hostId, listOf(Dart.triple(20), Dart.single(5), Dart.MISS))
        )

        assertEquals(1, applied.turn.seq)
        assertEquals("房主", applied.turn.playerName)
        assertEquals("T20 5 MISS", applied.turn.darts)
        assertEquals(65, applied.turn.scored)
        assertEquals(501 - 65, applied.turn.remaining)
        assertFalse(applied.turn.isBust)
        assertFalse(applied.turn.isCheckout)
    }

    @Test
    fun `爆分回滚剩余分且本回合不得分`() {
        val match = started(checkoutConfig(legsToWin = 3))

        val applied = applied(RoomMatchRules.submit(match, hostId, listOf(Dart.triple(20))))

        assertEquals("爆分后剩余分回到回合开始值", 40, applied.match.leg.players[0].remaining)
        assertTrue(applied.turn.isBust)
        assertEquals(0, applied.turn.scored)
        assertEquals(40, applied.turn.remaining)
        assertEquals("爆分不改变换手规则", guestId, applied.match.currentPlayerId)
    }

    @Test
    fun `Double-In 未开镖时不计分但仍换手`() {
        val match = started(
            MatchConfig(targetScore = 501, inMode = InMode.DOUBLE_IN, outMode = OutMode.DOUBLE_OUT)
        )

        val applied = applied(RoomMatchRules.submit(match, hostId, listOf(Dart.single(20))))

        assertEquals("未开镖前非双倍不进分", 501, applied.match.leg.players[0].remaining)
        assertEquals(0, applied.turn.scored)
        assertFalse("既不是爆分也不该判成收局", applied.turn.isBust)
        assertFalse(applied.turn.isCheckout)
        assertEquals(guestId, applied.match.currentPlayerId)
    }

    // ===== 换局 / 终局 =====

    @Test
    fun `赢下一局但未赢整场时重开一局且先手回零号`() {
        val first = started(checkoutConfig(legsToWin = 3))

        val applied = applied(RoomMatchRules.submit(first, hostId, listOf(Dart.double(20))))
        val match = applied.match

        assertEquals("赢一局要记在跨局计数上", 1, match.legsWon[hostId])
        assertEquals(2, match.leg.legNumber)
        assertFalse(match.isFinished)
        assertNull(match.winnerId)
        assertTrue("收局的那一镖要标出来，观战页据此断句", applied.turn.isCheckout)
        assertEquals("0", applied.turn.remaining.toString())
        assertEquals("新一局先手固定回 0 号成员，与本地对局一致", hostId, match.currentPlayerId)
        assertEquals(listOf(40, 40), match.leg.players.map { it.remaining })
    }

    @Test
    fun `达到目标局数即终局`() {
        val first = started(checkoutConfig(legsToWin = 1))

        val applied = applied(RoomMatchRules.submit(first, hostId, listOf(Dart.double(20))))
        val match = applied.match

        assertTrue(match.isFinished)
        assertEquals(hostId, match.winnerId)
        assertEquals("终局后不再重开一局", 1, match.leg.legNumber)
        assertEquals(
            "终局提交一律回 Finished",
            TurnSubmit.Finished,
            RoomMatchRules.submit(match, guestId, listOf(Dart.single(20)))
        )
    }

    @Test
    fun `休闲模式一局定胜负`() {
        val casual = started(checkoutConfig(legsToWin = 0, mode = MatchMode.CASUAL))

        val match = checkout(casual, hostId)

        assertTrue("legsToWin<=0 语义是一局定胜负，取 0 会让第一局收镖即结束，看似对实则改坏规则", match.isFinished)
        assertTrue("休闲模式的收局就是终局", RoomMatchView(RoomMatchRules.snapshot(match), myTurn = false).isOver)
    }

    // ===== 帧派生 =====

    @Test
    fun `帧带出选手顺序活动位与跨局胜场`() {
        val match = checkout(started(checkoutConfig(legsToWin = 3)), hostId)

        val snapshot = RoomMatchRules.snapshot(match)

        assertEquals("384712", snapshot.roomId)
        assertEquals("X01 - 40", snapshot.configName)
        assertEquals(2, snapshot.leg)
        assertEquals(3, snapshot.legsToWin)
        assertEquals(listOf(hostId, guestId), snapshot.players.map { it.id })
        assertEquals(listOf(1, 0), snapshot.players.map { it.legsWon })
        assertEquals(listOf(true, false), snapshot.players.map { it.isActive })
        assertFalse(snapshot.isFinished)
    }

    @Test
    fun `帧里选手缺格时退回目标分而不是崩溃`() {
        val match = started(MatchConfig(targetScore = 501))
        val truncated = match.copy(
            leg = match.leg.copy(players = match.leg.players.take(1))
        )

        val snapshot = RoomMatchRules.snapshot(truncated)

        assertEquals("成员数决定帧的行数", 2, snapshot.players.size)
        assertEquals(501, snapshot.players[1].score)
    }

    @Test
    fun `帧最多带走三十条流水且序号不重置`() {
        val config = MatchConfig(
            targetScore = 1101,
            outMode = OutMode.STRAIGHT_OUT,
            inMode = InMode.STRAIGHT_IN,
            legsToWin = 3
        )
        var match = started(config)
        repeat(31) { index ->
            val shooter = if (index % 2 == 0) hostId else guestId
            match = applied(RoomMatchRules.submit(match, shooter, listOf(Dart.single(1)))).match
        }

        assertEquals("序号取最后一条 seq，不受截断影响", 31, match.turnCount)
        assertEquals(RoomMatchRules.MAX_TURNS_IN_FRAME, match.turns.size)
        assertEquals("被截断的是最早的那条", 2, match.turns.first().seq)
        assertEquals(31, match.turns.last().seq)
    }

    // ===== 「我」的视图 =====

    @Test
    fun `只有线上身份对上当前活动位才算我的回合`() {
        val snapshot = RoomMatchRules.snapshot(started())

        assertTrue(snapshot.forSelf(hostId).myTurn)
        assertTrue(snapshot.forSelf(hostId).canThrow)
        assertFalse(snapshot.forSelf(guestId).myTurn)
        assertFalse("尚未握手（wireId 为 null）一律不算我的回合", snapshot.forSelf(null).myTurn)
    }

    @Test
    fun `终局后不再能录镖`() {
        val match = checkout(started(checkoutConfig(legsToWin = 1)), hostId)

        val view = RoomMatchRules.snapshot(match).forSelf(hostId)

        assertTrue(view.isOver)
        assertTrue("终局时先手仍停在收镖者身上，myTurn 为 true", view.myTurn)
        assertFalse("myTurn 为 true 但 isOver 为 true，canThrow 必须再挡一道", view.canThrow)
        assertEquals(hostId, view.currentPlayer?.id)
    }

    @Test
    fun `观战帧能直接投影成视图`() {
        val snapshot = RoomMatchRules.snapshot(started())

        val view: RoomMatchView = snapshot.forSelf(guestId)

        assertEquals(snapshot, view.snapshot)
        assertEquals("房主", view.currentPlayer?.name)
    }
}
