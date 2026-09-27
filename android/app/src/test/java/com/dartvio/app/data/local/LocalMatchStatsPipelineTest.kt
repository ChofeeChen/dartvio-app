package com.dartvio.app.data.local

import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.domain.model.AiDifficulty
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.Player
import com.dartvio.app.domain.model.PlayerType
import com.dartvio.app.domain.stats.MatchFacts
import com.dartvio.app.domain.stats.PlayerMatchFacts
import com.dartvio.app.domain.stats.StatsCalculator
import com.dartvio.app.ui.game.GameUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **「打完一场本地 X01 ⇒ 数据页有数据」这条链路**（2026-09-27 真机反馈：打完一局本地 X01，
 * 「本地对局 · X01」却是空的）。
 *
 * 为什么单独测这一段：这条链路上任何一环断掉，界面都不会报错 ——
 * 它只是安静地显示「还没有对局记录」。而报错至少能指出是哪一环，静默的空页面不能。
 *
 * 覆盖的三环：
 * 1. [MatchMapper] 把结算那一刻的 [GameUiState] 落成库里的两行（比赛 + 玩家）；
 * 2. 落下去的行在**战绩口径**上成立（不是联机局，见 [MatchStatsFilter]）；
 * 3. [StatsCalculator] 从这两行算出的 X01 指标非空（PPR / 胜率 / 最高收尾都有值）。
 */
class LocalMatchStatsPipelineTest {

    private val config = MatchConfig(
        matchType = MatchType.X01,
        targetScore = 501,
        // 默认先赢 1 局：赢下第一局即整场结束（见 X01SetupScreen.legsToWin 的注释）。
        mode = MatchMode.MULTI_LEG,
        legsToWin = 1,
    )

    private val players = listOf(
        Player(id = "p1", name = "我"),
        Player(
            id = "p2", name = "AI",
            type = PlayerType.AI,
            aiDifficulty = AiDifficulty.INTERMEDIATE,
        ),
    )

    /** 一场打完的 501：本人 18 镖 300 分收镖，AI 21 镖 240 分。 */
    private fun finishedState(): GameUiState = GameUiState(
        config = config,
        players = players,
        isMatchFinished = true,
        winnerPlayerId = "p1",
        legsWon = mapOf("p1" to 1),
        facts = MatchFacts(
            mapOf(
                "p1" to PlayerMatchFacts(
                    dartsThrown = 18, turnsPlayed = 6, totalScore = 300,
                    maxTurnScore = 100, bestCheckout = 40, checkoutAttempts = 2,
                ),
                "p2" to PlayerMatchFacts(
                    dartsThrown = 21, turnsPlayed = 7, totalScore = 240,
                    maxTurnScore = 60, busts = 1,
                ),
            )
        ),
    )

    @Test
    fun `打完一场本地 X01 落库后就出现在统计里`() {
        val state = finishedState()
        val record = MatchMapper.toMatchEntity(state, "m1", endedAt = 1_000L, localProfileId = "local-profile")
        val rows = MatchMapper.toPlayerEntities(state, "m1", localProfileId = "local-profile")

        // 1. 玩法与来源：单机局必须被战绩口径接受，否则后面一切都是空的。
        assertEquals(MatchType.X01.name, record.gameType)
        assertTrue(MatchStatsFilter.countsForStats(record))
        // 2. 本人那一行：位置 ID 换成稳定档案 ID，且是出手顺序 0（统计只认这一行）。
        val self = rows.first { it.orderIndex == 0 }
        assertEquals("local-profile", self.playerId)
        assertTrue(self.isWinner)

        // 3. 指标必须非空 —— 这正是用户看到「空白」的那一步。
        val stats = StatsCalculator.compute(listOf(MatchWithPlayers(record, rows))).x01
        assertTrue(stats.hasData)
        assertEquals(1, stats.matchCount)
        assertEquals(1, stats.winCount)
        // PPR = 300 ÷ (18 / 3)
        assertEquals(50.0, stats.pprAll, 1e-9)
        assertEquals(1.0, stats.winRateAll, 1e-9)
        assertEquals(40, stats.highestCheckout)
        assertEquals(100, stats.maxTurnScore)
    }

    @Test
    fun `联机局不进本地对局口径`() {
        // 反过来钉住红线：大厅里打的那一场不能算进「本地对局」，
        // 否则「拔网线」就成了提高数据的手段（M5 T10）。
        //
        // 这道闸门在**取数侧**（MatchRepository.observeStatsMatches → MatchStatsFilter），
        // 不在计算器里 —— 计算器只算「递给它的那些行」，不知道行是从哪来的。
        val state = finishedState()
        val lanRecord = MatchMapper.toMatchEntity(state, "m2", endedAt = 1_000L)
            .copy(source = com.dartvio.app.domain.model.MatchSource.LAN.name)
        val rows = MatchMapper.toPlayerEntities(state, "m2")

        assertTrue(lanRecord.isLan)
        assertTrue(!MatchStatsFilter.countsForStats(lanRecord))
    }

    @Test
    fun `整场没打完就不该有这条记录`() {
        // 与实现约定一致：只有整场结束才落库（一局获胜只是中场）。
        // 这条断言钉住「结算时机」—— 它是这次空白问题的根因所在的一侧。
        val state = finishedState().copy(isMatchFinished = false, winnerPlayerId = null)
        val record = MatchMapper.toMatchEntity(state, "m3", endedAt = 1_000L)
        assertEquals(null, record.winnerPlayerId)
    }
}
