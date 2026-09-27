package com.dartvio.app.domain.leaderboard

import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.data.local.entity.MatchPlayerEntity
import com.dartvio.app.data.local.entity.MatchRecordEntity
import com.dartvio.app.domain.model.MatchType

/**
 * 本地排行榜（第③期 ③B）—— 从落库的原始事实现算，**不落库**。
 *
 * 与 [com.dartvio.app.domain.stats.StatsCalculator] 同构：只读 `match_records` /
 * `match_players` 的原始事实，不新增派生列、不改「只落事实、不落指标」的原则，
 * 因此口径调整（排序维度、样本门槛）不需要任何数据迁移。
 *
 * ## 准入规则（三条，缺一不可）
 * 1. **玩法范围**：[compute] 的 `gameType` 参数，本版本固定 X01。
 *    排序维度里的 PPR 与最高收镖都是 X01 独有口径，Cricket 局没有可比的分母
 *    （Cricket 的分受玩法变体影响，M9 §8.3.1 只允许 standard 计入），
 *    混进同一张榜会算出一个没有意义的数字。Cricket 榜是否需要、按什么维度排，
 *    属于待 PRD 确认的开放问题（本版本不做）。
 * 2. **可信度**：只认**已完成的正式赛**（`isFormal` = 多局模式 + 全真人，且 `winnerPlayerId != null`）。
 *    休闲局与 AI 对战不参与排名 —— 与 M9 §7 的分层、以及成就系统「排名语义严格要求 S 级」一致。
 * 3. **身份**：只认**本机已建档的玩家**。[LeaderboardPlayer.playerId] 必须是稳定档案 ID；
 *    旧数据里的位置 ID（`"p1"` / `"p2"`）与 AI 行一律过滤掉 ——
 *    位置 ID 无法归属到具体人，混进来等于把「玩家 1」当成一个人计入。
 *
 * ⚠️ PRD §6.2 的「本地正式赛权重 0.8」**不在本类实现**：那是本地榜与在线榜**混合**时的权重，
 * 本地榜内部不存在混合。顶部公示文案保留（见 `LeaderboardScreen`）。
 */
object LeaderboardCalculator {

    /**
     * 本版本榜单只跑 X01（理由见类注释「准入规则 1」）。
     *
     * 取 `MatchType.X01.name` 而不是字面量 `"X01"`：落库写入的是 `config.matchType.name`，
     * 一旦枚举被重命名，字面量会让过滤条件**静默**匹配不到任何对局（榜永远是空的）。
     */
    val DEFAULT_GAME_TYPE: String = MatchType.X01.name

    /**
     * 最小样本门槛：正式赛场次不足 3 场的玩家不进榜，单独归入「数据不足」区。
     *
     * 理由：1 场就能刷榜首（一把 180 收工），榜会立刻失去区分度。
     * 3 场是「至少打过一整晚」的最小可信量级 —— 与 M9 §8.1 里 PPR 只在
     * 多局正式赛上才具排名意义的既有口径保持一致。
     */
    const val MIN_RANKED_MATCHES = 3

    /**
     * 计算榜单。
     *
     * @param matches 全量对局历史（`observeMatches()` 的原始输出，不做预过滤）
     * @param players 本机已建档的玩家（当前实现只有本机那一份档案，
     *                多档案建档后本方法无需修改）
     * @param sort    排序维度
     * @param gameType 玩法范围，默认 [DEFAULT_GAME_TYPE]
     */
    fun compute(
        matches: List<MatchWithPlayers>,
        players: List<LeaderboardPlayer>,
        sort: LeaderboardSort = LeaderboardSort.PPR,
        gameType: String = DEFAULT_GAME_TYPE,
    ): LeaderboardSnapshot {
        if (players.isEmpty()) return LeaderboardSnapshot()

        val profileIds = players.mapTo(mutableSetOf()) { it.playerId }
        val rows = rankedRows(matches, profileIds, gameType)

        val entries = players
            .map { player -> summarize(player, rows.filter { it.player.playerId == player.playerId }) }
            // 一场准入记录都没有的玩家直接不出现：不是「数据不足」，是「还没打过」。
            // 让他出现在「数据不足」区会把空态变成一张只有自己名字的空表 ——
            // 用户看不到任何信息，只看到自己被排在最后。
            .filter { it.formalMatches > 0 }
        val (qualified, insufficient) = entries.partition { it.qualified }

        return LeaderboardSnapshot(
            ranked = qualified.sortedWith(sort.comparator()),
            insufficient = insufficient.sortedWith(sort.comparator()),
        )
    }

    // ------------------------------------------------------------------ 内部

    /** 一条「已准入」的玩家行：所属对局 + 该玩家的原始事实。 */
    private data class RankedRow(
        val match: MatchRecordEntity,
        val player: MatchPlayerEntity,
    )

