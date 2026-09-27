package com.dartvio.app.data.achievement

import android.content.Context
import android.content.SharedPreferences

/**
 * 成就解锁状态的本地持久化（独立 prefs：`dartvio_achievements`）。
 *
 * **只存状态、不存进度** —— 进度每次由 `AchievementCalculator` 现算，
 * 这样口径调整（改阈值、改公式）不需要任何数据迁移，也不会把用户坑在旧口径上。
 *
 * 与练习 `dartvio_practice` 分文件存放的理由：清空练习记录 ≠ 清空成就，
 * 两者生命周期不同，混在一起会让「清除本地数据」的语义变得说不清。
 *
 * **依赖倒置（③期收口）**：所有方法接受 [SharedPreferences] 而非 [Context]。
 * `SharedPreferences` 是接口、可在纯 JVM 测试里自行实现；而 `Context` 在 unit test 中
 * 只有 `android.jar` 的 stub（一律抛异常），必须引入 Robolectric 才能测。
 * 本类只被 [AchievementRepository] 使用、`PREFS_NAME` 也无外部引用，改造成本极低，
 * 换来的是 `data/` 层的持久化契约第一次变得可测（此前只有 `domain/` 纯逻辑有测试）。
 * 生产代码用 [prefs] 这个薄壳取句柄，与 `PracticePrefsStore.readCheckIn` 的既有模式一致。
 *
 * 键空间分四类，互不干扰（[unlockedMap] 靠前缀过滤，测试已钉死）：
 * - `unlocked_<成就ID>` → 解锁时刻
 * - `viewed_ids` → 已查看的成就 ID 集合（红点判定，决策④）
 * - `backfill_done` → 是否已完成历史追溯补算（决策③）
 * - `backfill_announce_count` → 待提示的补算数量（决策③，消费式）
 */
object AchievementStore {

    const val PREFS_NAME = "dartvio_achievements"

    private const val PREFIX_UNLOCKED = "unlocked_"
    private const val KEY_BACKFILL_DONE = "backfill_done"
    private const val KEY_VIEWED_IDS = "viewed_ids"
    private const val KEY_BACKFILL_ANNOUNCE = "backfill_announce_count"

    /** 取本模块的 prefs 句柄（框架按 name 缓存，重复调用拿到同一实例）。 */
    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------------- 已解锁 ----------------

    /** 全部已解锁记录：成就 ID → 解锁时刻（epoch millis）。 */
    fun unlockedMap(prefs: SharedPreferences): Map<String, Long> =
        prefs.all
            .mapNotNull { (key, value) ->
                if (!key.startsWith(PREFIX_UNLOCKED)) {
                    null
                } else {
                    (value as? Long)?.let { key.removePrefix(PREFIX_UNLOCKED) to it }
                }
            }
            .toMap()

    /**
     * 写入本次新解锁的成就。
     *
     * 决策③（历史追溯）：补发历史成就时 [at] 统一传**首次重算时刻**，
     * 不伪造历史时间 —— 用户看到的时间必须是他真正看到成就的时刻。
     *
     * 已存在的不覆盖：成就墙上的「解锁于」必须稳定，不能被后续重算改写成更晚的时间，
     * 否则用户会以为成就"刚刚才解锁"。
     */
    fun saveNewlyUnlocked(prefs: SharedPreferences, ids: Collection<String>, at: Long) {
        if (ids.isEmpty()) return
        val editor = prefs.edit()
        ids.forEach { id ->
            if (!prefs.contains(PREFIX_UNLOCKED + id)) {
                editor.putLong(PREFIX_UNLOCKED + id, at)
            }
        }
        editor.apply()
    }

    // ---------------- 补算标记（决策③） ----------------

    /**
     * 是否已完成一次「历史追溯补算」。
     *
     * 首帧为 false 时，UI 需要提示「已根据你的历史对局补算成就」（决策③），
     * 让老用户明白自己不是从零开始。
     */
    fun isBackfilled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_BACKFILL_DONE, false)

    fun markBackfilled(prefs: SharedPreferences) {
        prefs.edit().putBoolean(KEY_BACKFILL_DONE, true).apply()
    }

    // ---------------- 查看态 / 红点（决策④） ----------------

    /** 用户已查看过的成就 ID 集合。 */
    fun viewedIds(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_VIEWED_IDS, emptySet())?.toSet().orEmpty()

    /**
     * 把这些成就标记为「已查看」。
     *
     * **为什么记集合而不是时间戳**：时间戳方案下判定是 `unlockedAt > viewedAt`，
     * 一旦用户把系统时间调前，新解锁的 `unlockedAt` 就会小于"上次查看时刻"，
     * 红点从此不亮 —— 而且会一直坏到系统时间追上来为止。
     * 集合方案只做差集运算，与时钟完全无关。
     *
     * 采用**合并**（而非覆盖）语义：传入空集合时不会误清已有记录，
     * 调用方因此不必保证「传进来的就是全集」。
     */
    fun markViewed(prefs: SharedPreferences, ids: Collection<String>) {
        if (ids.isEmpty()) return
        // getStringSet 返回的集合不得直接改动（Android 文档约束），先复制
        val merged = viewedIds(prefs).toMutableSet()
        merged.addAll(ids)
        prefs.edit().putStringSet(KEY_VIEWED_IDS, merged).apply()
    }

    /** 有多少项解锁是用户还没查看过的（= 「我的」Tab 红点是否点亮的依据）。 */
    fun unseenCount(prefs: SharedPreferences): Int {
        val viewed = viewedIds(prefs)
        return unlockedMap(prefs).count { (id, _) -> id !in viewed }
    }

    // ---------------- 补算提示（决策③） ----------------

    fun writeBackfillAnnouncement(prefs: SharedPreferences, count: Int) {
        prefs.edit().putInt(KEY_BACKFILL_ANNOUNCE, count).apply()
    }

    /**
     * 取出并清空待提示的补算数量。
     *
     * **无条件清理**（哪怕读到 0）：语义是"消费"，读一次即作废，
     * 这既保证提示只出现一次，也不会在 prefs 里留下无意义的残留键。
     */
    fun consumeBackfillAnnouncement(prefs: SharedPreferences): Int {
        val pending = prefs.getInt(KEY_BACKFILL_ANNOUNCE, 0)
        prefs.edit().remove(KEY_BACKFILL_ANNOUNCE).apply()
        return pending
    }

    // ---------------- 清空 ----------------

    /** 清空全部成就状态（设置页「清除本地数据」用）。 */
    fun clear(prefs: SharedPreferences) {
        prefs.edit().clear().apply()
    }
}
