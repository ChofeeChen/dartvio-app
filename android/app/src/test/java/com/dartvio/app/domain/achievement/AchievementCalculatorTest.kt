package com.dartvio.app.domain.achievement

import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.data.local.entity.MatchPlayerEntity
import com.dartvio.app.data.local.entity.MatchRecordEntity
import com.dartvio.app.domain.model.AiDifficulty
import com.dartvio.app.domain.model.MatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成就判定口径单测 —— 第③期 ③A 的**可执行规格**。
 *
 * 覆盖六条产品决策中最容易被改坏的四处：
 * ① 清单数量与分组、② 入门层放宽 / 连胜限正式赛、③ 已解锁不回退、⑥ 按达成度排序。
 */
class AchievementCalculatorTest {

    // ------------------------------------------------------------ 空态

    @Test
    fun `空数据时 26 项全部未解锁`() {
        val snapshot = AchievementCalculator.compute(AchievementInput())
        assertEquals(26, snapshot.total)
        assertEquals(0, snapshot.unlockedCount)
        assertTrue(snapshot.newlyUnlockedIds.isEmpty())
    }

    // ---------------------------------------------- 决策②：入门层放宽

    @Test
    fun `首场含 AI 的胜利即解锁四枚入门成就`() {
        val matches = listOf(
            match(
                matchId = "m1",
                containsAi = true,
                aiDifficulty = AiDifficulty.INTERMEDIATE.name,
                winner = true,
                count180 = 1,
                bestCheckout = 100,
            ),
        )
        val snapshot = AchievementCalculator.compute(AchievementInput(matches = matches))

        assertUnlocked(
            snapshot,
            "milestone_first_match",
            "milestone_first_win",
            "milestone_first_180",
            "milestone_checkout_100",
        )
        assertLocked(snapshot, "milestone_match_10", "x01_checkout_120", "x01_180_3", "x01_streak_3")
        assertEquals(1, progressOf(snapshot, "milestone_first_win").current)
    }

    @Test
    fun `180 累计到 3 次解锁三连击，未到 10 次仍锁着`() {
        val snapshot = computeMatches(match(matchId = "m1", count180 = 3))

        assertUnlocked(snapshot, "x01_180_3")
        assertLocked(snapshot, "x01_180_10")

        val progress = progressOf(snapshot, "x01_180_10")
        assertEquals(3, progress.current)
        assertEquals(0.3f, progress.ratio, 0.001f)
    }

    @Test
    fun `最高收镖 170 一次点亮三档收镖成就`() {
        val snapshot = computeMatches(match(matchId = "m1", bestCheckout = 170))
        assertUnlocked(snapshot, "milestone_checkout_100", "x01_checkout_120", "x01_checkout_170")
    }

    @Test
    fun `PPR 向下取整，59 点 9 不算达到 60`() {
        val below = computeMatches(match(matchId = "m1", dartsThrown = 30, totalScore = 599))
        assertLocked(below, "x01_ppr_60")

        val reach = computeMatches(match(matchId = "m2", dartsThrown = 30, totalScore = 600))
        assertUnlocked(reach, "x01_ppr_60")
        assertEquals(60, progressOf(reach, "x01_ppr_60").current)
    }

    // ------------------------------------- 决策②：连胜只认 S 级正式赛

    @Test
    fun `非正式赛的连胜不解锁连胜成就`() {
        val casual = (1..3).map { index ->
            match(matchId = "c$index", winner = true, isFormal = false, endedAt = index.toLong())
        }
        assertLocked(AchievementCalculator.compute(AchievementInput(matches = casual)), "x01_streak_3")
    }

    @Test
    fun `正式赛三连胜解锁，五连胜仍锁着`() {
        val formal = (1..3).map { index ->
            match(matchId = "f$index", winner = true, isFormal = true, endedAt = index.toLong())
        }
        val snapshot = AchievementCalculator.compute(AchievementInput(matches = formal))

        assertUnlocked(snapshot, "x01_streak_3")
        assertLocked(snapshot, "x01_streak_5", "x01_streak_10")
        assertEquals(3, progressOf(snapshot, "x01_streak_5").current)
    }

    @Test
    fun `正式赛连胜被败场打断后只算最长的一段`() {
        val matches = listOf(
            match(matchId = "f1", winner = true, isFormal = true, endedAt = 1),
            match(matchId = "f2", winner = false, isFormal = true, endedAt = 2),
            match(matchId = "f3", winner = true, isFormal = true, endedAt = 3),
            match(matchId = "f4", winner = true, isFormal = true, endedAt = 4),
        )
        assertEquals(2, progressOf(AchievementCalculator.compute(AchievementInput(matches = matches)), "x01_streak_3").current)
    }

    // ------------------------------------------------------- Cricket 口径

