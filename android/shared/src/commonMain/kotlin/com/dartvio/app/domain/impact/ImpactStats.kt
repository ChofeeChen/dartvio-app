package com.dartvio.app.domain.impact

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 一组同一意图落点的统计量。
 *
 * `sigma` / `r95` 是**上限估计**：观测噪声 = 真实散布 + 手点误差（加性），
 * 均值类结论（[bias]）对零均值噪声稳健，散布类结论会被系统性高估 —— UI 必须带这个脚注。
 */
data class ImpactStats(
    val n: Int,
    val biasRad: Double,
    val biasTan: Double,
    val sigmaRad: Double,
    val sigmaTan: Double,
    /** 以**自身质心**为圆心、覆盖 95% 点的半径（mm）。 */
    val r95: Double,
    /** `sqrt(mean(‖e‖²))` = 综合偏离（趋势图主轴）。 */
    val rmse: Double
) {
    /** 系统偏移大小（mm）。 */
    val bias: Double get() = hypot(biasRad, biasTan)
}

/** 统计与诊断规则。阈值以**环宽（8 mm）**为单位 —— 这是玩家与教练都能立刻理解的标尺。 */
object ImpactCalculator {

    /** 环宽（mm）：标准靶三倍 / 双倍环宽。容差即 ±4 mm。 */
    const val RING_WIDTH_MM = 8.0

    /** 样本门槛：低于此值不给任何数字结论。 */
    const val MIN_BIAS_N = 12

    /** 门槛：达到才开始给散布与趋势。 */
    const val MIN_FULL_N = 30

    /** `r95` 判据（≈2 / ≈4 个环宽）。 */
    const val R95_GOOD_MM = 16.0
    const val R95_WIDE_MM = 32.0

    enum class Confidence { TOO_FEW, BIAS_ONLY, FULL }

    enum class BiasLevel { GOOD, ACCEPTABLE, OBVIOUS, SEVERE }

    /** σ 的相对标准误 ≈ `1/√(2(n−1))`，据此分档（n<12 → 21% 以上，不给结论）。 */
    fun confidenceOf(n: Int): Confidence = when {
        n < MIN_BIAS_N -> Confidence.TOO_FEW
        n < MIN_FULL_N -> Confidence.BIAS_ONLY
        else -> Confidence.FULL
    }

    fun biasLevelOf(biasMm: Double): BiasLevel = when {
        biasMm < 2.0 -> BiasLevel.GOOD
        biasMm < 4.0 -> BiasLevel.ACCEPTABLE
        biasMm < 8.0 -> BiasLevel.OBVIOUS
        else -> BiasLevel.SEVERE
    }

    /** 「偏内 / 偏外」由 `eRad` 的符号决定（不是上下）。 */
    fun biasDirectionWord(biasRad: Double): String = if (biasRad >= 0.0) "偏外" else "偏内"

    fun of(frames: List<ImpactFrame>): ImpactStats? {
        val n = frames.size
        if (n == 0) return null
        val meanRad = frames.sumOf { it.eRad } / n
        val meanTan = frames.sumOf { it.eTan } / n
        return ImpactStats(
            n = n,
            biasRad = meanRad,
            biasTan = meanTan,
            sigmaRad = sampleSd(frames.map { it.eRad }, meanRad),
            sigmaTan = sampleSd(frames.map { it.eTan }, meanTan),
            r95 = r95AboutCentroid(frames, meanRad, meanTan),
            rmse = sqrt(frames.sumOf { it.eRad * it.eRad + it.eTan * it.eTan } / n)
        )
    }

    /** 样本标准差（n−1）；n < 2 时为 0（单点没有散布可言）。 */
    private fun sampleSd(values: List<Double>, mean: Double): Double {
        if (values.size < 2) return 0.0
        val sumSq = values.sumOf { (it - mean) * (it - mean) }
        return sqrt(sumSq / (values.size - 1))
    }

    private fun r95AboutCentroid(frames: List<ImpactFrame>, meanRad: Double, meanTan: Double): Double {
        val distances = frames.map { hypot(it.eRad - meanRad, it.eTan - meanTan) }.sorted()
        val index = (ceil(0.95 * distances.size).toInt() - 1).coerceIn(0, distances.size - 1)
        return distances[index]
    }

