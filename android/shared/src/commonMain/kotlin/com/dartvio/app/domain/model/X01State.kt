package com.dartvio.app.domain.model

/** X01 单局的玩家状态。 */
data class X01PlayerState(
    val playerId: String,
    /** 剩余分数。 */
    val remaining: Int,
    /** 已获胜局数（跨 leg 累计，由上层维护）。 */
    val legsWon: Int = 0,
    /** 本局回合数。 */
    val dartsThrown: Int = 0,
    /** 本局 3 镖总得分（用于 PPR 统计）。 */
    val totalScored: Int = 0,
    /** 是否已 Double-In（若开启）。 */
    val hasOpened: Boolean = true,
    /** 上一回合得分（用于卡片上删除线对照展示）。 */
    val lastTurnScore: Int? = null,
    /** 上一回合结算前的剩余分（用于卡片上删除线对照展示）。 */
    val previousRemaining: Int? = null
) {
    /** 平均每回合得分 Points Per Round。 */
    val ppr: Double
        get() = if (dartsThrown == 0) 0.0 else totalScored.toDouble() / (dartsThrown / 3.0)
}

/** 回合结算结果。 */
data class TurnOutcome(
    val playerId: String,
    val darts: List<Dart>,
    val result: TurnResult,
    /** 结算后剩余分。 */
    val remainingAfter: Int,
    /** 本次实际得分（Bust 则为 0）。 */
    val scored: Int,
    /** 是否以合法收尾（或超时终局）结束本局。 */
    val won: Boolean = false,
    /**
     * 是否因打满「最多轮数」而终局（超时终局）。
     *
     * 为 true 时 [playerId] 是**剩余分最低的胜者**，而不是本回合的出手者；
     * [won] 同时为 true，上层沿用同一条「本局结束」回调即可。
     */
    val endedByRoundLimit: Boolean = false,
    /** 提示信息（例如 "BUST"、"GAME SHOT"、"MAX ROUNDS"）。 */
    val message: String? = null
)

/** 一局 X01 的完整状态（可序列化用于持久化/回合重建）。 */
data class X01LegState(
    val config: MatchConfig,
    val players: List<X01PlayerState>,
    val currentPlayerIndex: Int,
    /** 当前回合已投掷的镖。 */
    val currentTurnDarts: List<Dart> = emptyList(),
    /** 当前 leg 编号（从 1 开始）。 */
    val legNumber: Int = 1,
    val isFinished: Boolean = false,
    val winnerIndex: Int? = null,
    /**
     * 已完成的完整轮数：每人各投完一轮记 1（与 Cricket 共用同一个 [MatchConfig.maxRounds]）。
     *
     * 只在「本局打满 [MatchConfig.maxRounds]」这条兜底规则里用得到；
     * 不在卡片上显示，所以它不参与「上一回合」对照那套展示状态。
     */
    val roundsCompleted: Int = 0,
    /** 是否因打满最多轮数而终局（此时 [winnerIndex] 是剩余分最低者）。 */
    val endedByRoundLimit: Boolean = false
) {
    val currentPlayer: X01PlayerState get() = players[currentPlayerIndex]
    val currentRemaining: Int get() = currentPlayer.remaining

    /** 检查分：Double-Out 时最大可安全投掷分。 */
    val checkoutSuggestion: String? get() = null
}
