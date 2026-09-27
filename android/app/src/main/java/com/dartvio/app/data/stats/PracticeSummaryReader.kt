package com.dartvio.app.data.stats

import android.content.Context
import com.dartvio.app.data.achievement.PracticePrefsStore
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.data.local.entity.VersusMatchRecordEntity
import com.dartvio.app.domain.stats.PracticeSummary
import com.dartvio.app.ui.practice.NinetyNineStatsStore

/**
 * 把散落在四处的练习数据读成一份 [PracticeSummary]。
 *
 * ## 为什么是「读」而不是「统计」
 *
 * 练习侧从来没有一张统一的表：99 Darts / Count Up 写 SharedPreferences
 * （`dartvio_practice`，各 ViewModel 自己写的键），极速结镖、对抗练习、落点诊断
 * 各写各的表。若为此新建一张 `practice_summary` 汇总表，就会出现第二份真相 ——
 * 「汇总表」与「明细」对不上时，没有谁能说清哪个是对的。
 * 所以这里**只读不写**：每次进入「练习」分类现算一次，数据永远等于明细。
 *
 * ## 失败的口径
 *
 * 任一数据源读不出来（数据库还没建、SP 为空）就按 0 处理，不让页面崩 ——
 * 练习汇总少一项不影响「我今天练没练」这个核心问题。
 */
object PracticeSummaryReader {

    /** 对抗练习只回扫最近这么多场：它的作用是「练了多少」，不是审计。 */
    private const val VERSUS_SCAN_LIMIT = 500

    /** 99 Darts 的分区范围（1–20）。 */
    private val NINETY_NINE_SECTORS = 1..20

    suspend fun read(context: Context, database: DartVioDatabase): PracticeSummary {
        val facts = PracticePrefsStore.read(context)

        var bestScore = 0
        var bestSector = 0
        for (sector in NINETY_NINE_SECTORS) {
            val score = NinetyNineStatsStore.best(context, sector)
            if (score > bestScore) {
                bestScore = score
                bestSector = sector
            }
        }

        val rushAttempts = database.checkoutRushDao().scoredAttemptCount()
        val rushCheckouts = database.checkoutRushDao().checkoutCount()

        val versus = database.versusDao().listFinished(VERSUS_SCAN_LIMIT)
        val impactDarts = database.dartHitDao().countPracticeHits()

        return PracticeSummary(
            sessions = facts.sessions,
            daysTotal = facts.daysTotal,
            streakCurrent = facts.streakCurrent,
            streakBest = facts.streakBest,
            ninetyNineCompleted = facts.ninetyNineCompleted,
            ninetyNineBestScore = bestScore,
            ninetyNineBestSector = bestSector,
            countUpBestScore = facts.countUpBestScore,
            mprBest = facts.bestMpr,
            mprSessions = facts.mprSessions,
            rushAttempts = rushAttempts,
            rushCheckouts = rushCheckouts,
            versusFinished = versus.size,
            versusWins = versus.count { it.winnerIndex == SELF_SEAT && it.isFinished },
            impactDarts = impactDarts,
        )
    }

    /**
     * 本机在对抗练习里的席位：配置页永远是「自己 = 玩家 1」。
     *
     * 为什么不用名字比对：昵称随时可改，改名后历史场次的名字对不上，
     * 胜场会凭空少掉一批 —— 而席位是这个模块自己的约定，与名字无关。
     */
    private const val SELF_SEAT = 0
}
