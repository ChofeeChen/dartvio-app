package com.dartvio.app.ui.lobby

import com.dartvio.app.domain.room.SpectatorPlayer
import com.dartvio.app.domain.room.SpectatorSnapshot
import com.dartvio.app.domain.room.SpectatorTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回合记分表的构建（[buildScoreTable]）与本局 PPR（[currentLegPpr]）：纯函数，不碰 Compose。
 *
 * 值得测试的原因很具体：这张表是用户判断「我还剩多少、能不能一镖收尾」的唯一依据，
 * 而剩余分的推进规则（爆分不变、只有投的那侧变、起始分靠反推、结镖后回满）
 * 一旦算错，界面不会报错 —— 它只是**安静地显示一个错误的剩余分**。
 *
 * 行口径对齐 n01 转播记分表（2026-09-27 反馈）：**一行 = 双方各投完一轮**，
 * 镖数列是本局累计的 3 的整数倍（0、3、6、9…）。
 */
class RoomScoreTableTest {

    private fun player(name: String, score: Int) = SpectatorPlayer(
        id = name, name = name, avatar = "HUMAN_1", legsWon = 0, score = score
    )

    private fun snapshot(turns: List<SpectatorTurn>) = SpectatorSnapshot(
        roomId = "000000",
        roomName = "测试房",
        configName = "501",
        leg = 1,
        legsToWin = 1,
        players = listOf(player("甲", 501), player("乙", 501)),
        turns = turns
    )

    private fun turn(seq: Int, name: String, darts: String, scored: Int, remaining: Int, bust: Boolean = false, checkout: Boolean = false) =
        SpectatorTurn(
            seq = seq, playerName = name, darts = darts, scored = scored,
            remaining = remaining, isBust = bust, isCheckout = checkout
        )

    @Test
    fun `没有回合时没有表`() {
        assertTrue(buildScoreTable(snapshot(emptyList())).isEmpty())
    }

    @Test
    fun `开局行写双方的起始分且没有得分`() {
        val table = buildScoreTable(
            snapshot(listOf(turn(1, "甲", "T20 T20 T20", 60, 441)))
        )
        assertEquals(ScoreRowKind.LEG_START, table.first().kind)
        val opening = table.first()
        assertNull(opening.leftScored)
        assertNull(opening.rightScored)
        assertEquals(501, opening.leftToGo)
        assertEquals(501, opening.rightToGo)
        assertEquals(0, opening.roundIndex)
        assertEquals(501, opening.startScore)
    }

    @Test
    fun `起始分由第一回合反推`() {
        // 第一回合就该显示完整起始分：帧里只有「投完之后的剩余」，起始分得靠得分倒推。
        val table = buildScoreTable(
            snapshot(listOf(turn(1, "甲", "T20 T20 T20", 60, 441)))
        )
        assertEquals(441, table[1].leftToGo)
        assertEquals(501, table[1].rightToGo)
    }

    @Test
    fun `同一轮并排在一行里`() {
        // n01 口径：甲投完 + 乙投完 = 一行。两人的得分与剩余分同屏对照。
        val table = buildScoreTable(
            snapshot(
                listOf(
                    turn(1, "甲", "T20 T20 T20", 60, 441),
                    turn(2, "乙", "T20 20 5", 45, 456)
                )
            )
        )
        assertEquals(2, table.size) // 起始行 + 第 1 轮
        val round = table[1]
        assertEquals(ScoreRowKind.ROUND, round.kind)
        assertEquals("60", round.leftScored)
        assertEquals("45", round.rightScored)
        assertEquals(441, round.leftToGo)
        assertEquals(456, round.rightToGo)
        assertEquals(1, round.roundIndex)
    }

    @Test
    fun `只有投掷那一侧的剩余分变化`() {
        val table = buildScoreTable(
            snapshot(
                listOf(
                    turn(1, "甲", "T20 T20 T20", 60, 441),
                    turn(2, "乙", "T20 20 5", 45, 456),
                    turn(3, "甲", "T20 T20 20", 80, 361)
                )
            )
        )
        // 第 1 轮：双方都投完 → 441 / 456。
        assertEquals(441 to 456, table[1].leftToGo to table[1].rightToGo)
        // 第 2 轮：甲投完、乙还没投 → 乙侧带下来 456（占位行）。
        assertEquals(361, table[2].leftToGo)
        assertEquals(456, table[2].rightToGo)
        assertEquals(2, table[2].pendingSide)
    }

