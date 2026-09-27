package com.dartvio.app.data.achievement

import android.content.Context
import android.content.SharedPreferences
import com.dartvio.app.data.local.MatchStatsFilter
import com.dartvio.app.data.local.dao.MatchRecordDao
import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.domain.achievement.AchievementCalculator
import com.dartvio.app.domain.achievement.AchievementInput
import com.dartvio.app.domain.achievement.AchievementSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 成就读写的唯一入口：把 Room 的对局事实与练习 prefs 事实合成一次重算，
 * 并把「新解锁」落盘。
 *
 * 单一职责边界：
 * - 判定口径 → [AchievementCalculator]（纯逻辑，可单测）
 * - 落盘 → [AchievementStore] / [PracticePrefsStore]
 * - 本类只负责**编排**这两者，并保证「读完即写」的顺序：
 *   先算、再写、最后把写入时刻回填进返回值，UI 拿到的快照不出现
 *   「已解锁但 unlockedAt = null」的中间态。
 */
class AchievementRepository(
    private val dao: MatchRecordDao,
    private val achievementPrefs: SharedPreferences,
    private val practicePrefs: SharedPreferences,
) {

    /**
     * 生产入口：从 [Context] 取两个 prefs 句柄。
     *
     * 主构造器刻意只依赖 [SharedPreferences]（接口，可在纯 JVM 单测里自行实现），
     * 与 [AchievementStore] / [PracticePrefsStore] 的依赖倒置保持一致 ——
     * 否则本类的编排逻辑（落盘时机、补算只算一次、红点不触发写入）全都无法测试，
     * 而这几条恰恰是 ③ 期最容易回归的地方。
     */
    constructor(context: Context, dao: MatchRecordDao) : this(
        dao = dao,
        achievementPrefs = AchievementStore.prefs(context),
        practicePrefs = PracticePrefsStore.prefs(context),
    )

    /**
     * 对局历史变化即触发重算（含首次进入时的历史追溯补算）。
     *
     * 取数走 `observeStatsSource()`（**排除联机**，M5 T10）：联机局无裁判且可能以判负收场，
     * 让它解锁成就等于承认「拔网线」也是一种达成方式。
     */
    fun observeSnapshot(): Flow<AchievementSnapshot> =
        dao.observeStatsSource().map { matches -> refresh(matches) }

    /**
     * 触发式重算：自行从 Room 取一次全量对局事实。
     *
     * 用于「对局落库后」「练习打卡后」这两个触发点 —— 它们都不一定产生新的 Flow 发射
     * （练习打卡根本不碰 Room），所以必须主动拉取一次，不能只依赖 [observeSnapshot]。
     */
    suspend fun recalculate(at: Long = System.currentTimeMillis()): AchievementSnapshot =
        refresh(dao.statsSourceOnce(), at)

    /**
     * 仅在「历史追溯补算尚未完成」时补算一次（决策③），已完成则直接返回 null、不产生任何 IO。
     *
     * 供 App 启动时调用：目的是把老用户的历史成就落盘，让「我的」Tab 红点立刻可用。
     *
     * 之所以要这个开关，而不是每次启动都 [recalculate]：红点数据一旦落盘就持久化了，
     * 后续由对局结算 / 练习结束的触发式重算增量写入即可，没必要每次冷启动都全量读一遍
     * `match_records`（对局数积累后这是一笔纯浪费的 IO）。
     *
     * 同时，**只有这条路径**会登记「待提示的补算数量」：它发生在用户见过任何解锁提示之前，
     * 一次性补出的是升级前就已达成、用户从未被告知的历史成就，因此有资格要求 UI 提示一句。
     * 若改由 [refresh] 登记，则「清除本地数据 → 立刻打一局 → 首次进成就墙」会被误判成补算，
     * 弹出一句「已根据历史对局补算 N 项」—— 而那 N 项里包含他刚打的那场，措辞超前于事实。
     * 数据层无法区分「补算」与「首次真实解锁」，但行为层可以：补算只由本方法触发。
     */
    suspend fun backfillIfNeeded(at: Long = System.currentTimeMillis()): AchievementSnapshot? {
        if (AchievementStore.isBackfilled(achievementPrefs)) return null
        val snapshot = refresh(dao.statsSourceOnce(), at)
        if (snapshot.backfilled) {
            AchievementStore.writeBackfillAnnouncement(
                achievementPrefs,
                snapshot.newlyUnlockedIds.size,
            )
        }
        return snapshot
    }

    /**
     * @param at 解锁时刻。历史追溯补发时统一记为此处传入的时刻（决策③），
     *           不伪造历史时间。
     */
    fun refresh(
        matches: List<MatchWithPlayers>,
        at: Long = System.currentTimeMillis(),
    ): AchievementSnapshot {
        val backfillPending = !AchievementStore.isBackfilled(achievementPrefs)

        // 兜底过滤：判据只有 MatchStatsFilter 一处，取数侧已经用 SQL 排除了联机局，
        // 这里再收一次口 —— 将来谁给它喂了一份未过滤的列表，成就也不会被联机局解锁。
        val counted = matches.filter { MatchStatsFilter.countsForStats(it.match) }

        val snapshot = AchievementCalculator.compute(
            input = AchievementInput(
                matches = counted,
                practice = PracticePrefsStore.read(practicePrefs),
            ),
            unlockedAt = AchievementStore.unlockedMap(achievementPrefs),
        )

        val newlyUnlocked = snapshot.newlyUnlockedIds
        val items = if (newlyUnlocked.isEmpty()) {
            snapshot.items
        } else {
            AchievementStore.saveNewlyUnlocked(achievementPrefs, newlyUnlocked, at)
            snapshot.items.map { progress ->
                if (newlyUnlocked.contains(progress.id)) progress.copy(unlockedAt = at) else progress
            }
        }

        if (backfillPending) {
            // 只标记「已补算过」。补算提示的登记放在 backfillIfNeeded() —— 见那里的注释。
            AchievementStore.markBackfilled(achievementPrefs)
        }

        return AchievementSnapshot(
            items = items,
            newlyUnlockedIds = newlyUnlocked,
            backfilled = backfillPending,
        )
    }

    /**
     * 「我的」Tab 红点：有多少项解锁是用户在成就墙**还没看过**的（决策④）。
     *
     * 纯集合差集，不做任何时间比较 —— 系统时钟被调前或调后都不影响判定。
     * 刻意只读 SharedPreferences、不触发重算：红点会在每次回到一级页面时重算，
     * 必须足够廉价；更重要的是避免「为了画红点而提前落盘解锁状态」，
     * 那会抢走对局结算页 / 练习结果页的「新解锁」宣布机会。
     */
    fun countUnseen(): Int = AchievementStore.unseenCount(achievementPrefs)

    /**
     * 用户已打开过成就墙 → 红点熄灭。
     *
     * 标记范围取 [AchievementStore.unlockedMap] 的**权威快照**，而不是让 UI 传列表进来：
     * 成就墙首帧的 Room 快照可能还没到达（那时 UI 手上是空列表），
     * 若由 UI 决定标记范围就会漏标、红点灭不掉。
     */
    fun markAllViewed() {
        AchievementStore.markViewed(achievementPrefs, AchievementStore.unlockedMap(achievementPrefs).keys)
    }

    /**
     * 取出并清空「待提示的补算数量」（决策③）。
     *
     * 消费式读取：既保证提示只出现一次，也让它与「Root 先补算、还是成就墙先补算」
     * 的执行顺序无关 —— 谁先跑都得先落盘，UI 读到的是最终值。
     */
    fun consumeBackfillAnnouncement(): Int =
        AchievementStore.consumeBackfillAnnouncement(achievementPrefs)

    /** 「清除本地数据」：成就记录与练习记账一起清掉，避免出现孤儿解锁状态。 */
    fun clearAll() {
        AchievementStore.clear(achievementPrefs)
        PracticePrefsStore.clear(practicePrefs)
    }
}
