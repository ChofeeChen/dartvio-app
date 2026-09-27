package com.dartvio.app.domain.versus

/**
 * 上海争霸（SHANGHAI）· V2（引擎先落地，UI 后置）。
 *
 * 规则（提示词 §5.4）：
 * - 共 7 轮，**第 n 轮打 n 分区**，每人每轮 3 镖；
 * - 命中当前分区的 S=1 / D=2 / T=3，3 镖累加；打错分区 0 分；
 * - **秒杀：同一轮内打出同一分区的 S+D+T → 立即获胜**（`endReason = SHANGHAI`）；
 * - 7 轮无人秒杀 → 总分高者胜；平分加赛 Bull（各 1 镖，离 Bull 近者胜）。
 *
 * 加赛在状态里就是 `playoff = true`（[BattleState.dartsPerRound] 随之变成 1），
 * 不另开一套流程：换手、快照、终局判定全部复用同一条路径，少一处分支就少一处漏判。
 */
object ShanghaiRule : VersusRule {

    const val MODE_KEY = "SHANGHAI"

    /** 常规轮数（第 n 轮打 n 分区，n = 1..7）。 */
    const val ROUNDS = 7

    override val modeKey: String = MODE_KEY

    override fun defaultConfig(): BattleConfig = BattleConfig(modeKey = MODE_KEY)

    override fun newState(config: BattleConfig, playerNames: List<String>): BattleState =
        withRoundTarget(versusBaseState(modeKey, config, playerNames))

    /** 第 n 轮的目标分区（`n` 超出 1..[ROUNDS] 时夹到边界）。 */
    fun sectorOfRound(roundNo: Int): Int = roundNo.coerceIn(1, ROUNDS)

    /** 加赛时只能点 Bull。 */
    override fun inputFilter(state: BattleState): KeyboardLayout =
        if (state.playoff) {
            KeyboardLayout(sectors = emptyList(), rings = emptyList(), bull = true, miss = true)
        } else {
            // 与倍区竞赛同理：**必须允许点错的分区**，否则「打偏」只能被记成 0 分区的假命中。
            KeyboardLayout(
                sectors = (1..20).toList(),
                rings = listOf(Ring.SINGLE, Ring.DOUBLE, Ring.TRIPLE),
                bull = true,
                miss = true,
            )
        }

    override fun targetCaption(state: BattleState): String =
        if (state.playoff) {
            "加赛 Bull · 各 1 镖，离牛心近者胜"
        } else {
            val round = state.roundNo.coerceAtMost(ROUNDS)
            "第 $round/$ROUNDS 轮 · 打 $round 分区 · 同轮 S+D+T 秒杀"
        }

    override fun onDart(state: BattleState, hit: BoardHit): Pair<BattleState, DartEvent?> {
        if (state.finished || state.isRoundComplete) return state to null

        val seat = state.currentPlayerIndex
        if (state.playoff) {
            // 加赛只比「离 Bull 近」，不加分。
            val next = state.withDart(hit)
                .updatePlayer(seat) { it.copy(playoffScore = bullProximity(hit)) }
            return next to null
        }

        val points = pointsOf(hit, sectorOfRound(state.roundNo))
        val next = state.withDart(hit).updatePlayer(seat) { it.copy(score = it.score + points) }
        return next to if (points > 0) DartEvent.Scored(points) else null
    }

    override fun onRoundEnd(state: BattleState): Pair<BattleState, DartEvent?> {
        if (state.finished || !state.isRoundComplete) return state to null
        return if (state.playoff) settlePlayoff(state) else settleRound(state)
    }

    private fun settleRound(state: BattleState): Pair<BattleState, DartEvent?> {
        val seat = state.currentPlayerIndex
        val round = sectorOfRound(state.roundNo)

        // 秒杀：同一轮内打出同一分区的 S + D + T（顺序不限）。
        val shanghai = !state.playoff && isShanghai(state.dartsInRound, round)
        val settled = state.commitRound(
            roundScore = state.roundScoreOf { pointsOf(it, round) },
            targetSnapshot = "第 $round 轮 · 打 $round 分区",
        )
        if (shanghai) {
            return settled.finish(seat, BattleEndReason.SHANGHAI) to DartEvent.Shanghai(round)
        }

        val next = withRoundTarget(settled.passTurn())
        // 双方都投完第 7 轮后（回合号被推进到 8）才结算总分。
        if (next.roundNo <= ROUNDS || next.currentPlayerIndex != 0) return next to null

        val leader = when {
            next.players[0].score > next.players[1].score -> 0
            next.players[1].score > next.players[0].score -> 1
            else -> null
        }
        return if (leader != null && next.players[leader].score > 0) {
            next.finish(leader, BattleEndReason.NORMAL) to DartEvent.Win(leader)
        } else {
            // 0:0 与平分都走加赛：0:0 时「总分高者胜」选不出人，加赛是唯一能收口的办法。
            withRoundTarget(
                next.copy(players = next.players.map { it.copy(playoffScore = null) })
                    .copy(playoff = true)
            ) to null
        }
    }

    private fun settlePlayoff(state: BattleState): Pair<BattleState, DartEvent?> {
        val settled = state.commitRound(state.dartsInRound.sumOf { bullProximity(it) }, "加赛 Bull")
        val next = withRoundTarget(settled.passTurn())
        if (next.currentPlayerIndex != 0) return next to null

        val first = next.players[0].playoffScore ?: 0
        val second = next.players[1].playoffScore ?: 0
        return when {
            first > second -> next.finish(0, BattleEndReason.NORMAL) to DartEvent.Win(0)
            second > first -> next.finish(1, BattleEndReason.NORMAL) to DartEvent.Win(1)
            // 同距（含双方都脱靶）：重开一轮加赛，而不是按席位判胜 —— 席位优势在 darts 里没有依据。
            else -> next.copy(players = next.players.map { it.copy(playoffScore = null) }) to null
        }
    }

    /** 把「本轮目标分区」写进双方席位：对局页的小靶直接读它，不自己重算 `roundNo → 分区`。 */
    private fun withRoundTarget(state: BattleState): BattleState {
        val sector = if (state.playoff) null else sectorOfRound(state.roundNo)
        return state.copy(players = state.players.map { it.copy(targetSector = sector) })
    }

    /** 一镖的分值：只有落在本轮目标分区才计分。 */
    public fun pointsOf(hit: BoardHit, round: Int): Int {
        if (hit.sector != round) return 0
        return when (hit.ring) {
            Ring.TRIPLE -> 3
            Ring.DOUBLE -> 2
            Ring.SINGLE -> 1
            else -> 0
        }
    }

    /** 同一轮内集齐同一分区的 S + D + T。 */
    public fun isShanghai(darts: List<BoardHit>, round: Int): Boolean {
        if (darts.size != VERSUS_DARTS_PER_ROUND) return false
        if (darts.any { it.sector != round }) return false
        return darts.map { it.ring }.toSet() ==
            setOf(Ring.SINGLE, Ring.DOUBLE, Ring.TRIPLE)
    }
}
