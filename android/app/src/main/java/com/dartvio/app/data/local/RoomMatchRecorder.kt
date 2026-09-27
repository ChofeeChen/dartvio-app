package com.dartvio.app.data.local

import com.dartvio.app.data.MatchRepository
import com.dartvio.app.data.local.entity.MatchPlayerEntity
import com.dartvio.app.data.local.entity.MatchRecordEntity
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.MatchSource
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.encodeCricketTargets
import com.dartvio.app.domain.room.LocalUser
import com.dartvio.app.domain.room.MatchFinish
import com.dartvio.app.domain.room.RoomMatchRules
import com.dartvio.app.domain.room.RoomMatchView
import com.dartvio.app.domain.room.SpectatorPlayer
import com.dartvio.app.domain.room.SpectatorSnapshot
import com.dartvio.app.domain.room.SpectatorTurn

/**
 * 把一场**联机**对局的结果落进本地 `match_records`（M5 T10 的写入侧）。
 *
 * ## 为什么必须落
 *
 * 此前这张表只有 [MatchMapper] 一个写入者（单机 `GameUiState`）：联机局打完一行都不留，
 * 于是「我在大厅里打了几场」在 App 里无处可查 —— 数据页的「比赛大厅」口径永远是空的，
 * 房间卡上的 PPR 也算不出来（`OnlineRoomRepository.arenaPpr()` 读的正是这一批记录）。
 *
 * 落的行 `source = LAN`：它能进**历史列表**（打完的对局是用户资产，必须能回看），
 * 但由 [MatchStatsFilter] 统一挡在战绩 / 成就 / 排行榜之外 ——
 * 有对手在的对局与可以一个人反复重开的本地局**不可比**，混在一张表里算 PPR，
 * 等于宣布两种数据是等价的。
 *
 * ## 这一份有多可信（务必往下传）
 *
 * 事实**只来自对局帧**，而帧最多带 [RoomMatchRules.MAX_TURNS_IN_FRAME] 条流水，
 * 长局的早期回合不在这里。**不补、不估**（与 `MatchStats` 同一取舍）：
 *
 * - 镖数按逐镖 token 如实数出来（`Dart.label()` 一镖一个 token），有多少算多少；
 * - 「尝试结镖的次数」帧里没有字段，`checkoutAttempts` 只能退化为**成功结镖的轮数**，
 *   于是 Checkout 率偏高。宁可偏高也不落 0 —— 0 会被读成「从来没尝试过结镖」。
 *
 * ## 幂等
 *
 * [com.dartvio.app.data.local.dao.MatchRecordDao.insertMatch] 用的是 REPLACE，
 * 而 matchId **由结果本身导出**（房间号 + 局数 + 局分），所以旋转屏幕、重回结算页
 * 都会落到同一行，不会每次进场多出一场历史。
 */
object RoomMatchRecorder {

