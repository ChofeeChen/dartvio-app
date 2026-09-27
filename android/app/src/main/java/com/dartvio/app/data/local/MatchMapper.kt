package com.dartvio.app.data.local

import com.dartvio.app.data.local.entity.MatchPlayerEntity
import com.dartvio.app.data.local.entity.MatchRecordEntity
import com.dartvio.app.domain.model.BullMode
import com.dartvio.app.domain.model.InMode
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.domain.model.encodeCricketTargets
import com.dartvio.app.ui.game.GameUiState
import java.util.UUID

/**
 * 把一局结束时的 [GameUiState] 落成数据库行。
 *
 * 只写原始事实；指标由 StatsCalculator 现算。
 *
 * **身份落库（③B 前置 P0）**：设置页传下来的 [com.dartvio.app.domain.model.Player.id] 是
 * **位置 ID**（`"p1"` / `"p2"` / …），语义是「玩家 1」而不是「某个人」。位置 ID 一旦落库，
 * 同一台设备上每一局的第一席位都会被合并成同一个人，排行榜与「我的」统计就都失去了区分度。
 * 因此落库时把**本机席位**那一行的 ID 换成稳定的 `localProfileId`（见 [storedPlayerId]），
 * 位置 ID 只留在内存态。**列不变、只改写入值**，所以不需要 Room 迁移。
 */
object MatchMapper {

    /** 可信度分层：多局 + 全真人 = S 级正式赛，其余（休闲 / AI 对战）= A 级。 */
    private fun isFormal(state: GameUiState): Boolean =
        state.config.mode == MatchMode.MULTI_LEG && state.players.none { it.isAi }

    /**
     * 本机席位在**内存态**里的 ID（即需要被替换掉的那个位置 ID）。
     *
     * 取「出手顺序最靠前的真人」而不是硬编码 `"p1"`：AI 对战时如果设置页把机器人排在首位，
     * 硬编码会去替换 AI 的行、把真人留在位置 ID 上。取真人则两种排法都成立。
     */
    private fun localSeatId(state: GameUiState): String? =
        state.players.firstOrNull { !it.isAi }?.id

    /** 落库用的玩家 ID：本机席位 → 稳定档案 ID，其余原样透传。 */
    private fun storedPlayerId(
        inMemoryId: String,
        selfSeatId: String?,
        localProfileId: String?,
    ): String = if (localProfileId != null && inMemoryId == selfSeatId) localProfileId else inMemoryId

    fun matchId(): String = UUID.randomUUID().toString()

