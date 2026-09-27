package com.dartvio.app.domain.impact

/**
 * 本轮量化目标的口径（V1.4 §4.8）。
 *
 * 只有这 5 个 —— 「综合评分」「稳定性分数」这类合成指标一律不做：
 * 它们既不能验证、也不能指导动作，只会让用户觉得「有个分数在变」。
 */
enum class PrescriptionMetric(
    val label: String,
    /** 显示单位。 */
    val unit: String,
    /** 是否**越小越好**。 */
    val lowerIsBetter: Boolean
) {
    BIAS_ABS("平均偏差", "mm", lowerIsBetter = true),
    R95("R95 散布", "mm", lowerIsBetter = true),
    RMSE("综合偏离", "mm", lowerIsBetter = true),
    HIT_RATE("命中率", "%", lowerIsBetter = false),
    OUT_RATE("出框率", "%", lowerIsBetter = true);

    companion object {
        /** 由落库字符串还原；空串 / 未知 → `null`（= 未设）。 */
        fun fromKey(key: String): PrescriptionMetric? =
            entries.firstOrNull { it.name == key }
    }
}

/** 本轮目标：`metric` 达到 `target`（`lowerIsBetter` 时是「≤」）。 */
data class Prescription(
    val metric: PrescriptionMetric,
    val target: Double,
    /** 建议来源（上一轮 `sessionId`）；用户自设时为空串。 */
    val fromSessionId: String = ""
) {
    /** 落库形式（`(prescriptionMetric, prescriptionTarget)`）。 */
    fun targetText(): String =
        if (metric.unit == "%") "${(target * 100).toInt()}%" else "${oneDecimal(target)} mm"
}

/**
 * 判定所需的**本轮实测**。
 *
 * 这里不用 [ImpactStats]：命中率 / 出框率的分母是**全部录入**（含出框），
 * 而 [ImpactStats] 只活在「窗内点」的口径里 —— 把两者强行合并会让出框镖既算又不算。
 */
data class SessionMetrics(
    val sessionId: String,
    val hitAt: Long,
    val n: Int,
    val bias: Double,
    val r95: Double,
    val rmse: Double,
    val hitRate: Double,
    val outRate: Double
)

/** 达成判定结果。`text` 一律现算，**不落库**。 */
enum class Verdict { NOT_SET, ACHIEVED, MISSED, INSUFFICIENT }

data class VerdictResult(
    val verdict: Verdict,
    /** 本轮实测值；`NOT_SET` 时为 `null`。 */
    val actual: Double?,
    /** 本轮目标阈值；`NOT_SET` 时为 `null`。 */
    val target: Double?,
    /** 成品文案（现算，**不落库**）。 */
    val note: String,
    /** 跨界提醒：判定口径是偏移类、但 R95 反而变大时的追问（与 [note] **并存**，不覆盖）。 */
    val crossWarning: String? = null,
    /** 连续达标达到退出条件时的提示。 */
    val exitHint: String? = null
)

/** 一次 `session` 的干预对照行。 */
data class SessionSummary(
    val sessionId: String,
    val hitAt: Long,
    /** 本轮打算刻意改动的自由文本；`''` = 未标注。 */
    val note: String,
    val n: Int,
    val bias: Double,
    val r95: Double,
    val hitRate: Double,
    /** 是否达成该轮目标；未设目标为 `null`。 */
    val achieved: Boolean?
)

/** 某个干预标签的**中性**对照（只描述相关，不作因果结论）。 */
data class InterventionComparison(
    val note: String,
    val rounds: Int,
    val darts: Int,
    /** `null` = 有效轮次 / 镖数不够，只列数据不给对比句。 */
    val text: String?
)

data class InterventionReport(
    val rows: List<SessionSummary>,
    val comparisons: List<InterventionComparison>,
    val footnote: String
)

/**
 * 处方 / 达成判定 / 干预对照（V1.4 §4.8 + A-IMP-27、A-IMP-31）。
 *
 * 这一层只做**算术与措辞**，不做因果推断 —— 干预日志的措辞门是硬约束：
 * 同一句话在 `因为 / 证明 / 因此 / 诊断出` 上翻车，整张卡的信任度就没了。
 */
object ImpactPrescription {

    /** 判定门槛：低于此值只出「样本不足」，不给达成与否。 */
    const val MIN_JUDGE_N = ImpactCalculator.MIN_BIAS_N

    /** 上轮无有效数据时的默认偏移目标（mm）。 */
    const val DEFAULT_BIAS_TARGET_MM = 3.0

    /** 目标下限（mm）：比 1 个环宽还紧的目标只会制造挫败。 */
    const val MIN_TARGET_MM = 1.5