    @Test
    fun `爆分记 X 且剩余分不变`() {
        val table = buildScoreTable(
            snapshot(
                listOf(
                    turn(1, "甲", "T20 T20 T20", 60, 441),
                    turn(2, "乙", "T20 T20 T20", 0, 501, bust = true)
                )
            )
        )
        assertEquals("X", table[1].rightScored)
        // 爆分不减分：乙的剩余仍是起始分，甲的也不受影响。
        assertEquals(501, table[1].rightToGo)
        assertEquals(441, table[1].leftToGo)
    }

    @Test
    fun `第一回合就爆分时的起始分`() {
        // 爆分时 remaining 就是回合开始前的分数，直接拿它当起始分（不能再减一次得分）。
        val table = buildScoreTable(
            snapshot(listOf(turn(1, "甲", "T20 T20 T20", 0, 501, bust = true)))
        )
        assertEquals(501, table.first().leftToGo)
        assertEquals(501, table.first().rightToGo)
    }

    @Test
    fun `镖数列按轮累计为三的整数倍`() {
        // n01 口径：0、3、6、9…（起始行是 0）。收镖那轮即使只投 2 镖也按 3 计 ——
        // 镖数列是「轮」的刻度，不是镖的流水。
        val table = buildScoreTable(
            snapshot(
                listOf(
                    turn(1, "甲", "T20 T20 T20", 60, 441),
                    turn(2, "乙", "T20 20 5", 45, 456),
                    turn(3, "甲", "T20 D20", 60, 0, checkout = true)
                )
            )
        )
        val roundIndexes = table.filter { it.kind == ScoreRowKind.ROUND }.map { it.roundIndex }
        assertEquals(listOf(1, 2), roundIndexes)
        // 界面显示 roundIndex * 3 ⇒ 0（起始行）、3、6，随后收镖结束本局、
        // 追加下一局的起始行（镖数列回到 0）。
        val dartsShown = table.map { if (it.kind == ScoreRowKind.LEG_START) 0 else it.roundIndex * 3 }
        assertEquals(listOf(0, 3, 6, 0), dartsShown)
    }

    @Test
    fun `三镖文本转镖数`() {
        // countDarts 仍被本局 PPR 使用。
        assertEquals(3, countDarts("T20 T20 D20"))
        assertEquals(1, countDarts("BULL"))
        assertEquals(0, countDarts(""))
        assertEquals(2, countDarts("T20  D20")) // 连续空格不该多数一镖
    }

    @Test
    fun `每一局都以一行初始分开头`() {
        // 结镖之后双方回到初始分并另起一行：界面靠它在两局之间画粗分隔线。
        val table = buildScoreTable(
            snapshot(
                listOf(
                    turn(1, "甲", "T20 T20 T20", 60, 441),
                    turn(2, "乙", "T20 T20 T20", 60, 441),
                    turn(3, "甲", "T20 T20 D20", 60, 0, checkout = true),
                    turn(4, "乙", "T20 T20 T20", 60, 441)
                )
            )
        )
        val startRows = table.withIndex().filter { it.value.kind == ScoreRowKind.LEG_START }
        assertEquals(2, startRows.size)
        // 第二局的起始行紧跟在收镖那一轮之后（收镖行 + 起始行）。
        assertEquals(3, startRows.last().index)
        assertEquals(1, startRows.first().value.legIndex)
        assertEquals(2, startRows.last().value.legIndex)
        assertEquals(501, startRows.last().value.startScore)
        // 新一局：乙打着的那一行，甲的剩余已经回到 501。
        assertEquals(501, table.last().leftToGo)
        assertEquals(441, table.last().rightToGo)
    }

    @Test
    fun `收镖那一轮可以只有一方投`() {
        // 收镖即时结束本局：对方这一轮不再存在，收镖行缺的一侧留空（不是 0 分）。
        val table = buildScoreTable(
            snapshot(
                listOf(
                    turn(1, "甲", "T20 T20 T20", 60, 441),
                    turn(2, "甲", "T20 T20 D20", 60, 381, checkout = true)
                )
            )
        )
        val checkoutRound = table[2]
        assertEquals(ScoreRowKind.ROUND, checkoutRound.kind)
        assertEquals("60", checkoutRound.leftScored)
        assertNull(checkoutRound.rightScored)
        assertEquals(381, checkoutRound.leftToGo)
        // 末尾是下一局的起始行。
        assertEquals(ScoreRowKind.LEG_START, table[3].kind)
    }

