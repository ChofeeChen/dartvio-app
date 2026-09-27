package com.dartvio.app.domain.impact

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.vision.BoardGeometry
import com.dartvio.app.domain.vision.Point2
import kotlin.math.sqrt

/**
 * 「改瞄点值不值」的量化（V1.4 §4.6 / A-IMP-26）。
 *
 * ## 口径（三条都不能省）
 *
 * 1. **平移法**：`q_i = c.anchor + (p_i − aim.anchor)`。它回答的是「如果我把瞄点挪到 c、
 *    **但我的手型误差原封不动**，落点会变成什么样」—— 因此**天然不含适应期**，
 *    文案必须写明这一点，绝不能写成「你换了目标就能涨这么多分」。
 * 2. **含 miss**：出框镖按落库的**折算点**参与算分（miss 是真实得分，不能假装没发生）；
 *    但它**不进** σ / R95 / KDE —— 那是散布画像，两件事的数据集本就不同。
 * 3. **配对检验**：逐镖取 `d_i = score_c(q_i) − score_aim(p_i)`，看 `d` 的 95% CI 是否离开 0。
 *    比的是**同一批镖**在新旧目标下的差，而不是「新目标组的平均分 vs 老目标组的平均分」——
 *    后者会把两组人的水平差混进来。
 *
 * 单位一律是**分/镖**；`n < [MIN_CEV_N]` 直接不给结论。
 */
data class CevOption(
    val target: IntentTarget,
    /** 期望得分（分/镖）。 */
    val cev: Double,
    /** 均值的 95% 半宽（`1.96·sd/√n`）。 */
    val ci95: Double,
    val n: Int
) {
    val low: Double get() = cev - ci95
    val high: Double get() = cev + ci95
    val label: String get() = target.label
}

/**
 * cEV 对比结果。
 *
 * [worthShowing] = 收益比例达到 [ImpactValue.SHOW_GAIN_RATIO]（**展示门槛**，不是显著）。
 * [significant] = 配对检验通过（**统计门槛**）。两者都要看：
 * 只显著但收益 <5% 不必让用户折腾，只有收益但样本不够也只是「值得一试」。
 */
data class CevResult(
    val n: Int,
    val baseline: CevOption,
    val best: CevOption,
    /** `best − baseline`（分/镖）。 */
    val gain: Double,
    /** 收益比例（对 baseline 取比值；baseline 为 0 时为 0）。 */
    val gainRatio: Double,
    val worthShowing: Boolean,
    val significant: Boolean,
    /** 全部候选（按 cEV 降序）。 */
    val options: List<CevOption>,
    /** 首屏只显示这 2 个（当前目标 + 最优候选，Q12）；折叠区才列更多。 */
    val shortlist: List<CevOption>,
    val note: String
)

/** 双倍区结镖命中率（分母 = 全部样本，含 miss 折算）。 */
data class FinishRate(
    val target: IntentTarget,
    val rate: Double,
    val hits: Int,
    val n: Int
) {
    val label: String get() = target.label
}

/** cEV 与双倍结镖率的计算（纯函数、无副作用）。 */
object ImpactValue {

    /** cEV 样本门槛：低于此值不出任何数字。 */
    const val MIN_CEV_N = 30

    /** 收益展示门槛：相对当前目标 ≥ 5% 才值得打扰用户。 */
    const val SHOW_GAIN_RATIO = 0.05

    /** 95% 双侧系数。 */
    const val Z95 = 1.96

    /** 默认候选：主项 T20/19/18/17 + BULL + 结镖双倍 D20/16/8/12（§4.6）。 */
    val CANDIDATES: List<IntentTarget> =
        IntentTarget.PRIMARY_CHIPS + IntentTarget.BULL + IntentTarget.CHECKOUT_CHIPS