    @Test
    fun `Cricket 关满分区与三倍率 Mark 率达阈值才解锁`() {
        val matches = listOf(
            match(
                matchId = "c1",
                gameType = CRICKET,
                dartsThrown = 10,
                marksTotal = 20,
                tripleHits = 3,
                closedAllSectionLegs = 1,
            ),
        )
        val snapshot = AchievementCalculator.compute(AchievementInput(matches = matches))

        assertUnlocked(snapshot, "cricket_close_all", "cricket_triple_30", "cricket_mark_rate_2")
        assertEquals(30, progressOf(snapshot, "cricket_triple_30").current)
        assertEquals(20, progressOf(snapshot, "cricket_mark_rate_2").current)
    }

    @Test
    fun `非 standard 的 Cricket 关满分区不计入关满成就`() {
        // 变体改变「分是否存在」，no_score 的关满被规则层记为 1，但按 M9 §8.3.1 不得解锁技术类成就。
        val matches = listOf(
            match(
                matchId = "c1",
                gameType = CRICKET,
                cricketVariant = "NO_SCORE",
                dartsThrown = 30,
                closedAllSectionLegs = 1,
            ),
        )
        val snapshot = AchievementCalculator.compute(AchievementInput(matches = matches))

        assertLocked(snapshot, "cricket_close_all")
        assertEquals(0, progressOf(snapshot, "cricket_close_all").current)
    }

    @Test
    fun `Tactics 与 Random 局的关满分区不计入关满成就`() {
        // §4.9.6③：Cricket 组成就同样**仅 standard**。二者的 `cricketVariant` 仍是 STANDARD
        // （Q7），只能靠目标集把它们与标准局区分开。
        val matches = listOf(
            match(
                matchId = "t1",
                gameType = CRICKET,
                cricketVariant = "STANDARD",
                targetSetCsv = "20,19,18,17,16,15,D,T,25",
                dartsThrown = 30,
                closedAllSectionLegs = 1,
            ),
            match(
                matchId = "r1",
                gameType = CRICKET,
                cricketVariant = "STANDARD",
                targetSetCsv = "20,19,18,17,16",
                dartsThrown = 30,
                closedAllSectionLegs = 1,
            ),
        )
        val snapshot = AchievementCalculator.compute(AchievementInput(matches = matches))

        assertLocked(snapshot, "cricket_close_all")
        assertEquals(0, progressOf(snapshot, "cricket_close_all").current)
    }

    @Test
    fun `standard 的 Cricket 关满分区照常计入`() {
        val matches = listOf(
            match(
                matchId = "c1",
                gameType = CRICKET,
                cricketVariant = "STANDARD",
                dartsThrown = 30,
                closedAllSectionLegs = 1,
            ),
        )
        val snapshot = AchievementCalculator.compute(AchievementInput(matches = matches))

        assertUnlocked(snapshot, "cricket_close_all")
    }

    @Test
    fun `Cricket 三倍率 20 百分之不到 30 不解锁`() {
        val matches = listOf(
            match(matchId = "c1", gameType = CRICKET, dartsThrown = 10, tripleHits = 2),
        )
        assertLocked(AchievementCalculator.compute(AchievementInput(matches = matches)), "cricket_triple_30")
    }

    @Test
    fun `MPR 练习最佳只开对应档位`() {
        val low = computePractice(PracticeFacts(bestMpr = 0.99f))
        assertLocked(low, "cricket_mpr_1")

        val mid = computePractice(PracticeFacts(bestMpr = 2f))
        assertUnlocked(mid, "cricket_mpr_1", "cricket_mpr_2")
        assertLocked(mid, "cricket_mpr_4")

        val top = computePractice(PracticeFacts(bestMpr = 4f))
        assertUnlocked(top, "cricket_mpr_1", "cricket_mpr_2", "cricket_mpr_4")
    }

    // ------------------------------------------------------------- AI 挑战

    @Test
    fun `只在与专业 AI 的对局中取胜才解锁 AI 挑战`() {
        val beatPro = computeMatches(
            match(matchId = "p1", containsAi = true, aiDifficulty = AiDifficulty.PRO.name, winner = true),
        )
        assertUnlocked(beatPro, "ai_pro_win")

        val beatAdvanced = computeMatches(
            match(matchId = "p2", containsAi = true, aiDifficulty = AiDifficulty.ADVANCED.name, winner = true),
        )
        assertLocked(beatAdvanced, "ai_pro_win")

        val lostToPro = computeMatches(
            match(matchId = "p3", containsAi = true, aiDifficulty = AiDifficulty.PRO.name, winner = false),
        )
        assertLocked(lostToPro, "ai_pro_win")
    }

    // ---------------------------------------------------------- 决策⑤ 练习

