package com.dartvio.app.domain.stats

import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.data.local.entity.MatchPlayerEntity
import com.dartvio.app.data.local.entity.MatchRecordEntity
import com.dartvio.app.domain.model.CricketTarget
import com.dartvio.app.domain.model.TargetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统计口径单测 —— M9 §8.3.1「指标准入表」的可执行规格。
 *
 * 二期 2C 起口径为「**C1–C10 全部仅 standard**」（M2 §4.9.6②，比一期更严）：
 * - 事实统计（对局数 / 胜场 / 镖数）**全部玩法计入**；
 * - 衍生指标只认标准局 —— 既排除 no_score / cut_throat（变体维度），
 *   也排除 Tactics / Random（目标集维度，它们的变体名仍是 STANDARD，见 Q7）。
 *
 * 若哪天有人把 `standardRows` 过滤去掉、或把 `isCricketStandard` 简化成**只看变体名 / 只看目标集 / 丢掉 `gameType`**（三段缺一，r7 口径），这里会立刻红。
 */
class StatsCalculatorTest {

    // --------------------------------------------------------------- 用例

    @Test
    fun `得分类指标只统计 standard 变体`() {
        val standard = cricketMatch(
            matchId = "s1", variant = "STANDARD", isFormal = true, isWinner = true,
            dartsThrown = 30, turnsPlayed = 10, totalScore = 300, maxTurnScore = 60,
            marksTotal = 30, tripleHits = 3, bullHits = 1,
            closedAllSectionLegs = 1, turnsInClosedLegs = 10, closedSectionsTotal = 7,
        )
        // 非 standard：分恒为 0、marks 虚高。若混算会同时低估 Mark 率、虚增 C7 分母。
        val noScore = cricketMatch(
            matchId = "n1", variant = "NO_SCORE", isFormal = false, isWinner = false,
            dartsThrown = 10, turnsPlayed = 4, totalScore = 0, maxTurnScore = 20,
            marksTotal = 99, tripleHits = 9, bullHits = 9,
            closedAllSectionLegs = 1, turnsInClosedLegs = 4, closedSectionsTotal = 3,
        )

        val stats = StatsCalculator.compute(listOf(standard, noScore)).cricket

        // 事实统计：全部变体计入
        assertEquals(2, stats.matchCount)
        assertEquals(1, stats.winCount)

        // C1/C3/C5/C6/C7/C9 仅 standard
        assertEquals(30.0 / 30, stats.markRate, 1e-9)
        assertEquals(3.0 / 30, stats.tripleRate, 1e-9)
        assertEquals(300.0 / 10, stats.avgTurnScore, 1e-9)
        assertEquals(10.0 / 1, stats.avgTurnsToClose, 1e-9)
        assertEquals(300.0 / 30, stats.scoreEfficiency, 1e-9)
        assertEquals(60, stats.maxTurnScore)

        // C8 仅 standard
        assertEquals(1.0, stats.winRate, 1e-9)
    }

    @Test
    fun `C 2 C 4 C 10 也收窄到 standard`() {
        // 2C 起这三项也**仅 standard**（§4.9.6②「C1–C10 全部仅 standard」）：
        // cut_throat 的关闭进度与首关顺序被「送分给对手」的策略带偏，与标准局不可比。
        val standard = cricketMatch(
            matchId = "s1", variant = "STANDARD", isFormal = false, isWinner = false,
            dartsThrown = 30, turnsPlayed = 10, totalScore = 0, bullHits = 1,
            closedSectionsTotal = 7, firstClosedCsv = "20,19",
        )
        val cutThroat = cricketMatch(
            matchId = "c1", variant = "CUT_THROAT", isFormal = false, isWinner = false,
            dartsThrown = 10, turnsPlayed = 4, totalScore = 0, bullHits = 3,
            closedSectionsTotal = 3, firstClosedCsv = "18",
        )

        val stats = StatsCalculator.compute(listOf(standard, cutThroat)).cricket

        // C2 只算 standard：7 / 7
        assertEquals(1.0, stats.closeRate, 1e-9)
        // C4 只算 standard：1 / 30
        assertEquals(1.0 / 30, stats.bullRate, 1e-9)
        // C10 直方图只含 standard 的首关记录
        assertEquals(
            mapOf(
                CricketTarget.Number(20) to 1,
                CricketTarget.Number(19) to 1,
            ),
            stats.firstClosedHistogram
        )
        // 事实统计仍然两局都算
        assertEquals(2, stats.matchCount)
    }