    /**
     * 一句话结论（成品文案）。样本不足时**只给还差多少镖**，不抛任何数字 ——
     * 「画一张看起来很专业的图」比「诚实说数据不够」更没有价值。
     */
    fun headline(stats: ImpactStats): String {
        if (stats.n < MIN_FULL_N) {
            return "还需要 ${MIN_FULL_N - stats.n} 镖才能给稳定结论"
        }
        val direction = biasDirectionWord(stats.biasRad)
        val biasText = oneDecimal(stats.bias)
        return when (biasLevelOf(stats.bias)) {
            BiasLevel.GOOD ->
                "瞄点非常准（平均偏差 $biasText mm）—— 问题不在瞄，别去改瞄点。"
            BiasLevel.ACCEPTABLE ->
                "平均$direction $biasText mm，还在环宽容差内。保持现状，继续攒样本。"
            BiasLevel.OBVIOUS ->
                "你的误差主要来自系统性偏移：平均$direction $biasText mm（≈${oneDecimal(stats.bias / RING_WIDTH_MM)} 个环宽）。" +
                    "先别急着求散布 —— 固定站位与出手点，把这个常数吃掉。"
            BiasLevel.SEVERE ->
                "平均$direction $biasText mm，已经超过 1 个环宽 ⇒ 大概率整镖落在相邻区。" +
                    "优先处理站位 / 瞄点，而不是加练更久。"
        }
    }

    /** 组合结论：偏移小 + 散布大 ⇒ 问题在一致性，别去改瞄点。 */
    fun scatterAdvice(stats: ImpactStats): String = when {
        stats.r95 <= R95_GOOD_MM -> "散布已打得住（95% 落点在 ${oneDecimal(stats.r95)} mm 内）。"
        stats.r95 > R95_WIDE_MM ->
            "散布偏大（95% 落点在 ${oneDecimal(stats.r95)} mm 内 ≈ ${oneDecimal(stats.r95 / RING_WIDTH_MM)} 个环宽）：" +
                "练节奏与随挥，而不是改瞄点。"
        else -> "散布中等（95% 落点在 ${oneDecimal(stats.r95)} mm 内）。"
    }

    private fun oneDecimal(value: Double): String = ((value * 10).toInt() / 10.0).toString()
}

/**
 * 一镖的**回合视角**记录（V1.4 §4.3）。与 [ImpactFrame] 的区别是它带「第几回合 / 回合内第几镖 / 是否命中」。
 *
 * `roundOrdinal` 是**本 `session` 内**第几回合（1 起）—— 跨会话的回合序号没有意义，
 * 因此键是 `(sessionId, roundOrdinal)`。
 */
data class RoundDart(
    val sessionId: String,
    val hitAt: Long,
    val roundOrdinal: Int,
    /** 回合内第几镖（1..3）。 */
    val dartInRound: Int,
    /** 出框 / 未记点位 → `null`（该回合不进散布统计）。 */
    val frame: ImpactFrame?,
    val hit: Boolean
)

/** 单个三镖回合的散布；`spreadMm` = 三点两两距离均值。 */
data class RoundSpread(
    val sessionId: String,
    val roundOrdinal: Int,
    val spreadMm: Double,
    val allHit: Boolean
)

/**
 * 三镖回合类指标（V1.4 §4.3）——`session` 是「一次 20 镖训练」、`round` 是「3 镖一回合」，
 * **代码里不存在「组」这个单位**（术语统一，A-IMP-22）。
 *
 * 全部口径：
 * - **完整回合**：恰好 3 镖、`dartInRound` 覆盖 1/2/3、且**无出框**（有任一 `frame == null` 即剔除）；
 *   撤销过、或录到一半结束的回合都算不完整，直接排除而不是补零。
 * - [perfectRounds]：完整回合中三镖**全部命中意图目标**的回合数。
 * - [roundSpread] = 完整回合 `spreadMm` 的均值；[roundSpreadMin] / [roundSpreadMax] 同口径极值。
 */
data class RoundMetrics(
    val completeRounds: Int,
    val roundSpread: Double,
    val roundSpreadMin: Double,
    val roundSpreadMax: Double,
    /** 最紧 / 最散回合的键，格式 `"sessionId#roundOrdinal"`；无完整回合时为 `""`。 */
    val tightRoundKey: String,
    val looseRoundKey: String,
    val perfectRounds: Int,
    /** 全部**完整**回合（按会话与序号升序）。 */
    val rounds: List<RoundSpread>
) {
    companion object {
        val EMPTY = RoundMetrics(0, 0.0, 0.0, 0.0, "", "", 0, emptyList())
    }
}

/** 三镖回合的解读（§4.3 的文案分叉）。 */
data class RoundAdvice(
    /** `false` = 完整回合数不足 [ImpactRounds.MIN_COMPLETE_ROUNDS]，只列最紧 / 最散。 */
    val ready: Boolean,
    val text: String
)

/** 三镖回合指标的计算入口（无状态、纯函数、可单测）。 */
object ImpactRounds {

    /**
     * 完整回合数门槛（§4.3）：低于此值**不出数字结论**。
     *
     * 8 个回合 = 24 镖。比单镖层面的 30 镖门槛低，因为回合指标本身已经做过一次平均 ——
     * 但它仍然不能拿 2 个回合去说「我的回合内散布是多少」。
     */
    const val MIN_COMPLETE_ROUNDS = 8

