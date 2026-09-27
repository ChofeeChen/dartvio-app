package com.dartvio.app.domain.rules

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.practice.CheckoutSolver
import com.dartvio.app.domain.model.TurnOutcome
import com.dartvio.app.domain.model.TurnResult
import com.dartvio.app.domain.model.X01LegState
import com.dartvio.app.domain.model.X01PlayerState

/**
 * X01 规则引擎。PRD M2。
 *
 * 核心规则：
 * 1. 每回合最多 3 镖，得分累加，从剩余分中扣除。
 * 2. Bust（爆分）条件：
 *    - 剩余分 < 0（扣分后为负）
 *    - 结束规则要求倍区收尾（双倍出/大师出）时剩余分 == 1
 *    - 剩余分 == 0 但最后一镖不满足结束规则
 *    爆分时本回合得分全部作废，剩余分回滚到回合开始值。
 * 3. 三组规则档位（2026-09-12 由布尔升级，见 [BullMode] / [InMode] / [OutMode]）：
 *    - 牛眼规则：外牛眼 25 分或 50 分（只影响计分，不改 `Dart.score`）
 *    - 开局规则：直入 / 双倍入 / 大师入 —— 未开镖前，不满足条件的镖整支不计
 *    - 结束规则：直出 / 双倍出 / 大师出 —— 最后一镖的区域要求
 * 4. 最多轮数（[MatchConfig.maxRounds]）：打满即终局，剩余分最低者获胜（防拖延兜底）。
 */
object X01Rules {

    /**
     * 结算一个完整回合。
     *
     * @param state 当前 leg 状态（回合开始前的快照）
     * @param darts 本回合投掷的镖（1..3 支）
     * @return 结算结果 + 新状态
     */
    fun applyTurn(state: X01LegState, darts: List<Dart>): Pair<X01LegState, TurnOutcome> {
        require(darts.isNotEmpty()) { "回合至少需要 1 支镖" }
        require(darts.size <= 3) { "回合最多 3 支镖" }

        val playerIndex = state.currentPlayerIndex
        val player = state.players[playerIndex]
        val startRemaining = player.remaining
        val sim = simulateTurn(state, darts)
        val remaining = sim.remaining
        val scored = sim.scored
        val bust = sim.bust
        val won = sim.won
        val opened = sim.opened

        val newPlayerState = player.copy(
            remaining = remaining,
            dartsThrown = player.dartsThrown + darts.size,
            totalScored = player.totalScored + scored,
            hasOpened = opened,
            // 记录本回合得分与结算前剩余分，供卡片做"上一回合"删除线对照。
            lastTurnScore = scored,
            previousRemaining = startRemaining
        )

        val newPlayers = state.players.toMutableList().also { it[playerIndex] = newPlayerState }

        val result = when {
            won -> TurnResult.COMPLETE
            bust -> TurnResult.BUST
            scored == 0 -> TurnResult.NO_SCORE
            else -> TurnResult.COMPLETE
        }

        val nextIndex = if (won) playerIndex else (playerIndex + 1) % state.players.size

        // 轮数只在「本轮最后一位投完、回到首位」时 +1 —— 单位是「每人各投一轮」，
        // 与 Cricket 共用同一个 `MatchConfig.maxRounds`（2026-09-12 合并），
        // 否则「最多轮数」在 4 人局会名不副实。
        val roundsCompleted =
            if (!won && nextIndex == 0) state.roundsCompleted + 1 else state.roundsCompleted

        // 最多轮数（Max Rounds）：打满即终局，不再让任何人继续投。
        val limitWinnerIndex =
            if (!won && state.config.maxRounds > 0 && roundsCompleted >= state.config.maxRounds) {
                lowestRemainingIndex(newPlayers)
            } else {
                null
            }

        val winnerIndex = when {
            won -> playerIndex
            limitWinnerIndex != null -> limitWinnerIndex
            else -> null
        }
        val finished = winnerIndex != null

        val message = when {
            limitWinnerIndex != null -> "MAX ROUNDS"
            won -> "GAME SHOT"
            bust -> "BUST"
            else -> null
        }

        val outcome = TurnOutcome(
            // 超时终局时「本回合的出手者」并不是「本局胜者」，所以这里直接取胜者 ——
            // 上层（GameViewModel）拿 playerId 记本局归属，不必再判一次规则。
            playerId = if (limitWinnerIndex != null) {
                newPlayers[limitWinnerIndex].playerId
            } else {
                player.playerId
            },
            darts = darts,
            result = result,
            remainingAfter = newPlayerState.remaining,
            scored = scored,
            won = finished,
            endedByRoundLimit = limitWinnerIndex != null,
            message = message
        )

        val newState = state.copy(
            players = newPlayers,
            currentPlayerIndex = if (finished) winnerIndex ?: nextIndex else nextIndex,
            currentTurnDarts = emptyList(),
            isFinished = finished,
            winnerIndex = winnerIndex,
            roundsCompleted = roundsCompleted,
            endedByRoundLimit = limitWinnerIndex != null
        )

        return newState to outcome
    }

    /**
     * 打满「最多轮数」（[MatchConfig.maxRounds]）时的胜者：**剩余分最低者**。
     *
     * X01 里剩余分越低越接近胜利，所以「分数更低者获胜」= 取剩余分最小的一席。
     * 并列最低时取**席序靠前者**：这条规则本身就是「防止无限拖延」的兜底，
     * 为了并列再开加赛等于把刚设的上限又绕开了 —— 宁可给一个确定的胜者。
     */
    private fun lowestRemainingIndex(players: List<X01PlayerState>): Int =
        players.indices.minByOrNull { players[it].remaining } ?: 0