    private fun rankedRows(
        matches: List<MatchWithPlayers>,
        profileIds: Set<String>,
        gameType: String,
    ): List<RankedRow> = matches.flatMap { wrapper ->
        val match = wrapper.match
        if (match.gameType != gameType) return@flatMap emptyList()
        if (!match.isFormal) return@flatMap emptyList()
        if (match.winnerPlayerId == null) return@flatMap emptyList()
        wrapper.players
            .filter { !it.isAi && it.playerId in profileIds }
            .map { RankedRow(match, it) }
    }

    /** 把一名玩家的全部准入行汇总成一个榜位。没有任何准入行时所有指标为零。 */
    private fun summarize(player: LeaderboardPlayer, rows: List<RankedRow>): LeaderboardEntry {
        val matches = rows.size
        val wins = rows.count { it.player.isWinner }
        val darts = rows.sumOf { it.player.dartsThrown }
        val score = rows.sumOf { it.player.totalScore }

        return LeaderboardEntry(
            playerId = player.playerId,
            name = player.name,
            formalMatches = matches,
            wins = wins,
            ppr = ppr(score, darts),
            winRate = if (matches == 0) 0.0 else wins.toDouble() / matches,
            highestCheckout = rows.maxOfOrNull { it.player.bestCheckout } ?: 0,
            maxTurnScore = rows.maxOfOrNull { it.player.maxTurnScore } ?: 0,
        )
    }

    /**
     * PPR = 总得分 ÷ (投镖数 ÷ 3)，与 M9 §8.1 及 `StatsCalculator.ppr` **同口径**。
     *
     * 刻意各写一份而不是互相调用：排行榜只跑正式赛，统计页把全量与正式赛都算，
     * 两者的过滤口径不同，强行共用一个「先过滤再相除」的函数会把两处口径绑死。
     * 因此这里把公式连同依据一起钉住 —— **改口径时两处必须同改**。
     */
    private fun ppr(totalScore: Int, dartsThrown: Int): Double =
        if (dartsThrown == 0) 0.0 else totalScore.toDouble() / (dartsThrown / 3.0)
}

/**
 * 上榜候选。本机档案的展示名可能被改过，所以名字由调用方传入而不是从历史行里取。
 */
data class LeaderboardPlayer(
    val playerId: String,
    val name: String,
)

/** 排序维度。默认 [PPR]（③B 建议值，待 PRD 确认）。 */
enum class LeaderboardSort(val label: String) {
    /** 全部正式赛的合并 PPR。 */
    PPR("PPR"),

    /** 正式赛胜率。 */
    WIN_RATE("胜率"),

    /** 最高收镖分（单局收尾回合得分的历史最大值）。 */
    HIGHEST_CHECKOUT("最高收镖"),

    /** 正式赛场次（参与度）。 */
    MATCH_COUNT("场次"),
}

/** 一个榜位。指标全部来自原始事实，无派生落库。 */
data class LeaderboardEntry(
    val playerId: String,
    val name: String,
    /** 准入的正式赛场次（Cricket 局、休闲局、AI 局都不计入）。 */
    val formalMatches: Int,
    val wins: Int,
    /** 全部准入场的合并 PPR。 */
    val ppr: Double,
    val winRate: Double,
    /** 最高收镖分。 */
    val highestCheckout: Int,
    /** 最高单回合得分（与最高收镖不是同一个量：收镖只在赢下的那局里产生）。 */
    val maxTurnScore: Int,
) {
    /** 是否达到最小样本门槛。未达标者不进主榜，只出现在「数据不足」区。 */
    val qualified: Boolean get() = formalMatches >= LeaderboardCalculator.MIN_RANKED_MATCHES
}

/** 榜单快照：[ranked] 为主榜，[insufficient] 为样本不足区（两者都已按当前维度排好序）。 */
data class LeaderboardSnapshot(
    val ranked: List<LeaderboardEntry> = emptyList(),
    val insufficient: List<LeaderboardEntry> = emptyList(),
) {
    /** 连一条准入记录都没有 —— UI 用「打一场正式赛就能上榜」引导，而不是画一张空表。 */
    val isEmpty: Boolean get() = ranked.isEmpty() && insufficient.isEmpty()
}

/**
 * 排序比较器：主维度降序 → 正式赛场次降序（同分时参与度高者在前）→ 昵称升序。
 *
 * 后两级是**为了确定性**：没有任何并列打破规则时，同一份数据在不同设备/不同时刻
 * 可能排出不同顺序，用户会看到名次莫名其妙地跳动。昵称升序兜底保证结果稳定。
 */
private fun LeaderboardSort.comparator(): Comparator<LeaderboardEntry> {
    val primary: Comparator<LeaderboardEntry> = when (this) {
        LeaderboardSort.PPR -> compareByDescending { it.ppr }
        LeaderboardSort.WIN_RATE -> compareByDescending { it.winRate }
        LeaderboardSort.HIGHEST_CHECKOUT -> compareByDescending { it.highestCheckout }
        LeaderboardSort.MATCH_COUNT -> compareByDescending { it.formalMatches }
    }
    return primary
        .thenByDescending { it.formalMatches }
        .thenBy { it.name }
}
