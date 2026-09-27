package com.dartvio.app.data

import com.dartvio.app.data.local.MatchStatsFilter
import com.dartvio.app.data.local.dao.MatchRecordDao
import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.data.local.entity.MatchPlayerEntity
import com.dartvio.app.data.local.entity.MatchRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 对局历史的唯一读写入口。
 *
 * ## 两个读取口径（M5 T10）
 *
 * - [observeMatches]：**历史口径**，含联机局 —— 打完的联机对局要能在历史里回看，
 *   这是用户资产，也是 T10 的验收项（「历史可见且标注来源」）。
 * - [observeStatsMatches]：**战绩口径**，排除联机局 —— M9 统计 / 排行榜用。
 *
 * 分成两个方法而不是「一个方法 + 调用方自己过滤」，是因为后者把红线的执行
 * 摊到了每一个调用点：漏掉一处不会报错，只会让某个指标悄悄多出一截。
 */
class MatchRepository(private val dao: MatchRecordDao) {

    /** 历史口径：全部对局（含联机），供历史列表 / 首页最近对局使用。 */
    fun observeMatches(): Flow<List<MatchWithPlayers>> = dao.observeAll()

    /**
     * 战绩口径：**排除联机**，供统计 / 排行榜使用。
     *
     * 这里再按 [MatchStatsFilter] 过滤一次（DAO 的 SQL 谓词已过滤过）：
     * 判据只应有一处，SQL 那一处只是性能前置，本处是兜底 ——
     * 两者指向同一个 [MatchStatsFilter]，因此不存在「两处口径分叉」。
     */
    fun observeStatsMatches(): Flow<List<MatchWithPlayers>> =
        dao.observeStatsSource().map { matches ->
            matches.filter { MatchStatsFilter.countsForStats(it.match) }
        }

    /**
     * 比赛大厅口径：**只要联机局**，与 [observeStatsMatches] 互补（两者相加 = 全部历史）。
     *
     * 为什么要单开一个口径：大厅里给陌生人看的 PPR，必须是「他在大厅里打出来的」。
     * 用战绩口径（排联机）去算，那个数永远是空的 —— 而「大厅 PPR」正是陌生人
     * 决定要不要进这间房的唯一依据（2026-09-26 反馈）。
     */
    fun observeArenaMatches(): Flow<List<MatchWithPlayers>> =
        dao.observeAll().map { matches -> matches.filter { it.match.isLan } }

    suspend fun saveMatch(match: MatchRecordEntity, players: List<MatchPlayerEntity>) =
        dao.insertFullMatch(match, players)

    suspend fun count(): Int = dao.count()

    suspend fun clearAll() = dao.clearAll()
}
