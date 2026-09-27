package com.dartvio.app.domain.impact

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 一次 `session`（本轮训练）里的一个样本：**已经算好误差分解**的镖。
 *
 * 出框镖的 `frame == null`：它只进计数与出框方向统计，
 * 绝不进 σ / R95 / KDE（见 [HeatmapGrid] 与落点诊断设计 §4.1）。
 *
 * [spanMm] = 该镖落库时的窗口档位；双组叠加图靠它**拒绝跨档位叠图**（§5.4-⑥）。
 */
data class ImpactSample(
    val sessionId: String,
    val hitAt: Long,
    val frame: ImpactFrame?,
    val hit: Boolean,
    val spanMm: Double = 0.0
)

/** 一次 `session`（本轮训练）的聚合：`stats == null` 表示该轮没有任何窗内点。 */
data class ImpactRound(
    val sessionId: String,
    val firstHitAt: Long,
    val dartCount: Int,
    val hitCount: Int,
    val stats: ImpactStats?,
    /** 该轮全部**窗内**误差帧（供双组叠加图绘制；不含出框）。 */
    val frames: List<ImpactFrame> = emptyList(),
    /** 该轮的窗口档位（同一 `session` 内唯一，§3.2.1）。 */
    val spanMm: Double = 0.0
) {
    /** 命中率（含出框镖，分母是全部录入）。 */
    val hitRate: Double get() = if (dartCount == 0) 0.0 else hitCount.toDouble() / dartCount
}

/** 趋势判定：**只看显著 vs 噪声，不给「看起来变好了」**。 */
enum class TrendDirection { IMPROVED, WORSENED, NOISE, NOT_ENOUGH }

data class TrendResult(
    val direction: TrendDirection,
    val summary: String,
    /** 轮末只给**一个**动作（设计 §4.4 的闭环）。 */
    val nextAction: String,
    val previousRmse: Double? = null,
    val recentRmse: Double? = null,
    /** 同一次比较的「变动阈值」，用于向用户交代为什么说「还是噪声」。 */
    val thresholdMm: Double? = null
)

/** 双组叠加图的一侧（§5.4-⑥）：点集 + 该侧 R95 + 样本数。 */
data class OverlaySide(
    val n: Int,
    val r95: Double,
    val frames: List<ImpactFrame>
)

/**
 * 新旧双色叠加图的数据口径（§5.4-⑥ / A-IMP-30）。
 *
 * 硬约束都收敛在这里，UI 只负责画：
 * ① **必须同档位** —— 混入两种 `windowSpanMm` 时 [mixedSpans] = true，叠加作废（分图或提示）；
 * ② [conclusionReady]：两组各 ≥ [ImpactCalculator.MIN_BIAS_N] 镖且合计 ≥ [ImpactCalculator.MIN_FULL_N]；
 * ③ [dense]：合计 > [MAX_OVERLAY_POINTS] 时改用「质心 + r95 圆」，不画全量散点。
 */
data class OverlayResult(
    val previous: OverlaySide,
    val recent: OverlaySide,
    val deltaR95: Double,
    val conclusionReady: Boolean,
    val mixedSpans: Boolean,
    val dense: Boolean
)

/**
 * 轮间趋势（设计 §4.4）。
 *
 * 门槛是**逐轮**的：一轮凑不到 [ImpactCalculator.MIN_BIAS_N] 镖就还没资格进对比 ——
 * 拿 12 镖一轮的均值去比 12 镖一轮，等于在比噪声。
 *
 * 判据是双侧 95%（`>1.96·sqrt(σ₁²/n₁ + σ₂²/n₂)`）；这里的 `n` 是**轮数**、`σ` 是
 * **轮间标准差**，不是镖数 —— 比的是「平均值有没有移动」，而不是单镖散布。
 */
object ImpactTrend {

    /** 每侧取最近多少轮（`session`）。 */
    const val COMPARE_ROUNDS = 3

    /** 叠加图点数上限：超过则改用「质心 + r95 圆」（§5.4-⑥）。 */
    const val MAX_OVERLAY_POINTS = 200

    /** 双侧 95% 的 z 值。 */
    const val Z = 1.96

    /** 双侧 95% 下两均值差是否显著（`n` 为组数，< 2 无组间方差可言 ⇒ 不显著）。 */
    fun isSignificant(delta: Double, sigma1: Double, n1: Int, n2: Int, sigma2: Double): Boolean {
        if (n1 < 2 || n2 < 2) return false
        val se = sqrt(sigma1 * sigma1 / n1 + sigma2 * sigma2 / n2)
        return se > 0.0 && abs(delta) > Z * se
    }

    /** 双侧检验的变动阈值（mm）；`null` = 组数不足，算不出阈值。 */
    fun thresholdOf(sigma1: Double, n1: Int, n2: Int, sigma2: Double): Double? {
        if (n1 < 2 || n2 < 2) return null
        return Z * sqrt(sigma1 * sigma1 / n1 + sigma2 * sigma2 / n2)
    }

