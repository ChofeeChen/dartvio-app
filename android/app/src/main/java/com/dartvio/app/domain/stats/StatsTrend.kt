package com.dartvio.app.domain.stats

import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.domain.model.MatchType

/** 趋势图上的一个点：**一场**的数据，按时间排。 */
data class TrendPoint(
    /** 这一场结束的毫秒时刻 —— 横轴。 */
    val atMs: Long,
    /** 纵轴的值（X01 = 三镖均）。 */
    val value: Double,
)

/**
 * 逐场趋势：把「每场一个数」按时间排成一条线。
 *
 * ## 为什么用**三镖均**而不是 PPR
 *
 * PPR 在 [StatsCalculator] 里会先按正式赛分级 ——
 * 它描述的是「一段时期的整体水平」，**不是一个可以在单场之间比较的值**；
 * 打一场抽出 PPR 会把某一局的手感读成趋势。三镖均每场自己就能算清楚：
 * 总分 ÷ (镖数 ÷ 3)，用户进步之后它往上走，一目了然。
 *
 * ## 为什么按结束时间排而不是落库顺序
 *
 * 顺序 = 时间，这是趋势图的横轴该有的含义；按 rowid 排会把数据同步、
 * 补录的那几场放错位置。
 */
object StatsTrend {

    /** 最多画多少个点：超过之后老 datapoint 挤在一起，斜率反而看不清。 */
    private const val MAX_POINTS = 20

    /**
     * X01 的逐场三镖均，按结束时升序，最多 [MAX_POINTS] 个。
     *
     * 只取**本机席位**（`orderIndex == 0`，与 [StatsCalculator] 同一约定）；
     * 镖数为 0 的场次拿不到值，直接跳过 —— 补一个 0 会在图上砸出一个假的低谷。
     */
    fun x01ThreeDartAvg(matches: List<MatchWithPlayers>): List<TrendPoint> = matches
        .filter { it.match.gameType == MatchType.X01.name }
        .mapNotNull { match ->
            val self = match.players.firstOrNull { it.orderIndex == 0 } ?: return@mapNotNull null
            val darts = self.dartsThrown
            if (darts <= 0) return@mapNotNull null
            TrendPoint(atMs = match.match.endedAt, value = self.totalScore.toDouble() / (darts / 3.0))
        }
        .sortedBy { it.atMs }
        .takeLast(MAX_POINTS)
}