    /**
     * 算出 [CANDIDATES] 各自的 cEV 与配对显著性。
     *
     * @param samples **全部**落库点（靶面 mm，含 miss 折算点）；出框镖不得被调用方预先过滤掉。
     * @param aim 当前瞄点（baseline）。
     */
    fun compare(samples: List<Point2>, aim: IntentTarget): CevResult {
        val n = samples.size
        val baselineScores = samples.map { BoardGeometry.dartAt(it).score }
        val baseline = CevOption(
            target = aim,
            cev = mean(baselineScores),
            ci95 = ci95Of(baselineScores),
            n = n
        )
        // 只在**同类**候选里比（Q12）：得分型（T）与收镖型（D）不可同尺，
        // 把 D16 塞进 T20 的候选表会得到一个「改打双倍更划算」的荒谬结论。
        val shortlistSource = CANDIDATES.filter { it.kind == aim.kind }
        val options = shortlistSource.map { candidate ->
            val anchor = candidate.anchorMm()
            val shiftX = anchor.x - aim.anchorMm().x
            val shiftY = anchor.y - aim.anchorMm().y
            val scores = samples.map { point ->
                BoardGeometry.dartAt(point.x + shiftX, point.y + shiftY).score
            }
            CevOption(
                target = candidate,
                cev = mean(scores),
                ci95 = ci95Of(scores),
                n = n
            )
        }.sortedByDescending { it.cev }

        val best = options.firstOrNull() ?: baseline
        val shiftX = best.target.anchorMm().x - aim.anchorMm().x
        val shiftY = best.target.anchorMm().y - aim.anchorMm().y
        val bestScores = samples.map { point ->
            BoardGeometry.dartAt(point.x + shiftX, point.y + shiftY).score
        }
        val diffs = bestScores.indices.map { bestScores[it] - baselineScores[it] }
        val gain = best.cev - baseline.cev
        val gainRatio = if (baseline.cev <= 0.0) 0.0 else gain / baseline.cev

        return CevResult(
            n = n,
            baseline = baseline,
            best = best,
            gain = gain,
            gainRatio = gainRatio,
            worthShowing = n >= MIN_CEV_N && gainRatio >= SHOW_GAIN_RATIO,
            significant = n >= MIN_CEV_N && pairedSignificant(diffs),
            options = options,
            shortlist = listOf(baseline, best).distinctBy { it.target },
            note = "按「把当前误差整体平移到新目标」估算，**不含你在新目标下的适应期**；" +
                "样本 n=$n（低于 $MIN_CEV_N 不出结论）。"
        )
    }

    /**
     * 各双倍区的结镖命中率（§4.6「先看结镖」）。
     *
     * 分母是**全部样本**：打不中的也要算进去，否则命中率会虚高 ——
     * 这正是「只统计命中的那些镖」最常见的自欺方式。
     */
    fun finishRates(samples: List<Point2>, aim: IntentTarget): List<FinishRate> {
        val n = samples.size
        val aimAnchor = aim.anchorMm()
        return IntentTarget.CHECKOUT_CHIPS.map { target ->
            val anchor = target.anchorMm()
            val hits = samples.count { point ->
                val dart = BoardGeometry.dartAt(
                    point.x + anchor.x - aimAnchor.x,
                    point.y + anchor.y - aimAnchor.y
                )
                dart.number == target.sector && dart.multiplier == 2
            }
            FinishRate(
                target = target,
                rate = if (n == 0) 0.0 else hits.toDouble() / n,
                hits = hits,
                n = n
            )
        }.sortedByDescending { it.rate }
    }

    /** 判定某镖在平移后是否命中候选（供 UI 高亮用）。 */
    fun hitsAfterShift(point: Point2, aim: IntentTarget, candidate: IntentTarget): Boolean {
        val aimAnchor = aim.anchorMm()
        val anchor = candidate.anchorMm()
        val dart: Dart = BoardGeometry.dartAt(
            point.x + anchor.x - aimAnchor.x,
            point.y + anchor.y - aimAnchor.y
        )
        return candidate.isHit(dart)
    }

    private fun mean(values: List<Int>): Double =
        if (values.isEmpty()) 0.0 else values.sum().toDouble() / values.size

    private fun ci95Of(values: List<Int>): Double {
        val n = values.size
        if (n < 2) return 0.0
        val m = mean(values)
        val sd = sqrt(values.sumOf { (it - m) * (it - m) } / (n - 1))
        return Z95 * sd / sqrt(n.toDouble())
    }

    /** 配对差 `d` 的 95% CI 是否离开 0（`|mean| > 1.96·sd/√n`）。 */
    private fun pairedSignificant(diffs: List<Int>): Boolean {
        val n = diffs.size
        if (n < 2) return false
        val m = mean(diffs)
        if (m == 0.0) return false
        val sd = sqrt(diffs.sumOf { (it - m) * (it - m) } / (n - 1))
        val se = sd / sqrt(n.toDouble())
        if (se <= 0.0) return false
        return kotlin.math.abs(m) > Z95 * se
    }
}