    /**
     * 把 [stats]（单镖散布）与 [metrics]（回合内散布）放在一起解读 ——
     * 这是两个**不同**的问题，别混成一句话：
     *
     * - `r95` 大而 `roundSpread` 小 ⇒ **回合间漂移**（每个回合内部挺紧，但回合与回合之间整体位置在移动：
     *   站位 / 节奏 / 疲劳），不是「手抖」；
     * - 两者接近 ⇒ 主要就是**手法抖动**。
     */
    fun advice(stats: ImpactStats?, metrics: RoundMetrics): RoundAdvice {
        if (stats == null || metrics.completeRounds < MIN_COMPLETE_ROUNDS) {
            val need = MIN_COMPLETE_ROUNDS - metrics.completeRounds
            return RoundAdvice(
                ready = false,
                text = "完整回合 ${metrics.completeRounds} 个，还差 $need 个才能给回合结论；" +
                    "先看极值：最紧回合 ${
                        oneDecimal(metrics.roundSpreadMin)
                    } mm、最散回合 ${oneDecimal(metrics.roundSpreadMax)} mm。"
            )
        }
        val text = if (stats.r95 > metrics.roundSpread * ROUND_DRIFT_RATIO) {
            "回合内很紧（${
                oneDecimal(metrics.roundSpread)
            } mm），但整体 R95 有 ${
                oneDecimal(stats.r95)
            } mm —— 这是**回合间漂移**：每个回合内部还行，回合与回合之间整体位置在移动。" +
                "先固定站位与节奏，别练单镖精度。"
        } else {
            "回合内散布 ${
                oneDecimal(metrics.roundSpread)
            } mm、整体 R95 ${
                oneDecimal(stats.r95)
            } mm，两者接近 —— 主要是**手法抖动**，练出手一致性。"
        }
        return RoundAdvice(ready = true, text = text)
    }

    /** `R95 > roundSpread × 1.5` 即判为「回合间漂移」。 */
    const val ROUND_DRIFT_RATIO = 1.5

    private fun oneDecimal(value: Double): String = ((value * 10).toInt() / 10.0).toString()

    /**
     * 从**同一意图 + 同一窗口档位**的镖里算回合指标（跨档位合并会污染极值，调用方负责先过滤）。
     *
     * 返回 [RoundMetrics.EMPTY] 而不是 `null`：区分「数据脏 → 报错」与「还没有完整回合 → 少显示一块」，
     * 后者是正常状态，UI 不该弹任何错误。
     */
    fun metricsOf(darts: List<RoundDart>): RoundMetrics {
        if (darts.isEmpty()) return RoundMetrics.EMPTY

        // 键 = (sessionId, roundOrdinal)，按首次出现顺序（输入已按时间升序）保序累加。
        val order = LinkedHashMap<String, MutableList<RoundDart>>()
        darts.forEach { dart ->
            if (dart.sessionId.isBlank()) return@forEach
            order.getOrPut("${dart.sessionId}#${dart.roundOrdinal}") { mutableListOf() }.add(dart)
        }

        val complete = ArrayList<RoundSpread>(order.size)
        var perfect = 0
        order.forEach { (key, list) ->
            val ordered = list.sortedBy { it.dartInRound }
            val isComplete = ordered.size == 3 &&
                ordered.map { it.dartInRound } == listOf(1, 2, 3) &&
                ordered.all { it.frame != null }
            if (!isComplete) return@forEach
            val spread = pairwiseMean(ordered.map { it.frame!! })
            val allHit = ordered.all { it.hit }
            if (allHit) perfect++
            complete.add(
                RoundSpread(
                    sessionId = key.substringBefore('#'),
                    roundOrdinal = key.substringAfter('#').toInt(),
                    spreadMm = spread,
                    allHit = allHit
                )
            )
        }
        if (complete.isEmpty()) return RoundMetrics.EMPTY

        val tight = complete.minBy { it.spreadMm }
        val loose = complete.maxBy { it.spreadMm }
        return RoundMetrics(
            completeRounds = complete.size,
            roundSpread = complete.sumOf { it.spreadMm } / complete.size,
            roundSpreadMin = tight.spreadMm,
            roundSpreadMax = loose.spreadMm,
            tightRoundKey = "${tight.sessionId}#${tight.roundOrdinal}",
            looseRoundKey = "${loose.sessionId}#${loose.roundOrdinal}",
            perfectRounds = perfect,
            rounds = complete
        )
    }

    /** 三点两两距离均值（6 个方向取 3 个不重复对）。 */
    private fun pairwiseMean(frames: List<ImpactFrame>): Double {
        var sum = 0.0
        var count = 0
        for (i in frames.indices) {
            for (j in i + 1 until frames.size) {
                sum += hypot(
                    frames[i].eRad - frames[j].eRad,
                    frames[i].eTan - frames[j].eTan
                )
                count++
            }
        }
        return if (count == 0) 0.0 else sum / count
    }
}
