package com.dartvio.app.domain.leaderboard

import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.data.local.entity.MatchPlayerEntity
import com.dartvio.app.data.local.entity.MatchRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地排行榜准入规则的可执行规格（第③期 ③B）。
 *
 * 四条准入规则各有一个「反例」用例：**只测「谁能进」不够，必须同时钉住「谁不能进」**。
 * 榜最容易出的错不是算错数，而是把不该算的算进去（休闲局、AI 局、位置 ID、Cricket 局），
 * 而且这种错在界面上看不出异常 —— 数字照样显示，只是没有意义。
 */
class LeaderboardCalculatorTest {

    private val alice = LeaderboardPlayer("profile-a", "阿达")
    private val bob = LeaderboardPlayer("profile-b", "小博")

    // ------------------------------------------------------------ 样本门槛

    @Test
    fun `正式赛不足 3 场只进数据不足区 不进主榜`() {
        val matches = listOf(
            match("m1", listOf(RowSpec("profile-a", isWinner = true))),
            match("m2", listOf(RowSpec("profile-a"))),
        )

        val snapshot = LeaderboardCalculator.compute(matches, listOf(alice))

        assertTrue(snapshot.ranked.isEmpty())
        assertEquals(1, snapshot.insufficient.size)
        assertEquals(2, snapshot.insufficient.first().formalMatches)
        assertFalse(snapshot.insufficient.first().qualified)
        assertFalse(snapshot.isEmpty)
    }

    @Test
    fun `正式赛满 3 场进入主榜并算出 PPR 与胜率`() {
        val matches = listOf(
            match("m1", listOf(RowSpec("profile-a", isWinner = true))),
            match("m2", listOf(RowSpec("profile-a", isWinner = true))),
            match("m3", listOf(RowSpec("profile-a"))),
        )

        val snapshot = LeaderboardCalculator.compute(matches, listOf(alice))

        assertTrue(snapshot.insufficient.isEmpty())
        val entry = snapshot.ranked.single()
        assertEquals(3, entry.formalMatches)
        assertEquals(2, entry.wins)
        // 每场 30 镖 300 分 → PPR = 900 / (90 / 3) = 30
        assertEquals(30.0, entry.ppr, 1e-9)
        assertEquals(2.0 / 3, entry.winRate, 1e-9)
    }

    @Test
    fun `连一场准入记录都没有时榜单为空`() {
        val snapshot = LeaderboardCalculator.compute(emptyList(), listOf(alice))
        assertTrue(snapshot.isEmpty)
    }

    @Test
    fun `没有任何建档玩家时榜单为空`() {
        val matches = listOf(match("m1", listOf(RowSpec("profile-a", isWinner = true))))
        assertTrue(LeaderboardCalculator.compute(matches, emptyList()).isEmpty)
    }

    // ------------------------------------------------------------ 准入反例

    @Test
    fun `休闲局不计入排名`() {
        val matches = (1..3).map { i ->
            match("m$i", listOf(RowSpec("profile-a", isWinner = true)), isFormal = false)
        }
        assertTrue(LeaderboardCalculator.compute(matches, listOf(alice)).isEmpty)
    }

    @Test
    fun `未结束的正式赛不计入排名`() {
        val matches = (1..3).map { i ->
            match("m$i", listOf(RowSpec("profile-a", isWinner = true)), finished = false)
        }
        assertTrue(LeaderboardCalculator.compute(matches, listOf(alice)).isEmpty)
    }

    @Test
    fun `AI 行不计入排名`() {
        val matches = (1..3).map { i ->
            match("m$i", listOf(RowSpec("profile-a", isAi = true, isWinner = true)))
        }
        assertTrue(LeaderboardCalculator.compute(matches, listOf(alice)).isEmpty)
    }

    @Test
    fun `旧数据里的位置 ID 不计入排名`() {
        // 升级前的历史行 player_id 是位置 ID，无法归属到具体人 → 必须过滤。
        val matches = (1..3).map { i ->
            match("m$i", listOf(RowSpec("p1", isWinner = true)))
        }
        assertTrue(LeaderboardCalculator.compute(matches, listOf(alice)).isEmpty)
    }

    @Test
    fun `Cricket 局不进 X01 榜`() {
        val matches = (1..3).map { i ->
            match("m$i", listOf(RowSpec("profile-a", isWinner = true)), gameType = "CRICKET")
        }
        assertTrue(LeaderboardCalculator.compute(matches, listOf(alice)).isEmpty)
    }

    @Test
    fun `同一场里只取本机那一行 对手行不混算`() {
        val matches = (1..3).map { i ->
            match(
                "m$i",
                listOf(
                    RowSpec("profile-a", isWinner = true, dartsThrown = 30, totalScore = 300),
                    // 对手的位置 ID 不该被当成第二名玩家
                    RowSpec("p2", dartsThrown = 90, totalScore = 900),
                ),
            )
        }

        val snapshot = LeaderboardCalculator.compute(matches, listOf(alice))

        val entry = snapshot.ranked.single()
        assertEquals(30.0, entry.ppr, 1e-9)
        assertEquals(3, entry.formalMatches)
    }

