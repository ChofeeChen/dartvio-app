package com.dartvio.app.domain.stats

import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.domain.model.CricketTarget

/**
 * 按 M9 §8 口径从落库的原始事实现算各项指标。
 *
 * 「个人」= 出手顺序为 0 的本机玩家（as-built：玩家 1）。AI 玩家的行同样落库，
 * 但不计入个人统计。
 */
object StatsCalculator {

    private const val X01 = "X01"
    private const val CRICKET = "CRICKET"

    fun compute(matches: List<MatchWithPlayers>): StatsSnapshot =
        StatsSnapshot(
            x01 = x01Stats(matches),
            cricket = cricketStats(matches),
        )

    // ------------------------------------------------------------------ X01

    private fun x01Stats(matches: List<MatchWithPlayers>): X01Stats {
        val rows = selfRowsOf(matches, X01)
        if (rows.isEmpty()) return X01Stats()

        val selfRows = rows
        val formalRows = rows.filter { it.match.isFormal }

        val darts = selfRows.sumOf { it.player.dartsThrown }
        val score = selfRows.sumOf { it.player.totalScore }
        val turns = selfRows.sumOf { it.player.turnsPlayed }
        val legs = selfRows.sumOf { it.match.legCount }

        val formalDarts = formalRows.sumOf { it.player.dartsThrown }
        val formalScore = formalRows.sumOf { it.player.totalScore }

        val wins = selfRows.count { it.player.isWinner }
        val formalWins = formalRows.count { it.player.isWinner }

        return X01Stats(
            matchCount = selfRows.size,
            formalMatchCount = formalRows.size,
            winCount = wins,
            formalWinCount = formalWins,
            legCount = legs,
            formalLegCount = formalRows.sumOf { it.match.legCount },
            pprAll = ppr(score, darts),
            pprFormal = ppr(formalScore, formalDarts),
            scorePerDart = ratio(score, darts),
            winRateAll = ratio(wins, selfRows.size),
            winRateFormal = ratio(formalWins, formalRows.size),
            highestCheckout = selfRows.maxOfOrNull { it.player.bestCheckout } ?: 0,
            longestWinStreak = longestWinStreak(formalRows),
            dartsPerLeg = ratio(darts, legs),
            total180 = selfRows.sumOf { it.player.count180 },
            maxTurnScore = selfRows.maxOfOrNull { it.player.maxTurnScore } ?: 0,
            bustRate = ratio(selfRows.sumOf { it.player.busts }, turns),
            checkoutRate = ratio(
                selfRows.sumOf { it.player.legsWon },
                selfRows.sumOf { it.player.checkoutAttempts },
            ),
        )
    }

    // -------------------------------------------------------------- Cricket

