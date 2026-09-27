package com.dartvio.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 极速结镖的一次「尝试」（DB **v10** 新增）。
 *
 * ## 为什么是独立表
 *
 * 与 v9 的 `versus_*` 同一个理由（写在同一份注释里，免得两处口径各自漂移）：
 * 极速结镖的数据红线是「**不写 `match_records`**、不进正式对局历史 / 胜率 / PPR /
 * 正式 Checkout 率 / 成就排名 / 段位 / 排行榜」。靠判别列实现这条红线，意味着
 * 每个既有统计查询都要补谓词 —— 漏一处就是一次污染；拆表之后**由表结构保证**。
 *
 * ## 粒度
 *
 * 一行 = **一道题的一次投掷**（最多 3 镖），而不是一场 / 一轮：
 * 「最后一镖完成入手后立即冻结（-50ms）」这个计时口径就落在这一层，
 * 一场一行的话拿不到单题用时；一镖一行则把「目标分 / 用时 / 是否用过提示」
 * 这些**整题才有意义**的信息重复三遍。
 *
 * 索引：
 * - `sessionId`：会话报告按会话读取；
 * - `difficulty + routeHintUsed + result`：个人最佳查询（同难度 · 无提示 · 最快成功）。
 */
@Entity(
    tableName = "checkout_rush_attempts",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["difficulty", "routeHintUsed", "result"]),
    ]
)
data class CheckoutRushAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 所属会话（一次 10 题挑战 / 一次自由练习）。 */
    val sessionId: String,
    /** 本题目标分（2..170）。 */
    val targetScore: Int,
    /** 结束方式枚举名（`OutMode.name`），落库为字符串，便于将来放开选项后兼容。 */
    val outMode: String,
    /** 牛眼规则枚举名（`BullMode.name`）。 */
    val bullMode: String,
    /** 难度枚举名（`RushDifficulty.name`）。 */
    val difficulty: String,
    /** 本题实际投出的镖，`|` 分隔的 `Dart.label()` 记号（`T20|D20`）；未投出的镖**不补 MISS**。 */
    val dartsCsv: String,
    /** 实际镖数：单独存一列，统计查询不必解析 CSV。 */
    val dartCount: Int,
    /** 录入方式（`DartSource.name`）：MVP 只有 `DART_BY_DART` / `BOARD_TAP`。 */
    val inputMode: String,
    /** **主成绩计时**：投掷用时（ms），揭示目标分开始 → 点击「完成投掷」冻结。 */
    val throwElapsedMs: Long,
    /** 交互分析计时（ms），**永不参与训练成绩排名**。 */
    val inputElapsedMs: Long,
    /** 本题是否看过路线提示：一旦为 true 不可恢复。 */
    val routeHintUsed: Boolean,
    /** 结果（`RushResult.name`）。 */
    val result: String,
    /** 结算后剩余分；爆分时为回滚后的目标分。 */
    val remainingAfter: Int,
    /** 爆分原因（`BustReason.code`），未爆分为空串。 */
    val bustReason: String,
    /** 「再试一次」指向的原记录 id；非重试为 0。 */
    val retryOfAttemptId: Long,
    /** 本题计时是否因切后台等原因失效：不进个人最佳比较（PRD §六.5）。 */
    val timingInvalidated: Boolean,
    val createdAt: Long,
)
