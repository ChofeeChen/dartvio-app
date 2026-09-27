package com.dartvio.app.domain.versus

/** 战报里的一轮（已把逐镖文本解析回 [BoardHit]）。 */
data class VersusRoundSummary(
    val playerIndex: Int,
    val playerName: String,
    val roundNo: Int,
    val isPlayoff: Boolean,
    val darts: List<BoardHit>,
    val roundScore: Int,
    val runningScore: Int,
    val targetSnapshot: String,
) {
    /** 「T20 · D16 · MISS」这类逐镖文本。 */
    val dartsLabel: String get() = darts.joinToString(" · ") { it.label() }

    val isEmpty: Boolean get() = darts.isEmpty()
}

/**
 * 结算战报的**视图数据**。
 *
 * 为什么不额外落一张「战报表」：战报里的每一项（总分、曲线、镖数、高光）都能由
 * `versus_round_records` 逐行推出来，多存一份就是多一份会和原始数据分叉的副本。
 * 这里的派生全部是纯函数，因此「曲线起点是 0」「高光取本轮最高分、并列取更早那轮」这类口径
 * 可以直接在单测里钉住（`VersusReportTest`）。
 *
 * 2026-09-27：随 `domain/` 下沉 KMP，删掉了原本挂在这里的「由 Room 实体组装」工厂 ——
 * 它让共享核心反向依赖 Android 的 Room 实体，而 iOS 端根本没有 Room（该工厂当时已无调用方）。
 * 将来若要做「历史战报」页，这份「实体 → 战报」的映射应当写在 app 侧的 data 层。
 *
 * 因此本文件现在是纯 Kotlin：数据类 + 派生口径，两端共用，`VersusReportTest` 直接覆盖。
 */
data class VersusBattleReport(
    val matchId: String,
    val modeKey: String,
    val modeLabel: String,
    val playerNames: List<String>,
    val rounds: List<VersusRoundSummary>,
    val winnerIndex: Int,
    val winnerName: String,
    val endReason: String,
    val totalDarts: Int,
    val wentToPlayoff: Boolean,
    val handicapSummary: String,
    val startedAt: Long,
    val endedAt: Long,
) {

    /** 无胜者（中止 / 未回填）。 */
    val isDraw: Boolean get() = winnerIndex < 0

    val aborted: Boolean get() = endReason == BattleEndReason.ABORT.name

    val modeInfo: VersusModeInfo? get() = VersusModes.infoOf(modeKey)

    fun nameOf(playerIndex: Int): String = playerNames.getOrNull(playerIndex) ?: "玩家${playerIndex + 1}"

    /** 某人**各轮**的轮次（时间顺序）。 */
    fun roundsOf(playerIndex: Int): List<VersusRoundSummary> =
        rounds.filter { it.playerIndex == playerIndex }

    /** 终局进度：最后一个已结算轮次的累计值；一轮都没打则为 0。 */
    fun finalProgressOf(playerIndex: Int): Int =
        roundsOf(playerIndex).lastOrNull()?.runningScore ?: 0

    fun dartCountOf(playerIndex: Int): Int = roundsOf(playerIndex).sumOf { it.darts.size }

    fun roundCountOf(playerIndex: Int): Int = roundsOf(playerIndex).size

    /**
     * 得分曲线，**起点固定补一个 0**。
     *
     * 曲线不补起点就会从「第一轮之后」开始，视觉上等于把第 1 轮的进展算进了初始值 ——
     * 双方的线会比真实进展「整体高一格」，看趋势时会被误读成「开局就领先」。
     */
    fun curveOf(playerIndex: Int): List<Int> =
        listOf(0) + roundsOf(playerIndex).map { it.runningScore }

    /**
     * 高光时刻 = 该玩家本轮得分最高的一轮；并列时取**更早**的那一轮
     * （`maxByOrNull` 返回第一个最大值，这里依赖该行为并已在单测里钉住）。
     */
    fun highlightOf(playerIndex: Int): VersusRoundSummary? =
        roundsOf(playerIndex).maxByOrNull { it.roundScore }

    /** 终局原因的中文标签；未回填时为「进行中」。 */
    fun endReasonLabel(): String =
        BattleEndReason.entries.firstOrNull { it.name == endReason }?.label ?: "进行中"

    val durationMs: Long get() = (endedAt - startedAt).coerceAtLeast(0L)
}
