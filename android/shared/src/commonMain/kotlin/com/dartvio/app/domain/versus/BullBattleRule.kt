package com.dartvio.app.domain.versus

/**
 * Bull 之争（BULL_BATTLE）· V1。
 *
 * 规则（提示词 §5.1）：
 * - 每轮 3 镖全部投向 Bull 区；
 * - 外 Bull = 1 分，内 Bull = 2 分（**统一模式**下两者都 1 分）；
 * - **先达到目标分者立即获胜**，没有爆分规则 —— 超出也算赢；
 * - 目标分 10 / 20（默认）/ 30 / 50，双方可分别设置（让分）。
 *
 * 两个实现要点：
 *  1. 计分与终局判定都在 [onDart] 完成，因为「达到目标即结束」意味着**后面几镖根本不该再投**；
 *     若等到回合结算才判，玩家会被要求投完剩下的镖，多出来的镖会污染 `totalDarts` 与命中分布。
 *  2. [onRoundEnd] 只做落快照 + 换手，并保留一次兜底判定（状态从外部反序列化回来时仍能收口）。
 */
object BullBattleRule : VersusRule {

    const val MODE_KEY = "BULL_BATTLE"

    /** 目标分候选（默认取 [DEFAULT_TARGET_SCORE]）。 */
    val TARGET_CHOICES = listOf(10, 20, 30, 50)
    const val DEFAULT_TARGET_SCORE = 20

    override val modeKey: String = MODE_KEY

    override fun defaultConfig(): BattleConfig = BattleConfig(
        modeKey = MODE_KEY,
        targetScore = DEFAULT_TARGET_SCORE,
        splitBull = true,
    )

    override fun newState(config: BattleConfig, playerNames: List<String>): BattleState =
        versusBaseState(modeKey, config, playerNames)

    /**
     * 只开 Bull 与 MISS 两键。
     *
     * 这里**不给**扇区键：本模式的目标就是牛眼，留着 20 个点了必然 0 分的扇区键
     * 只会让人误以为「打到 20 也算」。脱靶保留 —— 不允许记 MISS 只会逼用户把脱靶记成外牛。
     */
    override fun inputFilter(state: BattleState): KeyboardLayout = KeyboardLayout(
        sectors = emptyList(),
        rings = emptyList(),
        bull = true,
        miss = true,
    )

    override fun targetCaption(state: BattleState): String {
        val inner = if (state.config.splitBull) 2 else 1
        return "Bull 区 · 外 1 分 / 内 $inner 分 · 先达 ${state.config.targetFor(state.currentPlayerIndex)} 分"
    }

    override fun onDart(state: BattleState, hit: BoardHit): Pair<BattleState, DartEvent?> {
        if (state.finished || state.isRoundComplete) return state to null

        val seat = state.currentPlayerIndex
        val points = pointsOf(hit, state.config.splitBull)
        val next = state.withDart(hit).updatePlayer(seat) { it.copy(score = it.score + points) }
        val target = state.config.targetFor(seat)

        if (next.players[seat].score >= target) {
            // 立即终局：不满 3 镖也算，超出也算赢。
            val settled = next
                .commitRound(next.roundScoreOf { pointsOf(it, state.config.splitBull) }, targetText(target))
                .finish(seat, BattleEndReason.NORMAL)
            return settled to DartEvent.Win(seat)
        }
        return next to if (points > 0) DartEvent.Scored(points) else null
    }

    override fun onRoundEnd(state: BattleState): Pair<BattleState, DartEvent?> {
        if (state.finished || !state.isRoundComplete) return state to null

        val seat = state.currentPlayerIndex
        val target = state.config.targetFor(seat)
        val settled = state.commitRound(
            roundScore = state.roundScoreOf { pointsOf(it, state.config.splitBull) },
            targetSnapshot = targetText(target),
        )
        // 兜底：正常路径下 onDart 已经判过胜负了。
        return if (settled.players[seat].score >= target) {
            settled.finish(seat, BattleEndReason.NORMAL) to DartEvent.Win(seat)
        } else {
            settled.passTurn() to null
        }
    }

    private fun pointsOf(hit: BoardHit, splitBull: Boolean): Int = when (hit.ring) {
        Ring.INNER_BULL -> if (splitBull) 2 else 1
        Ring.OUTER_BULL -> 1
        else -> 0
    }

    private fun targetText(target: Int): String = "先达 $target 分"
}