    /** 偏移类目标相对上轮实测的收紧系数。 */
    const val TIGHTEN_RATIO = 0.5

    /** 比例类目标相对上轮的收紧系数（命中率 +10%、出框率 −10%）。 */
    const val RATE_TIGHTEN_RATIO = 1.1

    /** R95 相对上一轮放大超过这个比例 ⇒ 跨界提醒。 */
    const val CROSS_WARN_RATIO = 1.25

    /** 连续达标多少轮 ⇒ 提示换下一档目标。 */
    const val EXIT_ACHIEVED_STREAK = 3

    /** 干预对照所需的最少轮次。 */
    const val MIN_INTERVENTION_ROUNDS = 3

    /** 干预对照所需的最少镖数（与散布结论同门槛）。 */
    const val MIN_INTERVENTION_DARTS = ImpactCalculator.MIN_FULL_N

    /** 干预标签字数上限（超出由 UI 截断，这里只作常量出口）。 */
    const val MAX_NOTE_LENGTH = 24

    /** 取本轮实测值。 */
    fun actualOf(m: SessionMetrics, metric: PrescriptionMetric): Double = when (metric) {
        PrescriptionMetric.BIAS_ABS -> m.bias
        PrescriptionMetric.R95 -> m.r95
        PrescriptionMetric.RMSE -> m.rmse
        PrescriptionMetric.HIT_RATE -> m.hitRate
        PrescriptionMetric.OUT_RATE -> m.outRate
    }

    /**
     * 达成判定（**现算不落库**）。
     *
     * @param p `null` 或用户没设目标 ⇒ [Verdict.NOT_SET]。
     * @param prevR95 上一轮 R95（用于跨界提醒）；`null` = 没有上一轮。
     * @param achievedStreak 本**之前**已连续达标的轮数（不含本轮）。
     */
    fun judge(
        m: SessionMetrics?,
        p: Prescription?,
        prevR95: Double? = null,
        achievedStreak: Int = 0
    ): VerdictResult {
        if (p == null) {
            return VerdictResult(
                verdict = Verdict.NOT_SET,
                actual = null,
                target = null,
                note = "本轮没设量化目标；下一轮开局可以选一个，不然结束只剩感觉。"
            )
        }
        if (m == null || m.n < MIN_JUDGE_N) {
            return VerdictResult(
                verdict = Verdict.INSUFFICIENT,
                actual = null,
                target = p.target,
                note = "本轮有效 ${m?.n ?: 0} 镖，还差 ${
                    (MIN_JUDGE_N - (m?.n ?: 0)).coerceAtLeast(0)
                } 镖 —— 先不判定达成与否。"
            )
        }
        val actual = actualOf(m, p.metric)
        val achieved = if (p.metric.lowerIsBetter) actual <= p.target else actual >= p.target
        val arrow = if (p.metric.lowerIsBetter) "≤" else "≥"
        val actualText = if (p.metric.unit == "%") "${(actual * 100).toInt()}%" else "${oneDecimal(actual)} mm"

        // 达成与「散布同时变大」必须**同时出**（A-IMP-25）：达成只说明这个指标动了，
        // 不说明是变好 —— 很可能是把误差从一个方向挪到了另一个方向。
        val crossWarning = if (
            achieved && p.metric.lowerIsBetter && p.metric != PrescriptionMetric.R95 &&
            prevR95 != null && prevR95 > 0.0 && m.r95 > prevR95 * CROSS_WARN_RATIO
        ) {
            "注意：达成的同时散布变大了 —— R95 ${oneDecimal(prevR95)} → ${oneDecimal(m.r95)} mm" +
                "（+${((m.r95 / prevR95 - 1.0) * 100).toInt()}%）。这个改动可能只是**把误差换了方向**，" +
                "而不是真的收紧了。"
        } else {
            null
        }

        val exitHint = if (achieved && achievedStreak + 1 >= EXIT_ACHIEVED_STREAK) {
            "你已经连续 ${achievedStreak + 1} 轮达标，这个目标不再有信息量了 —— 把 ${p.metric.label} 收紧一档。"
        } else {
            null
        }

