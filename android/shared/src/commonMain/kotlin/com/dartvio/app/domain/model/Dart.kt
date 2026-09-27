package com.dartvio.app.domain.model

/**
 * 单支飞镖落点。
 * PRD M2：number 取值 1..20；Bull=25；MISS=0。
 * multiplier：Single=1, Double=2, Triple=3；Inner Bull 为 25 分 = number(25) * multiplier(2)。
 */
data class Dart(
    val number: Int,
    val multiplier: Int
) {
    /** 本镖得分。MISS(number=0) 得 0 分。 */
    val score: Int get() = number * multiplier

    val isMiss: Boolean get() = number == 0
    val isSingle: Boolean get() = multiplier == 1
    val isDouble: Boolean get() = multiplier == 2
    val isTriple: Boolean get() = multiplier == 3

    /** Outer Bull (25 分, S25)。 */
    val isOuterBull: Boolean get() = number == 25 && multiplier == 1

    /** Inner Bull (50 分, D25) —— 计分写作 25 x 2。 */
    val isInnerBull: Boolean get() = number == 25 && multiplier == 2

    val isBull: Boolean get() = number == 25

    /** 用于展示的文本，例如 "T20"、"D16"、"BULL"、"MISS"。 */
    fun label(): String = when {
        isMiss -> "MISS"
        isInnerBull -> "BULL"
        isOuterBull -> "25"
        multiplier == 3 -> "T$number"
        multiplier == 2 -> "D$number"
        else -> "$number"
    }

    companion object {
        val MISS = Dart(0, 1)
        val OUTER_BULL = Dart(25, 1)
        val INNER_BULL = Dart(25, 2)

        fun single(n: Int) = Dart(n, 1)
        fun double(n: Int) = Dart(n, 2)
        fun triple(n: Int) = Dart(n, 3)
    }
}

/** 一个回合：最多 3 支镖。 */
data class Turn(
    val playerIndex: Int,
    val darts: List<Dart> = emptyList()
) {
    val totalScore: Int get() = darts.sumOf { it.score }
    val isEmpty: Boolean get() = darts.isEmpty()
    val isComplete: Boolean get() = darts.size >= 3
}