    fun toMatchEntity(
        state: GameUiState,
        matchId: String,
        endedAt: Long,
        localProfileId: String? = null,
    ): MatchRecordEntity {
        val config = state.config
        val legNumber = state.x01Leg?.legNumber ?: state.cricketLeg?.legNumber ?: 1
        val isX01 = config.matchType == MatchType.X01
        val playerIds = state.players.map { it.id }.toSet()
        val selfSeatId = localSeatId(state)
        // X01 规则档位只在 X01 局有意义。Cricket 行统一写「无 X01 规则」的中性档：
        // 既让列 NOT NULL 有值可落，也保证 **老布尔列与新档位列永远同源**
        // （都由这三个值推出），不会出现「doubleOut = true 但 outMode = STRAIGHT_OUT」这种自相矛盾的行。
        // 对真实 Cricket 配置（[MatchConfig.CRICKET] 已是直出）而言这是**零行为变化**。
        val x01OutMode = if (isX01) config.outMode else OutMode.STRAIGHT_OUT
        val x01InMode = if (isX01) config.inMode else InMode.STRAIGHT_IN
        val x01BullMode = if (isX01) config.bullMode else BullMode.STANDARD_25_50
        val x01MaxRounds = if (isX01) config.maxRounds else 0
        return MatchRecordEntity(
            matchId = matchId,
            gameType = config.matchType.name,
            matchType = config.matchType.name,
            matchTypeLabel = matchTypeLabel(config.matchType),
            isFormal = isFormal(state),
            containsAi = state.players.any { it.isAi },
            playerCount = state.players.size,
            startedAt = state.startedAt,
            endedAt = endedAt,
            durationMs = (endedAt - state.startedAt).coerceAtLeast(0),
            // 胜者同样换成稳定 ID：否则「本场谁赢了」与「本场本机那一行」对不上，
            // 后续任何按人追溯胜负的读取都要再做一次位置 ID 映射。
            winnerPlayerId = state.winnerPlayerId?.let {
                storedPlayerId(it, selfSeatId, localProfileId)
            },
            legCount = legNumber,
            legsToWin = config.legsToWin,
            startScore = if (isX01) config.targetScore else 0,
            x01Mode = if (isX01) config.mode.name else null,
            doubleOut = x01OutMode != OutMode.STRAIGHT_OUT,
            doubleIn = x01InMode != InMode.STRAIGHT_IN,
            overtimeRule = if (isX01) (x01MaxRounds > 0).toString() else null,
            // 规则档位列（v4→v5 新增）：写枚举名，与老布尔**同源**（都由上面的 x01OutMode 等推出）。
            // 只写布尔会把「大师出」丢成「双倍出」，历史记录显示错误且不可逆。
            outMode = x01OutMode.name,
            inMode = x01InMode.name,
            bullMode = x01BullMode.name,
            // 轮数上限（v5 列）：字段已与 Cricket 的轮数上限合并（2026-09-12），
            // 所以这里写 `config.maxRounds`（X01 = 最多轮数；Cricket = §4.9.8 轮数上限，仅 Tactics 非 0），
            // 而不是只服务 X01 的 `x01MaxRounds` —— 该列的含义从「X01 最多轮数」升格为「本局轮数上限」。
            // 老布尔 `overtimeRule` 仍是 **X01 专属**（它是 X01 那句废弃开关的落点），不受影响。
            maxRounds = config.maxRounds,
            cricketVariant = config.cricketVariant.name,
            // 二期 2C：本局是否由轮数上限终局（超时）。落它的是为了 M9 能说明
            // 「超时局的关满统计不计入」，而不是靠分数反推。
            // X01 与 Cricket 各有一份局状态，两侧都要读（2026-09-12 补齐）：此前只读 cricketLeg，
            // 于是 **X01 打满 maxRounds 的收分局在库里恒 false**，只能靠「maxRounds > 0 且胜者剩余分 > 0」反推。
            endedByRoundLimit = state.x01Leg?.endedByRoundLimit
                ?: state.cricketLeg?.endedByRoundLimit
                ?: false,
            // 默认 7 分区写空串（提示词 §4.1）：历史行就是这么存的，新行必须逐字节一致，
            // 否则「旧行 == 新行」在数据里不成立，任何按列值分叉的统计都会踩到。
            // X01 局的目标集恒为默认值，同样落到空串。
            targetSetCsv = encodeCricketTargets(config.cricketTargets),
            totalDarts = playerIds.sumOf { state.facts.of(it).dartsThrown },
        )
    }

    fun toPlayerEntities(
        state: GameUiState,
        matchId: String,
        localProfileId: String? = null,
    ): List<MatchPlayerEntity> {
        val selfSeatId = localSeatId(state)
        return state.players.mapIndexed { index, player ->
            val f = state.facts.of(player.id)
            MatchPlayerEntity(
                matchId = matchId,
                playerId = storedPlayerId(player.id, selfSeatId, localProfileId),
                name = player.name,
                isAi = player.isAi,
                aiDifficulty = player.aiDifficulty?.name,
                orderIndex = index,
                isWinner = state.winnerPlayerId == player.id,
                legsWon = state.legsWon[player.id] ?: 0,
                dartsThrown = f.dartsThrown,
                turnsPlayed = f.turnsPlayed,
                totalScore = f.totalScore,
                maxTurnScore = f.maxTurnScore,
                remaining = state.x01Leg?.players?.firstOrNull { it.playerId == player.id }
                    ?.remaining ?: 0,
                busts = f.busts,
                count180 = f.count180,
                bestCheckout = f.bestCheckout,
                checkoutAttempts = f.checkoutAttempts,
                marksTotal = f.marksTotal,
                tripleHits = f.tripleHits,
                bullHits = f.bullHits,
                closedAllSectionLegs = f.closedAllSectionLegs,
                turnsInClosedLegs = f.turnsInClosedLegs,
                closedSectionsTotal = f.closedSectionsTotal,
                // 写 token（二期 2A）：数字分区仍是 "20" / "25"，与一期逐字节相同；
                // 类别档写 "D" / "T"。用 token 而不是 toString()，后者会写出 "Number(value=20)"。
                firstClosedCsv = f.firstClosed.joinToString(",") { it.token },
            )
        }
    }

    private fun matchTypeLabel(type: MatchType): String = when (type) {
        MatchType.X01 -> "X01"
        MatchType.CRICKET -> "Cricket"
        MatchType.AROUND_THE_CLOCK -> "Around the Clock"
        MatchType.SHANGHAI -> "Shanghai"
        MatchType.HALVE_IT -> "Halve It"
        MatchType.KILLER -> "Killer"
    }
}
