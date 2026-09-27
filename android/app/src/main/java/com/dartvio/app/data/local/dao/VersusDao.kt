package com.dartvio.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.dartvio.app.data.local.entity.VersusMatchRecordEntity
import com.dartvio.app.data.local.entity.VersusRoundRecordEntity

/**
 * 「某人 × 某模式」的对抗训练统计（镖数与轮数）。
 *
 * 这就是需求里「镖数与命中分布计入双方训练统计」的落点：
 * 统计只在本模块内按**名字**聚合，不写进 `match_records` / `achievements`，
 * 所以既有胜率、成就、排行榜的数字一位都不会变。
 */
data class VersusPlayerModeStat(
    val modeKey: String,
    val dartCount: Int,
    val roundCount: Int,
)

/**
 * 双人对战（`versus_*` 两张表）的读写接口。
 *
 * 写入是**逐轮追加**的（[insertRound]），不是终局时一次性落库：
 * 需求「中途退出不丢数据」在这里就是一句 `INSERT` 的时机问题，没有别的机制。
 */
@Dao
interface VersusDao {

    /** 开赛插入 / 终局回填（同一行 REPLACE，不改主键）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMatch(match: VersusMatchRecordEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRound(round: VersusRoundRecordEntity)

    /**
     * 一条轮次 + 回写父行的累计计数，同一事务。
     *
     * 父行的 `roundCount` / `totalDarts` 随每一轮一起更新（而不是等终局才写）：
     * 这样即使进程在局中被杀，库里那行「进行中」的场次也带着正确的进度，
     * 「中途退出不丢数据」才真的成立 —— 否则计数会停在开赛那一刻的 0。
     */
    @Transaction
    suspend fun appendRound(
        match: VersusMatchRecordEntity,
        round: VersusRoundRecordEntity,
    ) {
        insertRound(round)
        upsertMatch(match)
    }

    @Query("SELECT * FROM versus_match_records WHERE matchId = :matchId")
    suspend fun findMatch(matchId: String): VersusMatchRecordEntity?

    @Query("SELECT * FROM versus_round_records WHERE matchId = :matchId ORDER BY id ASC")
    suspend fun listRounds(matchId: String): List<VersusRoundRecordEntity>

    /** 已结束的场次（战报 / 历史列表），最近结束在前。 */
    @Query("SELECT * FROM versus_match_records WHERE endedAt > 0 ORDER BY endedAt DESC LIMIT :limit")
    suspend fun listFinished(limit: Int): List<VersusMatchRecordEntity>

    /** 全部场次（含进行中），用于「上次配置」这类「最近一次」查询。 */
    @Query("SELECT * FROM versus_match_records ORDER BY startedAt DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<VersusMatchRecordEntity>

    @Query("SELECT COUNT(*) FROM versus_match_records WHERE endedAt > 0")
    suspend fun countFinished(): Int

    @Query(
        "SELECT modeKey, COALESCE(SUM(dartCount), 0) AS dartCount, COUNT(*) AS roundCount " +
            "FROM versus_round_records WHERE playerName = :playerName GROUP BY modeKey"
    )
    suspend fun statsByName(playerName: String): List<VersusPlayerModeStat>

    @Query(
        "SELECT modeKey, COALESCE(SUM(dartCount), 0) AS dartCount, COUNT(*) AS roundCount " +
            "FROM versus_round_records GROUP BY modeKey"
    )
    suspend fun statsAll(): List<VersusPlayerModeStat>

    /** 某人最近的出手轮次（含逐镖文本），命中分布的分析源。 */
    @Query(
        "SELECT * FROM versus_round_records WHERE playerName = :playerName " +
            "ORDER BY id DESC LIMIT :limit"
    )
    suspend fun listRoundsByPlayer(playerName: String, limit: Int): List<VersusRoundRecordEntity>

    @Query("DELETE FROM versus_round_records WHERE matchId = :matchId")
    suspend fun deleteRoundsOf(matchId: String)

    @Query("DELETE FROM versus_match_records WHERE matchId = :matchId")
    suspend fun deleteMatch(matchId: String)

    @Transaction
    suspend fun deleteById(matchId: String) {
        deleteRoundsOf(matchId)
        deleteMatch(matchId)
    }

    @Query("SELECT matchId FROM versus_match_records WHERE endedAt = 0")
    suspend fun unfinishedIds(): List<String>

    /** 清空本模块数据（设置页「清除统计数据」用）。 */
    @Transaction
    suspend fun clearAll() {
        deleteAllRounds()
        deleteAllMatches()
    }

    @Query("DELETE FROM versus_round_records")
    suspend fun deleteAllRounds()

    @Query("DELETE FROM versus_match_records")
    suspend fun deleteAllMatches()
}
