package com.dartvio.app.domain.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 赛后统计（M2 R4）。
 *
 * 这里守的是**口径**，不是公式：3 镖平均到底除的是轮数还是镖数、High Finish 取的是
 * 结镖那一轮的得分还是结镖后的剩余分（那是 0）—— 这些错了不会报错，
 * 只会让两个人各执一份都说不通的数字。
 */
class MatchStatsTest {

    private fun turn(
        name: String,
        scored: Int,
        remaining: Int = 501 - scored,
        bust: Boolean = false,
        checkout: Boolean = false
    ) = SpectatorTurn(
        seq = 0,
        playerName = name,
        darts = "T20 T20 T20",
        scored = scored,
        remaining = remaining,
        isBust = bust,
        isCheckout = checkout
    )

    private fun snapshot(turns: List<SpectatorTurn>, legsWonA: Int = 0, legsWonB: Int = 0) =
        SpectatorSnapshot(
            roomId = "123456",
            roomName = "房间",
            configName = "501",
            leg = 1,
            legsToWin = 3,
            players = listOf(
                SpectatorPlayer("a", "甲", "HUMAN_1", legsWonA, 0),
                SpectatorPlayer("b", "乙", "HUMAN_2", legsWonB, 0)
            ),
            turns = turns
        )

    @Test
    fun `3 镖平均按轮数算，爆分轮算一轮不给分`() {
        // 60 + 0(爆) + 100 ⇒ 160 / 3 轮。爆分那一轮确实投了 3 镖，不该被剔掉。
        val report = MatchStats.of(
            snapshot(
                listOf(
                    turn("甲", 60),
                    turn("甲", 0, bust = true),
                    turn("甲", 100)
                )
            )
        )
        val a = report.players.first { it.playerName == "甲" }
        assertEquals(3, a.turns)
        assertEquals(160.0 / 3, a.threeDartAvg, 0.001)
    }

    @Test
    fun `First 9 只算前三轮，不足三轮不给数`() {
        val short = MatchStats.of(snapshot(listOf(turn("甲", 60), turn("甲", 60))))
            .players.first { it.playerName == "甲" }
        assertNull("不足 3 轮时不给半个答案", short.first9Avg)

        val full = MatchStats.of(
            snapshot(listOf(turn("甲", 60), turn("甲", 90), turn("甲", 120), turn("甲", 20)))
        ).players.first { it.playerName == "甲" }
        assertEquals((60 + 90 + 120) / 3.0, full.first9Avg!!, 0.001)
    }

    @Test
    fun `100+ 与 140+ 与 180 分别计数`() {
        val report = MatchStats.of(
            snapshot(
                listOf(
                    turn("甲", 100),
                    turn("甲", 140),
                    turn("甲", 180),
                    turn("甲", 60)
                )
            )
        )
        val a = report.players.first { it.playerName == "甲" }
        assertEquals(3, a.count100)
        assertEquals(2, a.count140)
        assertEquals(1, a.count180)
    }

    @Test
    fun `High Finish 取结镖那一轮的得分`() {
        val report = MatchStats.of(
            snapshot(
                listOf(
                    turn("甲", 60),
                    turn("甲", 20, checkout = true),
                    turn("甲", 40, checkout = true)
                )
            )
        )
        val a = report.players.first { it.playerName == "甲" }
        // 结镖后剩余分是 0，那不是 High Finish；D20 收 40 才是。
        assertEquals(40, a.highFinish)
    }

    @Test
    fun `Best Leg = 赢下一局用掉的最少轮数`() {
        val report = MatchStats.of(
            snapshot(
                listOf(
                    turn("甲", 60),
                    turn("甲", 60),
                    turn("甲", 40, checkout = true), // 甲 3 轮赢一局
                    turn("乙", 60),
                    turn("乙", 40, checkout = true) // 乙 2 轮赢一局
                )
            )
        )
        assertEquals(3, report.players.first { it.playerName == "甲" }.bestLegTurns)
        assertEquals(2, report.players.first { it.playerName == "乙" }.bestLegTurns)
    }

    @Test
    fun `没有结镖就没有 Best Leg 与 High Finish`() {
        val report = MatchStats.of(snapshot(listOf(turn("甲", 60), turn("乙", 60))))
        val a = report.players.first { it.playerName == "甲" }
        assertNull(a.bestLegTurns)
        assertNull(a.highFinish)
    }

    @Test
    fun `流水打满一帧时标为可能截断`() {
        val turns = MutableList(RoomMatchRules.MAX_TURNS_IN_FRAME) { turn("甲", 60) }
        assertTrue(MatchStats.of(snapshot(turns)).truncated)
        assertTrue(!MatchStats.of(snapshot(turns.dropLast(1))).truncated)
    }

    @Test
    fun `没有流水的人各项为 0 或空，不炸`() {
        val report = MatchStats.of(snapshot(emptyList()))
        assertEquals(2, report.players.size)
        report.players.forEach {
            assertEquals(0, it.turns)
            assertEquals(0.0, it.threeDartAvg, 0.001)
            assertNull(it.first9Avg)
        }
    }
}
