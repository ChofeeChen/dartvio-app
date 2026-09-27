package com.dartvio.app.domain.practice

import com.dartvio.app.domain.model.BullMode
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.DartSource
import com.dartvio.app.domain.model.OutMode

/**
 * 极速结镖的一次「尝试」（= 一道题的一次投掷 + 录入）以及会话级统计口径。
 *
 * ## 数据红线（PRD §十.1-5）
 *
 * - 极速结镖始终是 **M9 B 级自报训练数据**：不写 `match_records`，
 *   不进正式对局历史 / 胜率 / PPR / 正式 Checkout 率 / 成就排名 / 段位 / 排行榜。
 *   红线靠**独立表**保证，而不是靠统计侧补谓词 —— 漏一个谓词就等于污染。
 * - 有提示与无提示**分开聚合**：[CheckoutRushStatistics.of] 同时给出两组成功率，永不合并口径。
 * - 始终保持 `SKIPPED` / `ABORTED` 不计入成功率分母（[RushResult.countsTowardsSuccessRate]）。
 */
data class RushAttemptRecord(
    val id: Long = 0,
    /** 会话标识：一次 10 题挑战或一次自由练习共用一个 id。 */
    val sessionId: String,
    val createdAt: Long,
    val target: Int,
    val outMode: OutMode = OutMode.DOUBLE_OUT,
    val bullMode: BullMode = BullMode.STANDARD_25_50,
    val difficulty: RushDifficulty = RushDifficulty.MIXED,
    /** 实际投出的有序镖序；未投出的镖不补 MISS。 */
    val darts: List<Dart> = emptyList(),
    /**
     * 录入方式：MVP 只允许 [DartSource.DART_BY_DART]（逐镖键盘）与 [DartSource.BOARD_TAP]（靶面点选）。
     *
     * 复用 M3 既有 [DartSource] 枚举而不是新造一套 `input_source` 字符串：
     * 名字不一致会让「同一个来源」在两张表里出现两个拼写。
     */
    val inputMode: DartSource = DartSource.DART_BY_DART,
    /** 主成绩计时：**投掷**用时，揭示目标分开始、点击「完成投掷」冻结。 */
    val throwElapsedMs: Long = 0,
    /** 交互分析计时：开始录入到确认结果，**永不参与训练成绩排名**。 */
    val inputElapsedMs: Long = 0,
    /** 本题是否看过路线提示：一旦为 true 不可恢复（PRD §七.3）。 */
    val routeHintUsed: Boolean = false,
    val result: RushResult = RushResult.NOT_FINISHED,
    val remainingAfter: Int = 0,
    val bustReason: BustReason? = null,
    /** 「再试一次」指向的原记录 id；非重试为 0。 */
    val retryOfAttemptId: Long = 0,
    /**
     * 本题是否因「切后台」等原因被标记不可刷新个人最佳（PRD §六.5）。
     *
     * 不改写本次结果 —— 题还是要做完、成绩照记；这里只影响它能否参与个人最佳比较。
     */
    val timingInvalidated: Boolean = false,
) {
    val dartsUsed: Int get() = darts.size
    val isSuccess: Boolean get() = result == RushResult.CHECKOUT
}

