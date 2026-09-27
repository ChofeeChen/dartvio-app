package com.dartvio.app.domain.model

/** 比赛类型。PRD M2：501/301/701/901/1101 为 X01 变体，统一到 targetScore 字段。 */
enum class MatchType {
    X01,
    CRICKET,
    AROUND_THE_CLOCK,
    SHANGHAI,
    HALVE_IT,
    KILLER
}

/** 比赛模式。PRD 决策点2：休闲模式(单局) 与 多局模式(多局定胜负)。 */
enum class MatchMode {
    /** 休闲模式：打完一局即结束，当局有胜负。 */
    CASUAL,

    /** 多局模式：按 legsToWin 先达者胜。 */
    MULTI_LEG
}

/** 玩家类型。 */
enum class PlayerType {
    HUMAN,
    AI,
    REMOTE
}

/**
 * AI 难度（M4 §6.1 四档）。
 *
 * 每档对应：PPR 区间 / 中值、结镖率、180 率、投镖延迟区间。
 * 命中率不在此固化，而由 [com.dartvio.app.domain.rules.AiProfile] 按当前 PPR 现算，
 * 以支持同级自适应（§6.1.1）。
 */
enum class AiDifficulty(
    val displayName: String,
    /** PPR 区间下限。 */
    val pprLow: Int,
    /** PPR 档位中值（自适应基准）。 */
    val pprMid: Int,
    /** PPR 区间上限。 */
    val pprHigh: Int,
    /** 结镖率：处于收尾镖时的命中概率。 */
    val checkoutRate: Double,
    /** 180 率：单回合形成 180 的概率。 */
    val percent180: Double,
    /** 单镖显示延迟下限（毫秒）。 */
    val minDelayMs: Long,
    /** 单镖显示延迟上限（毫秒）。 */
    val maxDelayMs: Long
) {
    BEGINNER("入门", 25, 32, 40, 0.15, 0.02, 1500, 2500),
    INTERMEDIATE("进阶", 40, 47, 55, 0.30, 0.05, 1000, 2000),
    ADVANCED("高手", 55, 62, 70, 0.50, 0.10, 800, 1500),
    PRO("专业", 70, 80, 90, 0.70, 0.20, 500, 1000);

    /** 档位基准 PPR（P0 四档中值）。 */
    val ppr: Int get() = pprMid

    companion object {
        fun fromName(name: String): AiDifficulty =
            entries.firstOrNull { it.name == name } ?: INTERMEDIATE

        /**
         * PPR → 单镖命中理想目标的概率（线性映射：25 → 0.20，90 → 0.80）。
         * 与旧实现（25/45/65/90 四档硬编码 0.20/0.40/0.60/0.80）斜率一致，
         * 但改为连续可调以支持自适应 PPR。
         */
        fun hitChanceFor(ppr: Double): Double =
            (0.20 + (ppr - 25.0) / (90.0 - 25.0) * 0.60).coerceIn(0.0, 1.0)
    }
}

/** 回合结束状态。 */
enum class TurnResult {
    /** 正常结束回合。 */
    COMPLETE,

    /** 爆分：超过剩余分。 */
    BUST,

    /** 未命中有效区（X01 未爆分但未得分）。 */
    NO_SCORE
}

/** 比赛结束原因。 */
enum class MatchEndReason {
    LEGS_REACHED,
    ABANDONED,
    DRAW
}
