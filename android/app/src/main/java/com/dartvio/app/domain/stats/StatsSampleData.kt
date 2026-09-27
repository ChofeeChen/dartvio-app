package com.dartvio.app.domain.stats

/**
 * **示例数据**（数据页右上角的「示例」开关打开时显示）。
 *
 * ## 它用来干什么
 *
 * Beta 小范围分发时，第一天打开的人手上**一场都没有**，数据页是空的 ——
 * 于是这一页最花心思的图表与指标编排，恰好在收集反馈的关键期里一次都露不出来。
 * 示例数据把「有数据时长什么样」提前展示出来，让人能对着形态提意见
 * （「这条线看不出进步」「这几个数排得太密」），而不是等他打满两周才有得说。
 *
 * ## 三条硬边界
 *
 * 1. **只活在内存里**：类型就是 [StatsSnapshot] / [TrendPoint]，与真实数据同一条渲染管线；
 *    不写库、不进任何统计口径、不上传 —— 关掉开关就消失，不留痕迹。
 * 2. **界面必须标出「示例」**：让人把自己的数据和演示数据看混，是比空页面更糟的错误。
 * 3. **数值刻意「像真的」但不圆整**：41.7 这种带零头的数，才看得出格式与对齐对不对。
 */
object StatsSampleData {

    /** 一天的毫秒数：示例的时间轴用它铺开，横轴的「时间」含义与真实数据一致。 */
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** 逐场三镖均：整体向上、中间有回落 —— 真实训练数据就是这种形状，直线上升反而假。 */
    private val THREE_DART_AVG = doubleArrayOf(
        38.4, 39.1, 37.6, 41.2, 42.0, 41.5,
        43.8, 44.6, 43.9, 46.2, 45.8, 47.1,
    )

    val snapshot: StatsSnapshot = StatsSnapshot(
        x01 = X01Stats(
            matchCount = 42,
            formalMatchCount = 26,
            winCount = 28,
            formalWinCount = 17,
            legCount = 96,
            formalLegCount = 61,
            pprAll = 42.67,
            pprFormal = 44.13,
            scorePerDart = 14.22,
            winRateAll = 0.667,
            winRateFormal = 0.654,
            highestCheckout = 116,
            longestWinStreak = 5,
            dartsPerLeg = 18.4,
            total180 = 7,
            maxTurnScore = 180,
            bustRate = 0.043,
            checkoutRate = 0.381,
        ),
        cricket = CricketStats(
            matchCount = 18,
            winCount = 11,
            markRate = 0.412,
            closeRate = 0.732,
            tripleRate = 0.186,
            bullRate = 0.094,
            avgTurnScore = 24.6,
            avgTurnsToClose = 8.3,
            scoreEfficiency = 8.2,
            winRate = 0.611,
            maxTurnScore = 96,
        ),
    )

    /** 示例趋势：12 场，越近的在右边。 */
    fun trend(nowMs: Long = System.currentTimeMillis()): List<TrendPoint> =
        THREE_DART_AVG.mapIndexed { index, value ->
            val daysAgo = (THREE_DART_AVG.size - 1 - index) * 2L
            TrendPoint(atMs = nowMs - daysAgo * DAY_MS, value = value)
        }

    /**
     * 分布示例：近 8 周每周场次。
     *
     * 用**柱状**而不是折线：每周是**独立的量**，不是同一个量随时间的变化 ——
     * 换成折线会凭空造出「周与周之间有过渡」的错觉。
     */
    val weeklyMatches: List<Pair<String, Double>> = listOf(
        "8 周前" to 2.0,
        "7 周前" to 3.0,
        "6 周前" to 3.0,
        "5 周前" to 5.0,
        "4 周前" to 4.0,
        "3 周前" to 7.0,
        "2 周前" to 6.0,
        "本周" to 9.0,
    )

    /**
     * 占比示例：42 场的胜负平构成。
     *
     * 用**条**而不是饼图：三类占比靠得很近（66% / 31% / 2%），饼图在这个差距下
     * 只能比出「大小」，读不出具体差多少；条可以配数字，且横向排下来标签不用斜着放。
     */
    val outcomeShare: List<Pair<String, Double>> = listOf(
        "胜" to 0.667,
        "负" to 0.310,
        "平" to 0.023,
    )
}
