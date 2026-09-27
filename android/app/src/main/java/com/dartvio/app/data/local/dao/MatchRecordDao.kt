package com.dartvio.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.dartvio.app.data.local.entity.MatchPlayerEntity
import com.dartvio.app.data.local.entity.MatchRecordEntity
import kotlinx.coroutines.flow.Flow

/** 一场比赛 + 其全部玩家行。 */
data class MatchWithPlayers(
    @Embedded val match: MatchRecordEntity,
    @Relation(parentColumn = "matchId", entityColumn = "matchId")
    val players: List<MatchPlayerEntity>,
)

@Dao
interface MatchRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: MatchRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayers(players: List<MatchPlayerEntity>)

    /** 整场落库（比赛 + 玩家），同一事务保证不出半截数据。 */
    @Transaction
    suspend fun insertFullMatch(match: MatchRecordEntity, players: List<MatchPlayerEntity>) {
        insertMatch(match)
        insertPlayers(players)
    }

    @Transaction
    @Query("SELECT * FROM match_records ORDER BY endedAt DESC")
    fun observeAll(): Flow<List<MatchWithPlayers>>

    @Transaction
    @Query("SELECT * FROM match_records ORDER BY endedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<MatchWithPlayers>>

    /**
     * M9 统计 / 成就 / 排行榜的**唯一**取数入口：**排除**联机（LAN）对局（M5 T10）。
     *
     * 与 [observeAll] 的关系：后者是**历史口径**（什么都能看到，历史列表用它，
     * 联机局要能被回看）；本方法是**战绩口径**。两者刻意分成两个名字 ——
     * 合成一个、再靠调用方补一句过滤，漏掉的那处不会报错，只会静默污染一个指标。
     *
     * 这里的 SQL 谓词只是**性能前置**（少读一批行），真正的判据是
     * [com.dartvio.app.data.local.MatchStatsFilter]：调用方还会按它再过滤一次，
     * 于是「忘了写谓词」退化成「慢一点」，而不是「数据错了」。
     */
    @Transaction
    @Query("SELECT * FROM match_records WHERE source <> 'LAN' ORDER BY endedAt DESC")
    fun observeStatsSource(): Flow<List<MatchWithPlayers>>

    /** [observeStatsSource] 的一次性版本（触发式重算用，它不常驻订阅）。 */
    @Transaction
    @Query("SELECT * FROM match_records WHERE source <> 'LAN' ORDER BY endedAt DESC")
    suspend fun statsSourceOnce(): List<MatchWithPlayers>

    @Transaction
    @Query("SELECT * FROM match_records WHERE matchId = :matchId")
    suspend fun findById(matchId: String): MatchWithPlayers?

    @Query("SELECT COUNT(*) FROM match_records")
    suspend fun count(): Int

    @Query("DELETE FROM match_players WHERE matchId = :matchId")
    suspend fun deletePlayersOf(matchId: String)

    @Query("DELETE FROM match_records WHERE matchId = :matchId")
    suspend fun deleteMatch(matchId: String)

    @Transaction
    suspend fun deleteById(matchId: String) {
        deletePlayersOf(matchId)
        deleteMatch(matchId)
    }

    /** 清空全部历史（设置页「清除统计数据」用）。 */
    @Transaction
    suspend fun clearAll() {
        val ids = allIds()
        ids.forEach { deleteById(it) }
    }

    @Query("SELECT matchId FROM match_records")
    suspend fun allIds(): List<String>
}
