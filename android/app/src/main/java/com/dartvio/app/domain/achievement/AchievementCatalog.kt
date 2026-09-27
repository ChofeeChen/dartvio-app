package com.dartvio.app.domain.achievement

import com.dartvio.app.domain.achievement.AchievementDataSource.CASUAL
import com.dartvio.app.domain.achievement.AchievementDataSource.FORMAL
import com.dartvio.app.domain.achievement.AchievementDataSource.PRACTICE
import com.dartvio.app.domain.achievement.AchievementGroup.CRICKET
import com.dartvio.app.domain.achievement.AchievementGroup.MILESTONE
import com.dartvio.app.domain.achievement.AchievementGroup.PRACTICE as PRACTICE_GROUP
import com.dartvio.app.domain.achievement.AchievementGroup.X01
import com.dartvio.app.domain.achievement.AchievementMetric.AI_PRO_WIN_COUNT
import com.dartvio.app.domain.achievement.AchievementMetric.COUNT_UP_BEST_SCORE
import com.dartvio.app.domain.achievement.AchievementMetric.CRICKET_BEST_MPR_X10
import com.dartvio.app.domain.achievement.AchievementMetric.CRICKET_CLOSED_ALL_LEGS
import com.dartvio.app.domain.achievement.AchievementMetric.CRICKET_MARK_RATE_X10
import com.dartvio.app.domain.achievement.AchievementMetric.CRICKET_TRIPLE_RATE_PCT
import com.dartvio.app.domain.achievement.AchievementMetric.FORMAL_WIN_STREAK
import com.dartvio.app.domain.achievement.AchievementMetric.HIGHEST_CHECKOUT
import com.dartvio.app.domain.achievement.AchievementMetric.MATCH_COUNT
import com.dartvio.app.domain.achievement.AchievementMetric.NINETY_NINE_COMPLETED
import com.dartvio.app.domain.achievement.AchievementMetric.PRACTICE_DAYS_TOTAL
import com.dartvio.app.domain.achievement.AchievementMetric.PRACTICE_SESSIONS
import com.dartvio.app.domain.achievement.AchievementMetric.PRACTICE_STREAK_BEST
import com.dartvio.app.domain.achievement.AchievementMetric.WIN_COUNT
import com.dartvio.app.domain.achievement.AchievementMetric.X01_PPR
import com.dartvio.app.domain.achievement.AchievementMetric.X01_TOTAL_180
import com.dartvio.app.domain.achievement.AchievementMetric.X01_WIN_COUNT
import com.dartvio.app.domain.achievement.AchievementPriority.P0
import com.dartvio.app.domain.achievement.AchievementPriority.P1

/**
 * 成就清单（决策①，2026-09-11）：**共 26 项 = 18 项 P0 + 8 项 P1**。
 *
 * 由 PRD M9 F9.4 的 18 项扩展而来。扩展理由（写入 PRD 时须说明）：
 * 成就是一次开发、长期复用的内容资产，边际成本极低；分批上线会让早期用户
 * 先看到缩水的成就墙，重新发布又要一次唤醒成本。
 *
 * 分组计数：里程碑 6（全 P0）/ 神枪手 8（P0 5 + P1 3）/
 * Cricket 6（P0 4 + P1 2）/ 练习室 6（P0 3 + P1 3）。
 *
 * 门槛口径的总原则（决策②）：**入门层放宽到「任意对局」，排名语义严格要求 S 级**。
 * 连胜类只认 `MatchRecordEntity.isFormal`（多局模式 + 全真人），否则用户拿
 * AI / 休闲局的连胜来解锁，这条成就就失去了说服力。
 *
 * ⚠️ [AchievementDefinition.id] 已作为落盘键的一部分（`unlocked_<id>`），
 * 发布后不得改名，否则等同于重置用户解锁状态。
 */
object AchievementCatalog {

