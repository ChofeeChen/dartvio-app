package com.dartvio.app.domain.versus

/**
 * 倍区竞赛（RING_RACE）· V1。
 *
 * 规则（提示词 §5.2）：
 * - 目标分区默认 20（可选 19 / 16）；
 * - **只有命中目标分区的镖计分**：T=3 / D=2 / S=1，打到别的分区 0 分；
 * - 先达目标分者胜（20 / 30（默认）/ 50 / 100），双方可分别设置（让分）；
 * - 「三倍独尊」让分变体：被让分的一侧仅三倍环计 3 分，D/S 均为 0。
 *
 * 与 Bull 之争同构：计分与终局都在 [onDart]。
 */
object RingRaceRule : VersusRule {

    const val MODE_KEY = "RING_RACE"

    /** 目标分区候选（默认 20）。 */
    val SECTOR_CHOICES = listOf(20, 19, 16)
    const val DEFAULT_SECTOR = 20

    /** 目标分候选（默认 30）。 */
    val TARGET_CHOICES = listOf(20, 30, 50, 100)
    const val DEFAULT_TARGET_SCORE = 30

    override val modeKey: String = MODE_KEY

    override fun defaultConfig(): BattleConfig = BattleConfig(
        modeKey = MODE_KEY,
        targetSector = DEFAULT_SECTOR,
        targetScore = DEFAULT_TARGET_SCORE,
    )

    override fun newState(config: BattleConfig, playerNames: List<String>): BattleState =
        versusBaseState(modeKey, config, playerNames)

    /**
     * **20 个扇区全开**，只按环带收窄。
     *
     * 这是本模式唯一不能省的一处「不要过滤」：规则是「打错分区 0 分」，
     * 如果键盘只留目标分区，用户打到 5 分区时无处可记，只能记成 MISS 或记成 T20 —— 两种都是假数据，
     * 而「打偏到哪」正是这个模式最该被记录下来的信息。
     */
    override fun inputFilter(state: BattleState): KeyboardLayout = KeyboardLayout(
        sectors = (1..20).toList(),
        rings = listOf(Ring.SINGLE, Ring.DOUBLE, Ring.TRIPLE),
        bull = true,
        miss = true,
    )

    override fun targetCaption(state: BattleState): String {
        val seat = state.currentPlayerIndex
        val handicap = if (state.config.tripleOnlyFor(seat)) " · 三倍独尊" else ""
        return "T${state.config.targetSector} 区 · T3 / D2 / S1 · 先达 ${state.config.targetFor(seat)} 分$handicap"
    }

    override fun onDart(state: BattleState, hit: BoardHit): Pair<BattleState, DartEvent?> {
        if (state.finished || state.isRoundComplete) return state to null

        val seat = state.currentPlayerIndex
        val points = pointsOf(hit, state.config, seat)
        val next = state.withDart(hit).updatePlayer(seat) { it.copy(score = it.score + points) }
        val target = state.config.targetFor(seat)

        if (next.players[seat].score >= target) {
            val settled = next
                .commitRound(next.roundScoreOf { pointsOf(it, state.config, seat) }, targetText(target))
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
            roundScore = state.roundScoreOf { pointsOf(it, state.config, seat) },
            targetSnapshot = targetText(target),
        )
        return if (settled.players[seat].score >= target) {
            settled.finish(seat, BattleEndReason.NORMAL) to DartEvent.Win(seat)
        } else {
            settled.passTurn() to null
        }
    }

    /**
     * 一镖的分值。`0` = 没打中目标分区，或该席位处于「三倍独尊」而这一镖不是三倍。
     */
    private fun pointsOf(hit: BoardHit, config: BattleConfig, seat: Int): Int {
        if (hit.sector == null || hit.sector != config.targetSector) return 0
        val tripleOnly = config.tripleOnlyFor(seat)
        return when (hit.ring) {
            Ring.TRIPLE -> 3
            Ring.DOUBLE -> if (tripleOnly) 0 else 2
            Ring.SINGLE -> if (tripleOnly) 0 else 1
            else -> 0
        }
    }

    private fun targetText(target: Int): String = "先达 $target 分"
}
