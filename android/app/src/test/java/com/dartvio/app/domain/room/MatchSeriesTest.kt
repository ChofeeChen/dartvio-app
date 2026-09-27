package com.dartvio.app.domain.room

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.ui.lobby.winnerNameOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多局制（M5 T9）：换局、赛制终结、再来一局。
 *
 * ## 为什么这些必须落在单测里
 *
 * 多局制的每一条规则都只在**某一局结束的那一瞬间**生效，而真机上那一瞬间
 * 混着「换局的广播」「结算页导航」与「键盘锁定」三件事 ——
 * 出了问题没有人分得清是哪一件错了。这里把它们拆成纯函数逐个钉住。
 *
 * 对局用 40 分目标分（一手 `D20` 即收镖），因此「打完一局」不需要
 * 先投十几回合 501 —— 那会让每个用例都变成一坨与规则无关的摆位代码。
 */
class MatchSeriesTest {

    private val hostId = "wire-host"
    private val guestId = "wire-guest"

    /** 收镖：D20 = 40 分。用双区收镖而不是 T20，是为了不依赖 `outMode` 的口径。 */
    private val checkout = listOf(Dart.double(20))

    /** 不收镖的一手：S20 = 20 分，用来把出手权交给对手。 */
    private val plain = listOf(Dart.single(20))

    @Test
    fun `赢一局但没到赛制目标就换局`() {
        val started = match(legsToWin = 2)
        val applied = applied(RoomMatchRules.submit(started, hostId, checkout, now = 1_000))

        assertEquals(1, applied.match.legsWon[hostId])
        assertFalse("还差一局，整场不该结束", applied.match.isFinished)
        assertEquals("换局了", 2, applied.match.leg.legNumber)
        assertEquals(
            "换局后先手回到 0 号成员（房主）—— 与本地对局同一口径，见 RoomMatchRules.submit 的注释",
            hostId, applied.match.currentPlayerId
        )
        assertEquals("新的一局从目标分重新计", TARGET_SCORE, remainingOf(applied.match, hostId))
    }

    @Test
    fun `换局不重置已赢局数`() {
        var match = match(legsToWin = 3)
        match = applied(RoomMatchRules.submit(match, hostId, checkout, now = 1_000)).match
        // 第 2 局：房主先投一手不收镖的（把出手权交给客人），客人再收掉这一局。
        match = applied(RoomMatchRules.submit(match, hostId, plain, now = 2_000)).match
        match = applied(RoomMatchRules.submit(match, guestId, checkout, now = 3_000)).match

        assertEquals("第一局赢的那一局必须还在：换局换的是比分，不是账",
            1, match.legsWon[hostId])
        assertEquals(1, match.legsWon[guestId])
        assertFalse("1:1 时赛制目标（3）未达成", match.isFinished)
    }

    @Test
    fun `达到赛制目标才结束`() {
        var match = match(legsToWin = 2)
        match = applied(RoomMatchRules.submit(match, hostId, checkout, now = 1_000)).match
        assertFalse("先赢一局不算赢下整场", match.isFinished)

        match = applied(RoomMatchRules.submit(match, hostId, checkout, now = 2_000)).match
        assertTrue(match.isFinished)
        assertEquals(hostId, match.winnerId)
        assertEquals(2, match.legsWon[hostId])
    }

    @Test
    fun `休闲模式一局定胜负`() {
        // legsToWin = 0 是休闲模式的编码："一局定胜负"（见 MatchConfig.legsToWin）。
        val applied = applied(RoomMatchRules.submit(match(legsToWin = 0), hostId, checkout, now = 1_000))

        assertTrue(applied.match.isFinished)
        assertEquals(hostId, applied.match.winnerId)
    }

    @Test
    fun `再来一局清零但保留成员与配置`() {
        var match = match(legsToWin = 2)
        match = applied(RoomMatchRules.submit(match, hostId, checkout, now = 1_000)).match
        match = applied(RoomMatchRules.submit(match, hostId, checkout, now = 2_000)).match
        assertTrue(match.isFinished)

        val next = RoomMatchRules.rematch(match)

        assertTrue(next.legsWon.isEmpty())
        assertTrue(next.turns.isEmpty())
        assertFalse(next.isFinished)
        assertNull(next.winnerId)
        assertEquals("新的一场回到第 1 局", 1, next.leg.legNumber)
        assertEquals(
            "版本归零：沿用旧版本号会让所有人下一手都撞一次冲突",
            0, next.version
        )
        assertEquals("成员保留 —— 换掉对手比「少一个人就开不了局」更让人措手不及",
            listOf(hostId, guestId), next.members.map { it.id })
        assertEquals("配置保留：局数目标仍是原来那个", 2, next.config.legsToWin)
        assertNull("没有可撤回的上一手", next.undoPoint)
    }

    // ===== 结算文案 =====

    @Test
    fun `胜者优先取服务端下发的结局`() {
        val snapshot = RoomMatchRules.snapshot(match(legsToWin = 2))

        assertEquals(
            "弃权没有留下任何一手镖，胜者只能来自服务端下发的那一句",
            "客人", winnerNameOf(snapshot, MatchFinish(winnerId = guestId, reason = MatchFinish.REASON_FORFEIT))
        )
    }

    @Test
    fun `没有结局时就看局数`() {
        var match = match(legsToWin = 2)
        match = applied(RoomMatchRules.submit(match, hostId, checkout, now = 1_000)).match
        match = applied(RoomMatchRules.submit(match, hostId, checkout, now = 2_000)).match

        assertEquals("房主", winnerNameOf(RoomMatchRules.snapshot(match), null))
    }

    @Test
    fun `休闲模式取最后收镖的人`() {
        val applied = applied(RoomMatchRules.submit(match(legsToWin = 0), hostId, checkout, now = 1_000))

        assertEquals(
            "休闲模式下 legsWon 恒 >= legsToWin(0)，不能拿它判胜者",
            "房主", winnerNameOf(RoomMatchRules.snapshot(applied.match), null)
        )
    }

    // ===== 辅助 =====

    private fun applied(result: TurnSubmit): TurnSubmit.Applied {
        assertTrue("期望提交生效，实际为 $result", result is TurnSubmit.Applied)
        return result as TurnSubmit.Applied
    }

    private fun remainingOf(match: RoomMatch, memberId: String): Int {
        val index = match.members.indexOfFirst { it.id == memberId }
        return match.leg.players[index].remaining
    }

    private fun match(legsToWin: Int): RoomMatch = requireNotNull(
        RoomMatchRules.start(
            Room(
                id = "384712",
                name = "测试房",
                creatorId = hostId,
                config = MatchConfig(
                    matchType = MatchType.X01,
                    targetScore = TARGET_SCORE,
                    legsToWin = legsToWin
                ),
                status = RoomStatus.PLAYING,
                members = listOf(
                    RoomMember(hostId, "房主", "HUMAN_1", isCreator = true, isReady = true),
                    RoomMember(guestId, "客人", "HUMAN_2", isReady = true)
                )
            )
        )
    )

    private companion object {
        const val TARGET_SCORE = 40
    }
}