    val ALL: List<AchievementDefinition> = listOf(
        // ===================== 里程碑（6 项，全 P0）=====================
        // 入门层：数据源放宽为「任意对局」（含 AI 与休闲）—— 决策②
        def(
            id = "milestone_first_match",
            group = MILESTONE,
            title = "初登镖盘",
            description = "完成第 1 场对局",
            metric = MATCH_COUNT,
            target = 1,
            unit = "场",
            dataSource = CASUAL,
            priority = P0,
        ),
        def(
            id = "milestone_first_win",
            group = MILESTONE,
            title = "旗开得胜",
            description = "赢下第 1 场对局（可含 AI 对战）",
            metric = WIN_COUNT,
            target = 1,
            unit = "场",
            dataSource = CASUAL,
            priority = P0,
        ),
        def(
            id = "milestone_first_180",
            group = MILESTONE,
            title = "首个 180",
            description = "单回合打出 180 分",
            metric = X01_TOTAL_180,
            target = 1,
            unit = "次",
            dataSource = CASUAL,
            priority = P0,
        ),
        def(
            id = "milestone_checkout_100",
            group = MILESTONE,
            title = "百分收镖",
            description = "单局收尾得分达到 100",
            metric = HIGHEST_CHECKOUT,
            target = 100,
            unit = "分",
            dataSource = CASUAL,
            priority = P0,
        ),
        def(
            id = "milestone_match_10",
            group = MILESTONE,
            title = "十场老兵",
            description = "累计完成 10 场对局",
            metric = MATCH_COUNT,
            target = 10,
            unit = "场",
            dataSource = CASUAL,
            priority = P0,
        ),
        def(
            id = "milestone_practice_first",
            group = MILESTONE,
            title = "练习初体验",
            description = "完成任意 1 次练习",
            metric = PRACTICE_SESSIONS,
            target = 1,
            unit = "次",
            dataSource = PRACTICE,
            priority = P0,
        ),

        // ===================== 神枪手（8 项：P0 5 + P1 3）=====================
        def(
            id = "x01_180_3",
            group = X01,
            title = "180 三连击",
            description = "累计打出 3 次 180",
            metric = X01_TOTAL_180,
            target = 3,
            unit = "次",
            dataSource = CASUAL,
            priority = P0,
        ),
        def(
            id = "x01_checkout_120",
            group = X01,
            title = "远程收镖",
            description = "单局收尾得分达到 120（六镖以内难度）",
            metric = HIGHEST_CHECKOUT,
            target = 120,
            unit = "分",
            dataSource = CASUAL,
            priority = P0,
        ),
        def(
            id = "x01_ppr_60",
            group = X01,
            title = "PPR 60 俱乐部",
            description = "全部对局 PPR 达到 60",
            metric = X01_PPR,
            target = 60,
            unit = "PPR",
            dataSource = CASUAL,
            priority = P0,
        ),
        def(
            id = "x01_streak_3",
            group = X01,
            title = "三连胜",
            description = "正式赛（多局 + 全真人）连胜 3 场",
            metric = FORMAL_WIN_STREAK,
            target = 3,
            unit = "场",
            dataSource = FORMAL,
            priority = P0,
        ),
        def(
            id = "x01_streak_5",
            group = X01,
            title = "五连胜",
            description = "正式赛（多局 + 全真人）连胜 5 场",
            metric = FORMAL_WIN_STREAK,
            target = 5,
            unit = "场",
            dataSource = FORMAL,
            priority = P0,
        ),
        def(
            id = "x01_180_10",
            group = X01,
            title = "180 达人",
            description = "累计打出 10 次 180",
            metric = X01_TOTAL_180,
            target = 10,
            unit = "次",
            dataSource = CASUAL,
            priority = P1,
        ),
        def(
            id = "x01_checkout_170",
            group = X01,
            title = "完美收镖 170",
            description = "单局收尾得分达到 170（最高收镖）",
            metric = HIGHEST_CHECKOUT,
            target = 170,
            unit = "分",
            dataSource = CASUAL,
            priority = P1,
        ),
        def(
            id = "x01_streak_10",
            group = X01,
            title = "十连胜",
            description = "正式赛（多局 + 全真人）连胜 10 场",
            metric = FORMAL_WIN_STREAK,
            target = 10,
            unit = "场",
            dataSource = FORMAL,
            priority = P1,
        ),

        // ===================== Cricket（6 项：P0 4 + P1 2）=====================
        def(
            id = "cricket_close_all",
            group = CRICKET,
            title = "一局清台",
            description = "在一局 Cricket 中关满全部 7 个分区",
            metric = CRICKET_CLOSED_ALL_LEGS,
            target = 1,
            unit = "局",
            dataSource = CASUAL,
            priority = P0,
        ),
        def(
            id = "cricket_triple_30",
            group = CRICKET,
            title = "三倍区猎手",
            description = "Cricket 三倍区命中率达到 30%",
            metric = CRICKET_TRIPLE_RATE_PCT,
            target = 30,
            unit = "%",
            dataSource = CASUAL,
            priority = P0,
        ),
        def(
            id = "cricket_mark_rate_2",
            group = CRICKET,
            title = "Mark 率 2.0",
            description = "Cricket 平均每镖获得 2.0 个 Mark",
            metric = CRICKET_MARK_RATE_X10,
            target = 20,
            unit = "Mark/镖",
            dataSource = CASUAL,
            priority = P0,
        ),
        def(
            id = "cricket_mpr_1",
            group = CRICKET,
            title = "MPR 进阶",
            description = "Cricket MPR 挑战最佳成绩达到 1.00",
            metric = CRICKET_BEST_MPR_X10,
            target = 10,
            unit = "MPR",
            dataSource = PRACTICE,
            priority = P0,
        ),
        def(
            id = "cricket_mpr_2",
            group = CRICKET,
            title = "MPR 熟练",
            description = "Cricket MPR 挑战最佳成绩达到 2.00",
            metric = CRICKET_BEST_MPR_X10,
            target = 20,
            unit = "MPR",
            dataSource = PRACTICE,
            priority = P1,
        ),
        def(
            id = "cricket_mpr_4",
            group = CRICKET,
            title = "MPR 大师",
            description = "Cricket MPR 挑战最佳成绩达到 4.00",
            metric = CRICKET_BEST_MPR_X10,
            target = 40,
            unit = "MPR",
            dataSource = PRACTICE,
            priority = P1,
        ),

        // ===================== 练习室（6 项：P0 3 + P1 3）=====================
        def(
            id = "practice_days_3",
            group = PRACTICE_GROUP,
            title = "勤练三日",
            description = "累计练习达到 3 天",
            metric = PRACTICE_DAYS_TOTAL,
            target = 3,
            unit = "天",
            dataSource = PRACTICE,
            priority = P0,
        ),
        def(
            id = "practice_streak_3",
            group = PRACTICE_GROUP,
            title = "连续三日",
            description = "连续 3 天完成练习（自然日）",
            metric = PRACTICE_STREAK_BEST,
            target = 3,
            unit = "天",
            dataSource = PRACTICE,
            priority = P0,
        ),
        def(
            id = "ninety_nine_complete",
            group = PRACTICE_GROUP,
            title = "99 镖全勤",
            description = "完整打完一次 99 Darts（99 镖）",
            metric = NINETY_NINE_COMPLETED,
            target = 1,
            unit = "次",
            dataSource = PRACTICE,
            priority = P0,
        ),
        def(
            id = "practice_streak_7",
            group = PRACTICE_GROUP,
            title = "连续七日",
            description = "连续 7 天完成练习（自然日）",
            metric = PRACTICE_STREAK_BEST,
            target = 7,
            unit = "天",
            dataSource = PRACTICE,
            priority = P1,
        ),
        def(
            id = "countup_600",
            group = PRACTICE_GROUP,
            title = "Count Up 600",
            description = "Count Up 历史最佳总分达到 600",
            metric = COUNT_UP_BEST_SCORE,
            target = 600,
            unit = "分",
            dataSource = PRACTICE,
            priority = P1,
        ),
        def(
            id = "ai_pro_win",
            group = PRACTICE_GROUP,
            title = "击败专业 AI",
            description = "在与「专业」难度 AI 的对局中获胜",
            metric = AI_PRO_WIN_COUNT,
            target = 1,
            unit = "场",
            dataSource = CASUAL,
            priority = P1,
        ),
    )

    /** 按 ID 取定义；未知名返回 null（防止脏数据导致崩溃）。 */
    fun of(id: String): AchievementDefinition? = ALL.firstOrNull { it.id == id }

    fun countOf(group: AchievementGroup): Int = ALL.count { it.group == group }

    fun countOf(priority: AchievementPriority): Int = ALL.count { it.priority == priority }

    private fun def(
        id: String,
        group: AchievementGroup,
        title: String,
        description: String,
        metric: AchievementMetric,
        target: Int,
        unit: String,
        dataSource: AchievementDataSource,
        priority: AchievementPriority,
    ): AchievementDefinition = AchievementDefinition(
        id = id,
        group = group,
        title = title,
        description = description,
        metric = metric,
        target = target,
        unit = unit,
        dataSource = dataSource,
        priority = priority,
    )
}