    @Test
    fun `首关分区 CSV 含类别档与未知 token 时不抛异常且未知被跳过`() {
        // 读端必须宽容：老版本读到新版本写入的类别档 token（"D"）、
        // 或读到完全不认识的 token（"X"）时跳过即可 ——
        // 抛异常会把整个统计页打死，代价远大于少算一条。
        val row = cricketMatch(
            matchId = "c1", variant = "STANDARD", isFormal = false, isWinner = false,
            dartsThrown = 30, turnsPlayed = 10, totalScore = 0, closedSectionsTotal = 3,
            firstClosedCsv = "20,X,D",
        )

        val stats = StatsCalculator.compute(listOf(row)).cricket

        assertEquals(
            mapOf(
                CricketTarget.Number(20) to 1,
                CricketTarget.Category(TargetCategory.DOUBLES) to 1,
            ),
            stats.firstClosedHistogram
        )
    }

    @Test
    fun `空目标集列按默认 7 分区计入 C 2 分母`() {
        // 历史行与 standard 行的 targetSetCsv 都是空串 —— 语义就是默认 7 分区。
        // 分母若按「解析出几个 token」算，老数据的关闭率会直接变成 0。
        val legacy = cricketMatch(
            matchId = "l1", variant = "STANDARD", isFormal = false, isWinner = false,
            dartsThrown = 30, turnsPlayed = 10, totalScore = 0, closedSectionsTotal = 7,
        )

        val stats = StatsCalculator.compute(listOf(legacy)).cricket

        assertEquals(1.0, stats.closeRate, 1e-9)
    }

    @Test
    fun `非默认目标集的局不计入 C 2`() {
        // 2B 时期的预期是「裁剪目标集也进 C2，分母按各家目标集大小动态算」；
        // 2C 起 C2 收窄到 standard，而非默认目标集（Tactics / Random）**恒不是** standard，
        // 所以这类局整体被排除。分母的动态计算对标准局仍成立：标准局写空串 ⇒ 默认 7 分区，
        // 与硬编码 7 逐位相同（见上一条 `空目标集列按默认 7 分区计入 C 2 分母`）。
        val trimmed = cricketMatch(
            matchId = "t1", variant = "STANDARD", isFormal = false, isWinner = false,
            dartsThrown = 9, turnsPlayed = 3, totalScore = 0, closedSectionsTotal = 3,
            targetSetCsv = "20,19,18",
        )

        assertTrue("非默认目标集必须被判为非标准局", !trimmed.match.isCricketStandard)

        val stats = StatsCalculator.compute(listOf(trimmed)).cricket

        assertEquals(0.0, stats.closeRate, 1e-9)
        // 事实统计仍计入（历史不丢弃）
        assertEquals(1, stats.matchCount)
    }

    @Test
    fun `Tactics 与 Random 局不计入标准指标`() {
        // Q7：Tactics / Random **不新增变体取值**，`cricketVariant` 仍是 STANDARD，
        // 靠目标集区分。所以「只看变体名」会把它们全部漏进标准局指标。
        val tactics = cricketMatch(
            matchId = "t1", variant = "STANDARD", isFormal = true, isWinner = true,
            dartsThrown = 30, turnsPlayed = 10, totalScore = 900, marksTotal = 99,
            tripleHits = 9, bullHits = 9, closedSectionsTotal = 9,
            targetSetCsv = "20,19,18,17,16,15,D,T,25",
        )
        val random = cricketMatch(
            matchId = "r1", variant = "STANDARD", isFormal = true, isWinner = false,
            dartsThrown = 15, turnsPlayed = 5, totalScore = 200, marksTotal = 50,
            targetSetCsv = "20,19,18,17,16",
        )
        val standard = cricketMatch(
            matchId = "s1", variant = "STANDARD", isFormal = true, isWinner = true,
            dartsThrown = 30, turnsPlayed = 10, totalScore = 300, marksTotal = 30,
            tripleHits = 3, bullHits = 1, closedSectionsTotal = 7,
        )

        assertTrue("Tactics 局不是标准局", !tactics.match.isCricketStandard)
        assertTrue("Random 局不是标准局", !random.match.isCricketStandard)
        assertTrue("默认 7 分区 + STANDARD 才是标准局", standard.match.isCricketStandard)

        val stats = StatsCalculator.compute(listOf(tactics, random, standard)).cricket

        // 事实统计：三种玩法全部计入
        assertEquals(3, stats.matchCount)
        assertEquals(2, stats.winCount)
        // 衍生指标：只算标准局
        assertEquals(30.0 / 30, stats.markRate, 1e-9)
        assertEquals(1.0, stats.closeRate, 1e-9)
        assertEquals(1.0 / 30, stats.bullRate, 1e-9)
        assertEquals(1.0, stats.winRate, 1e-9)
    }