    /**
     * 预览当前回合已录入镖对应的剩余分。
     *
     * 用于"每投完一镖立即刷新玩家卡片分数"：回合尚未提交时，卡片按本回合已录镖
     * 实时推算剩余分，而不是等三镖全部确认后才变化。
     *
     * 规则与 [applyTurn] 完全一致（双倍入 / 大师入未开镖时不计分；爆分回滚到回合开始值）。
     */
    fun previewRemaining(state: X01LegState, darts: List<Dart>): Int {
        if (darts.isEmpty()) return state.currentPlayer.remaining
        return simulateTurn(state, darts).remaining
    }

    /** 回合试算结果：结算与"录入中"实时预览共用同一套规则。 */
    private data class TurnSimulation(
        val remaining: Int,
        val scored: Int,
        val bust: Boolean,
        val won: Boolean,
        val opened: Boolean
    )

    /** 按规则逐镖试算一个回合（不修改状态）。 */
    private fun simulateTurn(state: X01LegState, darts: List<Dart>): TurnSimulation {
        val config = state.config
        val player = state.currentPlayer
        val startRemaining = player.remaining

        var remaining = startRemaining
        var scored = 0
        var bust = false
        var won = false
        var opened = player.hasOpened

        for ((i, dart) in darts.withIndex()) {
            // 开局规则（In Mode）：未开镖前，不满足开镖条件的镖**整支不计** ——
            // 分数不减、也不算「已开局」。直入时 opened 一开始就是 true，这段不会触发。
            if (!opened) {
                if (config.inMode.opens(dart)) {
                    opened = true
                } else {
                    continue
                }
            }

            // 牛眼规则（Bull Mode）：外牛眼算 25 还是 50 分。
            // 只在本函数里换算，不动 Dart.score —— 那是 Cricket 与统计共用的原始口径。
            val dartScore = config.bullMode.scoreOf(dart)
            val before = remaining
            val after = before - dartScore

            // 爆分判定：结束规则要求倍区收尾时，剩 1 分是死局（D1=2、T1=3 都收不掉）。
            val isBust = after < 0 ||
                (config.outMode.requiresMultiplierFinish && after == 1) ||
                (after == 0 && !config.outMode.finishes(dart))

            if (isBust) {
                bust = true
                remaining = startRemaining
                scored = 0
                opened = player.hasOpened
                break
            }

            remaining = after
            scored += dartScore

            // 胜利判定：剩余 0 且最后一镖符合结束规则。
            // 「剩余 0 但不符合结束规则」在上面已判为爆分，走到这里必然成立。
            if (remaining == 0) {
                won = true
                break
            }

            if (i == darts.lastIndex) break
        }

        return TurnSimulation(remaining, scored, bust, won, opened)
    }

    /** 校验单支镖是否合法。 */
    fun isValidDart(dart: Dart): Boolean = when {
        dart.isMiss -> true
        dart.number == 25 -> dart.multiplier in 1..2
        dart.number in 1..20 -> dart.multiplier in 1..3
        else -> false
    }

    /** 计算建议收尾路径（简化版：仅提示是否 bobbs/可能收分）。 */
    fun checkoutHint(remaining: Int, doubleOut: Boolean): String? {
        if (!doubleOut) return null
        if (remaining <= 40 && remaining % 2 == 0) {
            return "D${remaining / 2}"
        }
        if (remaining == 50) return "BULL"
        return null
    }

    // ---------------------------------------------------------------------------------
    // 结镖路线的**统一事实出口**（极速结镖 / 结镖训练依赖）
    // ---------------------------------------------------------------------------------

    /**
     * 给定剩余分，返回**具体可按镖序执行**的结镖路线（每条 `List<Dart>` 有序，1..3 镖）。
     *
     * 这里是 M2 侧的对外出口：上层（M11 选题、UI 提示）**一律从这里取路线事实**，
     * 不得再各自维护一份「能不能结镖」的分数表。
     *
     * 事实计算仍由 [CheckoutSolver] 承担 —— 它是当前工程里唯一的双结路线预计算实现，
     * 换到这里等于复制一份规则；因此这里是**委派**而不是重写（包结构的反向依赖已记录，
     * 待它迁到 `domain.rules` 后即可去掉跨层导入）。
     *
     * @return 按「镖数少 → 多、最高单镖分降序」排列的路线；**空列表 = 该剩余分没有合法结镖路线**
     *         （Double Out 下的奇数 1、以及 >170 的分数都会得到空列表）。
     */
    fun checkoutRoutes(remaining: Int): List<List<Dart>> = CheckoutSolver.routesFor(remaining)

    /**
     * 该剩余分是否存在合法结镖路线 —— 命题关系正宗生成器的唯一判据。
     *
     * ⚠️ 与未来「快速总分」模式的 `isCheckoutReachable()` **不是一回事**：那个看的是
     * 「三镖总分是否可能」，这里看的是「有没有具体镖序」。极速结镖只走这里。
     */
    fun hasCheckoutRoute(remaining: Int): Boolean = checkoutRoutes(remaining).isNotEmpty()

    /** 首选路线（最短、最高分优先）的可读文本；没有路线时返回 null。 */
    fun checkoutSummary(remaining: Int): String? =
        checkoutRoutes(remaining).firstOrNull()?.let { CheckoutSolver.formatRoute(it) }

    /** 初始化一局的状态。 */
    fun newLeg(config: MatchConfig, players: List<com.dartvio.app.domain.model.Player>, legNumber: Int): X01LegState {
        return X01LegState(
            config = config,
            players = players.map { p ->
                X01PlayerState(
                    playerId = p.id,
                    remaining = config.targetScore,
                    hasOpened = config.inMode.opensInitially
                )
            },
            currentPlayerIndex = 0,
            legNumber = legNumber
        )
    }
}