    /**
     * 落一场已结束的联机局。
     *
     * 失败由调用方统一吞掉：结算页的职责是显示结果，
     * 一场统计没记下来不该让用户看到崩溃或报错。
     */
    suspend fun record(
        repository: MatchRepository,
        roomId: String,
        config: MatchConfig,
        view: RoomMatchView,
        endedAt: Long = System.currentTimeMillis(),
        /** 开局时刻；拿不到就传 null，这一行只有 `endedAt` 是准的。 */
        startedAt: Long? = null,
        /** 本机稳定档案 ID（`ProfileStore`）；本机席位用它落库，见 [MatchMapper]。 */
        localProfileId: String? = null,
    ): MatchRecordEntity {
        val snapshot = view.snapshot
        val rows = selfFirstRows(snapshot)
        val winnerId = winnerIdOf(snapshot, view.finish, localProfileId)
        val matchId = matchIdOf(roomId, snapshot)
        val start = startedAt?.coerceAtMost(endedAt) ?: endedAt
        val record = MatchRecordEntity(
            matchId = matchId,
            gameType = config.matchType.name,
            matchType = config.matchType.name,
            matchTypeLabel = matchTypeLabel(config.matchType),
            // 多局 + 双真人 = 正式赛。联机局没有 AI 席位，`containsAi` 恒假。
            isFormal = config.mode == MatchMode.MULTI_LEG,
            containsAi = false,
            playerCount = rows.size,
            startedAt = start,
            endedAt = endedAt,
            durationMs = (endedAt - start).coerceAtLeast(0),
            winnerPlayerId = winnerId,
            legCount = snapshot.leg,
            legsToWin = snapshot.legsToWin,
            startScore = if (config.matchType == MatchType.X01) config.targetScore else 0,
            x01Mode = config.mode.name,
            // 老布尔与新档位列必须**同源**（都由 config 推出），否则历史行会出现自相矛盾的一行。
            doubleOut = config.doubleOut,
            doubleIn = config.doubleIn,
            overtimeRule = config.overtimeRule.toString(),
            outMode = config.outMode.name,
            inMode = config.inMode.name,
            bullMode = config.bullMode.name,
            maxRounds = config.maxRounds,
            cricketVariant = config.cricketVariant.name,
            targetSetCsv = encodeCricketTargets(config.cricketTargets),
            endedByRoundLimit = false,
            totalDarts = rows.sumOf { it.dartsThrown() },
            source = MatchSource.LAN.name,
            roomId = roomId,
            // 对手不在本机档案里：名字必须随行落库，否则历史里只剩一个谁也不认识的 id。
            winnerName = winnerDisplayName(snapshot, view.finish),
            forfeited = view.finish?.reason == MatchFinish.REASON_FORFEIT,
        )
        repository.saveMatch(record, rows.mapIndexed { index, row -> row.toEntity(matchId, index, winnerId, localProfileId) })
        return record
    }

    /** 与 [MatchMapper] 同一套展示名（历史行**不因枚举改名而失真**，见那边的注释）。 */
    private fun matchTypeLabel(type: MatchType): String = when (type) {
        MatchType.X01 -> "X01"
        MatchType.CRICKET -> "Cricket"
        MatchType.AROUND_THE_CLOCK -> "Around the Clock"
        MatchType.SHANGHAI -> "Shanghai"
        MatchType.HALVE_IT -> "Halve It"
        MatchType.KILLER -> "Killer"
    }

    /**
     * 玩家行按「**我** → 其他人」排序。
     *
     * 我必须排在第 0 位：[com.dartvio.app.domain.stats.StatsCalculator] 以 `orderIndex == 0`
     * 认本机玩家，与单机落库（[MatchMapper] 取首个真人席位）是同一个约定。
     * 顺序错了不会报错，只会让两个人的数据互相对调 —— 那是最难发现的一种错。
     */
    private fun selfFirstRows(snapshot: SpectatorSnapshot): List<SelfRow> {
        val turnsByName = LinkedHashMap<String, MutableList<SpectatorTurn>>()
        snapshot.turns.forEach { turn ->
            turnsByName.getOrPut(turn.playerName) { ArrayList() }.add(turn)
        }
        val rows = snapshot.players.map { player ->
            SelfRow(player = player, turns = turnsByName[player.name].orEmpty())
        }
        // 「我」在帧里的 id 已由 `withLocal` 投影成 [LocalUser.ID]（见 `RoomIdentity`）。
        val me = rows.firstOrNull { it.player.id == LocalUser.ID } ?: return rows
        return listOf(me) + rows.filterNot { it === me }
    }

    private fun SelfRow.isSelf(): Boolean = player.id == LocalUser.ID

    private fun SelfRow.storedPlayerId(localProfileId: String?): String =
        if (isSelf() && localProfileId != null) localProfileId else player.id

    private fun winnerIdOf(
        snapshot: SpectatorSnapshot,
        finish: MatchFinish?,
        localProfileId: String?
    ): String? {
        val winner = finish?.winnerId?.takeIf { it.isNotBlank() }
            ?.let { id -> snapshot.players.firstOrNull { it.id == id } }
            ?: firstToTarget(snapshot)
            ?: lastCheckout(snapshot)
            ?: return null
        return if (winner.id == LocalUser.ID && localProfileId != null) localProfileId else winner.id
    }

