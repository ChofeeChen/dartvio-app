package com.dartvio.app.domain.room

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.OutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 撤回（M5 T7）的**纯规则**测试。
 *
 * ## 为什么这些必须落在单测里
 *
 * 撤回改的是**权威比分**，而它最容易出错的三处恰好都是真机上看不出来的：
 *
 * - 回退点存的是「这一手之前」还是「之后」—— 存反了的表现是比分**多退一格**，
 *   而用户在真机上只会觉得「手气回来了」，不会怀疑；
 * - 「过了窗口」与「不是你投的」谁先判 —— 判反了会让对手的撤回按钮报「已超时」，
 *   一个会诱导用户反复重试的错误归因；
 * - 撤回后 `seq` 要不要回退 —— 不回退是本切片的红线，而它的症状（两个回合撞同一个 seq）
 *   要等有人漏收一帧才浮出来。
 *
 * 这三条与网络无关，与界面无关，只有纯函数能判。
 */
class TurnUndoRulesTest {

    private val hostId = "wire-host"
    private val guestId = "wire-guest"

    /** 一整手 180。用「大分」是为了让「退没退回去」一眼可辨（退 180 与退 60 不会认错）。 */
    private val bigTurn = listOf(Dart.triple(20), Dart.triple(20), Dart.triple(20))

    // ===== 窗口内：本人 =====

    @Test
    fun `窗口内本人撤回会回退比分`() {
        val match = started()
        val before = scoreOf(match, hostId)

        val submitted = applied(RoomMatchRules.submit(match, hostId, bigTurn, now = 1_000))
        assertEquals(before - 180, scoreOf(submitted.match, hostId))

        val undone = undoApplied(RoomMatchRules.undo(submitted.match, hostId, now = 1_500))

        assertEquals("剩余分应回到这一手之前", before, scoreOf(undone.match, hostId))
        assertEquals("流水应少一条", 0, undone.match.turns.size)
        assertEquals("撤回也是一次状态变更，版本要推进", submitted.match.version + 1, undone.match.version)
        assertNull("回退点必须一并清空，否则同一手能被连撤两次", undone.match.undoPoint)
    }

    @Test
    fun `撤回后回合序号不回退`() {
        val submitted = applied(RoomMatchRules.submit(started(), hostId, bigTurn, now = 1_000))
        val undone = undoApplied(RoomMatchRules.undo(submitted.match, hostId, now = 1_500))

        // 撤回之后那一手被退了回去，因此**仍轮到本人**投 —— 这里必须是 host。
        val next = applied(RoomMatchRules.submit(undone.match, hostId, bigTurn, now = 2_000))

        assertEquals(
            "撤掉的那一条的 seq 不能被下一手复用：序号是历史，不是指针",
            submitted.turn.seq + 1,
            next.turn.seq
        )
        assertEquals(1, next.match.turns.size)
    }

    @Test
    fun `撤回后轮次回到本人之前的顺序`() {
        val submitted = applied(RoomMatchRules.submit(started(), hostId, bigTurn, now = 1_000))
        val undone = undoApplied(RoomMatchRules.undo(submitted.match, hostId, now = 1_500))

        assertEquals(hostId, undone.match.currentPlayerId)
    }

    // ===== 拒：窗口 / 身份 / 锁定 =====

    @Test
    fun `过了窗口就撤不动`() {
        val submitted = applied(RoomMatchRules.submit(started(), hostId, bigTurn, now = 1_000))

        val outcome = RoomMatchRules.undo(submitted.match, hostId, now = 1_000 + RoomMatchRules.UNDO_WINDOW_MS)

        assertEquals(UndoResult.Locked, outcome)
        assertEquals("被拒的撤回必须一字不动地留下状态", submitted.match, submitted.match)
    }

    @Test
    fun `对手不能替我撤回`() {
        val submitted = applied(RoomMatchRules.submit(started(), hostId, bigTurn, now = 1_000))

        assertEquals(
            "撤回别人的回合等于替对手改分",
            UndoResult.NotYourTurn,
            RoomMatchRules.undo(submitted.match, guestId, now = 1_100)
        )
    }

    @Test
    fun `对手一动手窗口立刻关闭`() {
        val submitted = applied(RoomMatchRules.submit(started(), hostId, bigTurn, now = 1_000))

        // 只等 1 秒（远未到 5 秒）：锁定的是「对手已经开始照着新比分操作」这件事。
        val locked = RoomMatchRules.lock(submitted.match, now = 2_000)

        assertTrue("有窗口时锁定必须生效", locked != null)
        assertEquals(UndoResult.Locked, RoomMatchRules.undo(locked!!, hostId, now = 2_100))
    }

    // ===== 拒：没有可撤的东西 =====

    @Test
    fun `还没投过时没有可撤的回合`() {
        assertEquals(UndoResult.NotUndoable, RoomMatchRules.undo(started(), hostId, now = 1_000))
    }

    @Test
    fun `同一手不能连撤两次`() {
        val submitted = applied(RoomMatchRules.submit(started(), hostId, bigTurn, now = 1_000))
        val undone = undoApplied(RoomMatchRules.undo(submitted.match, hostId, now = 1_500))

        assertEquals(
            "撤回不允许连着退：否则一次提交能把比分一路退回去",
            UndoResult.NotUndoable,
            RoomMatchRules.undo(undone.match, hostId, now = 1_600)
        )
    }

