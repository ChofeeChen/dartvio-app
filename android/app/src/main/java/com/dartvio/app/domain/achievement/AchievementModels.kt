package com.dartvio.app.domain.achievement

import com.dartvio.app.data.local.dao.MatchWithPlayers

/**
 * 本地成就系统（第③期 ③A）。
 *
 * 三条设计原则：
 * 1. **只算不存进度**：除「已解锁时刻」外不落任何派生指标，进度每次现算 ——
 *    与 [com.dartvio.app.domain.stats.StatsCalculator] / Room「只落原始事实」的思路一致，
 *    口径调整时无需数据迁移。
 * 2. **判定全部本地可算**：数据源只有两处 —— Room 的 `match_records` / `match_players`
 *    与练习 `SharedPreferences("dartvio_practice")`，不依赖后端。
 * 3. **数据源分层（M9 §7）**：`isFormal` = S 级（多局 + 全真人）；其余对局 = A 级；
 *    练习 = B 级。分层通过每项的 [AchievementDefinition.dataSource] 以徽标形式标注。
 *
 * 分组（[AchievementGroup]）刻意使用用户语言，不把上面的数据治理维度暴露给用户。
 */

/** 成就分组（UI 分组，用户语言）。 */
enum class AchievementGroup(val label: String) {
    /** 里程碑：入门层，首次成功体验。 */
    MILESTONE("里程碑"),

    /** 神枪手：X01 成长曲线。 */
    X01("神枪手"),

    /** Cricket：Mark 率 / MPR / 关区。 */
    CRICKET("Cricket"),

    /** 练习室：勤奋项与 AI 挑战。 */
    PRACTICE("练习室"),
}

/**
 * 数据源徽标 —— 成就判定所依据数据的可信度（M9 §7）。
 *
 * 决策②（2026-09-11）：入门层成就放宽到「任意对局」，因此徽标不写死 `含 AI`，
 * 而写 `任意对局` —— 它同时覆盖免费休闲局与含 AI 的对局，标注不夸大可信度。
 */
enum class AchievementDataSource(val label: String) {
    /** S 级：多局模式 + 全真人（`MatchRecordEntity.isFormal == true`）。 */
    FORMAL("正式赛"),

    /** A 级：休闲模式或含 AI 对战。 */
    CASUAL("任意对局"),

    /** B 级：练习模式本地数据（M11）。 */
    PRACTICE("练习"),
}

/** 交付优先级：P0 = 本期必交付；P1 = 深化项。 */
enum class AchievementPriority { P0, P1 }

/**
 * 进度取值口径。
 *
 * 一律用 Int 表达，达成语义固定为 `metric >= target`；
 * 小数指标按各自 KDoc 说明放大后**向下取整**（不四舍五入），
 * 避免「差一点却算达成」的虚高解锁。
 */
enum class AchievementMetric {
    /** 任意玩法（X01 + Cricket）的对局数，含 AI 与休闲。 */
    MATCH_COUNT,

    /** 任意玩法的胜场数。 */
    WIN_COUNT,

    /** X01 胜场数。 */
    X01_WIN_COUNT,

    /** X01 累计 180 次数。 */
    X01_TOTAL_180,

    /** X01 单局最高收尾分。 */
    HIGHEST_CHECKOUT,

    /** X01 全部对局 PPR（含 AI），向下取整。 */
    X01_PPR,

    /** 正式赛最长连胜（仅 S 级；口径同 M9 §8.1 longestWinStreak）。 */
    FORMAL_WIN_STREAK,

    /** Cricket 关满全部 7 分区的局数。 */
    CRICKET_CLOSED_ALL_LEGS,

    /** Cricket 三倍区命中率 × 100（百分数），向下取整。 */
    CRICKET_TRIPLE_RATE_PCT,

    /** Cricket Mark 率 × 10（即 2.0 Mark/镖 → 20），向下取整。 */
    CRICKET_MARK_RATE_X10,

    /** Cricket MPR 挑战历史最佳 MPR × 10（即 4.00 → 40），向下取整。 */
    CRICKET_BEST_MPR_X10,

    /** 击败「专业」难度 AI 的胜场数。 */
    AI_PRO_WIN_COUNT,

    /** 累计练习次数（四种练习合计）。 */
    PRACTICE_SESSIONS,

    /** 累计练习天数（自然日）。 */
    PRACTICE_DAYS_TOTAL,

    /** 练习最长连续天数（历史最高纪录，永不下调）。 */
    PRACTICE_STREAK_BEST,

    /** 99 Darts 完整打完 99 镖的次数。 */
    NINETY_NINE_COMPLETED,

    /** Count Up 历史最佳总分。 */
    COUNT_UP_BEST_SCORE,
}

/** 单条成就的定义（不可变常量，见 [AchievementCatalog]）。 */
data class AchievementDefinition(
    /** 稳定 ID，落盘用；一旦发布不得改名（改名等于重置用户解锁状态）。 */
    val id: String,
    val group: AchievementGroup,
    val title: String,
    val description: String,
    val metric: AchievementMetric,
    /** 达成阈值，语义为 `metric >= target`。 */
    val target: Int,
    /** 进度单位（展示用）。 */
    val unit: String,
    val dataSource: AchievementDataSource,
    val priority: AchievementPriority,
)

/** 成就计算的输入：对局事实 + 练习事实。 */
data class AchievementInput(
    val matches: List<MatchWithPlayers> = emptyList(),
    val practice: PracticeFacts = PracticeFacts(),
)

/** 单条成就的当前进度。 */
data class AchievementProgress(
    val definition: AchievementDefinition,
    /** 当前进度值（该成就的口径，见 [AchievementMetric]）。 */
    val current: Int,
    val unlocked: Boolean,
    /** 解锁时刻；null = 未解锁。 */
    val unlockedAt: Long?,
) {
    val id: String get() = definition.id
    val target: Int get() = definition.target

    /** 达成比例 0..1，用于进度条与「距离达成最近」排序。 */
    val ratio: Float
        get() = if (target <= 0) 0f else (current.toFloat() / target).coerceIn(0f, 1f)

    val remaining: Int get() = (target - current).coerceAtLeast(0)

    /** 展示用进度文本，如 `7/10 次`；已解锁返回空串。 */
    val progressText: String
        get() = if (unlocked) "" else "$current/$target ${definition.unit}".trim()
}

/** 一次成就重算的完整结果。 */
data class AchievementSnapshot(
    val items: List<AchievementProgress> = emptyList(),
    /** 本次重算后仍未落库的新解锁 ID（按 [AchievementCatalog] 顺序）。 */
    val newlyUnlockedIds: List<String> = emptyList(),
    /**
     * 本次快照是否含**历史追溯补算**（决策③）。
     * 首帧为 true 时，UI 应提示「已根据你的历史对局补算成就」。
     */
    val backfilled: Boolean = false,
) {
    val total: Int get() = items.size
    val unlockedCount: Int get() = items.count { it.unlocked }

    fun progressOf(id: String): AchievementProgress? = items.firstOrNull { it.id == id }
}
