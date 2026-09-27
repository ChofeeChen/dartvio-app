package com.dartvio.app.domain.room

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.MatchConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 弃权（M5 T8）的**纯规则**测试。
 *
 * ## 为什么这些必须落在单测里
 *
 * 判负是本切片里唯一一个**在没有人投镖的情况下结束对局**的动作，它最容易写错的两处
 * 在真机上都看不出来：
 *
 * - 胜者取「对手」还是「当前领先的人」。取领先者的版本在大多数情况下**看起来也是对的**
 *   （领先者通常就是对手），只在「落后的一方拔网线」时才露出破绽 —— 而那时它把作弊变成了划算的选择；
 * - 比分动不动。把剩余分改成 0，界面上只是「他归零了」，与「他输了」字面一致，
 *   于是没有任何人会怀疑那是一份从未发生过的比分。
 */
class ForfeitRulesTest {

    private val hostId = "wire-host"
    private val guestId = "wire-guest"

    private val turn = listOf(Dart.triple(20), Dart.triple(20), Dart.triple(20))

    @Test
    fun `判负让对手获胜`() {
        val submitted = applied(RoomMatchRules.submit(started(), hostId, turn, now = 1_000))

        val result = RoomMatchRules.forfeit(submitted.match, hostId)

        assertTrue(result is ForfeitResult.Applied)
        val outcome = result as ForfeitResult.Applied
        assertEquals("弃权是惩罚性的：判给领先者会让拔网线变成划算的选择", guestId, outcome.winnerId)
        assertEquals(guestId, outcome.match.winnerId)
        assertTrue(outcome.match.isFinished)
    }

    @Test
    fun `判负不动比分`() {
        val submitted = applied(RoomMatchRules.submit(started(), hostId, turn, now = 1_000))
        val before = RoomMatchRules.snapshot(submitted.match)

        val outcome = RoomMatchRules.forfeit(submitted.match, hostId) as ForfeitResult.Applied
        val after = RoomMatchRules.snapshot(outcome.match)

        assertEquals(
            "剩余分一字不改：改了就等于在回看里留下一份从未发生过的比分",
            before.players.map { it.score },
            after.players.map { it.score }
        )
        assertEquals("已经投出来的流水同样是历史", before.turns.size, after.turns.size)
        assertEquals("判负也是一次权威变更，持旧版本的客户端要据此刷新",
            submitted.match.version + 1, outcome.match.version)
    }

    @Test
    fun `判负之后不能再提交`() {
        val submitted = applied(RoomMatchRules.submit(started(), hostId, turn, now = 1_000))
        val outcome = RoomMatchRules.forfeit(submitted.match, hostId) as ForfeitResult.Applied

        assertEquals(
            "胜负已定之后还能投，等于让输的一方继续改比分",
            TurnSubmit.Finished,
            RoomMatchRules.submit(outcome.match, guestId, turn, now = 2_000)
        )
    }

    @Test
    fun `已结束的对局不再判负`() {
        val submitted = applied(RoomMatchRules.submit(started(), hostId, turn, now = 1_000))
        val outcome = RoomMatchRules.forfeit(submitted.match, hostId) as ForfeitResult.Applied

        assertEquals(ForfeitResult.AlreadyOver, RoomMatchRules.forfeit(outcome.match, guestId))
    }

    @Test
    fun `不是本局的人掉线与本局无关`() {
        val match = started()

        assertEquals(
            "观战者或不在本局的人掉线不该把这一局判掉",
            ForfeitResult.NotInMatch,
            RoomMatchRules.forfeit(match, "wire-spectator")
        )
        assertFalse(RoomMatchRules.forfeit(match, "wire-spectator") is ForfeitResult.Applied)
    }

    @Test
    fun `弃权不制造不存在的收镖`() {
        val submitted = applied(RoomMatchRules.submit(started(), hostId, turn, now = 1_000))
        val outcome = RoomMatchRules.forfeit(submitted.match, hostId) as ForfeitResult.Applied

        val snapshot = RoomMatchRules.snapshot(outcome.match)
        assertFalse("流水里不该出现一条没人投过的收镖", snapshot.turns.any { it.isCheckout })
        assertTrue("但界面必须知道对局结束了", outcome.match.isFinished)
    }

    // ===== 辅助 =====

    private fun applied(result: TurnSubmit): TurnSubmit.Applied {
        assertTrue("期望提交生效，实际为 $result", result is TurnSubmit.Applied)
        return result as TurnSubmit.Applied
    }

    private fun started(): RoomMatch = requireNotNull(
        RoomMatchRules.start(
            Room(
                id = "384712",
                name = "测试房",
                creatorId = hostId,
                config = MatchConfig(),
                status = RoomStatus.PLAYING,
                members = listOf(
                    RoomMember(hostId, "房主", "HUMAN_1", isCreator = true, isReady = true),
                    RoomMember(guestId, "客人", "HUMAN_2", isReady = true)
                )
            )
        )
    )
}