    private fun winnerDisplayName(snapshot: SpectatorSnapshot, finish: MatchFinish?): String {
        finish?.winnerId?.takeIf { it.isNotBlank() }
            ?.let { id -> snapshot.players.firstOrNull { it.id == id }?.name }
            ?.let { return it }
        return listOfNotNull(firstToTarget(snapshot), lastCheckout(snapshot))
            .firstOrNull()?.name.orEmpty()
    }

    /** 多局模式下先赢到目标局数者；休闲局（`legsToWin <= 0`）不参与，否则第一位永远是「赢家」。 */
    private fun firstToTarget(snapshot: SpectatorSnapshot): SpectatorPlayer? =
        if (snapshot.legsToWin > 0) {
            snapshot.players.firstOrNull { it.legsWon >= snapshot.legsToWin }
        } else {
            null
        }

    /** 单局定胜负：最后一支收镖的人。 */
    private fun lastCheckout(snapshot: SpectatorSnapshot): SpectatorPlayer? =
        snapshot.turns.lastOrNull()?.takeIf { it.isCheckout }
            ?.let { turn -> snapshot.players.firstOrNull { it.name == turn.playerName } }

    /** 这场的行 id：**由结果导出**（见本类注释「幂等」）。 */
    private fun matchIdOf(roomId: String, snapshot: SpectatorSnapshot): String = buildString {
        append(LAN_MATCH_PREFIX)
        append(roomId)
        append('-')
        append(snapshot.leg)
        snapshot.players.forEach { player -> append('-').append(player.legsWon) }
    }

    /**
     * 投了多少镖：逐镖 token 计数，与 `Dart.label()` 同源。
     *
     * 不足三镖的收尾轮（D20 一镖收掉）**真的只投了一镖**，用 3 补齐会让 PPR 的分母虚高 ——
     * 而 PPR 正是用户看这张表唯一的目的。解析不出来才退到 3（保守方向，不许判成 0）。
     */
    private fun SelfRow.dartsThrown(): Int = turns.sumOf { turn -> dartsIn(turn) }

    private fun dartsIn(turn: SpectatorTurn): Int {
        val count = turn.darts.split(' ').count { it.isNotBlank() }
        return if (count in 1..3) count else 3
    }

    private fun SelfRow.toEntity(
        matchId: String,
        orderIndex: Int,
        winnerId: String?,
        localProfileId: String?
    ): MatchPlayerEntity = MatchPlayerEntity(
        matchId = matchId,
        playerId = storedPlayerId(localProfileId),
        name = player.name,
        isAi = false,
        aiDifficulty = null,
        orderIndex = orderIndex,
        isWinner = storedPlayerId(localProfileId) == winnerId,
        legsWon = player.legsWon,
        dartsThrown = dartsThrown(),
        turnsPlayed = turns.size,
        totalScore = turns.sumOf { it.scored },
        maxTurnScore = turns.maxOfOrNull { it.scored } ?: 0,
        remaining = player.score,
        busts = turns.count { it.isBust },
        count180 = turns.count { it.scored >= 180 },
        bestCheckout = turns.filter { it.isCheckout }.maxOfOrNull { it.scored } ?: 0,
        // 帧里没有「尝试过几次结镖」，退化成成功次数（见本类注释）。
        checkoutAttempts = turns.count { it.isCheckout },
        marksTotal = 0,
        tripleHits = 0,
        bullHits = 0,
        closedAllSectionLegs = 0,
        turnsInClosedLegs = 0,
        closedSectionsTotal = 0,
        firstClosedCsv = "",
    )

    /** 一方的席位 + 他在这份帧里的流水。 */
    private data class SelfRow(
        val player: SpectatorPlayer,
        val turns: List<SpectatorTurn>,
    )

    private const val LAN_MATCH_PREFIX = "lan-"
}
