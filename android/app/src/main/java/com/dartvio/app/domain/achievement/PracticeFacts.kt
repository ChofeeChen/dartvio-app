package com.dartvio.app.domain.achievement

import com.dartvio.app.platform.CivilDateTime
import com.dartvio.app.platform.PlatformTime

/**
 * 练习侧事实（B 级数据源，来自 M11 的 `SharedPreferences("dartvio_practice")`）。
 *
 * 与 Room 一样**只承载事实**，不承载判定结论。
 */
data class PracticeFacts(
    /** 累计练习次数（四种练习合计）。 */
    val sessions: Int = 0,
    /** 累计练习天数（自然日）。 */
    val daysTotal: Int = 0,
    /** 当前连续练习天数。 */
    val streakCurrent: Int = 0,
    /** 历史最长连续练习天数（永不下调）。 */
    val streakBest: Int = 0,
    /** Cricket MPR 挑战历史最佳 MPR。 */
    val bestMpr: Float = 0f,
    /** MPR 挑战累计练习次数。 */
    val mprSessions: Int = 0,
    /** 99 Darts 完整打完 99 镖的次数。 */
    val ninetyNineCompleted: Int = 0,
    /** Count Up 历史最佳总分。 */
    val countUpBestScore: Int = 0,
)

/**
 * 练习打卡记录（决策⑤，2026-09-11）。
 *
 * 判定口径为**自然日**（本地时区），不使用 24 小时滑动窗口 ——
 * 用户的心智是「昨天练过」，滑窗口会在 23:50 与次日 00:10 练习时莫名断签，
 * 那是纯粹的流失。
 *
 * 断签时 [streakCurrent] 重置为 1，但 [streakBest] 永不下调；
 * 对应 UI 文案为「最高纪录 Y 天，今天再来一次即可重连」，而非清空式挫败表达。
 */
data class PracticeCheckIn(
    /** 最近一次练习的自然日键（`yyyy-MM-dd`）；空串 = 从未练习。 */
    val lastDateKey: String = "",
    val streakCurrent: Int = 0,
    val streakBest: Int = 0,
    val daysTotal: Int = 0,
) {
    /**
     * 记录一次练习并返回新记录。
     *
     * @param todayKey 今天的自然日键
     * @param yesterdayKey 昨天的自然日键（由调用方按本地时区算好，便于单测固定时间）
     */
    fun afterPractice(todayKey: String, yesterdayKey: String): PracticeCheckIn = when {
        // 同日重复练习：不重复计数、不打断连续
        todayKey == lastDateKey -> this

        // 连续：昨天练过
        yesterdayKey == lastDateKey -> copy(
            lastDateKey = todayKey,
            streakCurrent = streakCurrent + 1,
            streakBest = maxOf(streakBest, streakCurrent + 1),
            daysTotal = daysTotal + 1,
        )

        // 断签或首日：当前连续重新从 1 开始，历史最高保留
        else -> copy(
            lastDateKey = todayKey,
            streakCurrent = 1,
            streakBest = maxOf(streakBest, 1),
            daysTotal = daysTotal + 1,
        )
    }
}

/**
 * 本地自然日键（`yyyy-MM-dd`）。
 *
 * 刻意**不使用 `java.time`**：本项目 `minSdk = 24`，`java.time` 需 core library desugaring，
 * 而决策⑤要求不引入新依赖、不改构建配置（见 `android/README_DEV.md`）。
 * 随 `domain/` 下沉 KMP 后 `Calendar` 同样不可用，故改用 [CivilDateTime] 的纯 Kotlin 实现 ——
 * 跨平台、跨月、跨年与夏令时均按**日序号**归一，两端同一份代码。
 */
object LocalDateKey {

    fun of(millis: Long): String =
        CivilDateTime.dateKey(millis, PlatformTime.zoneOffsetMillis(millis))

    /** [millis] 之前 [days] 个自然日的键（[days] = 1 即「昨天」）。 */
    fun daysBefore(millis: Long, days: Int): String =
        CivilDateTime.dateKeyDaysBefore(millis, PlatformTime.zoneOffsetMillis(millis), days)
}