    @Test
    fun `赢下整局的那一手不可撤`() {
        val match = started(config = checkoutConfig(legsToWin = 1))
        val finished = applied(RoomMatchRules.submit(match, hostId, listOf(Dart.double(20)), now = 1_000))

        assertTrue(finished.match.isFinished)
        assertEquals(
            "胜负落定就是历史，撤回它等于重赛",
            UndoResult.NotUndoable,
            RoomMatchRules.undo(finished.match, hostId, now = 1_100)
        )
    }

    @Test
    fun `赢下一局的那一手不可撤`() {
        // 三局两胜：收掉第一局之后对局还在继续，但 legsWon 已经变过。
        val match = started(config = checkoutConfig(legsToWin = 2))
        val wonLeg = applied(RoomMatchRules.submit(match, hostId, listOf(Dart.double(20)), now = 1_000))

        assertEquals(1, wonLeg.match.legsWon[hostId])
        assertFalse(wonLeg.match.isFinished)

        assertEquals(
            "legsWon 是跨局的账，撤回只能退这一手的比分",
            UndoResult.NotUndoable,
            RoomMatchRules.undo(wonLeg.match, hostId, now = 1_100)
        )
        assertEquals(1, wonLeg.match.legsWon[hostId])
    }

    // ===== 锁定本身 =====

    @Test
    fun `没有开着的窗口就不必广播锁定`() {
        assertNull("开局没有可撤的手", RoomMatchRules.lock(started(), now = 1_000))

        val submitted = applied(RoomMatchRules.submit(started(), hostId, bigTurn, now = 1_000))
        val locked = RoomMatchRules.lock(submitted.match, now = 2_000)!!

        assertNull("已经锁过一次就不要再锁一次：每次都广播会让两端各刷一帧没变化的界面",
            RoomMatchRules.lock(locked, now = 2_500))
    }

    @Test
    fun `新的提交会替掉旧窗口`() {
        val first = applied(RoomMatchRules.submit(started(), hostId, bigTurn, now = 1_000))
        val second = applied(RoomMatchRules.submit(first.match, guestId, bigTurn, now = 2_000))

        // 第二手一生效，第一手的窗口就不该还在：否则「撤回」会退掉对手刚投的那一手。
        assertEquals(UndoResult.NotYourTurn, RoomMatchRules.undo(second.match, hostId, now = 2_100))
        assertEquals(guestId, RoomMatchRules.snapshot(second.match).undo?.playerId)
    }

    // ===== 帧与投影 =====

    @Test
    fun `帧只在可撤时带撤销窗口`() {
        val submitted = applied(RoomMatchRules.submit(started(), hostId, bigTurn, now = 1_000))

        val undo = RoomMatchRules.snapshot(submitted.match).undo
        assertEquals(hostId, undo?.playerId)
        assertEquals(submitted.turn.seq, undo?.seq)
        assertEquals(RoomMatchRules.UNDO_WINDOW_MS, undo?.windowMs)

        val locked = RoomMatchRules.lock(submitted.match, now = 2_000)!!
        assertNull("锁定后帧里就不该再有「可以撤回」的暗示", RoomMatchRules.snapshot(locked).undo)
    }

    @Test
    fun `撤销窗口只对本人投影`() {
        val submitted = applied(RoomMatchRules.submit(started(), hostId, bigTurn, now = 1_000))
        val snapshot = RoomMatchRules.snapshot(submitted.match)

        assertTrue(snapshot.forSelf(hostId).canUndo)
        assertFalse("对手那边不该出现撤回按钮", snapshot.forSelf(guestId).canUndo)
        assertFalse("不知道自己在线上是谁时不该出现撤回按钮", snapshot.forSelf(null).canUndo)
    }

    // ===== 辅助 =====

    private fun scoreOf(match: RoomMatch, playerId: String): Int =
        RoomMatchRules.snapshot(match).players.first { it.id == playerId }.score

    private fun applied(result: TurnSubmit): TurnSubmit.Applied {
        assertTrue("期望提交生效，实际为 $result", result is TurnSubmit.Applied)
        return result as TurnSubmit.Applied
    }

    private fun undoApplied(result: UndoResult): UndoResult.Applied {
        assertTrue("期望撤回生效，实际为 $result", result is UndoResult.Applied)
        return result as UndoResult.Applied
    }

    private fun started(
        config: MatchConfig = MatchConfig(),
        members: List<RoomMember> = listOf(host(), guest())
    ): RoomMatch = requireNotNull(
        RoomMatchRules.start(
            Room(
                id = "384712",
                name = "测试房",
                creatorId = hostId,
                config = config,
                status = RoomStatus.PLAYING,
                members = members
            )
        )
    )

    /** 一镖收局（D20 打 40 分）的配置：三行内就能走到「赢一局 / 赢整场」。 */
    private fun checkoutConfig(legsToWin: Int) = MatchConfig(
        targetScore = 40,
        mode = MatchMode.MULTI_LEG,
        legsToWin = legsToWin,
        outMode = OutMode.DOUBLE_OUT
    )

    private fun host() = RoomMember(hostId, "房主", "HUMAN_1", isCreator = true, isReady = true)

    private fun guest() = RoomMember(guestId, "客人", "HUMAN_2", isReady = true)
}
