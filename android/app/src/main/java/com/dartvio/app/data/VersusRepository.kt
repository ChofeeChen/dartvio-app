package com.dartvio.app.data

import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.data.local.dao.VersusDao
import com.dartvio.app.data.local.dao.VersusPlayerModeStat
import com.dartvio.app.data.local.entity.VersusMatchRecordEntity
import com.dartvio.app.data.local.entity.VersusRoundRecordEntity
import com.dartvio.app.domain.versus.BattleConfig
import com.dartvio.app.domain.versus.BattleEndReason
import com.dartvio.app.domain.versus.BoardHit
import com.dartvio.app.domain.versus.VersusDartCodec
import java.util.UUID

/**
 * 双人对战的落库入口。
 *
 * **不是单例**：按项目约定手工构造（[of]），页面自己持有 —— 与联机仓库那种「唯一换源点」不同，
 * 本模块没有换源需求。
 *
 * 三个写入时机（对应需求「逐轮落库、中途退出不丢数据」）：
 *  1. [startMatch] 开赛即插一行（`endedAt = 0`）—— 此时这场就已经在库里了；
 *  2. [saveRound] 每次换手追加一条轮次，并把父行的累计计数一起回写（同一事务）；
 *  3. [finishMatch] / [abortMatch] 回填终局信息。
 *
 * 全部方法都是 `suspend`：调用方（页面）在自己的 scope 里调，不阻塞 UI。
 */
class VersusRepository(private val dao: VersusDao) {

    /** 开赛：写入「进行中」的场次行。 */
    suspend fun startMatch(
        matchId: String,
        modeKey: String,
        modeLabel: String,
        config: BattleConfig,
        playerNames: List<String>,
        handicapSummary: String,
        startedAt: Long,
    ): VersusMatchRecordEntity {
        val match = VersusMatchRecordEntity(
            matchId = matchId,
            modeKey = modeKey,
            modeLabel = modeLabel,
            configJson = config.toStorageString(),
            playerNamesCsv = VersusMatchRecordEntity.namesCsv(playerNames),
            playerCount = playerNames.size,
            handicapSummary = handicapSummary,
            startedAt = startedAt,
        )
        dao.upsertMatch(match)
        return match
    }

    /**
     * 换手结算：追加一条轮次 + 回写父行累计计数。
     *
     * 返回**更新后的父行**（计数已加），调用方把它留在会话里，下一次 [saveRound] 继续用 ——
     * 避免每轮都回库读一次父行。
     */
    suspend fun saveRound(
        match: VersusMatchRecordEntity,
        playerIndex: Int,
        playerName: String,
        roundNo: Int,
        darts: List<BoardHit>,
        roundScore: Int,
        runningScore: Int,
        targetSnapshot: String,
        isPlayoff: Boolean,
        recordedAt: Long,
    ): VersusMatchRecordEntity {
        val updated = match.copy(
            roundCount = match.roundCount + 1,
            totalDarts = match.totalDarts + darts.size,
        )
        dao.appendRound(
            match = updated,
            round = VersusRoundRecordEntity(
                matchId = match.matchId,
                modeKey = match.modeKey,
                playerIndex = playerIndex,
                playerName = playerName,
                roundNo = roundNo,
                isPlayoff = isPlayoff,
                dartsCsv = VersusDartCodec.encode(darts),
                dartCount = darts.size,
                roundScore = roundScore,
                runningScore = runningScore,
                targetSnapshot = targetSnapshot,
                recordedAt = recordedAt,
            )
        )
        return updated
    }

    /** 终局回填（达成目标 / 秒杀 / 认输 / 加赛分出）。 */
    suspend fun finishMatch(
        match: VersusMatchRecordEntity,
        winnerIndex: Int,
        winnerName: String,
        endReason: BattleEndReason,
        wentToPlayoff: Boolean,
        endedAt: Long,
    ): VersusMatchRecordEntity {
        val finished = match.finished(
            winnerIndex = winnerIndex,
            winnerName = winnerName,
            endReason = endReason.name,
            roundCount = match.roundCount,
            totalDarts = match.totalDarts,
            wentToPlayoff = wentToPlayoff,
            endedAt = endedAt,
        )
        dao.upsertMatch(finished)
        return finished
    }

    /** 中途退出：同样是回填，但 `endReason = ABORT`、无胜者 —— 已落库的轮次一条都不删。 */
    suspend fun abortMatch(match: VersusMatchRecordEntity, endedAt: Long): VersusMatchRecordEntity {
        val aborted = match.finished(
            winnerIndex = VersusMatchRecordEntity.NO_WINNER,
            winnerName = "",
            endReason = BattleEndReason.ABORT.name,
            roundCount = match.roundCount,
            totalDarts = match.totalDarts,
            wentToPlayoff = false,
            endedAt = endedAt,
        )
        dao.upsertMatch(aborted)
        return aborted
    }

    suspend fun loadMatch(matchId: String): VersusMatchRecordEntity? = dao.findMatch(matchId)

    suspend fun loadRounds(matchId: String): List<VersusRoundRecordEntity> = dao.listRounds(matchId)

    /** 某一席在本场的全部轮次（按录入顺序），战报的双方分列与高光都用它。 */
    suspend fun loadRoundsOf(matchId: String, playerIndex: Int): List<VersusRoundRecordEntity> =
        dao.listRounds(matchId).filter { it.playerIndex == playerIndex }

    suspend fun recentFinished(limit: Int = 20): List<VersusMatchRecordEntity> = dao.listFinished(limit)

    /**
     * 「某人 × 某模式」的对抗训练统计（镖数 / 轮数）。
     *
     * 这是「镖数计入双方训练统计」的读取口：按**名字**聚合（对局双方都是本机输入的名字，
     * 第二席未必有本地档案），且**只读 `versus_*` 表** —— 既有胜率 / 成就 / 排行榜的数字不受影响。
     */
    suspend fun trainingStats(playerName: String): List<VersusPlayerModeStat> =
        dao.statsByName(playerName)

    suspend fun allTrainingStats(): List<VersusPlayerModeStat> = dao.statsAll()

    /** 某人最近的出手轮次（含逐镖文本），命中分布的分析源。 */
    suspend fun recentRoundsOfPlayer(
        playerName: String,
        limit: Int = 50,
    ): List<VersusRoundRecordEntity> = dao.listRoundsByPlayer(playerName, limit)

    suspend fun countFinished(): Int = dao.countFinished()

    suspend fun deleteById(matchId: String) = dao.deleteById(matchId)

    suspend fun clearAll() = dao.clearAll()

    companion object {

        /**
         * 场次 id。用 UUID 而不是时间戳：同一毫秒内连开两场（「再来一局」连点）也不会撞主键。
         */
        fun newMatchId(): String = UUID.randomUUID().toString()

        /** 页面里的构造口径（与其它仓库一致：手工注入 DAO）。 */
        fun of(database: DartVioDatabase): VersusRepository = VersusRepository(database.versusDao())
    }
}
