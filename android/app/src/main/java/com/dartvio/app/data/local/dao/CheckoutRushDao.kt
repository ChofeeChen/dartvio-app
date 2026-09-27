package com.dartvio.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dartvio.app.data.local.entity.CheckoutRushAttemptEntity
import kotlinx.coroutines.flow.Flow

/**
 * 极速结镖（`checkout_rush_attempts`）的读写接口。
 *
 * **只接触这一张表**：Service 层不提供任何写 `match_records` / `versus_*` 的路径，
 * 于是「这条训练数据不会污染正式统计」是由接口的形状保证的，而不是靠调用者记得传对参数。
 */
@Dao
interface CheckoutRushDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(attempt: CheckoutRushAttemptEntity): Long

    /** 会话报告的数据源：按写入顺序（id 升序）读取 —— 连续成功/最常失败这两个口径都依赖顺序。 */
    @Query(
        "SELECT * FROM checkout_rush_attempts WHERE sessionId = :sessionId ORDER BY id ASC"
    )
    suspend fun listSession(sessionId: String): List<CheckoutRushAttemptEntity>

    @Query(
        "SELECT * FROM checkout_rush_attempts WHERE sessionId = :sessionId ORDER BY id ASC"
    )
    fun observeSession(sessionId: String): Flow<List<CheckoutRushAttemptEntity>>

    /**
     * 个人最佳：同难度 · 无提示 · 未失效的**最快成功**投掷用时。
     *
     * 条件写在 SQL 里而不是拉回内存再过滤：历史里有几百条之后，全表扫描显然不划算。
     *
     * @return 没有可比较的成绩时返回 `null`。
     */
    @Query(
        "SELECT MIN(throwElapsedMs) FROM checkout_rush_attempts " +
            "WHERE difficulty = :difficulty AND result = 'CHECKOUT' " +
            "AND routeHintUsed = 0 AND timingInvalidated = 0"
    )
    suspend fun bestUnhintedThrowMs(difficulty: String): Long?

    /** 累计已完成的题目数（不含 SKIPPED / ABORTED），用于会话页与统计页的自我介绍。 */
    @Query(
        "SELECT COUNT(*) FROM checkout_rush_attempts " +
            "WHERE result IN ('CHECKOUT', 'BUST', 'NOT_FINISHED')"
    )
    suspend fun scoredAttemptCount(): Int

    /**
     * 累计成功结镖数（数据页「练习」口径用）。
     *
     * 与 [scoredAttemptCount] 成对出现：成功率 = 成功 ÷ 已判题，
     * 两个分母必须来自同一张表、同一套「什么算一题」的判据，不能一边去重一边不去重。
     */
    @Query("SELECT COUNT(*) FROM checkout_rush_attempts WHERE result = 'CHECKOUT'")
    suspend fun checkoutCount(): Int

    @Query("DELETE FROM checkout_rush_attempts WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)
}