    @Test
    fun `同一人连投时上一轮先落表`() {
        // 帧是不可信输入：万一同一人连投两轮，覆盖会让表上凭空少一行。
        val table = buildScoreTable(
            snapshot(
                listOf(
                    turn(1, "甲", "T20 T20 T20", 60, 441),
                    turn(2, "甲", "20 20 20", 20, 421)
                )
            )
        )
        assertEquals(60, table[1].leftScored?.toInt())
        assertEquals(441, table[1].leftToGo)
        assertEquals(20, table[2].leftScored?.toInt())
        assertEquals(421, table[2].leftToGo)
    }

    @Test
    fun `轮次按本局重算`() {
        val s = snapshot(
            listOf(
                turn(1, "甲", "T20 T20 T20", 60, 441),
                turn(2, "乙", "T20 T20 T20", 60, 441),
                turn(3, "甲", "T20 T20 D20", 60, 0, checkout = true),
                turn(4, "乙", "T20 T20 T20", 60, 441)
            )
        )
        // 新一局已经打了 1 轮 ⇒ 显示 R 2（本局序号从「正在打的第几轮」算）
        assertEquals(2, currentRound(s))
    }
}

/**
 * 本局实时 PPR（[currentLegPpr]）。
 *
 * 它是卡上唯一会**随每一手变化**的数字，也是判断「谁今天手感更好」的依据；
 * 算错的表现非常隐蔽 —— 界面照常显示一个看起来合理的数。
 */
class CurrentLegPprTest {

    private fun snapshot(turns: List<SpectatorTurn>) = SpectatorSnapshot(
        roomId = "r",
        roomName = "测试房",
        configName = "501",
        leg = 1,
        legsToWin = 1,
        players = listOf(
            SpectatorPlayer("甲", "甲", "HUMAN_1", 0, 501),
            SpectatorPlayer("乙", "乙", "HUMAN_1", 0, 501)
        ),
        turns = turns
    )

    private fun turn(seq: Int, name: String, darts: String, scored: Int, bust: Boolean = false, checkout: Boolean = false) =
        SpectatorTurn(seq, name, darts, scored, 0, bust, checkout)

    @Test
    fun `本局还没投过镖就不给数`() {
        // 给 0.0 会被读成「他很菜」，而真实含义只是「还没轮到」。
        assertNull(currentLegPpr(snapshot(emptyList()), "甲"))
    }

    @Test
    fun `只算本局且按三镖一轮折算`() {
        val s = snapshot(
            listOf(
                turn(1, "甲", "T20 T20 T20", 60),
                turn(2, "乙", "20 20 20", 20),
                turn(3, "甲", "T20 T20 T20", 60)
            )
        )
        // 甲两轮 6 镖共 120 分 → 每轮 60（PPR = 得分 ÷ (镖数 / 3)）
        assertEquals(60.0, currentLegPpr(s, "甲")!!, 1e-9)
        assertEquals(20.0, currentLegPpr(s, "乙")!!, 1e-9)
    }

    @Test
    fun `爆分照常计入分母`() {
        // 爆分真实占用了这一轮的镖：剔除它会让 PPR 只统计「发挥好的回合」。
        val s = snapshot(
            listOf(
                turn(1, "甲", "T20 T20 T20", 60),
                turn(2, "甲", "T20 T20 T20", 0, bust = true)
            )
        )
        assertEquals(30.0, currentLegPpr(s, "甲")!!, 1e-9)
    }

    @Test
    fun `上一局的成绩不带到新一局`() {
        val s = snapshot(
            listOf(
                turn(1, "甲", "T20 T20 T20", 60),
                turn(2, "甲", "T20 D20", 60, checkout = true),
                turn(3, "甲", "20 20 20", 20)
            )
        )
        assertEquals(20.0, currentLegPpr(s, "甲")!!, 1e-9)
    }
}