    @Test
    fun `练习打卡推进累计天数与连续天数`() {
        val practice = PracticeFacts(sessions = 3, daysTotal = 3, streakCurrent = 3, streakBest = 3)
        val snapshot = AchievementCalculator.compute(AchievementInput(practice = practice))

        assertUnlocked(snapshot, "milestone_practice_first", "practice_days_3", "practice_streak_3")
        assertLocked(snapshot, "practice_streak_7")
    }

    @Test
    fun `连续天数按历史最高纪录判定，断签后仍保留成果`() {
        // 断签后的实际状态：当前 1 天，但历史最高 7 天
        val practice = PracticeFacts(sessions = 20, daysTotal = 20, streakCurrent = 1, streakBest = 7)
        val snapshot = AchievementCalculator.compute(AchievementInput(practice = practice))

        assertUnlocked(
            snapshot,
            "practice_streak_3",
            "practice_streak_7",
            "practice_days_3",
            "milestone_practice_first",
        )
        assertEquals(7, progressOf(snapshot, "practice_streak_7").current)
    }

    @Test
    fun `99 Darts 完成次数与 Count Up 最佳分各自解锁`() {
        val practice = PracticeFacts(ninetyNineCompleted = 1, countUpBestScore = 600)
        val snapshot = AchievementCalculator.compute(AchievementInput(practice = practice))

        assertUnlocked(snapshot, "ninety_nine_complete", "countup_600")
        assertLocked(computePractice(PracticeFacts(countUpBestScore = 599)), "countup_600")
    }

    // ---------------------------------------------------- 决策③ 不回退

    @Test
    fun `已落库的解锁状态优先，进度回落也不回退`() {
        val snapshot = AchievementCalculator.compute(
            input = AchievementInput(),
            unlockedAt = mapOf("x01_streak_10" to 123L),
        )
        val progress = progressOf(snapshot, "x01_streak_10")

        assertTrue(progress.unlocked)
        assertEquals(123L, progress.unlockedAt)
        assertTrue("已落库的成就不得重复上报为新解锁", snapshot.newlyUnlockedIds.isEmpty())
    }

    @Test
    fun `新解锁列表排除已落库项`() {
        val snapshot = AchievementCalculator.compute(
            input = AchievementInput(matches = listOf(match(matchId = "m1", count180 = 3))),
            unlockedAt = mapOf("milestone_first_match" to 1L),
        )
        assertFalse(snapshot.newlyUnlockedIds.contains("milestone_first_match"))
        assertTrue(snapshot.newlyUnlockedIds.contains("milestone_first_180"))
    }

    // ------------------------------------------------ 决策⑥ 展示排序

    @Test
    fun `未解锁项按达成比例降序置顶，已解锁项排在其后`() {
        val snapshot = computeMatches(match(matchId = "m1", count180 = 9))
        val ordered = AchievementCalculator.sortedForDisplay(snapshot.items)

        assertFalse("首个应是最接近达成的未解锁项", ordered.first().unlocked)
        assertEquals("x01_180_10", ordered.first().id)
        assertTrue("已解锁项应排在展示列表末尾", ordered.last().unlocked)
    }

    @Test
    fun `分组切分覆盖全部 26 项`() {
        val grouped = AchievementCalculator.byGroup(AchievementCalculator.compute(AchievementInput()).items)
        assertEquals(4, grouped.size)
        assertEquals(26, grouped.values.sumOf { it.size })
    }

    // ============================================================ 测试工具

    private fun computeMatches(vararg matches: MatchWithPlayers): AchievementSnapshot =
        AchievementCalculator.compute(AchievementInput(matches = matches.toList()))

    private fun computePractice(practice: PracticeFacts): AchievementSnapshot =
        AchievementCalculator.compute(AchievementInput(practice = practice))

    private fun progressOf(snapshot: AchievementSnapshot, id: String): AchievementProgress {
        val progress = snapshot.progressOf(id)
        assertNotNull("成就 $id 不在清单中", progress)
        return progress!!
    }

    private fun assertUnlocked(snapshot: AchievementSnapshot, vararg ids: String) {
        ids.forEach { id ->
            val progress = progressOf(snapshot, id)
            assertTrue(
                "期望 $id 已解锁，实际 current=${progress.current}/${progress.target}",
                progress.unlocked,
            )
        }
    }

    private fun assertLocked(snapshot: AchievementSnapshot, vararg ids: String) {
        ids.forEach { id ->
            val progress = progressOf(snapshot, id)
            assertFalse(
                "期望 $id 未解锁，实际 current=${progress.current}/${progress.target}",
                progress.unlocked,
            )
        }
    }

