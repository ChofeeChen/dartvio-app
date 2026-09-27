package com.dartvio.app.data.local

import com.dartvio.app.data.MatchRepository
import com.dartvio.app.data.local.dao.MatchRecordDao
import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.data.local.entity.MatchPlayerEntity
import com.dartvio.app.data.local.entity.MatchRecordEntity
import com.dartvio.app.domain.model.MatchSource
import com.dartvio.app.domain.model.MatchType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「联机局不算战绩」这条红线（M5 T10）。
 *
 * 两条断言缺一不可：
 * 1. 判据**本身**正确（[MatchStatsFilter]）；
 * 2. 判据**被执行**了 —— 即使取数侧忘了在 SQL 里排除，Repository 仍会过滤（见 [统计入口自己兜底]）。
 *
 * 只测第 1 条是不够的：红线失效最常见的形态不是判据写错，而是**没人调用它**。
 */
class MatchStatsFilterTest {

    @Test
    fun `只有联机局被排除`() {
        assertTrue("单机局照旧计入", MatchStatsFilter.countsForStats(record(source = MatchSource.LOCAL.name)))
        assertFalse("联机局不计入", MatchStatsFilter.countsForStats(record(source = MatchSource.LAN.name)))

        assertFalse(
            "读不出来的一行不计入：宁可少算一场，也不要去猜它的来源",
            MatchStatsFilter.countsForStats(null)
        )
    }

    @Test
    fun `未知来源按单机处理而不是被剔除`() {
        // 历史行补列时回填的是 LOCAL，与它们当年实际的来源一致；
        // 反过来（未知即剔除）会让一次枚举扩充静默抹掉一批老战绩。
        assertTrue(MatchStatsFilter.countsForStats(record(source = "BLUETOOTH")))
        assertEquals(MatchSource.LOCAL, MatchRecordEntity::class.java.let { MatchSource.fromKey("BLUETOOTH") })
    }

    /**
     * 统计入口**自己**兜底。
     *
     * 这里刻意给一个「SQL 没排除联机局」的 DAO：真实 DAO 的 `observeStatsSource`
     * 带 `WHERE source <> 'LAN'` 谓词，但那只是性能前置 —— 判据只有
     * [MatchStatsFilter] 一处，因此绕过谓词应当只是慢一点，而不是数据错了。
     */
    @Test
    fun `取数侧漏掉谓词时统计入口仍然只返回单机局`() = runBlocking {
        val repository = MatchRepository(FakeDao(rows = listOf(localRow(), lanRow())))

        val counted = repository.observeStatsMatches().first()

        assertEquals(1, counted.size)
        assertEquals(MatchSource.LOCAL.name, counted.single().match.source)
    }

    /** 历史口径包含联机局：打完要能回看，这是用户资产。 */
    @Test
    fun `历史口径仍然看得到联机局`() = runBlocking {
        val repository = MatchRepository(FakeDao(rows = listOf(localRow(), lanRow())))

        assertEquals(2, repository.observeMatches().first().size)
    }

    // ---------------------------------------------------------------- 辅助

    private fun record(source: String): MatchRecordEntity = MatchRecordEntity(
        matchId = "m-$source",
        gameType = MatchType.X01.name,
        matchType = MatchType.X01.name,
        matchTypeLabel = "X01",
        isFormal = false,
        containsAi = false,
        playerCount = 2,
        startedAt = 0L,
        endedAt = 1_000L,
        durationMs = 1_000L,
        winnerPlayerId = "p1",
        legCount = 1,
        legsToWin = 1,
        startScore = 501,
        x01Mode = "SINGLE_LEG",
        doubleOut = true,
        doubleIn = false,
        overtimeRule = "false",
        totalDarts = 9,
        source = source,
    )

    private fun localRow(): MatchWithPlayers =
        MatchWithPlayers(match = record(MatchSource.LOCAL.name), players = emptyList())

    private fun lanRow(): MatchWithPlayers =
        MatchWithPlayers(match = record(MatchSource.LAN.name), players = emptyList())

    /** 只实现被 Repository 用到的两个查询；其余留空实现（`clearAll` 等在接口里有默认实现）。 */
    private class FakeDao(private val rows: List<MatchWithPlayers>) : MatchRecordDao {

        override fun observeAll(): Flow<List<MatchWithPlayers>> = flowOf(rows)

        /** 故意**不**过滤：模拟「SQL 谓词被绕过」的场景。 */
        override fun observeStatsSource(): Flow<List<MatchWithPlayers>> = flowOf(rows)

        override suspend fun statsSourceOnce(): List<MatchWithPlayers> = rows

        override fun observeRecent(limit: Int): Flow<List<MatchWithPlayers>> = flowOf(rows)

        override suspend fun insertMatch(match: MatchRecordEntity) = Unit

        override suspend fun insertPlayers(players: List<MatchPlayerEntity>) = Unit

        override suspend fun findById(matchId: String): MatchWithPlayers? = null

        override suspend fun count(): Int = rows.size

        override suspend fun deletePlayersOf(matchId: String) = Unit

        override suspend fun deleteMatch(matchId: String) = Unit

        override suspend fun allIds(): List<String> = rows.map { it.match.matchId }
    }
}