    private fun cricketStats(matches: List<MatchWithPlayers>): CricketStats {
        val rows = selfRowsOf(matches, CRICKET)
        if (rows.isEmpty()) return CricketStats()

        // M9 §8.3.1 指标准入表（与 §8.3 / §7 冲突时以此为准）：
        // **C1–C10 全部仅 standard 计入**（M2 §4.9.6②，比一期更严）——
        // 变体改变了「分是否存在」与「分归谁」，混算会把 cut_throat 送对手的分、
        // no_score 恒为 0 的分当成自己的表现，直接污染指标。
        //
        // 二期 2C 起准入判据见 `MatchRecordEntity.isCricketStandard`（gameType + 变体 + 目标集
        // 三合一）：既要有变体那半来挡住 no_score / cut_throat，也要有目标集那半来挡住
        // Tactics / Random —— 后两者的 `cricketVariant` 仍是 `STANDARD`（Q7），只看变体名会把
        // 它们全判成标准局。C2/C4/C10 也一并收窄到标准局：Tactics 的 9 档（含 D/T）
        // 会把「关闭率」与「首关分布」拉到与标准局不可比的口径上。
        val standardRows = rows.filter { it.match.isCricketStandard }
        // C8 还要再叠一层 S 级（多局全真人）
        val formalStandardRows = standardRows.filter { it.match.isFormal }

        val standardDarts = standardRows.sumOf { it.player.dartsThrown }
        val standardScore = standardRows.sumOf { it.player.totalScore }
        val standardTurns = standardRows.sumOf { it.player.turnsPlayed }
        val standardClosedAllLegs = standardRows.sumOf { it.player.closedAllSectionLegs }

        val histogram = mutableMapOf<CricketTarget, Int>()
        standardRows.forEach { row ->
            parseTokens(row.player.firstClosedCsv).forEach { histogram[it] = (histogram[it] ?: 0) + 1 }
        }

        return CricketStats(
            // 事实统计（对局数 / 胜场 / 镖数）：全部变体计入，历史不丢弃
            matchCount = rows.size,
            winCount = rows.count { it.player.isWinner },
            markRate = ratio(standardRows.sumOf { it.player.marksTotal }, standardDarts),   // C1 仅 standard
            // C2 仅 standard（2C 收窄）。分母取「**各局自己的目标集大小**」而不是硬编码 7：
            // 标准局的 `targetSetCsv` 是空串 → 默认 7 分区，所以这个式子在现有数据上与
            // `standardRows.size * 7L` 逐位相同；留成动态值是为了不再埋一个「7」的假设。
            closeRate = ratio(
                standardRows.sumOf { it.player.closedSectionsTotal },
                standardRows.sumOf { it.match.cricketTargets.size.toLong() },
            ),
            tripleRate = ratio(standardRows.sumOf { it.player.tripleHits }, standardDarts),  // C3 仅 standard
            bullRate = ratio(standardRows.sumOf { it.player.bullHits }, standardDarts),      // C4 仅 standard
            avgTurnScore = ratio(standardScore, standardTurns),                              // C5 仅 standard
            avgTurnsToClose = ratio(
                standardRows.sumOf { it.player.turnsInClosedLegs },
                standardClosedAllLegs
            ),                                                                               // C6 仅 standard
            scoreEfficiency = ratio(standardScore, standardDarts),                           // C7 仅 standard
            winRate = ratio(
                formalStandardRows.count { it.player.isWinner },
                formalStandardRows.size
            ),                                                                               // C8 仅 standard + S 级
            maxTurnScore = standardRows.maxOfOrNull { it.player.maxTurnScore } ?: 0,         // C9 仅 standard
            firstClosedHistogram = histogram,                                                // C10 仅 standard
        )
    }

    // ------------------------------------------------------------- 工具方法

    private data class SelfRow(
        val match: com.dartvio.app.data.local.entity.MatchRecordEntity,
        val player: com.dartvio.app.data.local.entity.MatchPlayerEntity,
    )

    /** 取某个玩法下、出手顺序 0 的本机玩家行。 */
    private fun selfRowsOf(matches: List<MatchWithPlayers>, gameType: String): List<SelfRow> =
        matches.mapNotNull { m ->
            if (m.match.gameType != gameType) return@mapNotNull null
            m.players.firstOrNull { it.orderIndex == 0 }?.let { SelfRow(m.match, it) }
        }

    /** PPR = 总得分 ÷ (投镖数 ÷ 3)。回合数按实际投镖数推算，见 M9 §8.1。 */
    private fun ppr(totalScore: Int, dartsThrown: Int): Double =
        if (dartsThrown == 0) 0.0 else totalScore.toDouble() / (dartsThrown / 3.0)

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else numerator.toDouble() / denominator

    private fun ratio(numerator: Int, denominator: Long): Double =
        if (denominator == 0L) 0.0 else numerator.toDouble() / denominator

    /** 最高连胜：按结束时间升序扫描正式赛，取最长连续获胜。 */
    private fun longestWinStreak(formalRows: List<SelfRow>): Int {
        val ordered = formalRows.sortedBy { it.match.endedAt }
        var best = 0
        var current = 0
        ordered.forEach { row ->
            if (row.player.isWinner) {
                current += 1
                if (current > best) best = current
            } else {
                current = 0
            }
        }
        return best
    }

    /**
     * 解析落库的首关分区串（二期 2A 起为 [CricketTarget] token，逗号分隔）。
     *
     * **未知 token 跳过、绝不抛异常**：列里可能存着更新版本写入的 token
     * （类别档 `"D"` / `"T"`、将来新增的标识），旧版本读到它们时少算一条即可 ——
     * 抛异常会让整个统计页打不开，那才是真的把老用户挡在门外。
     * 空串（历史行为：该局没有首关记录）自然解析为空列表。
     */
    private fun parseTokens(raw: String): List<CricketTarget> =
        raw.split(',').mapNotNull { CricketTarget.parse(it) }
}