    /**
     * 构造一场对局：玩家 1 为「个人」（`orderIndex = 0`，与 StatsCalculator 口径一致），
     * 需要时追加一行 AI。
     */
    private fun match(
        matchId: String,
        gameType: String = X01,
        isFormal: Boolean = false,
        containsAi: Boolean = false,
        aiDifficulty: String? = null,
        winner: Boolean = false,
        dartsThrown: Int = 30,
        turnsPlayed: Int = 10,
        totalScore: Int = 300,
        count180: Int = 0,
        bestCheckout: Int = 0,
        marksTotal: Int = 0,
        tripleHits: Int = 0,
        closedAllSectionLegs: Int = 0,
        endedAt: Long = 0L,
        cricketVariant: String = "STANDARD",
        /** 二期 2C：默认空串 = 标准 7 分区；非空 = Tactics / Random 的裁剪目标集。 */
        targetSetCsv: String = "",
    ): MatchWithPlayers {
        val isX01 = gameType == X01

        val players = buildList {
            add(
                player(
                    matchId = matchId,
                    playerId = "p1",
                    orderIndex = 0,
                    isWinner = winner,
                    dartsThrown = dartsThrown,
                    turnsPlayed = turnsPlayed,
                    totalScore = totalScore,
                    count180 = count180,
                    bestCheckout = bestCheckout,
                    marksTotal = marksTotal,
                    tripleHits = tripleHits,
                    closedAllSectionLegs = closedAllSectionLegs,
                ),
            )
            if (containsAi) {
                add(
                    player(
                        matchId = matchId,
                        playerId = "p2",
                        name = "AI",
                        isAi = true,
                        aiDifficulty = aiDifficulty,
                        orderIndex = 1,
                        isWinner = !winner,
                        dartsThrown = dartsThrown,
                        turnsPlayed = turnsPlayed,
                    ),
                )
            }
        }

        return MatchWithPlayers(
            match = MatchRecordEntity(
                matchId = matchId,
                gameType = gameType,
                matchType = gameType,
                matchTypeLabel = gameType,
                isFormal = isFormal,
                containsAi = containsAi,
                playerCount = players.size,
                startedAt = 0L,
                endedAt = endedAt,
                durationMs = 60_000L,
                winnerPlayerId = when {
                    winner -> "p1"
                    containsAi -> "p2"
                    else -> null
                },
                legCount = 1,
                legsToWin = if (isFormal) 3 else 1,
                startScore = if (isX01) 501 else 0,
                x01Mode = if (isX01) {
                    if (isFormal) MatchMode.MULTI_LEG.name else MatchMode.CASUAL.name
                } else {
                    null
                },
                doubleOut = true,
                doubleIn = false,
                overtimeRule = null,
                cricketVariant = cricketVariant,
                targetSetCsv = targetSetCsv,
                totalDarts = if (containsAi) dartsThrown * 2 else dartsThrown,
            ),
            players = players,
        )
    }

    private fun player(
        matchId: String,
        playerId: String = "p1",
        name: String = "玩家 1",
        isAi: Boolean = false,
        aiDifficulty: String? = null,
        orderIndex: Int = 0,
        isWinner: Boolean = false,
        legsWon: Int = if (isWinner) 1 else 0,
        dartsThrown: Int = 30,
        turnsPlayed: Int = 10,
        totalScore: Int = 300,
        maxTurnScore: Int = 100,
        busts: Int = 0,
        count180: Int = 0,
        bestCheckout: Int = 0,
        checkoutAttempts: Int = 0,
        marksTotal: Int = 0,
        tripleHits: Int = 0,
        bullHits: Int = 0,
        closedAllSectionLegs: Int = 0,
        turnsInClosedLegs: Int = 0,
        closedSectionsTotal: Int = 0,
        firstClosedCsv: String = "",
    ): MatchPlayerEntity = MatchPlayerEntity(
        matchId = matchId,
        playerId = playerId,
        name = name,
        isAi = isAi,
        aiDifficulty = aiDifficulty,
        orderIndex = orderIndex,
        isWinner = isWinner,
        legsWon = legsWon,
        dartsThrown = dartsThrown,
        turnsPlayed = turnsPlayed,
        totalScore = totalScore,
        maxTurnScore = maxTurnScore,
        remaining = 0,
        busts = busts,
        count180 = count180,
        bestCheckout = bestCheckout,
        checkoutAttempts = checkoutAttempts,
        marksTotal = marksTotal,
        tripleHits = tripleHits,
        bullHits = bullHits,
        closedAllSectionLegs = closedAllSectionLegs,
        turnsInClosedLegs = turnsInClosedLegs,
        closedSectionsTotal = closedSectionsTotal,
        firstClosedCsv = firstClosedCsv,
    )

    private companion object {
        const val X01 = "X01"
        const val CRICKET = "CRICKET"
    }
}