    // ------------------------------------------------------------ 排序维度

    @Test
    fun `四种排序维度各自生效`() {
        // 阿达：分高但只打了 3 场、只赢 1 场、没收过镖
        val aliceMatches = (1..3).map { i ->
            match("a$i", listOf(RowSpec("profile-a", isWinner = i == 1, checkout = 0)))
        }
        // 小博：分低但打了 4 场全胜、最高收镖 100
        val bobMatches = (1..4).map { i ->
            match(
                "b$i",
                listOf(RowSpec("profile-b", isWinner = true, totalScore = 150, checkout = 100)),
            )
        }
        val all = aliceMatches + bobMatches
        val players = listOf(alice, bob)

        val byPpr = LeaderboardCalculator.compute(all, players, LeaderboardSort.PPR).ranked
        assertEquals(listOf("阿达", "小博"), byPpr.map { it.name })

        val byWinRate = LeaderboardCalculator.compute(all, players, LeaderboardSort.WIN_RATE).ranked
        assertEquals(listOf("小博", "阿达"), byWinRate.map { it.name })

        val byCheckout =
            LeaderboardCalculator.compute(all, players, LeaderboardSort.HIGHEST_CHECKOUT).ranked
        assertEquals(listOf("小博", "阿达"), byCheckout.map { it.name })
        assertEquals(100, byCheckout.first().highestCheckout)

        val byCount = LeaderboardCalculator.compute(all, players, LeaderboardSort.MATCH_COUNT).ranked
        assertEquals(listOf("小博", "阿达"), byCount.map { it.name })
        assertEquals(listOf(4, 3), byCount.map { it.formalMatches })
    }

    @Test
    fun `样本不足区同样按当前维度排序`() {
        val aliceMatches = listOf(
            match("a1", listOf(RowSpec("profile-a", totalScore = 300))),
            match("a2", listOf(RowSpec("profile-a", totalScore = 300))),
        )
        val bobMatches = listOf(
            match("b1", listOf(RowSpec("profile-b", totalScore = 150))),
            match("b2", listOf(RowSpec("profile-b", totalScore = 150))),
        )

        val snapshot = LeaderboardCalculator.compute(
            aliceMatches + bobMatches,
            listOf(alice, bob),
            LeaderboardSort.PPR,
        )

        assertEquals(listOf("阿达", "小博"), snapshot.insufficient.map { it.name })
    }

    // ------------------------------------------------------------ 构造

    private data class RowSpec(
        val playerId: String,
        val name: String = playerId,
        val isAi: Boolean = false,
        val isWinner: Boolean = false,
        val dartsThrown: Int = 30,
        val totalScore: Int = 300,
        val checkout: Int = 0,
    )

    private fun match(
        matchId: String,
        rows: List<RowSpec>,
        isFormal: Boolean = true,
        finished: Boolean = true,
        gameType: String = "X01",
    ): MatchWithPlayers = MatchWithPlayers(
        match = MatchRecordEntity(
            matchId = matchId,
            gameType = gameType,
            matchType = gameType,
            matchTypeLabel = gameType,
            isFormal = isFormal,
            containsAi = rows.any { it.isAi },
            playerCount = rows.size,
            startedAt = 0L,
            endedAt = 1_000L,
            durationMs = 1_000L,
            winnerPlayerId = when {
                !finished -> null
                else -> rows.firstOrNull { it.isWinner }?.playerId ?: rows.first().playerId
            },
            legCount = 3,
            legsToWin = 3,
            startScore = 501,
            x01Mode = null,
            doubleOut = true,
            doubleIn = false,
            overtimeRule = null,
            cricketVariant = "STANDARD",
            totalDarts = rows.sumOf { it.dartsThrown },
        ),
        players = rows.mapIndexed { index, spec ->
            MatchPlayerEntity(
                matchId = matchId,
                playerId = spec.playerId,
                name = spec.name,
                isAi = spec.isAi,
                aiDifficulty = null,
                orderIndex = index,
                isWinner = spec.isWinner,
                legsWon = if (spec.isWinner) 3 else 0,
                dartsThrown = spec.dartsThrown,
                turnsPlayed = spec.dartsThrown / 3,
                totalScore = spec.totalScore,
                maxTurnScore = 0,
                remaining = 0,
                busts = 0,
                count180 = 0,
                bestCheckout = spec.checkout,
                checkoutAttempts = 0,
                marksTotal = 0,
                tripleHits = 0,
                bullHits = 0,
                closedAllSectionLegs = 0,
                turnsInClosedLegs = 0,
                closedSectionsTotal = 0,
                firstClosedCsv = "",
            )
        },
    )
}
