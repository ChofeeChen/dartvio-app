package com.dartvio.app.domain.versus

import kotlin.math.floor

/**
 * 减半挑战（HALVE_IT）· V2（引擎先落地，UI 后置）。
 *
 * 规则（提示词 §5.5）：
 * - 目标序列 8 轮：`20 → 16 → 任意双倍 → 任意三倍 → 25 → Bull50 → 17 → 15`；
 * - 「任意双倍」轮：命中任意双倍区的镖按其分值计入（D16 计 32）；
 * - 「25」轮每支外 Bull 计 25；「Bull50」轮每支内 Bull 计 50；
 * - **惩罚：本轮 3 镖全部 0 分 → 当前总分减半（floor）**；
 * - 序列完成 → 总分高者胜；平分加赛 Bull。
 *
 * 序列用枚举表达（而不是字符串 / 字符串列表）：这样「任意双倍」与「D16」在类型上就不会被混为一谈，
 * 计分与键盘过滤都能 `when` 穷尽 —— 少一个 `else 0` 分支，就少一处「加了新槽位却忘了给分」的静默错。
 */
object HalveItRule : VersusRule {

    const val MODE_KEY = "HALVE_IT"

    /** 一个目标槽位。`number` 仅对 [S20] / [S16] / [S17] / [S15] / 两个 Bull 槽有意义。 */
    enum class Slot(val label: String) {
        S20("20"),
        S16("16"),
        ANY_DOUBLE("任意双倍"),
        ANY_TRIPLE("任意三倍"),
        OUTER_BULL("25"),
        INNER_BULL("Bull 50"),
        S17("17"),
        S15("15"),
    }

    /** 经典 8 轮序列（可配置的落点就是这个常量列表）。 */
    val SEQUENCE: List<Slot> = listOf(
        Slot.S20,
        Slot.S16,
        Slot.ANY_DOUBLE,
        Slot.ANY_TRIPLE,
        Slot.OUTER_BULL,
        Slot.INNER_BULL,
        Slot.S17,
        Slot.S15,
    )

    override val modeKey: String = MODE_KEY

    override fun defaultConfig(): BattleConfig = BattleConfig(modeKey = MODE_KEY)

    override fun newState(config: BattleConfig, playerNames: List<String>): BattleState =
        withSlotTarget(versusBaseState(modeKey, config, playerNames), roundNo = 1)

    /** 第 n 轮（1 起）的槽位；越界夹到序列边界。 */
    fun slotOfRound(roundNo: Int): Slot = SEQUENCE[(roundNo - 1).coerceIn(0, SEQUENCE.size - 1)]

    /** 槽位对应的「单一目标分区」；`null` = 本轮不针对某个具体扇区（任意双倍 / 任意三倍）。 */
    fun targetSectorOf(slot: Slot): Int? = when (slot) {
        Slot.S20 -> 20
        Slot.S16 -> 16
        Slot.S17 -> 17
        Slot.S15 -> 15
        Slot.OUTER_BULL, Slot.INNER_BULL -> 25
        Slot.ANY_DOUBLE, Slot.ANY_TRIPLE -> null
    }

    override fun inputFilter(state: BattleState): KeyboardLayout {
        if (state.playoff) {
            return KeyboardLayout(sectors = emptyList(), rings = emptyList(), bull = true, miss = true)
        }
        return when (val slot = slotOfRound(state.roundNo)) {
            Slot.ANY_DOUBLE -> KeyboardLayout(
                sectors = (1..20).toList(),
                rings = listOf(Ring.DOUBLE),
                bull = false,
                miss = true,
            )

            Slot.ANY_TRIPLE -> KeyboardLayout(
                sectors = (1..20).toList(),
                rings = listOf(Ring.TRIPLE),
                bull = false,
                miss = true,
            )

            Slot.OUTER_BULL, Slot.INNER_BULL -> KeyboardLayout(
                sectors = emptyList(),
                rings = emptyList(),
                bull = true,
                miss = true,
            )

            else -> KeyboardLayout(
                sectors = listOfNotNull(targetSectorOf(slot)),
                rings = listOf(Ring.SINGLE, Ring.DOUBLE, Ring.TRIPLE),
                bull = true,
                miss = true,
            )
        }
    }

    override fun targetCaption(state: BattleState): String =
        if (state.playoff) {
            "加赛 Bull · 各 1 镖，离牛心近者胜"
        } else {
            val round = state.roundNo.coerceAtMost(SEQUENCE.size)
            "第 $round/${SEQUENCE.size} 轮 · ${slotOfRound(round).label} · 本轮全 0 则总分减半"
        }

