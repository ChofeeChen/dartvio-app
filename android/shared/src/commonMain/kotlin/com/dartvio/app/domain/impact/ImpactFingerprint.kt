package com.dartvio.app.domain.impact

import kotlin.math.abs

/** 误差的**主导方向**（V1.4 §4.7）。这是画像，不是成绩。 */
enum class FingerprintAxis(val label: String, val hint: String) {
    RADIAL("径向为主", "误差主要沿「偏内 / 偏外」方向 —— 先查站位距离与出手点。"),
    TANGENT("切向为主", "误差主要沿「左右」方向 —— 先查握姿与随挥的横向一致性。"),
    BALANCED("两向均衡", "径向与切向相当 —— 先固定节奏，不要单练某一向。")
}

/**
 * 投掷指纹（V1.4 §4.7 / A-IMP-28）—— 回答「我这类误差稳不稳」。
 *
 * ## 为什么用**占比**而不是均值
 *
 * 窗口一换，毫米数就换了一把尺子；径向 / 切向的**比例**是尺度无关的，
 * 因此跨档位、跨目标汇总也不会失真（这也是它敢把全部历史放在一起的原因）。
 *
 * ## 三条禁令（验收会查）
 *
 * 1. **不打分**：不出现 rating / 星级 / 百分位 / 排名 —— 指纹是描述，不是成绩单；
 * 2. **不给结论门**：只有 [n] ≥ [ImpactFingerprintCalculator.MIN_DOMINANT_N] 才填 [dominant]，
 *    否则 `null`（UI 只画两条占比条，不写「你属于哪一类」）；
 * 3. **不出框镖不进占比**：出框镖单独统计在 [outShare]，其方向分布用不上「径向 vs 切向」这套分解。
 */
data class ImpactFingerprint(
    /** 参与统计的**全部**镖（含出框，作 [outShare] 的分母）。 */
    val n: Int,
    /** 窗内镖中 |径向| 的占比（0–1）。 */
    val radialShare: Double,
    /** 窗内镖中 |切向| 的占比（0–1），恒 = `1 − radialShare`。 */
    val tangentShare: Double,
    /** 出框占比（0–1）。 */
    val outShare: Double,
    /** 径向误差的**带符号**均值（`> 0` = 偏外；供「偏外型 / 偏内型」文案用）。 */
    val meanERad: Double,
    /** 切向误差的**带符号**均值。 */
    val meanETan: Double,
    /** `null` = 样本不足以定性（n < [ImpactFingerprintCalculator.MIN_DOMINANT_N]）。 */
    val dominant: FingerprintAxis?
) {
    /**
     * 一句话自画像（**只描述方向，不含任何评分**）。
     *
     * `dominant == null` ⇒ 只说「样本还不够定性」，不猜。
     */
    fun portrait(): String {
        val axis = dominant ?: return "已记录 $n 镖；到 ${ImpactFingerprintCalculator.MIN_DOMINANT_N} 镖才谈得上「你属于哪一类」。"
        return when (axis) {
            FingerprintAxis.RADIAL -> "径向为主（${(radialShare * 100).toInt()}%）：" +
                if (meanERad > 0) "偏外型 —— 误差主要来自瞄点比实际落点更靠内。" else "偏内型 —— 误差主要来自瞄点比实际落点更靠外。"
            FingerprintAxis.TANGENT -> "切向为主（${(tangentShare * 100).toInt()}%）：" +
                "左右漂移型 —— 误差主要来自横向一致性。"
            FingerprintAxis.BALANCED -> "两向均衡：径向 ${(radialShare * 100).toInt()}% / 切向 ${(tangentShare * 100).toInt()}%。"
        }
    }
}

/** 指纹计算（纯函数）。 */
object ImpactFingerprintCalculator {

    /** 低于此值**整体不出**指纹（连占比条也不给）。 */
    const val MIN_FINGERPRINT_N = 30

    /** 低于此值只给两条占比条，不给 [ImpactFingerprint.dominant]。 */
    const val MIN_DOMINANT_N = 100

    /** 定性阈值：`≥ 0.565` 归径向、`≤ 0.435` 归切向，中间为均衡（留 0.13 的带宽避免抖动）。 */
    const val SHARE_HIGH = 0.565
    const val SHARE_LOW = 0.435

    /**
     * @param frames 窗内误差帧（**必须同 `windowSpanMm` 档位**，跨档位合并会让占比失去意义）。
     * @param outCount `outBand != 0` 的镖数（只进 [ImpactFingerprint.outShare]）。
     * @param total 全部录入镖数（含出框）。
     */
    fun fingerprint(frames: List<ImpactFrame>, outCount: Int, total: Int): ImpactFingerprint? {
        if (total < MIN_FINGERPRINT_N || frames.isEmpty()) return null
        val sumAbsRad = frames.sumOf { abs(it.eRad) }
        val sumAbsTan = frames.sumOf { abs(it.eTan) }
        val sum = sumAbsRad + sumAbsTan
        val radialShare = if (sum <= 0.0) 0.5 else sumAbsRad / sum

        return ImpactFingerprint(
            n = total,
            radialShare = radialShare,
            tangentShare = 1.0 - radialShare,
            outShare = outCount.toDouble() / total,
            meanERad = frames.sumOf { it.eRad } / frames.size,
            meanETan = frames.sumOf { it.eTan } / frames.size,
            dominant = if (total >= MIN_DOMINANT_N) axisOf(radialShare) else null
        )
    }

    /** 便捷入口：直接吃 [ImpactSample]（`frame == null` 记为出框）。 */
    fun of(samples: List<ImpactSample>): ImpactFingerprint? =
        fingerprint(
            frames = samples.mapNotNull { it.frame },
            outCount = samples.count { it.frame == null },
            total = samples.size
        )

    /** 由径向占比定性；不做任何评分。 */
    fun axisOf(radialShare: Double): FingerprintAxis = when {
        radialShare >= SHARE_HIGH -> FingerprintAxis.RADIAL
        radialShare <= SHARE_LOW -> FingerprintAxis.TANGENT
        else -> FingerprintAxis.BALANCED
    }
}
