package com.dartvio.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dartvio.app.data.local.entity.DartHitEntity

/** 出框统计的投影：`outBand` × `outLevel` 各有多少镖（M11 落点诊断用的计数）。 */
data class MissBandCount(
    val outBand: Int,
    val outLevel: Int,
    val count: Int
)

/**
 * 逐镖落点的读写接口。
 *
 * v7 新增的 4 个查询（[listByProfileIntent] / [listBySession] / [deleteLast] / [countMissByBand]）
 * 全部**按 `sessionId` 或 `(intentNumber, intentMultiplier)` 收窄**，不会把对局镖混进诊断统计：
 * 对局镖的 `sessionId = ''` 且意图为 `0`，天然落在这些谓词之外。
 */
@Dao
interface DartHitDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(hits: List<DartHitEntity>)

    /** 某位玩家的全部落点（最近在前），热力图的数据源。 */
    @Query("SELECT * FROM dart_hits WHERE profileId = :profileId ORDER BY hitAt DESC")
    suspend fun listByProfile(profileId: String): List<DartHitEntity>

    @Query("SELECT COUNT(*) FROM dart_hits")
    suspend fun count(): Int

    @Query("DELETE FROM dart_hits")
    suspend fun clearAll()

    /**
     * 某人 + 同一意图 + 同一窗口档位的历史点（最近在前）。
     *
     * `windowSpanMm` 必须参与收窄：不同档位的毫米数不能合并算散布（窗口越小，表现越好是必然的）。
     */
    @Query(
        "SELECT * FROM dart_hits WHERE profileId = :profileId " +
            "AND intentNumber = :intentNumber AND intentMultiplier = :intentMultiplier " +
            "AND windowSpanMm = :windowSpanMm ORDER BY hitAt DESC LIMIT :limit"
    )
    suspend fun listByProfileIntent(
        profileId: String,
        intentNumber: Int,
        intentMultiplier: Int,
        windowSpanMm: Float,
        limit: Int
    ): List<DartHitEntity>

    /** 一次练习会话的全部点，按写入顺序（id 升序）——撤销「最后一镖」要按这个顺序。 */
    @Query("SELECT * FROM dart_hits WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun listBySession(sessionId: String): List<DartHitEntity>

    /** 撤销：删掉该会话的最后一镖（按 id 倒序取一条）。 */
    @Query(
        "DELETE FROM dart_hits WHERE id IN (" +
            "SELECT id FROM dart_hits WHERE sessionId = :sessionId ORDER BY id DESC LIMIT 1)"
    )
    suspend fun deleteLast(sessionId: String)

    /** 按来源计数（如落点诊断累计录了多少镖）。 */
    @Query("SELECT COUNT(*) FROM dart_hits WHERE source = :source")
    suspend fun countBySource(source: String): Int

    /**
     * 练习落点累计镖数（数据页「练习」口径用）。
     *
     * 判据用 `sessionId != ''`：对局镖不带会话号（见类注释），
     * 于是「练习练了多少镖」与「对局投了多少镖」在 SQL 层面就分开了，
     * 不需要调用方记得传对参数。
     */
    @Query("SELECT COUNT(*) FROM dart_hits WHERE sessionId != ''")
    suspend fun countPracticeHits(): Int

    /** 某来源最近若干镖（最近在前），给入口卡展示「最近 R95」用。 */
    @Query("SELECT * FROM dart_hits WHERE source = :source ORDER BY hitAt DESC LIMIT :limit")
    suspend fun listRecentBySource(source: String, limit: Int): List<DartHitEntity>

    /**
     * 一次会话里出框镖的分布。
     *
     * **计数必须包含出框点**：它们不进 σ / R95 / KDE，但「往哪边跑」正是要诊断的第一件事。
     */
    @Query(
        "SELECT outBand, outLevel, COUNT(*) AS count FROM dart_hits " +
            "WHERE sessionId = :sessionId AND outBand != 0 GROUP BY outBand, outLevel"
    )
    suspend fun countMissByBand(sessionId: String): List<MissBandCount>

    /**
     * 某人在**同一窗口档位**下的全部落点诊断镖（跨目标，最近在前）。
     *
     * 报告的「跨目标合并」视图要用它：合并的前提是同一档位（不同档位的毫米数不可比），
     * 而每个点的意图由它自己的 `intentNumber / intentMultiplier` 决定（在领域层再算误差帧）。
     * `sessionId != ''` 把对局镖挡在外面。
     */
    @Query(
        "SELECT * FROM dart_hits WHERE profileId = :profileId AND windowSpanMm = :windowSpanMm " +
            "AND sessionId != '' ORDER BY hitAt DESC LIMIT :limit"
    )
    suspend fun listByProfileSpan(
        profileId: String,
        windowSpanMm: Float,
        limit: Int
    ): List<DartHitEntity>

    /** 某意图曾用过的窗口档位（去重），用于诊断页说明「这些成绩来自几档窗口」。 */
    @Query(
        "SELECT DISTINCT windowSpanMm FROM dart_hits WHERE profileId = :profileId " +
            "AND intentNumber = :intentNumber AND intentMultiplier = :intentMultiplier " +
            "ORDER BY windowSpanMm ASC"
    )
    suspend fun listWindowSpans(
        profileId: String,
        intentNumber: Int,
        intentMultiplier: Int
    ): List<Float>

    /**
     * 某人**全部**落点诊断镖（跨目标、跨档位，最近在前）——V1.4 投掷指纹的数据源（§4.7）。
     *
     * 指纹回答的是「我这类误差稳不稳」，因此**不分目标**；但档位分组、`outBand` 过滤都在领域层做
     * （本查询只负责把样本取回来）。`sessionId != ''` 把对局镖挡在外面。
     */
    @Query(
        "SELECT * FROM dart_hits WHERE profileId = :profileId AND sessionId != '' " +
            "ORDER BY hitAt DESC LIMIT :limit"
    )
    suspend fun listAllByProfile(profileId: String, limit: Int): List<DartHitEntity>

    /**
     * 某人**设过本轮目标**的镖（最近在前）——V1.4 干预对照表的数据源（§4.8）。
     *
     * 谓词用 `prescriptionMetric != ''` 而不是 `prescriptionTarget > 0`：
     * `0.0` 是合法目标（§3.2.1），存在性只能看口径字段。
     */
    @Query(
        "SELECT * FROM dart_hits WHERE profileId = :profileId AND sessionId != '' " +
            "AND prescriptionMetric != '' ORDER BY hitAt DESC LIMIT :limit"
    )
    suspend fun listSessionsWithPrescription(
        profileId: String,
        limit: Int
    ): List<DartHitEntity>
}
