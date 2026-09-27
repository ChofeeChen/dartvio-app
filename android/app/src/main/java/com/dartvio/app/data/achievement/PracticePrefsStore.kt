package com.dartvio.app.data.achievement

import android.content.Context
import android.content.SharedPreferences
import com.dartvio.app.domain.achievement.LocalDateKey
import com.dartvio.app.domain.achievement.PracticeCheckIn
import com.dartvio.app.domain.achievement.PracticeFacts

/**
 * 练习侧本地存储（`SharedPreferences("dartvio_practice")`）。
 *
 * 两类键：
 * - **既有键（只读）**：练习最佳成绩与次数由各练习 ViewModel 写入（M11 as-built），
 *   本类只读它们用于成就判定。字符串字面量刻意与各 ViewModel 保持一致
 *   —— M11 的 prefs 名与键目前散落在 4 个 ViewModel 中，本类沿用同一约定，
 *   不做集中化重构（约束①：不重构已有命名与架构）。
 * - **新增键（读写）**：成就系统引入的「练习次数累计 / 自然日打卡 / 99 完成次数」。
 */
object PracticePrefsStore {

    const val PREFS_NAME = "dartvio_practice"

    // ---- 既有键（只读，与练习 ViewModel 中的键名一致）----
    private const val KEY_COUNT_UP_BEST = "countup_best_score"
    private const val KEY_MPR_BEST_X100 = "cricket_mpr_best_x100"
    private const val KEY_MPR_SESSIONS = "cricket_mpr_sessions"

    // ---- 成就系统新增键 ----
    const val KEY_SESSIONS = "practice_sessions"
    const val KEY_DAYS_TOTAL = "practice_days_total"
    const val KEY_STREAK_CURRENT = "practice_streak_current"
    const val KEY_STREAK_BEST = "practice_streak_best"
    const val KEY_LAST_DATE = "practice_last_date"
    const val KEY_NINETY_NINE_COMPLETED = "ninety_nine_completed_sessions"

    /** 取本模块的 prefs 句柄（框架按 name 缓存，重复调用拿到同一实例）。 */
    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读取练习事实（成就判定的 B 级数据源）。 */
    fun read(context: Context): PracticeFacts = read(prefs(context))

    /**
     * 核心实现：只依赖 [SharedPreferences] 这个接口，便于在纯 JVM 单测中覆盖
     * （与 `AchievementStore` 的依赖倒置同理，见那里的说明）。
     */
    fun read(prefs: SharedPreferences): PracticeFacts {
        return PracticeFacts(
            sessions = prefs.getInt(KEY_SESSIONS, 0),
            daysTotal = prefs.getInt(KEY_DAYS_TOTAL, 0),
            streakCurrent = prefs.getInt(KEY_STREAK_CURRENT, 0),
            streakBest = prefs.getInt(KEY_STREAK_BEST, 0),
            bestMpr = prefs.getInt(KEY_MPR_BEST_X100, 0) / 100f,
            mprSessions = prefs.getInt(KEY_MPR_SESSIONS, 0),
            ninetyNineCompleted = prefs.getInt(KEY_NINETY_NINE_COMPLETED, 0),
            countUpBestScore = prefs.getInt(KEY_COUNT_UP_BEST, 0),
        )
    }

    fun readCheckIn(context: Context): PracticeCheckIn =
        readCheckIn(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    /**
     * 记录一次练习打卡（决策⑤：自然日口径）。
     *
     * 练习次数 +1；按自然日推进连续天数（同日重复不重复计数）；
     * [ninetyNineCompleted] 仅在 99 Darts 打满 99 镖时传 true。
     *
     * @return 写入后的最新练习事实，供调用方直接用于重算成就
     */
    fun recordSession(
        context: Context,
        atMillis: Long,
        ninetyNineCompleted: Boolean = false,
    ): PracticeFacts {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val checkIn = readCheckIn(prefs).afterPractice(
            todayKey = LocalDateKey.of(atMillis),
            yesterdayKey = LocalDateKey.daysBefore(atMillis, 1),
        )

        val editor = prefs.edit()
        editor.putInt(KEY_SESSIONS, prefs.getInt(KEY_SESSIONS, 0) + 1)
        editor.putInt(KEY_DAYS_TOTAL, checkIn.daysTotal)
        editor.putInt(KEY_STREAK_CURRENT, checkIn.streakCurrent)
        editor.putInt(KEY_STREAK_BEST, checkIn.streakBest)
        editor.putString(KEY_LAST_DATE, checkIn.lastDateKey)
        if (ninetyNineCompleted) {
            editor.putInt(
                KEY_NINETY_NINE_COMPLETED,
                prefs.getInt(KEY_NINETY_NINE_COMPLETED, 0) + 1,
            )
        }
        editor.apply()

        return read(context)
    }

    /** 清空全部练习数据（含既有最佳成绩与打卡记录）。 */
    fun clear(context: Context) = clear(prefs(context))

    fun clear(prefs: SharedPreferences) {
        prefs.edit().clear().apply()
    }

    private fun readCheckIn(prefs: SharedPreferences): PracticeCheckIn =
        PracticeCheckIn(
            lastDateKey = prefs.getString(KEY_LAST_DATE, "").orEmpty(),
            streakCurrent = prefs.getInt(KEY_STREAK_CURRENT, 0),
            streakBest = prefs.getInt(KEY_STREAK_BEST, 0),
            daysTotal = prefs.getInt(KEY_DAYS_TOTAL, 0),
        )
}