    /**
     * 按 `sessionId` 聚合为轮，轮间按时间升序（最老的在前，尾部 = 最近）。
     *
     * 这里手动累加而不用 `groupBy`：本包（`domain/impact/`）的标识符**禁用 `group` 词根**
     * （V1.4 术语统一，三镖单位一律用 `round`，见 A-IMP-22）。
     */
    fun rounds(samples: List<ImpactSample>): List<ImpactRound> {
        val buckets = LinkedHashMap<String, MutableList<ImpactSample>>()
        samples.forEach { sample ->
            if (sample.sessionId.isBlank()) return@forEach
            buckets.getOrPut(sample.sessionId) { mutableListOf() }.add(sample)
        }
        val result = ArrayList<ImpactRound>(buckets.size)
        buckets.forEach { (sessionId, list) ->
            val frames = list.mapNotNull { it.frame }
            result.add(
                ImpactRound(
                    sessionId = sessionId,
                    firstHitAt = list.minOf { it.hitAt },
                    dartCount = list.size,
                    hitCount = list.count { it.hit },
                    stats = ImpactCalculator.of(frames),
                    frames = frames,
                    spanMm = list.firstOrNull { it.spanMm > 0.0 }?.spanMm ?: 0.0
                )
            )
        }
        result.sortBy { it.firstHitAt }
        return result
    }

