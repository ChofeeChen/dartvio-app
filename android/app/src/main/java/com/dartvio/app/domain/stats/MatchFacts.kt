package com.dartvio.app.domain.stats

import com.dartvio.app.domain.model.CricketTarget

/**
 * 一场比赛中单个玩家的**原始事实累计**。
 *
 * 只在「回合结算」时累加，只在「开局 / 进入下一局」时重置 —— 因此撤销（undoLastDart）
 * 天然安全：撤销只影响尚未结算的当前回合，不会污染已累计的事实。
 *
 * 这里刻意只存事实、不存指标：PPR / Mark率 等一律到
 * [StatsCalculator] 里按 M9 §8 口径现算，口径调整时无需数据迁移。
 */
data class PlayerMatchFacts(
    /** 实际投出镖数（M9 多处以它为分母） */
    val dartsThrown: Int = 0,
    /** 已结算的回合数 */
    val turnsPlayed: Int = 0,
    /** 累计得分 */
    val totalScore: Int = 0,
    /** 单回合得分最大值（C9 / X01 回合最高分） */
    val maxTurnScore: Int = 0,
    /** X01 单回合 180 次数 */
    val count180: Int = 0,
    /** X01 Bust 次数 */
    val busts: Int = 0,
    /** X01 进入「可一回合收镖」状态的回合数（Checkout率分母） */
    val checkoutAttempts: Int = 0,
    /** X01 成功终结一局的回合得分中的最大值（最高收尾） */
    val bestCheckout: Int = 0,

    // ---- Cricket ----
    /** ⚠️ 逐镖累加的获得 Mark 数（每镖 0–3），不得由封顶 3 的 marks 求和 —— M9 §8.3.1 C1 */
    val marksTotal: Int = 0,
    /** 命中三倍区的镖数（C3 分子，分母 = dartsThrown） */
    val tripleHits: Int = 0,
    /** 命中 Bull（Outer + Inner）的镖数（C4 分子，分母 = dartsThrown） */
    val bullHits: Int = 0,
    /** 关满全部 7 分区的局数（C6 分子） */
    val closedAllSectionLegs: Int = 0,
    /** 上述「关满 7 分区」的局所花回合数合计（C6 分母） */
    val turnsInClosedLegs: Int = 0,
    /** 累计关闭的分区数（C2 分子） */
    val closedSectionsTotal: Int = 0,
    /** 每局首个关闭的目标位（C10）。二期 2A 起用标识而非数字 —— 类别档没有数字。 */
    val firstClosed: List<CricketTarget> = emptyList(),
)

/** 整场累计，key = playerId。 */
data class MatchFacts(
    val byPlayer: Map<String, PlayerMatchFacts> = emptyMap(),
) {
    fun of(playerId: String): PlayerMatchFacts = byPlayer[playerId] ?: PlayerMatchFacts()

    /** 对某个玩家的累计做一次增量更新，其余玩家不变。 */
    fun update(playerId: String, delta: (PlayerMatchFacts) -> PlayerMatchFacts): MatchFacts =
        copy(byPlayer = byPlayer + (playerId to delta(of(playerId))))
}