        return VerdictResult(
            verdict = if (achieved) Verdict.ACHIEVED else Verdict.MISSED,
            actual = actual,
            target = p.target,
            note = "本轮目标：${p.metric.label} $arrow ${p.targetText()}；实测 $actualText。" +
                if (achieved) "达成。" else "未达标 —— 下一轮别加量，先把这一个指标吃掉。",
            crossWarning = crossWarning,
            exitHint = exitHint
        )
    }

    /**
     * 系统建议值（V1.4 §4.8 ①）。
     *
     * - 上轮无有效数据 ⇒ `BIAS_ABS ≤ [DEFAULT_BIAS_TARGET_MM] mm`；
     * - 有数据 ⇒ 偏移类取 `max([MIN_TARGET_MM], 上轮实测 × [TIGHTEN_RATIO])`（**永不低于 1.5 mm**）。
     *
     * 建议**一律从 BIAS_ABS 起步**：它是唯一「练一次就能看见变化」的指标，
     * 一上来就让新手盯 R95 会得到一个几乎不动的数字，然后放弃。
     */
    fun suggestPrescription(prev: SessionMetrics?): Prescription =
        suggestPrescription(prev, PrescriptionMetric.BIAS_ABS)

    /**
     * 指定口径的建议值（UI 依 `ImpactGoal` 选择口径：偏移 → [PrescriptionMetric.BIAS_ABS]、
     * 散布 → [PrescriptionMetric.R95]、都看 → [PrescriptionMetric.RMSE]）。
     *
     * 比例类（命中率 / 出框率）走另一套映射：命中率 ×[RATE_TIGHTEN_RATIO]（更高），
     * 出框率 ÷[RATE_TIGHTEN_RATIO]（更低），且比例类**不做 1.5 mm 下限**（单位根本不是 mm）。
     */
    fun suggestPrescription(prev: SessionMetrics?, metric: PrescriptionMetric): Prescription {
        if (prev == null || prev.n < MIN_JUDGE_N) {
            return Prescription(PrescriptionMetric.BIAS_ABS, DEFAULT_BIAS_TARGET_MM)
        }
        val target = when (metric) {
            PrescriptionMetric.BIAS_ABS ->
                maxOf(MIN_TARGET_MM, prev.bias * TIGHTEN_RATIO)
            PrescriptionMetric.R95 ->
                maxOf(MIN_TARGET_MM, prev.r95 * TIGHTEN_RATIO)
            PrescriptionMetric.RMSE ->
                maxOf(MIN_TARGET_MM, prev.rmse * TIGHTEN_RATIO)
            PrescriptionMetric.HIT_RATE ->
                (prev.hitRate * RATE_TIGHTEN_RATIO).coerceAtMost(1.0)
            PrescriptionMetric.OUT_RATE ->
                (prev.outRate / RATE_TIGHTEN_RATIO).coerceAtLeast(0.0)
        }
        return Prescription(metric = metric, target = target, fromSessionId = prev.sessionId)
    }

    /**
     * 干预日志对照（V1.4 §4.8 ⑥ / A-IMP-31）。
     *
     * **中性措辞是硬约束**：只在有效轮次（≥ [MIN_INTERVENTION_ROUNDS] 轮且 ≥ [MIN_INTERVENTION_DARTS] 镖）
     * 时给一句「描述性差异」，措辞里不出现因果词；不满足时 [InterventionComparison.text] 为 `null`，
     * UI 只列数据 —— 「样本不够时宁可不说话」比「给一句似是而非的结论」重要。
     */
    fun interventionCompare(
        rows: List<SessionSummary>,
        minRounds: Int = MIN_INTERVENTION_ROUNDS
    ): InterventionReport {
        val sorted = rows.sortedBy { it.hitAt }
        val notes = LinkedHashMap<String, MutableList<SessionSummary>>()
        sorted.forEach { row ->
            if (row.note.isNotBlank()) notes.getOrPut(row.note.trim()) { mutableListOf() }.add(row)
        }
        val baseline = sorted.filter { it.note.isBlank() }

        val comparisons = notes.map { (note, list) ->
            val darts = list.sumOf { it.n }
            val enough = list.size >= minRounds && darts >= MIN_INTERVENTION_DARTS && baseline.isNotEmpty()
            InterventionComparison(
                note = note,
                rounds = list.size,
                darts = darts,
                text = if (enough) {
                    val labeled = oneDecimal(list.map { it.r95 }.average())
                    val unlabeled = oneDecimal(baseline.map { it.r95 }.average())
                    "「$note」这类轮次 ${list.size} 轮 / $darts 镖：R95 均值 $labeled mm；" +
                        "未标注轮 R95 均值 $unlabeled mm。"
                } else {
                    null
                }
            )
        }

        return InterventionReport(
            rows = sorted,
            comparisons = comparisons,
            footnote = "这一栏只描述「你标注过的轮次」与其它轮次的数据差异，**不作因果结论**：" +
                "同一时刻你可能同时改了站位、节奏和器材。至少要重复 $minRounds 轮、$MIN_INTERVENTION_DARTS 镖以上，才值得当成一条线索。"
        )
    }
}

/** 一位小数（`Prescription.targetText()` 与对象内部文案共用，故放文件级）。 */
private fun oneDecimal(value: Double): String = ((value * 10).toInt() / 10.0).toString()
