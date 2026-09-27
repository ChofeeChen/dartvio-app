package com.dartvio.app.domain.practice

import com.dartvio.app.domain.model.Dart

/** Count Up 练习：固定 8 轮，每轮 3 镖。 */
const val COUNT_UP_ROUNDS = 8
const val COUNT_UP_DARTS_PER_ROUND = 3

/**
 * Count Up 练习状态（M11 §6.2）。
 *
 * - [roundScores] 长度固定为 [COUNT_UP_ROUNDS]，null 表示该轮尚未进行。
 * - Bust 轮次记为 0 分。
 */
data class CountUpState(
    val roundScores: List<Int?> = List(COUNT_UP_ROUNDS) { null },
    val currentRoundIndex: Int = 0,
    val currentDarts: List<Dart> = emptyList(),
    val dartsThrown: Int = 0,
    /** 3 镖已满，等待自动进入下一轮。 */
    val roundLocked: Boolean = false,
    /** 短暂显示 "BUST" 提示。 */
    val bustFlash: Boolean = false,
    val finished: Boolean = false,
) {
    val currentRoundNumber: Int get() = (currentRoundIndex + 1).coerceAtMost(COUNT_UP_ROUNDS)

    val totalScore: Int get() = roundScores.filterNotNull().sum()

    val roundsPlayed: Int get() = roundScores.count { it != null }

    val currentRoundScore: Int get() = currentDarts.sumOf { it.score }

    /** 平均每轮得分（PPR）。 */
    val averagePerRound: Int get() = if (roundsPlayed == 0) 0 else totalScore / roundsPlayed

    val maxRoundScore: Int get() = roundScores.filterNotNull().maxOrNull() ?: 0

    val count180: Int get() = roundScores.count { it == 180 }

    val isRoundFull: Boolean get() = currentDarts.size >= COUNT_UP_DARTS_PER_ROUND

    val isLastRound: Boolean get() = currentRoundIndex >= COUNT_UP_ROUNDS - 1
}

/** Count Up 纯逻辑，便于单测且不依赖 Compose。 */
object CountUpRules {

    /** 录入一镖；满 3 镖时由调用方延迟结算，保持与 X01 一致的可视节奏。 */
    fun throwDart(state: CountUpState, dart: Dart): CountUpState {
        if (state.finished || state.isRoundFull) return state
        val darts = state.currentDarts + dart
        return state.copy(
            currentDarts = darts,
            dartsThrown = state.dartsThrown + 1,
            roundLocked = darts.size >= COUNT_UP_DARTS_PER_ROUND,
            bustFlash = false,
        )
    }

    /** 当前回合计入实际镖数得分并进入下一轮。 */
    fun finalizeRound(state: CountUpState): CountUpState {
        if (state.finished) return state
        return advance(state, state.currentRoundIndex, state.currentDarts.sumOf { it.score })
    }

    /** Bust：本轮 0 分，直接进入下一轮。 */
    fun bust(state: CountUpState): CountUpState {
        if (state.finished) return state
        return advance(state, state.currentRoundIndex, score = 0, bust = true)
    }

    /** 提前结束：已录入的当前回合计入，未录入则不计。 */
    fun finishEarly(state: CountUpState): CountUpState {
        if (state.finished) return state
        val scores = state.roundScores.toMutableList()
        if (state.currentDarts.isNotEmpty()) {
            scores[state.currentRoundIndex] = state.currentDarts.sumOf { it.score }
        }
        return state.copy(
            roundScores = scores,
            finished = true,
            roundLocked = true,
            bustFlash = false,
        )
    }

    fun undoLastDart(state: CountUpState): CountUpState {
        if (state.finished || state.currentDarts.isEmpty()) return state
        return state.copy(
            currentDarts = state.currentDarts.dropLast(1),
            dartsThrown = (state.dartsThrown - 1).coerceAtLeast(0),
            roundLocked = false,
        )
    }

    private fun advance(
        state: CountUpState,
        roundIndex: Int,
        score: Int,
        bust: Boolean = false,
    ): CountUpState {
        val scores = state.roundScores.toMutableList().also { it[roundIndex] = score }
        val nextIndex = roundIndex + 1
        val isLast = nextIndex >= COUNT_UP_ROUNDS
        return state.copy(
            roundScores = scores,
            currentRoundIndex = if (isLast) roundIndex else nextIndex,
            currentDarts = emptyList(),
            roundLocked = isLast,
            bustFlash = bust,
            finished = isLast,
        )
    }
}