    override fun onDart(state: BattleState, hit: BoardHit): Pair<BattleState, DartEvent?> {
        if (state.finished || state.isRoundComplete) return state to null

        val seat = state.currentPlayerIndex
        if (state.playoff) {
            val next = state.withDart(hit)
                .updatePlayer(seat) { it.copy(playoffScore = bullProximity(hit)) }
            return next to null
        }

        val points = pointsOf(hit, slotOfRound(state.roundNo))
        val next = state.withDart(hit).updatePlayer(seat) { it.copy(score = it.score + points) }
        return next to if (points > 0) DartEvent.Scored(points) else null
    }

    override fun onRoundEnd(state: BattleState): Pair<BattleState, DartEvent?> {
        if (state.finished || !state.isRoundComplete) return state to null
        return if (state.playoff) settlePlayoff(state) else settleRound(state)
    }

    private fun settleRound(state: BattleState): Pair<BattleState, DartEvent?> {
        val seat = state.currentPlayerIndex
        val slot = slotOfRound(state.roundNo)
        val roundScore = state.roundScoreOf { pointsOf(it, slot) }
        val before = state.players[seat].score
        val settled = state.commitRound(roundScore, slot.label)

        // 惩罚：3 镖全 0 分（含「有镖但一个都不满足目标」）⇒ 当前总分减半（floor）。
        val halved = floor(before / 2.0).toInt()
        val next = if (roundScore == 0) {
            settled.updatePlayer(seat) { it.copy(score = halved) }
        } else {
            settled
        }
        val halveEvent = if (roundScore == 0) DartEvent.Halve(before, halved) else null

        // 目标槽位在换手后重算：轮号一变，双方席位上的目标分区必须跟着变，否则对局页的小靶会指向上一个槽位。
        val passed = next.passTurn()
        val handed = withSlotTarget(passed, passed.roundNo)
        if (handed.roundNo <= SEQUENCE.size || handed.currentPlayerIndex != 0) {
            return handed to halveEvent
        }

        val leader = when {
            handed.players[0].score > handed.players[1].score -> 0
            handed.players[1].score > handed.players[0].score -> 1
            else -> null
        }
        return when {
            leader != null && handed.players[leader].score > 0 ->
                handed.finish(leader, BattleEndReason.NORMAL) to DartEvent.Win(leader)

            else -> handed.copy(
                playoff = true,
                players = handed.players.map { it.copy(playoffScore = null) },
            ) to halveEvent
        }
    }

    private fun settlePlayoff(state: BattleState): Pair<BattleState, DartEvent?> {
        val settled = state.commitRound(state.dartsInRound.sumOf { bullProximity(it) }, "加赛 Bull")
        val next = settled.passTurn()
        if (next.currentPlayerIndex != 0) return next to null

        val first = next.players[0].playoffScore ?: 0
        val second = next.players[1].playoffScore ?: 0
        return when {
            first > second -> next.finish(0, BattleEndReason.NORMAL) to DartEvent.Win(0)
            second > first -> next.finish(1, BattleEndReason.NORMAL) to DartEvent.Win(1)
            else -> next.copy(players = next.players.map { it.copy(playoffScore = null) }) to null
        }
    }

    /** 一镖在某个槽位下的得分。 */
    public fun pointsOf(hit: BoardHit, slot: Slot): Int = when (slot) {
        Slot.S20 -> if (hit.sector == 20) hit.sectorScore() else 0
        Slot.S16 -> if (hit.sector == 16) hit.sectorScore() else 0
        Slot.S17 -> if (hit.sector == 17) hit.sectorScore() else 0
        Slot.S15 -> if (hit.sector == 15) hit.sectorScore() else 0
        Slot.ANY_DOUBLE -> if (hit.ring == Ring.DOUBLE) hit.sectorScore() else 0
        Slot.ANY_TRIPLE -> if (hit.ring == Ring.TRIPLE) hit.sectorScore() else 0
        Slot.OUTER_BULL -> if (hit.ring == Ring.OUTER_BULL) 25 else 0
        Slot.INNER_BULL -> if (hit.ring == Ring.INNER_BULL) 50 else 0
    }

    /** 把「本轮目标」写进双方席位，供对局页的小靶与文案直接读（不各自重算槽位）。 */
    private fun withSlotTarget(state: BattleState, roundNo: Int): BattleState {
        val slot = slotOfRound(roundNo)
        val sector = targetSectorOf(slot)
        return state.copy(players = state.players.map { it.copy(targetSector = sector) })
    }
}