    @Test
    fun `C 8 只认 standard 的正式赛`() {
        val formalStandardWin = cricketMatch(
            matchId = "f1", variant = "STANDARD", isFormal = true, isWinner = true,
            dartsThrown = 30, turnsPlayed = 10, totalScore = 300,
        )
        // 休闲 standard 胜：计入事实胜场，但不进 C8
        val casualStandardWin = cricketMatch(
            matchId = "c1", variant = "STANDARD", isFormal = false, isWinner = true,
            dartsThrown = 30, turnsPlayed = 10, totalScore = 300,
        )
        // 正式 no_score 负：不是 standard，不进 C8
        val formalNoScoreLoss = cricketMatch(
            matchId = "f2", variant = "NO_SCORE", isFormal = true, isWinner = false,
            dartsThrown = 30, turnsPlayed = 10, totalScore = 0,
        )

        val stats = StatsCalculator.compute(
            listOf(formalStandardWin, casualStandardWin, formalNoScoreLoss)
        ).cricket

        assertEquals(1.0, stats.winRate, 1e-9)
        assertEquals(3, stats.matchCount)
        assertEquals(2, stats.winCount)
    }

    @Test
    fun `没有 Cricket 对局时不产生数据`() {
        val stats = StatsCalculator.compute(emptyList()).cricket
        assertTrue(!stats.hasData)
        assertEquals(0.0, stats.markRate, 1e-9)
    }

    // --------------------------------------------------------------- 构造

    private fun cricketMatch(
        matchId: String,
        variant: String,
        isFormal: Boolean,
        isWinner: Boolean,
        dartsThrown: Int,
        turnsPlayed: Int,
        totalScore: Int,
        maxTurnScore: Int = 0,
        marksTotal: Int = 0,
        tripleHits: Int = 0,
        bullHits: Int = 0,
        closedAllSectionLegs: Int = 0,
        turnsInClosedLegs: Int = 0,
        closedSectionsTotal: Int = 0,
        firstClosedCsv: String = "",
        targetSetCsv: String = "",
    ): MatchWithPlayers = MatchWithPlayers(
        match = MatchRecordEntity(
            matchId = matchId,
            gameType = "CRICKET",
            matchType = "CRICKET",
            matchTypeLabel = "Cricket",
            isFormal = isFormal,
            containsAi = false,
            playerCount = 1,
            startedAt = 0L,
            endedAt = 0L,
            durationMs = 1_000L,
            winnerPlayerId = if (isWinner) "p1" else null,
            legCount = 1,
            legsToWin = if (isFormal) 3 else 1,
            startScore = 0,
            x01Mode = null,
            doubleOut = false,
            doubleIn = false,
            overtimeRule = null,
            cricketVariant = variant,
            targetSetCsv = targetSetCsv,
            totalDarts = dartsThrown,
        ),
        players = listOf(
            MatchPlayerEntity(
                matchId = matchId,
                playerId = "p1",
                name = "玩家 1",
                isAi = false,
                aiDifficulty = null,
                orderIndex = 0,
                isWinner = isWinner,
                legsWon = if (isWinner) 1 else 0,
                dartsThrown = dartsThrown,
                turnsPlayed = turnsPlayed,
                totalScore = totalScore,
                maxTurnScore = maxTurnScore,
                remaining = 0,
                busts = 0,
                count180 = 0,
                bestCheckout = 0,
                checkoutAttempts = 0,
                marksTotal = marksTotal,
                tripleHits = tripleHits,
                bullHits = bullHits,
                closedAllSectionLegs = closedAllSectionLegs,
                turnsInClosedLegs = turnsInClosedLegs,
                closedSectionsTotal = closedSectionsTotal,
                firstClosedCsv = firstClosedCsv,
            )
        ),
    )
}
