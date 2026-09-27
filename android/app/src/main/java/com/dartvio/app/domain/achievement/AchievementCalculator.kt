package com.dartvio.app.domain.achievement

import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.data.local.entity.MatchPlayerEntity
import com.dartvio.app.domain.model.AiDifficulty
import com.dartvio.app.domain.stats.StatsCalculator

/**
 * 成就判定引擎（纯逻辑，无 Android 依赖，可纯 JVM 单测）。
 *
 * **口径复用而非重写**：PPR / 最高收尾 / 180 / 连胜 / Mark 率 / 三倍命中率
 * 一律取自 [StatsCalculator]，与「统计与历史」页保持同一套口径 ——
 * 否则同一个数字在两个页面不一致，是用户最先发现的那类 bug。
 *
 * **解锁状态不回退**：`unlockedAt != null` 即视为已解锁，即使当前进度回落
 * （例如连胜断掉后 longestWinStreak 仍是历史最高，但练习连续天数会回落）。
 */
object AchievementCalculator {

    private const val X01 = "X01"
    private const val CRICKET = "CRICKET"

    /**
     * @param unlockedAt 已落库的解锁时刻（key = 成就 ID），来自 `AchievementStore`。
     */
    fun compute(
        input: AchievementInput,
        unlockedAt: Map<String, Long> = emptyMap(),
    ): AchievementSnapshot {
        val metrics = metricValues(input)
        val newlyUnlocked = mutableListOf<String>()

        val items = AchievementCatalog.ALL.map { def ->
            val current = metrics[def.metric] ?: 0
            val at = unlockedAt[def.id]
            val reached = current >= def.target
            if (at == null && reached) newlyUnlocked += def.id
            AchievementProgress(
                definition = def,
                current = current,
                unlocked = at != null || reached,
                unlockedAt = at,
            )
        }

        return AchievementSnapshot(items = items, newlyUnlockedIds = newlyUnlocked)
    }

    /**
     * 展示排序（决策⑥）：**未解锁项按「距离达成最近」置顶**，已解锁项按解锁时间倒序排其后。
     *
     * 未解锁在前是为了把「下一个目标」推到用户眼前 —— 这是提升回访最便宜的手段。
     */
    fun sortedForDisplay(items: List<AchievementProgress>): List<AchievementProgress> {
        val locked = items.filterNot { it.unlocked }
            .sortedWith(compareByDescending<AchievementProgress> { it.ratio }.thenBy { it.id })
        val unlocked = items.filter { it.unlocked }
            .sortedWith(compareByDescending<AchievementProgress> { it.unlockedAt ?: 0L }.thenBy { it.id })
        return locked + unlocked
    }

    /** 按分组切分，组内秩序同 [sortedForDisplay]；组顺序即 [AchievementGroup] 声明顺序。 */
    fun byGroup(items: List<AchievementProgress>): Map<AchievementGroup, List<AchievementProgress>> =
        AchievementGroup.entries.associateWith { group ->
            sortedForDisplay(items.filter { it.definition.group == group })
        }

    // ------------------------------------------------------------ 指标取值

    private fun metricValues(input: AchievementInput): Map<AchievementMetric, Int> {
        val matches = input.matches
        // 一次算全量：StatsCalculator 内部按 gameType 分派，重复调用无意义
        val stats = StatsCalculator.compute(matches)
        val x01 = stats.x01
        val cricket = stats.cricket
        val practice = input.practice

        return buildMap {
            // ---- 对局（任意玩法）----
            put(AchievementMetric.MATCH_COUNT, x01.matchCount + cricket.matchCount)
            put(AchievementMetric.WIN_COUNT, x01.winCount + cricket.winCount)

            // ---- X01 ----
            put(AchievementMetric.X01_WIN_COUNT, x01.winCount)
            put(AchievementMetric.X01_TOTAL_180, x01.total180)
            put(AchievementMetric.HIGHEST_CHECKOUT, x01.highestCheckout)
            // 向下取整：59.9 不算达到 60，避免虚高解锁
            put(AchievementMetric.X01_PPR, x01.pprAll.toInt())

            // ---- 正式赛（S 级）----
            put(AchievementMetric.FORMAL_WIN_STREAK, x01.longestWinStreak)

            // ---- Cricket ----
            put(AchievementMetric.CRICKET_CLOSED_ALL_LEGS, cricketClosedAllLegs(matches))
            put(AchievementMetric.CRICKET_TRIPLE_RATE_PCT, scaled(cricket.tripleRate, 100))
            put(AchievementMetric.CRICKET_MARK_RATE_X10, scaled(cricket.markRate, 10))

            // ---- 练习（B 级）----
            put(AchievementMetric.CRICKET_BEST_MPR_X10, scaled(practice.bestMpr.toDouble(), 10))
            put(AchievementMetric.PRACTICE_SESSIONS, practice.sessions)
            put(AchievementMetric.PRACTICE_DAYS_TOTAL, practice.daysTotal)
            put(AchievementMetric.PRACTICE_STREAK_BEST, practice.streakBest)
            put(AchievementMetric.NINETY_NINE_COMPLETED, practice.ninetyNineCompleted)
            put(AchievementMetric.COUNT_UP_BEST_SCORE, practice.countUpBestScore)

            // ---- AI 挑战 ----
            put(AchievementMetric.AI_PRO_WIN_COUNT, aiProWinCount(matches))
        }
    }

    /** 个人 = 出手顺序 0 的本机玩家（as-built：玩家 1），与 [StatsCalculator] 口径一致。 */
    private fun selfOf(match: MatchWithPlayers): MatchPlayerEntity? =
        match.players.firstOrNull { it.orderIndex == 0 }

    /**
     * 比例 → 放大后的整数，**向下取整**。
     *
     * 加 `1e-9` 是为了抵消浮点误差（例如 3/10 得到 0.3 再 ×100 可能是 29.999…）：
     * 该 epsilon 远小于「整数样本比例」能达到的最小误差量级，
     * 因此既不会把 29.9% 抬成 30%，也不会把真实的 30% 压成 29%。
     */
    private fun scaled(rate: Double, scale: Int): Int = ((rate * scale) + 1e-9).toInt()

    /**
     * Cricket 关满 7 分区的局数（CricketStats 未承载该字段，需回到原始事实求和）。
     *
     * M9 §7.2：Cricket 组成就仅 standard 变体计入 —— 变体改了得分口径，
     * 拿它去解锁「关区 / Mark 率」这类技术指标会失真。
     * 里程碑组的「对局数合计」（[AchievementMetric.MATCH_COUNT]）不受影响，仍取全量。
     * 三倍率 / Mark 率两项走 [StatsCalculator]，已在那边按准入表切分好。
     */
    private fun cricketClosedAllLegs(matches: List<MatchWithPlayers>): Int =
        matches.sumOf { match ->
            if (!match.match.isCricketStandard) {
                0
            } else {
                selfOf(match)?.closedAllSectionLegs ?: 0
            }
        }

    /** 击败「专业」难度 AI 的场数。 */
    private fun aiProWinCount(matches: List<MatchWithPlayers>): Int =
        matches.count { match ->
            val self = selfOf(match) ?: return@count false
            match.match.containsAi &&
                self.isWinner &&
                match.players.any { it.isAi && it.aiDifficulty == AiDifficulty.PRO.name }
        }
}
