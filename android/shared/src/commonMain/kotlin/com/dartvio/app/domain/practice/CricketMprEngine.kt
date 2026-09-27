package com.dartvio.app.domain.practice

import com.dartvio.app.domain.model.Dart
import kotlin.math.abs
import kotlin.math.round

/** MPR 挑战轮数（10 轮 × 3 镖 = 30 镖）。 */
const val CRICKET_MPR_ROUNDS = 10
const val CRICKET_MPR_DARTS_PER_ROUND = 3

/** 单轮满标记数（3 镖全部三倍区）。 */
const val CRICKET_MPR_MARKS_PER_ROUND_MAX = 9

/** MPR 挑战的计分分区：15-20 与 Bull。 */
val CRICKET_MPR_TARGETS: List<Int> = listOf(20, 19, 18, 17, 16, 15, 25)

/** 分区显示名（Bull 特殊处理）。 */
fun cricketTargetLabel(number: Int): String = if (number == 25) "BULL" else "$number"

/**
 * 计算单镖在 MPR 挑战中的标记数。
 *
 * 仅 15-20 与 Bull 计分：S=1 / D=2 / T=3；Outer Bull=1 / Inner Bull=2；
 * 其余分区与脱靶均记 0。
 */
fun cricketMarks(dart: Dart): Int = when {
    dart.isMiss -> 0
    dart.isBull -> if (dart.multiplier >= 3) 3 else dart.multiplier.coerceAtLeast(1)
    dart.number in 15..20 -> dart.multiplier
    else -> 0
}

/** MPR 挑战中的一镖记录。 */
data class MprDart(
    val dart: Dart,
    val marks: Int
) {
    /** 是否命中目标分区。 */
    val isHit: Boolean get() = marks > 0
}

/**
 * Cricket MPR 挑战状态。
 *
 * 规则：10 轮 × 3 镖；统计 15-20 与 Bull 的标记总和，
 * **分区不封顶**（超出 3 个标记继续累加，用于反映真实准度）。
 * MPR = 总标记 ÷ 已完成轮数；进行中按已投镖数折算以便实时反馈。
 */
data class CricketMprState(
    val darts: List<MprDart> = emptyList(),
    val finished: Boolean = false
) {
    val dartsThrown: Int get() = darts.size

    val totalMarks: Int get() = darts.sumOf { it.marks }

    val roundsCompleted: Int get() = dartsThrown / CRICKET_MPR_DARTS_PER_ROUND

    /** 当前轮下标（0 基），已结束时锁定在最后一轮。 */
    val currentRoundIndex: Int
        get() = roundsCompleted.coerceAtMost(CRICKET_MPR_ROUNDS - 1)

    /** 当前轮号（1 基），用于展示。 */
    val currentRoundNumber: Int
        get() = if (finished) CRICKET_MPR_ROUNDS else currentRoundIndex + 1

    /** 当前轮已投的镖。 */
    val currentRoundDarts: List<MprDart>
        get() = darts.drop(roundsCompleted * CRICKET_MPR_DARTS_PER_ROUND)

    val currentRoundMarks: Int get() = currentRoundDarts.sumOf { it.marks }

    val hits: Int get() = darts.count { it.isHit }

    /** 命中率（命中目标分区的镖数占比）。 */
    val hitRate: Int get() = if (dartsThrown == 0) 0 else hits * 100 / dartsThrown

    /** 实时 MPR。 */
    val mpr: Float
        get() {
            if (dartsThrown == 0) return 0f
            val rounds = dartsThrown / CRICKET_MPR_DARTS_PER_ROUND.toFloat()
            return totalMarks / rounds
        }

    /** 各目标分区的累计标记数（不封顶）。 */
    val marksByTarget: Map<Int, Int>
        get() = CRICKET_MPR_TARGETS.associateWith { target ->
            darts.sumOf { if (it.dart.number == target) it.marks else 0 }
        }

    /** 已完成轮次中的单轮最高标记数。 */
    val bestRoundMarks: Int
        get() {
            if (roundsCompleted == 0) return 0
            return (0 until roundsCompleted).maxOf { round ->
                darts.subList(
                    round * CRICKET_MPR_DARTS_PER_ROUND,
                    round * CRICKET_MPR_DARTS_PER_ROUND + CRICKET_MPR_DARTS_PER_ROUND
                ).sumOf { it.marks }
            }
        }

    /** 是否完成全部轮次。 */
    val isFullSession: Boolean get() = roundsCompleted >= CRICKET_MPR_ROUNDS
}

/** MPR 挑战规则（纯函数，便于测试与复用）。 */
object CricketMprRules {

    private const val TOTAL_DARTS = CRICKET_MPR_ROUNDS * CRICKET_MPR_DARTS_PER_ROUND

    fun newSession(): CricketMprState = CricketMprState()

    /** 记录一镖；满 30 镖自动结束。 */
    fun record(state: CricketMprState, dart: Dart): CricketMprState {
        if (state.finished || state.dartsThrown >= TOTAL_DARTS) return state
        val next = state.darts + MprDart(dart, cricketMarks(dart))
        return state.copy(darts = next, finished = next.size >= TOTAL_DARTS)
    }

    /** 撤销上一镖。 */
    fun undo(state: CricketMprState): CricketMprState {
        if (state.darts.isEmpty()) return state
        return state.copy(darts = state.darts.dropLast(1), finished = false)
    }

    /** 提前结束（按已完成轮次结算）。 */
    fun finishEarly(state: CricketMprState): CricketMprState =
        if (state.darts.isEmpty()) state else state.copy(finished = true)
}

/** MPR 水平评级。 */
fun mprRating(mpr: Float): String = when {
    mpr >= 4f -> "大师"
    mpr >= 3f -> "精通"
    mpr >= 2f -> "熟练"
    mpr >= 1f -> "进阶"
    mpr > 0f -> "入门"
    else -> "未开始"
}

/**
 * MPR 保留两位小数的展示文本。
 *
 * 不用 `String.format("%.2f", ...)`：它既是 JVM-only（KMP 的 commonMain 用不了），
 * 又跟着默认 Locale 走（部分语言环境会输出 `1,23`）。这里**固定**用 `.` 作小数点，
 * 按四舍五入（正负都远离零）取两位 —— 口径与原实现一致。
 */
fun formatMpr(mpr: Float): String {
    if (mpr.isNaN() || mpr.isInfinite()) return "0.00"
    val negative = mpr < 0f
    val cents = round(abs(mpr.toDouble()) * 100.0).toLong()
    return "${if (negative) "-" else ""}${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
