package com.dartvio.app.domain.practice

import com.dartvio.app.domain.model.Dart

/**
 * 随机结镖练习状态机。
 *
 * 规则：
 * - 目标生成后，玩家最多投 3 镖双结（最后一镖必须是 D 或 BULL）。
 * - 剩余分数 < 0 或 == 1，或 0 但最后一镖不是双，视为 Bust。\n * - 3 镖仍未结镖，视为失败（FAIL_NO_CHECKOUT）。\n */
enum class CheckoutResult {
    IN_PROGRESS,
    SUCCESS,
    FAIL_BUST,
    FAIL_NO_CHECKOUT
}

data class RandomCheckoutState(
    val target: Int = 0,
    val darts: List<Dart> = emptyList(),
    val remaining: Int = 0,
    val result: CheckoutResult = CheckoutResult.IN_PROGRESS,
    val showAnswer: Boolean = false,
    val routes: List<List<Dart>> = emptyList(),
) {
    val isFinished: Boolean get() = result != CheckoutResult.IN_PROGRESS
    val bestRoute: List<Dart> get() = routes.firstOrNull() ?: emptyList()
}

object RandomCheckoutRules {

    fun newTarget(target: Int, routes: List<List<Dart>>): RandomCheckoutState {
        return RandomCheckoutState(
            target = target,
            darts = emptyList(),
            remaining = target,
            result = CheckoutResult.IN_PROGRESS,
            showAnswer = false,
            routes = routes,
        )
    }

    fun throwDart(state: RandomCheckoutState, dart: Dart): RandomCheckoutState {
        if (state.isFinished || state.darts.size >= 3) return state
        val remaining = state.remaining - dart.score
        val darts = state.darts + dart

        return when {
            remaining < 0 || remaining == 1 -> {
                state.copy(
                    darts = darts,
                    remaining = remaining.coerceAtLeast(0),
                    result = CheckoutResult.FAIL_BUST
                )
            }
            remaining == 0 -> {
                if (dart.isDouble || dart.isInnerBull) {
                    state.copy(darts = darts, remaining = 0, result = CheckoutResult.SUCCESS)
                } else {
                    state.copy(darts = darts, remaining = 0, result = CheckoutResult.FAIL_BUST)
                }
            }
            darts.size >= 3 -> {
                state.copy(darts = darts, remaining = remaining, result = CheckoutResult.FAIL_NO_CHECKOUT)
            }
            else -> {
                state.copy(darts = darts, remaining = remaining)
            }
        }
    }

    fun retry(state: RandomCheckoutState): RandomCheckoutState {
        return state.copy(
            darts = emptyList(),
            remaining = state.target,
            result = CheckoutResult.IN_PROGRESS,
            showAnswer = false,
        )
    }

    fun toggleAnswer(state: RandomCheckoutState): RandomCheckoutState {
        return state.copy(showAnswer = !state.showAnswer)
    }
}
