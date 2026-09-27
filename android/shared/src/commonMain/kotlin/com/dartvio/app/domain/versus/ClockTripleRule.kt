package com.dartvio.app.domain.versus

import com.dartvio.app.domain.vision.BoardGeometry

/**
 * 环游三镖（CLOCK_TRIPLE）· V1。
 *
 * 规则（提示词 §5.3）：
 * - 从分区 1 出发，按标准靶顺时针序推进；
 * - 每轮 3 镖全部投向当前目标分区，**命中口径 = 分区命中（环带不限）**；
 * - 核心前进规则：**3 镖全部命中当前分区 → 下一轮目标前进一格；否则下一轮继续打当前分区**（不后退）；
 * - 先完成 20 个分区者胜；
 * - 变体：经典版（1 镖命中即前进一格）/ 双倍版（须命中双倍环）/ 让分（强者起点靠后）。
 *
 * 序列**从 [BoardGeometry.SECTOR_ORDER] 旋转得到**，不抄一遍字面量：
 * 环形顺序是靶面几何事实，抄错一格就是一个「永远打不到」的死循环，
 * 而两处各存一份是它必然会被抄错的原因。
 */
object ClockTripleRule : VersusRule {

    const val MODE_KEY = "CLOCK_TRIPLE"

    /**
     * 环游顺序：`1 → 18 → 4 → 13 → … → 5 → 20`（自 1 分区起、顺时针一圈）。
     *
     * 由 `SECTOR_ORDER`（自 12 点方向的 20 起）旋转到「以 1 开头」得到，
     * 逐项与提示词 §5.3 的序列一致 —— 单测里钉了这条恒等。
     */
    val ORDER: List<Int> = run {
        val clockwise = BoardGeometry.SECTOR_ORDER.toList()
        val start = clockwise.indexOf(1).coerceAtLeast(0)
        List(clockwise.size) { clockwise[(start + it) % clockwise.size] }
    }

    /** 走完 [ORDER] 即获胜。 */
    val TOTAL_STEPS: Int get() = ORDER.size

    override val modeKey: String = MODE_KEY

    override fun defaultConfig(): BattleConfig = BattleConfig(
        modeKey = MODE_KEY,
        // 核心规则：3 镖全中才前进（经典版在配置页勾选后置 true）。
        singleHitAdvance = false,
        doubleOnly = false,
    )

    override fun newState(config: BattleConfig, playerNames: List<String>): BattleState =
        clockBaseState(modeKey, config, playerNames, ORDER)

    /**
     * 键盘只留「当前目标分区的环」+ MISS。
     *
     * 与倍区竞赛相反：本模式的目标就是「打到这个分区」，打偏到别的分区对推进没有任何影响，
     * 留着非目标扇区只会让一次错误的点击把目标分区的命中记丢。要求里也明确「靶面小图必须高亮当前分区」，
     * 键盘跟着小靶走 ⇒ 两者永远说的是同一个分区。
     */
    override fun inputFilter(state: BattleState): KeyboardLayout {
        if (state.finished) return KeyboardLayout(sectors = emptyList(), rings = emptyList(), bull = false)
        val target = state.current.targetSector ?: return KeyboardLayout(sectors = emptyList(), rings = emptyList())
        return KeyboardLayout(
            sectors = listOf(target),
            rings = orderRings(state.config.doubleOnly),
            bull = false,
            miss = true,
        )
    }

    override fun targetCaption(state: BattleState): String {
        val player = state.current
        val mode = when {
            state.config.doubleOnly -> "双倍版"
            state.config.singleHitAdvance -> "经典版"
            else -> "核心规则（3 镖全中才进）"
        }
        val target = player.targetSector ?: return mode
        return "打 $target 分区 · 已进 ${player.step}/$TOTAL_STEPS · $mode"
    }

    override fun progressOf(state: BattleState, playerIndex: Int): Int =
        state.players.getOrNull(playerIndex)?.step ?: 0

    override fun progressText(state: BattleState, playerIndex: Int): String =
        "${progressOf(state, playerIndex)}/$TOTAL_STEPS"

    override fun onDart(state: BattleState, hit: BoardHit): Pair<BattleState, DartEvent?> {
        if (state.finished || state.isRoundComplete) return state to null

        val seat = state.currentPlayerIndex
        val target = state.players[seat].targetSector
        val recorded = state.withDart(hit)

        // 经典版：命中即刻前进（同一轮内目标会变，所以 inputFilter 收的是 state 而不是 config）。
        if (!state.config.singleHitAdvance || target == null || !isHit(hit, target, state.config.doubleOnly)) {
            return recorded to null
        }
        val advanced = advance(recorded, seat)
        if (isComplete(advanced.players[seat])) {
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
        val hits = if (target == null) 0 else state.dartsInRound.count {
            isHit(it, target, state.config.doubleOnly)
        }
        val settled = state.commitRound(hits, targetText(target))

        // 核心规则：3 镖全中才前进一格（经典版的前进已在 onDart 完成，这里不再重复推进）。
        val completed = !state.config.singleHitAdvance &&
            target != null &&
            state.dartsInRound.size == VERSUS_DARTS_PER_ROUND &&
            hits == VERSUS_DARTS_PER_ROUND
        val next = if (completed) advance(settled, seat) else settled

        return when {
            completed && isComplete(next.players[seat]) ->
                next.finish(seat, BattleEndReason.NORMAL) to DartEvent.Win(seat)

            completed ->
                next.passTurn() to DartEvent.Advance(next.players[seat].targetSector ?: target!!)

            else -> next.passTurn() to null
        }
    }

    /** 命中当前目标分区（[doubleOnly] 时还要求落在双倍环上）。 */
    public fun isHit(hit: BoardHit, target: Int, doubleOnly: Boolean): Boolean =
        hit.sector == target && (!doubleOnly || hit.ring == Ring.DOUBLE)

    private fun orderRings(doubleOnly: Boolean): List<Ring> =
        if (doubleOnly) listOf(Ring.DOUBLE) else listOf(Ring.SINGLE, Ring.DOUBLE, Ring.TRIPLE)

    /** 推进一步：目标分区换成序列里的下一格。走完最后一格后目标回绕到起点（此时已终局，只作占位）。 */
    private fun advance(state: BattleState, seat: Int): BattleState =
        state.updatePlayer(seat) { player ->
            val step = player.step + 1
            player.copy(step = step, targetSector = ORDER[step % ORDER.size])
        }

    private fun isComplete(player: BattlePlayer): Boolean = player.step >= TOTAL_STEPS

    private fun targetText(target: Int?): String = if (target == null) "—" else "打 $target 分区"
}
