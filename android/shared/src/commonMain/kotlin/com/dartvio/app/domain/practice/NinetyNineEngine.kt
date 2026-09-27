package com.dartvio.app.domain.practice

/** 99 Darts 练习：固定 33 轮、每轮 3 镖，共 99 镖。 */
const val NINETY_NINE_ROUNDS = 33
const val NINETY_NINE_DARTS_PER_ROUND = 3
const val NINETY_NINE_TOTAL_DARTS = NINETY_NINE_ROUNDS * NINETY_NINE_DARTS_PER_ROUND

/**
 * 单镖命中结果。倍率即得分：
 * - TRIPLE(T) = 3 分
 * - DOUBLE(D) = 2 分
 * - SINGLE(S) = 1 分
 * - MISS     = 0 分
 */
enum class SectorHit(val multiplier: Int, val shortLabel: String) {
    MISS(0, "MISS"),
    SINGLE(1, "S"),
    DOUBLE(2, "D"),
    TRIPLE(3, "T");

    val points: Int get() = multiplier
}

/**
 * 99 Darts 状态。
 *
 * 使用一个扁平的投镖序列 [throws] 表达进度，轮次/当前轮镖数均由长度推导，
 * 这样「撤销」只需去掉最后一镖，天然支持跨轮回退。
 */
data class NinetyNineState(
    val sector: Int = 20,
    val throws: List<SectorHit> = emptyList(),
) {
    val dartsThrown: Int get() = throws.size
    val finished: Boolean get() = throws.size >= NINETY_NINE_TOTAL_DARTS

    val currentRoundIndex: Int
        get() = (throws.size / NINETY_NINE_DARTS_PER_ROUND).coerceAtMost(NINETY_NINE_ROUNDS - 1)

    val currentRoundNumber: Int get() = (currentRoundIndex + 1).coerceAtMost(NINETY_NINE_ROUNDS)
    val dartsInCurrentRound: Int get() = throws.size % NINETY_NINE_DARTS_PER_ROUND
    val currentRoundThrows: List<SectorHit>
        get() = if (dartsInCurrentRound == 0) emptyList() else throws.takeLast(dartsInCurrentRound)
    val currentRoundPoints: Int get() = currentRoundThrows.sumOf { it.points }

    // ===== 累计指标 =====
    val totalPoints: Int get() = throws.sumOf { it.points }
    val hitCount: Int get() = throws.count { it.multiplier > 0 }
    val tripleCount: Int get() = throws.count { it == SectorHit.TRIPLE }
    val doubleCount: Int get() = throws.count { it == SectorHit.DOUBLE }
    val singleCount: Int get() = throws.count { it == SectorHit.SINGLE }
    val missCount: Int get() = throws.count { it == SectorHit.MISS }

    /** 综合命中率（命中镖数 / 已投镖数）。 */
    val hitRatePercent: Int
        get() = if (dartsThrown == 0) 0 else hitCount * 100 / dartsThrown

    /** 三倍命中率（T 镖数 / 已投镖数）。 */
    val tripleRatePercent: Int
        get() = if (dartsThrown == 0) 0 else tripleCount * 100 / dartsThrown

    /** 双倍命中率（D 镖数 / 已投镖数）。 */
    val doubleRatePercent: Int
        get() = if (dartsThrown == 0) 0 else doubleCount * 100 / dartsThrown

    /** 每一轮的得分（含未完成的当前轮，按已投镖计）。 */
    val roundScores: List<Int>
        get() = (0 until NINETY_NINE_ROUNDS).map { r ->
            throws.drop(r * NINETY_NINE_DARTS_PER_ROUND)
                .take(NINETY_NINE_DARTS_PER_ROUND)
                .sumOf { it.points }
        }

    val maxRoundPoints: Int get() = roundScores.maxOrNull() ?: 0

    /** 满分轮数（单轮 3 镖全部为 T，9 分）。 */
    val perfectRounds: Int get() = roundScores.count { it == NINETY_NINE_DARTS_PER_ROUND * 3 }

    /** 已完成轮次的平均得分（忽略未完成的当前轮）。 */
    val averagePerRound: Double
        get() {
            val completed = dartsThrown / NINETY_NINE_DARTS_PER_ROUND
            if (completed == 0) return 0.0
            var sum = 0
            for (r in 0 until completed) {
                sum += throws.drop(r * NINETY_NINE_DARTS_PER_ROUND)
                    .take(NINETY_NINE_DARTS_PER_ROUND)
                    .sumOf { it.points }
            }
            return sum.toDouble() / completed
        }
}

object NinetyNineRules {

    fun newSession(sector: Int): NinetyNineState = NinetyNineState(sector = sector.coerceIn(1, 20))

    fun record(state: NinetyNineState, hit: SectorHit): NinetyNineState {
        if (state.finished) return state
        return state.copy(throws = state.throws + hit)
    }

    fun undo(state: NinetyNineState): NinetyNineState {
        if (state.throws.isEmpty()) return state
        return state.copy(throws = state.throws.dropLast(1))
    }
}
