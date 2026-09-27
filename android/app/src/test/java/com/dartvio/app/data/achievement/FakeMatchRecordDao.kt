package com.dartvio.app.data.achievement

import com.dartvio.app.data.local.MatchStatsFilter
import com.dartvio.app.data.local.dao.MatchRecordDao
import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.data.local.entity.MatchPlayerEntity
import com.dartvio.app.data.local.entity.MatchRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 纯 JVM 的 [MatchRecordDao] 替身，供 `AchievementRepository` 的编排测试使用。
 *
 * `MatchRecordDao` 是接口，读操作都是 `suspend` / `Flow`，不需要真库即可实现。
 * 用不着 Room 的 in-memory database：`AchievementRepository` 只消费「对局列表」这一种
 * 事实，把所有 Room 能力都模拟出来反而会掩盖它真正依赖的契约。
 *
 * 刻意**不缓存 Flow**：每次 [observeAll] 都按当下的 [matches] 现取，
 * 这样测试改完夹具再调用就能读到新值，与真库「每次订阅都查一次」的行为一致。
 * （`@Transaction` 的 [MatchRecordDao.insertFullMatch] / `deleteById` / `clearAll` 是带默认实现
 * 的接口方法，不用也不应在这里重写。）
 */
class FakeMatchRecordDao : MatchRecordDao {

    /** 当前「库里」的对局。测试直接赋值即可，无需走插入流程。 */
    var matches: List<MatchWithPlayers> = emptyList()

    override fun observeAll(): Flow<List<MatchWithPlayers>> = flowOf(matches)

    override fun observeRecent(limit: Int): Flow<List<MatchWithPlayers>> =
        flowOf(matches.take(limit))

    /**
     * 战绩口径（M5 T10）：排除联机局。
     *
     * 真库的这一条是 SQL 谓词（`WHERE source <> 'LAN'`），这里用 [MatchStatsFilter]
     * 表达——**判据只有那一处**，替身不应该另立一套口径，否则成就测试通过
     * 而真机上的联机局照样能解锁成就。
     */
    override fun observeStatsSource(): Flow<List<MatchWithPlayers>> =
        flowOf(matches.filter { MatchStatsFilter.countsForStats(it.match) })

    override suspend fun statsSourceOnce(): List<MatchWithPlayers> =
        matches.filter { MatchStatsFilter.countsForStats(it.match) }

    override suspend fun findById(matchId: String): MatchWithPlayers? =
        matches.firstOrNull { it.match.matchId == matchId }

    override suspend fun count(): Int = matches.size

    override suspend fun allIds(): List<String> = matches.map { it.match.matchId }

    override suspend fun insertMatch(match: MatchRecordEntity) {
        matches = matches.filterNot { it.match.matchId == match.matchId } +
            MatchWithPlayers(match = match, players = emptyList())
    }

    /** 本测试直接构造完整的 [MatchWithPlayers] 夹具，不模拟「先插比赛再补玩家」的两步流程。 */
    override suspend fun insertPlayers(players: List<MatchPlayerEntity>) = Unit

    override suspend fun deleteMatch(matchId: String) {
        matches = matches.filterNot { it.match.matchId == matchId }
    }

    override suspend fun deletePlayersOf(matchId: String) = Unit
}