    /**
     * 最近 [COMPARE_ROUNDS] 轮 vs 紧邻此前的 [COMPARE_ROUNDS] 轮。
     *
     * 命中率上升而 R95 未动时**单独给文案**：那是瞄点修正，不是散布改善 ——
     * 把这两件事混成一句「有进步」，用户下一轮就会去练错的东西。
     */
    fun compare(allRounds: List<ImpactRound>): TrendResult {
        val usable = allRounds.filter { it.stats != null && it.stats.n >= ImpactCalculator.MIN_BIAS_N }
        if (usable.size < COMPARE_ROUNDS + 2) {
            val need = COMPARE_ROUNDS + 2 - usable.size
            return TrendResult(
                direction = TrendDirection.NOT_ENOUGH,
                summary = "还需要 $need 轮有效数据（每轮 ≥ ${ImpactCalculator.MIN_BIAS_N} 镖）才能比趋势。",
                nextAction = "先按现在的条件再录 1 轮，别急着换动作。"
            )
        }

        val recent = usable.takeLast(COMPARE_ROUNDS)
        val previous = usable.dropLast(COMPARE_ROUNDS).takeLast(COMPARE_ROUNDS)

        val prevRmse = recentStats(previous) { it.stats!!.rmse }
        val recRmse = recentStats(recent) { it.stats!!.rmse }
        val prevR95 = recentStats(previous) { it.stats!!.r95 }
        val recR95 = recentStats(recent) { it.stats!!.r95 }
        val prevBias = recentStats(previous) { it.stats!!.bias }
        val recBias = recentStats(recent) { it.stats!!.bias }
        val prevRate = recentStats(previous) { it.hitRate }
        val recRate = recentStats(recent) { it.hitRate }

        val rmseDelta = prevRmse.mean - recRmse.mean          // > 0 = 综合偏离变小 = 改善
        val rmseThreshold = thresholdOf(prevRmse.sd, previous.size, recent.size, recRmse.sd)
        val rmseSignificant = isSignificant(rmseDelta, prevRmse.sd, previous.size, recent.size, recRmse.sd)
        val r95Significant = isSignificant(
            prevR95.mean - recR95.mean, prevR95.sd, previous.size, recent.size, recR95.sd
        )
        val biasSignificant = isSignificant(
            prevBias.mean - recBias.mean, prevBias.sd, previous.size, recent.size, recBias.sd
        )
        val rateSignificant = isSignificant(
            recRate.mean - prevRate.mean, prevRate.sd, previous.size, recent.size, recRate.sd
        )

        val numbers = "此前 3 轮综合偏离 ${oneDecimal(prevRmse.mean)} mm → 最近 3 轮 " +
            "${oneDecimal(recRmse.mean)} mm"

        return when {
            rmseSignificant && rmseDelta > 0.0 -> TrendResult(
                direction = TrendDirection.IMPROVED,
                summary = "$numbers，变化 ${
                    oneDecimal(rmseDelta)
                } mm 超过了噪声阈值（阈值 ${
                    oneDecimal(rmseThreshold ?: 0.0)
                } mm）—— 改善是真的。",
                nextAction = "把窗口收窄一档再录一轮：如果仍然稳，说明进步不是窗口给的。",
                previousRmse = prevRmse.mean,
                recentRmse = recRmse.mean,
                thresholdMm = rmseThreshold
            )
            rmseSignificant && rmseDelta < 0.0 -> TrendResult(
                direction = TrendDirection.WORSENED,
                summary = "$numbers，综合偏离变大了 ${
                    oneDecimal(-rmseDelta)
                } mm（超过噪声阈值）。",
                nextAction = "先回到上一轮的状态（站位 / 节奏 / 器材）再录一轮，这一轮不要改瞄点。",
                previousRmse = prevRmse.mean,
                recentRmse = recRmse.mean,
                thresholdMm = rmseThreshold
            )
            rateSignificant && !r95Significant -> TrendResult(
                direction = TrendDirection.NOISE,
                summary = "命中率上来了（${
                    (prevRate.mean * 100).toInt()
                }% → ${(recRate.mean * 100).toInt()}%），但散布没有变（R95 基本没动）—— " +
                    "这是**瞄点修正**的效果，不是散布改善。",
                nextAction = "下一轮只盯散布：目标固定同一个点，练出手的一致性。",
                previousRmse = prevRmse.mean,
                recentRmse = recRmse.mean,
                thresholdMm = rmseThreshold
            )
            biasSignificant -> TrendResult(
                direction = TrendDirection.NOISE,
                summary = "系统偏移变了（${oneDecimal(prevBias.mean)} mm → ${
                    oneDecimal(recBias.mean)
                } mm），但综合散布还在噪声里。",
                nextAction = "保持新瞄点再录 1 轮，让散布也走出噪声再看结论。",
                previousRmse = prevRmse.mean,
                recentRmse = recRmse.mean,
                thresholdMm = rmseThreshold
            )
            else -> TrendResult(
                direction = TrendDirection.NOISE,
                summary = "两侧差 ${
                    oneDecimal(abs(rmseDelta))
                } mm，没超过噪声阈值（${
                    oneDecimal(rmseThreshold ?: 0.0)
                } mm）—— 现在还说不上改善。",
                nextAction = "条件别动，再录 2–3 轮；样本不够时任何「进步」都可能是运气。",
                previousRmse = prevRmse.mean,
                recentRmse = recRmse.mean,
                thresholdMm = rmseThreshold
            )
        }
    }

    /**
     * 新旧双色叠加图的数据口径（§5.4-⑥ / A-IMP-30）。
     *
     * **口径先行**：先把两组各自的窗内帧与 R95 算出来，再把「能不能叠、能不能下结论」交给调用方。
     * 跨档位（[OverlayResult.mixedSpans]）不是「提醒一下就行」—— 60 mm 窗口和 120 mm 窗口的点云
     * 根本不在同一张尺子上，叠在一起会**凭空造出**一个「收敛」的假象，因此直接判定作废。
     *
     * @param allRounds [rounds] 的输出；不足 2×[COMPARE_ROUNDS] 轮可用时两侧都为空、[OverlayResult.conclusionReady]=false。
     */
    fun overlay(allRounds: List<ImpactRound>): OverlayResult {
        val usable = allRounds.filter { it.stats != null && it.stats.n >= ImpactCalculator.MIN_BIAS_N }
        if (usable.size < COMPARE_ROUNDS * 2) {
            return OverlayResult(
                previous = OverlaySide(0, 0.0, emptyList()),
                recent = OverlaySide(0, 0.0, emptyList()),
                deltaR95 = 0.0,
                conclusionReady = false,
                mixedSpans = false,
                dense = false
            )
        }
        val recent = usable.takeLast(COMPARE_ROUNDS)
        val previous = usable.dropLast(COMPARE_ROUNDS).takeLast(COMPARE_ROUNDS)
        val prevSide = flatten(previous)
        val recSide = flatten(recent)

        val spans = (previous + recent).map { it.spanMm }.filter { it > 0.0 }.distinct()
        val totalPoints = prevSide.frames.size + recSide.frames.size
        return OverlayResult(
            previous = prevSide,
            recent = recSide,
            deltaR95 = prevSide.r95 - recSide.r95,
            conclusionReady = prevSide.n >= ImpactCalculator.MIN_BIAS_N &&
                recSide.n >= ImpactCalculator.MIN_BIAS_N &&
                (prevSide.n + recSide.n) >= ImpactCalculator.MIN_FULL_N,
            mixedSpans = spans.size > 1,
            dense = totalPoints > MAX_OVERLAY_POINTS
        )
    }

    private fun flatten(rounds: List<ImpactRound>): OverlaySide {
        val frames = rounds.flatMap { it.frames }
        val n = rounds.sumOf { it.stats?.n ?: 0 }
        val r95 = if (rounds.size < 2) {
            rounds.firstOrNull()?.stats?.r95 ?: 0.0
        } else {
            // 两组的 R95 用「加权平均」而不是重新算分位：叠加图只做直观对比，
            // 精确分位仍以单轮报告为准（§5.4-⑥ 明确「不做精确检验」）。
            val weighted = rounds.sumOf { (it.stats?.r95 ?: 0.0) * (it.stats?.n ?: 0) }
            if (n == 0) 0.0 else weighted / n
        }
        return OverlaySide(n = n, r95 = r95, frames = frames)
    }

    private class Side(val mean: Double, val sd: Double)

    private fun recentStats(rounds: List<ImpactRound>, pick: (ImpactRound) -> Double): Side {
        val values = rounds.map(pick)
        val mean = values.sum() / values.size
        val sd = if (values.size < 2) 0.0 else sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
        return Side(mean, sd)
    }

    private fun oneDecimal(value: Double): String = ((value * 10).toInt() / 10.0).toString()
}
