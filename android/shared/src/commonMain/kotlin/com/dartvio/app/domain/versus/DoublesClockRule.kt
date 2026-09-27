package com.dartvio.app.domain.versus

/**
 * 双倍环游（DOUBLES_CLOCK）· V2（引擎先落地，UI 后置）。
 *
 * 规则（提示词 §5.6）：
 * - 按 `D1 → D2 → … → D20` 顺序；可选收尾 Bull；
 * - 每轮 3 镖全部投向当前双倍区；**1 镖命中即前进一格（最多连进 3 格）**；
 * - 变体：三镖同中才前进（地狱模式）；让分：强者起点靠后（如 D10）；
 * - 先完成者胜。
 *
 * 与 [ClockTripleRule] 的两处差别都在配置里表达，不另立一套状态机：
 *  1. `singleHitAdvance` 默认 **true**（本模式的常态是「中一镖走一格」，地狱模式才置 false）；
 *  2. 目标恒为**双倍环**（[Ring.DOUBLE]），收尾 Bull 格则改判牛眼。
 */
object DoublesClockRule : VersusRule {

    const val MODE_KEY = "DOUBLES_CLOCK"

    /** 收尾 Bull 格在状态里用 `25` 表示（与 `dart_hits.number` 的「25 = 外牛」约定一致）。 */
    const val BULL_STEP = 25

    /** 序列：D1..D20，可按 [BattleConfig.finishOnBull] 追加一格 Bull 收尾。 */
    val ORDER: List<Int> = (1..20).toList()

    /** 地狱模式「三镖同中才前进」只开放给 UI 文案，这里给出可读标签。 */
    val VARIANT_LABELS = listOf("标准（1 镖即进）", "地狱（3 镖同中）")

    override val modeKey: String = MODE_KEY

    override fun defaultConfig(): BattleConfig = BattleConfig(
        modeKey = MODE_KEY,
        singleHitAdvance = true,
        finishOnBull = false,
    )

    override fun newState(config: BattleConfig, playerNames: List<String>): BattleState =
        clockBaseState(modeKey, config, playerNames, orderOf(config))

    override fun inputFilter(state: BattleState): KeyboardLayout {
        if (state.finished) return KeyboardLayout(sectors = emptyList(), rings = emptyList())
        val target = state.current.targetSector ?: return KeyboardLayout(sectors = emptyList(), rings = emptyList())
        return if (target == BULL_STEP) {
            KeyboardLayout(sectors = emptyList(), rings = emptyList(), bull = true, miss = true)
        } else {
            KeyboardLayout(sectors = listOf(target), rings = listOf(Ring.DOUBLE), bull = false, miss = true)
        }
    }

    override fun targetCaption(state: BattleState): String {
        val player = state.current
        val total = orderOf(state.config).size
        val mode = if (state.config.singleHitAdvance) "1 镖即进" else "3 镖同中才进"
        val target = player.targetSector ?: return mode
        val label = if (target == BULL_STEP) "收尾 Bull" else "D$target"
        return "$label · 已进 ${player.step}/$total · $mode"
    }

    override fun progressOf(state: BattleState, playerIndex: Int): Int =
        state.players.getOrNull(playerIndex)?.step ?: 0

    override fun progressText(state: BattleState, playerIndex: Int): String {
        val total = orderOf(state.config).size
        return "${progressOf(state, playerIndex)}/$total"
    }

    override fun onDart(state: BattleState, hit: BoardHit): Pair<BattleState, DartEvent?> {
        if (state.finished || state.isRoundComplete) return state to null

        val seat = state.currentPlayerIndex
        val target = state.players[seat].targetSector
        val recorded = state.withDart(hit)

        if (!state.config.singleHitAdvance || target == null || !isHit(hit, target)) {
            return recorded to null
        }
        val advanced = advance(recorded, seat)
        if (isComplete(advanced.players[seat], state.config)) {
            val settled = advanced
                .commitRound(advanced.dartsInRound.size, targetText(target))
                .finish(seat, BattleEndReason.NORMAL)
            return settled to DartEvent.Win(seat)
        }
        return advanced to DartEvent.Advance(advanced.players[seat].targetSector ?: target)
    }

    override fun onRoundEnd(state: BattleState): Pair<BattleState, DartEvent?> {
        if (state.finished || !state.isRoundComplete) return state to null

        val seat = state.currentPlayerIndex
        val target = state.players[seat].targetSector
        val hits = if (target == null) 0 else state.dartsInRound.count { isHit(it, target) }
        val settled = state.commitRound(hits, targetText(target))

        val completed = !state.config.singleHitAdvance &&
            target != null &&
            state.dartsInRound.size == VERSUS_DARTS_PER_ROUND &&
            hits == VERSUS_DARTS_PER_ROUND
        val next = if (completed) advance(settled, seat) else settled

        return when {
            completed && isComplete(next.players[seat], state.config) ->
                next.finish(seat, BattleEndReason.NORMAL) to DartEvent.Win(seat)

            completed ->
                next.passTurn() to DartEvent.Advance(next.players[seat].targetSector ?: target!!)

            else -> next.passTurn() to null
        }
    }

    /** 命中当前格：D1..D20 要落在对应双倍环上，收尾格命中任意 Bull 都算。 */
    public fun isHit(hit: BoardHit, target: Int): Boolean =
        if (target == BULL_STEP) hit.isBull else hit.sector == target && hit.ring == Ring.DOUBLE

    private fun orderOf(config: BattleConfig): List<Int> =
        if (config.finishOnBull) ORDER + BULL_STEP else ORDER

    private fun advance(state: BattleState, seat: Int): BattleState =
        state.updatePlayer(seat) { player ->
            val step = player.step + 1
            val order = orderOf(state.config)
            player.copy(step = step, targetSector = order[step.coerceAtMost(order.lastIndex)])
        }

    private fun isComplete(player: BattlePlayer, config: BattleConfig): Boolean =
        player.step >= orderOf(config).size

    private fun targetText(target: Int?): String = when {
        target == null -> "—"
        target == BULL_STEP -> "收尾 Bull"
        else -> "D$target"
    }
}