/** 会话级统计。所有字段都是「同一次会话、同一批记录」的派生值，纯函数算出来，不落库。 */
data class CheckoutRushStatistics(
    /** 计入分母的题目数（不含 SKIPPED / ABORTED）。 */
    val scoredAttempts: Int,
    val successCount: Int,
    /** 成功率（%），含使用过提示的题目。 */
    val successRatePct: Int,
    /** 无提示成功数 / 无提示题目数 —— 与 [successRatePct] 分开聚合，不合并。 */
    val unhintedAttempts: Int,
    val unhintedSuccessCount: Int,
    val unhintedSuccessRatePct: Int,
    /** 看过提示的题目数 / 成功率，只作对照。 */
    val hintedAttempts: Int,
    val hintedSuccessRatePct: Int,
    /** 投掷用时的平均与中位（毫秒）：只统计完成投入的题目（排除 SKIPPED / ABORTED）。 */
    val avgThrowMs: Long?,
    val medianThrowMs: Long?,
    /** 最快的一次**无提示成功**投掷用时。 */
    val fastestUnhintedSuccessMs: Long?,
    /** 1 / 2 / 3 镖结镖分布（只数成功题）。 */
    val checkoutDartDistribution: Map<Int, Int>,
    /** 爆分次数与原因分布。 */
    val bustCount: Int,
    val bustReasons: Map<BustReason, Int>,
    /** 连续成功的最高纪录（按顺序扫描，遇到非成功即断）。 */
    val longestSuccessStreak: Int,
    /** 最常失败的目标分：失败次数最多的目标（并列取先出现者），没有失败时为 null。 */
    val mostFailedTarget: Int?,
    /** 录入用时均值，仅作交互分析 —— 不参与任何训练成绩。 */
    val avgInputMs: Long?,
    /** 键盘 / 靶面点选的录入用时均值，同样只作交互分析。 */
    val avgInputMsBySource: Map<DartSource, Long>,
    /** 跳过次数：单独计数，不进成功率分母。 */
    val skippedCount: Int,
    /** 中断次数：不形成成绩。 */
    val abortedCount: Int,
) {
    companion object {
        /** 空会话：所有数值退化为 0 / null，UI 直接判 0 即可，不需要空分支。 */
        fun empty(): CheckoutRushStatistics = CheckoutRushStatistics(
            scoredAttempts = 0,
            successCount = 0,
            successRatePct = 0,
            unhintedAttempts = 0,
            unhintedSuccessCount = 0,
            unhintedSuccessRatePct = 0,
            hintedAttempts = 0,
            hintedSuccessRatePct = 0,
            avgThrowMs = null,
            medianThrowMs = null,
            fastestUnhintedSuccessMs = null,
            checkoutDartDistribution = emptyMap(),
            bustCount = 0,
            bustReasons = emptyMap(),
            longestSuccessStreak = 0,
            mostFailedTarget = null,
            avgInputMs = null,
            avgInputMsBySource = emptyMap(),
            skippedCount = 0,
            abortedCount = 0,
        )

        /**
         * 按会话顺序统计。
         *
         * **顺序即会话记录顺序**：连续成功、最常失败目标这两个口径都依赖它，
         * 所以调用方必须按 `createdAt` 升序传入（仓库层已排序）。
         */
        fun of(records: List<RushAttemptRecord>): CheckoutRushStatistics {
            if (records.isEmpty()) return empty()

            val timed = records.filter { it.result != RushResult.SKIPPED && it.result != RushResult.ABORTED }
            val scored = timed.filter { it.result.countsTowardsSuccessRate }
            val successes = scored.count { it.isSuccess }

            val unhinted = scored.filter { !it.routeHintUsed }
            val hinted = scored.filter { it.routeHintUsed }

            // 投掷用时：尚未分出「最高 / 最低」之前先排序 —— 中位与最快都从同一份有序数据取。
            val throwTimes = timed.map { it.throwElapsedMs }.sorted()
            val unhintedSuccessTimes = unhinted.filter { it.isSuccess }.map { it.throwElapsedMs }

            val distribution = scored
                .filter { it.isSuccess }
                .groupingBy { it.dartsUsed.coerceIn(1, CheckoutRushRules.MAX_DARTS) }
                .eachCount()

            val bustRecords = scored.filter { it.result == RushResult.BUST }

            // 最常失败：爆分 + 三镖未完成都算失败；并列时取**先出现**的目标。
            val failureCounts = mutableMapOf<Int, Int>()
            val failureOrder = mutableListOf<Int>()
            scored.filter { !it.isSuccess }.forEach { record ->
                if (record.target !in failureCounts) failureOrder.add(record.target)
                failureCounts[record.target] = (failureCounts[record.target] ?: 0) + 1
            }
            val top = failureCounts.entries.maxWithOrNull(
                // 并列时 `order` 里靠前的胜出，因此取 index 的相反数。
                compareBy({ it.value }, { -failureOrder.indexOf(it.key) })
            )

            val inputTimes = timed.map { it.inputElapsedMs }
            val bySource = timed.groupBy { it.inputMode }
                .mapValues { (_, rows) -> rows.map { it.inputElapsedMs }.average().toLong() }

            return CheckoutRushStatistics(
                scoredAttempts = scored.size,
                successCount = successes,
                successRatePct = pct(successes, scored.size),
                unhintedAttempts = unhinted.size,
                unhintedSuccessCount = unhinted.count { it.isSuccess },
                unhintedSuccessRatePct = pct(unhinted.count { it.isSuccess }, unhinted.size),
                hintedAttempts = hinted.size,
                hintedSuccessRatePct = pct(hinted.count { it.isSuccess }, hinted.size),
                avgThrowMs = if (throwTimes.isEmpty()) null else throwTimes.average().toLong(),
                medianThrowMs = throwTimes.medianOrNull(),
                fastestUnhintedSuccessMs = unhintedSuccessTimes.minOrNull(),
                checkoutDartDistribution = distribution,
                bustCount = bustRecords.size,
                bustReasons = bustRecords.mapNotNull { it.bustReason }
                    .groupingBy { it }.eachCount(),
                longestSuccessStreak = longestStreak(records.map { it.result }),
                mostFailedTarget = top?.key,
                avgInputMs = if (inputTimes.isEmpty()) null else inputTimes.average().toLong(),
                avgInputMsBySource = bySource,
                skippedCount = records.count { it.result == RushResult.SKIPPED },
                abortedCount = records.count { it.result == RushResult.ABORTED },
            )
        }

        private fun pct(numerator: Int, denominator: Int): Int =
            if (denominator == 0) 0 else numerator * 100 / denominator

        /** 有序列表的中位数：偶数取中间两个的平均，不用 (n-1)/2，否则偶数个时会偏向左侧。 */
        private fun List<Long>.medianOrNull(): Long? {
            if (isEmpty()) return null
            val mid = size / 2
            return if (size % 2 == 1) this[mid] else (this[mid - 1] + this[mid]) / 2
        }

        /** 最长连续成功：结算类结果之外（跳过 / 中断）同样打断连击。 */
        private fun longestStreak(results: List<RushResult>): Int {
            var best = 0
            var current = 0
            results.forEach { result ->
                if (result == RushResult.CHECKOUT) {
                    current++
                    if (current > best) best = current
                } else {
                    current = 0
                }
            }
            return best
        }
    }
}

/**
 * 个人最佳：**同难度 · 无提示 · 未失效**的最快结镖投掷用时。
 *
 * 三个条件缺一不可：
 * - 同难度：跨难度比快慢没有意义（题库本来就不是一个难度）；
 * - 无提示：看过路线的成绩是辅助成绩，不得刷新无提示最佳（PRD §七.4）；
 * - 未失效：切过后台的那一题计时不可信（PRD §六.5）。
 *
 * `null` = 还没有可比较的成绩。
 */
fun bestUnhintedThrowMs(
    history: List<RushAttemptRecord>,
    difficulty: RushDifficulty
): Long? = history.asSequence()
    .filter { it.difficulty == difficulty }
    .filter { it.isSuccess && !it.routeHintUsed && !it.timingInvalidated }
    .minByOrNull { it.throwElapsedMs }
    ?.throwElapsedMs
