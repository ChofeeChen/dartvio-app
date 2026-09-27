package com.dartvio.app.domain.stats

/**
 * **练习口径**的本机汇总（数据页「练习」分类）。
 *
 * ## 为什么单独一个模型，而不是混进 [StatsSnapshot]
 *
 * 练习与对局在存储层就是两套东西：对局写 `match_records`（有比分、有 PPR、有胜负），
 * 练习各写各的（99 Darts 的最佳分在 SP、极速结镖在 `checkout_rush_attempts`、
 * 对抗练习在 `versus_*`、落点诊断在 `dart_hits`），**没有一张表能算出 PPR**。
 *
 * 此前数据页把「本地训练」标成「含练习落库的对局」，而练习根本不写那张表 ——
 * 于是打了一晚上练习的人打开数据页看到一片空白（2026-09-27 反馈）。
 * 与其把练习硬塞进 PPR 的分母（那会造出一个假指标），不如如实分成两个口径：
 * 一边是「打完整局」的成绩，一边是「练了多少、练到什么水平」。
 *
 * ## 口径纪律
 *
 * 这里**只汇总本机已经存在的数据**，不新增任何采集点：所有数字都能在
 * `dartvio.db` 或 `dartvio_practice` 里找到出处，因此它不涉及任何新的个人信息。
 */
data class PracticeSummary(
    /** 练习打卡次数（任意练习结束一次 +1）。 */
    val sessions: Int = 0,
    /** 有练习记录的自然日数。 */
    val daysTotal: Int = 0,
    /** 当前连续练习天数。 */
    val streakCurrent: Int = 0,
    /** 最长连续练习天数。 */
    val streakBest: Int = 0,
    /** 99 Darts 打满 99 镖的次数。 */
    val ninetyNineCompleted: Int = 0,
    /** 99 Darts 全分区最高分。 */
    val ninetyNineBestScore: Int = 0,
    /** [ninetyNineBestScore] 出自哪个分区（0 = 还没有成绩）。 */
    val ninetyNineBestSector: Int = 0,
    /** Count Up 最高分。 */
    val countUpBestScore: Int = 0,
    /** Cricket MPR 个人最佳。 */
    val mprBest: Float = 0f,
    /** Cricket MPR 练习场次。 */
    val mprSessions: Int = 0,
    /** 极速结镖已判题数（不含跳过 / 中止）。 */
    val rushAttempts: Int = 0,
    /** 极速结镖成功结镖数。 */
    val rushCheckouts: Int = 0,
    /** 对抗练习已结束场次。 */
    val versusFinished: Int = 0,
    /** 对抗练习胜场（本机席位为 0 号）。 */
    val versusWins: Int = 0,
    /** 精准工坊 / 落点诊断累计录镖数（不含对局镖）。 */
    val impactDarts: Int = 0,
) {

    /** 极速结镖成功率；没打过就不显示 0%，由 UI 侧按 [hasRush] 判断。 */
    val rushHitRate: Double
        get() = rate(rushCheckouts, rushAttempts)

    /** 对抗练习胜率（只算已结束且有胜者的场次）。 */
    val versusWinRate: Double
        get() = rate(versusWins, versusFinished)

    val hasAny: Boolean
        get() = sessions > 0 || ninetyNineBestScore > 0 || countUpBestScore > 0 ||
            mprSessions > 0 || rushAttempts > 0 || versusFinished > 0 || impactDarts > 0

    val hasRush: Boolean get() = rushAttempts > 0
    val hasVersus: Boolean get() = versusFinished > 0

    private fun rate(part: Int, total: Int): Double =
        if (total <= 0) 0.0 else part.toDouble() / total.toDouble()
}
