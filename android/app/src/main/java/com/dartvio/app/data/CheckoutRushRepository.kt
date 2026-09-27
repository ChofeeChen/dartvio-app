package com.dartvio.app.data

import android.content.Context
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.data.local.entity.CheckoutRushAttemptEntity
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.DartSource
import com.dartvio.app.domain.practice.BustReason
import com.dartvio.app.domain.practice.CheckoutRushStatistics
import com.dartvio.app.domain.practice.RushAttemptRecord
import com.dartvio.app.domain.practice.RushDifficulty
import com.dartvio.app.domain.practice.RushResult
import com.dartvio.app.domain.model.BullMode
import com.dartvio.app.domain.model.OutMode

/**
 * 极速结镖（`checkout_rush_attempts`）的唯一出口。
 *
 * 与 [com.dartvio.app.data.VersusRepository] / [ImpactRepository] 同一条约定：
 * 页面不碰 DAO 与实体，只认领域对象（[RushAttemptRecord]），
 * 「一行 = 一道题的一次投掷」的粒度、`dartsCsv` 的编码格式、枚举的字符串落库口径
 * 全部封在这里 —— 换存储方式时只改这一个文件。
 */
class CheckoutRushRepository(context: Context) {

    private val dao = DartVioDatabase.get(context).checkoutRushDao()

    /** 写入一次尝试，返回其 id（「再试一次」靠它做 `retryOfAttemptId` 关联）。 */
    suspend fun append(record: RushAttemptRecord): Long = dao.insert(record.toEntity())

    /** 会话报告的数据源：按写入顺序（id 升序）读出 —— 连续成功 / 最常失败都依赖这个顺序。 */
    suspend fun sessionRecords(sessionId: String): List<RushAttemptRecord> =
        dao.listSession(sessionId).map { it.toDomain() }

    suspend fun statisticsOf(sessionId: String): CheckoutRushStatistics =
        CheckoutRushStatistics.of(sessionRecords(sessionId))

    /**
     * 同难度 · 无提示 · 未失效的**最快成功**投掷用时（个人最佳）。
     *
     * 条件写在 SQL 里而不是拉回内存过滤：历史攒到几百条之后全表扫描显然不划算。
     */
    suspend fun bestUnhintedThrowMs(difficulty: RushDifficulty): Long? =
        dao.bestUnhintedThrowMs(difficulty.name)

    /** 累计已完成的题目数（不含 SKIPPED / ABORTED）。 */
    suspend fun scoredAttemptCount(): Int = dao.scoredAttemptCount()
}

// =====================================================================================
// 领域 ⇄ 实体
// =====================================================================================

private fun RushAttemptRecord.toEntity(): CheckoutRushAttemptEntity = CheckoutRushAttemptEntity(
    id = id,
    sessionId = sessionId,
    targetScore = target,
    outMode = outMode.name,
    bullMode = bullMode.name,
    difficulty = difficulty.name,
    dartsCsv = darts.toCsv(),
    dartCount = darts.size,
    inputMode = inputMode.name,
    throwElapsedMs = throwElapsedMs,
    inputElapsedMs = inputElapsedMs,
    routeHintUsed = routeHintUsed,
    result = result.name,
    remainingAfter = remainingAfter,
    // 未爆分空串（列注释：空串 = 未爆分），不用 nullable 列，避免查询侧多判一次空。
    bustReason = bustReason?.code ?: "",
    retryOfAttemptId = retryOfAttemptId,
    timingInvalidated = timingInvalidated,
    createdAt = createdAt,
)

private fun CheckoutRushAttemptEntity.toDomain(): RushAttemptRecord = RushAttemptRecord(
    id = id,
    sessionId = sessionId,
    createdAt = createdAt,
    target = targetScore,
    // 旧值识别不了时回落到当前规则默认值（DOUBLE_OUT / 标准 Bull），而不是抛异常：
    // 这张表只由本模块写入，取不到说明将来放宽了选项，回落比崩溃更符合「历史不能丢」。
    outMode = runCatching { OutMode.valueOf(outMode) }.getOrDefault(OutMode.DOUBLE_OUT),
    bullMode = runCatching { BullMode.valueOf(bullMode) }.getOrDefault(BullMode.STANDARD_25_50),
    difficulty = runCatching { RushDifficulty.valueOf(difficulty) }.getOrDefault(RushDifficulty.MIXED),
    darts = parseDarts(dartsCsv),
    inputMode = DartSource.fromName(inputMode),
    throwElapsedMs = throwElapsedMs,
    inputElapsedMs = inputElapsedMs,
    routeHintUsed = routeHintUsed,
    result = runCatching { RushResult.valueOf(result) }.getOrDefault(RushResult.NOT_FINISHED),
    remainingAfter = remainingAfter,
    bustReason = BustReason.from(bustReason),
    retryOfAttemptId = retryOfAttemptId,
    timingInvalidated = timingInvalidated,
)

/**
 * 镖序 ⇄ `|` 分隔的 [Dart.label()] 记号（`T20|D20`）。
 *
 * **未投出的镖不补 MISS**：补了之后「实际用镖数」会被读成 3，
 * 「1 镖结镖 vs 3 镖结镖」这个分布统计就废了。真正的 MISS 是用户明确录入的，会被如实写下。
 */
private fun List<Dart>.toCsv(): String = joinToString("|") { it.label() }

private fun parseDarts(csv: String): List<Dart> =
    csv.split("|")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull(::parseDartLabel)

private fun parseDartLabel(label: String): Dart? = when {
    label.equals("MISS", ignoreCase = true) -> Dart.MISS
    label.equals("BULL", ignoreCase = true) -> Dart.INNER_BULL
    // "25" 由 label() 给 Outer Bull（number=25 ×1）专用，不可能是 D25 —— 那是 "BULL"。
    label == "25" -> Dart.OUTER_BULL
    label.startsWith("T", ignoreCase = true) -> label.drop(1).toIntOrNull()?.let(Dart::triple)
    label.startsWith("D", ignoreCase = true) -> label.drop(1).toIntOrNull()?.let(Dart::double)
    else -> label.toIntOrNull()?.let(Dart::single)
}
