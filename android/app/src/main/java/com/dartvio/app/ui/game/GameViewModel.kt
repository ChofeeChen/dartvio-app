package com.dartvio.app.ui.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dartvio.app.DartVioApp
import com.dartvio.app.data.DartHitRepository
import com.dartvio.app.data.MatchRepository
import com.dartvio.app.data.achievement.AchievementRepository
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.data.local.MatchMapper
import com.dartvio.app.data.local.entity.DartHitEntity
import com.dartvio.app.data.profile.ProfileStore
import com.dartvio.app.domain.achievement.AchievementProgress
import com.dartvio.app.domain.model.*
import com.dartvio.app.domain.rules.AdaptiveAiController
import com.dartvio.app.domain.rules.AiProfile
import com.dartvio.app.domain.rules.CricketAi
import com.dartvio.app.domain.rules.CricketRules
import com.dartvio.app.domain.rules.X01Ai
import com.dartvio.app.domain.rules.X01Rules
import com.dartvio.app.domain.stats.MatchFacts
import com.dartvio.app.ui.components.BoardTap
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 对局主 ViewModel。统一管理 X01 与 Cricket 的生命周期。
 * PRD M4：回合管理、legsToWin、多局模式、结束判定、AI 托管。
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: MatchRepository = when (val ctx = app.applicationContext) {
        is DartVioApp -> ctx.matchRepository
        else -> MatchRepository(DartVioDatabase.get(ctx).matchRecordDao())
    }

    private val achievementRepository: AchievementRepository = when (val ctx = app.applicationContext) {
        is DartVioApp -> ctx.achievementRepository
        else -> AchievementRepository(ctx, DartVioDatabase.get(ctx).matchRecordDao())
    }

    // `DartVioDatabase.get()` 本身是进程级单例，这里不必再走 DartVioApp 也能共用同一个库。
    private val hitRepository: DartHitRepository = DartHitRepository(
        DartVioDatabase.get(app.applicationContext).dartHitDao()
    )

    /**
     * 本机玩家的稳定身份 ID（③B 前置 P0）：落库时替换本机席位的位置 ID（`"p1"`）。
     *
     * 走 `DartVioApp.localProfileId`（惰性 + 幂等建档，进程内只读一次）；
     * 兜底分支直接读 prefs —— 兜底路径本来只在 `DartVioApp` 不可用时走到（如单元测试
     * 用裸 Application），这里保底不再多做缓存。
     */
    private val localProfileId: String = when (val ctx = app.applicationContext) {
        is DartVioApp -> ctx.localProfileId
        else -> ProfileStore.ensure(ctx).profileId
    }

    /** 已落库的比赛 ID，保证同一场比赛只写一次。 */
    private var persistedMatchId: String? = null

    /**
     * 本场通过靶盘点选采集到的点位，待落库（`matchId` 要等比赛结束才生成）。
     *
     * **只采本机真人**：AI 托管的镖没有点位，键盘录入的镖也不调用这里 ——
     * 热力图要回答的是「这个人的手感随时间的收敛」，混进别人的点等于自毁数据。
     */
    private data class PendingHit(
        val legNumber: Int,
        val xMm: Float,
        val yMm: Float,
        val number: Int,
        val multiplier: Int,
        val hitAt: Long
    )

    private val pendingHits = mutableListOf<PendingHit>()

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /** 本场结算后新解锁的成就，供胜利页展示解锁卡片（决策④ 主路径）。 */
    private val _newlyUnlocked = MutableStateFlow<List<AchievementProgress>>(emptyList())
    val newlyUnlocked: StateFlow<List<AchievementProgress>> = _newlyUnlocked.asStateFlow()

    /** 当前正在执行的 AI 出镖协程，用于避免重复触发 / 支持取消。 */
    private var aiJob: Job? = null

    /** 每个 AI 玩家对应的自适应难度控制器（键为 playerId，M4 §6.1.1）。 */
    private val aiControllers = mutableMapOf<String, AdaptiveAiController>()

    /** AI 显示延迟的随机源（独立于出镖随机，避免相互干扰）。 */
    private val aiDelayRandom = Random(System.currentTimeMillis())

    /** 开始一场新比赛。支持 1..4 人（1 人为练习，2-4 人对战）。 */
    fun startMatch(config: MatchConfig, players: List<Player>) {
        require(players.size in 1..4) { "玩家人数需为 1-4 人" }
        aiJob?.cancel()
        aiJob = null
        persistedMatchId = null
        _newlyUnlocked.value = emptyList()
        _uiState.update { current ->
            GameUiState(
                config = config,
                players = players,
                x01Leg = if (config.matchType == MatchType.X01)
                    X01Rules.newLeg(config, players, 1) else null,
                cricketLeg = if (config.matchType == MatchType.CRICKET)
                    CricketRules.newLeg(config, players, 1) else null,
                isMatchFinished = false,
                message = null
            )
        }
        // 按赛制与 AI 玩家重建自适应控制器（多局 + 智能难度开启时自适应才生效）。
        aiControllers.clear()
        players.filter { it.isAi }.forEach { p ->
            aiControllers[p.id] = AdaptiveAiController.forMatch(
                config = config,
                difficulty = p.aiDifficulty ?: AiDifficulty.INTERMEDIATE,
                smartEnabled = config.smartAi
            )
        }

        // 若开局第一位就是 AI，立即托管。
        scheduleAiTurnIfNeeded()
    }

    /**
     * 若当前轮到 AI，则自动完成其回合（逐镖 + 停顿，便于观感）。
     * 人类回合 / 比赛结束 / 已有 AI 任务在跑时不做任何事。
     * 支持 X01 与 Cricket 两种玩法。
     */
    fun scheduleAiTurnIfNeeded() {
        val s = _uiState.value
        val player = s.currentPlayer ?: return
        if (!player.isAi) return
        if (s.isMatchFinished) return
        if (aiJob?.isActive == true) return
        if (s.config.matchType != MatchType.X01 && s.config.matchType != MatchType.CRICKET) return

        val isX01 = s.config.matchType == MatchType.X01

        aiJob = viewModelScope.launch {
            while (true) {
                val current = _uiState.value
                if (current.isMatchFinished) break
                val curPlayer = current.currentPlayer ?: break
                if (!curPlayer.isAi) break

                // 取该 AI 当前生效的强度画像（含自适应后的 PPR）。
                val profile: AiProfile = aiControllers[curPlayer.id]?.profile()
                    ?: AiProfile.of(curPlayer.aiDifficulty ?: AiDifficulty.INTERMEDIATE)

                // 两种玩法的回合产出统一成 ClaimedDart：X01 没有归属裁决，恒为 NUMBER。
                val darts: List<ClaimedDart> = if (isX01) {
                    val leg = current.x01Leg ?: break
                    val shooter = leg.players[leg.currentPlayerIndex]
                    // 把开镖状态一并交给 AI：双倍入 / 大师入下它得先打出合规的开镖镖。
                    X01Ai.generateTurn(
                        remaining = shooter.remaining,
                        profile = profile,
                        config = current.config,
                        hasOpened = shooter.hasOpened
                    ).map { ClaimedDart(it) }
                } else {
                    val leg = current.cricketLeg ?: break
                    CricketAi.generateTurn(leg, profile)
                }

                // 逐镖投掷，每镖间按难度延迟，让玩家看清三镖显示区的分数。
                for (claimed in darts) {
                    if (_uiState.value.isMatchFinished) break
                    throwDart(claimed.dart, DartSource.AI_GENERATED, claimed.claim)
                    delay(profile.nextDelayMs(aiDelayRandom))
                    val now = _uiState.value
                    if (now.isMatchFinished) break
                    // 本镖被自动结算清空（如 X01 爆分）时停止续投。
                    if (now.turnDartsCount == 0) break
                }
                if (_uiState.value.isMatchFinished) break
                delay(profile.nextDelayMs(aiDelayRandom) / 2)
                // 满 3 镖后需手动确认：AI 自动完成"确认"动作。
                commitTurn()
                delay(300)
            }
            aiJob = null
        }
    }

    /** 停止 AI 托管（例如玩家退出或比赛结束）。 */
    fun cancelAi() {
        aiJob?.cancel()
        aiJob = null
    }

    override fun onCleared() {
        super.onCleared()
        aiJob?.cancel()
    }

    /**
     * 录入一支镖。
     *
     * @param source 录入来源（M3 `input_source`）。M12 手机视觉识别走 [DartSource.PHONE_VISION]，
     *   且**必须经用户确认**后才可调用本方法；默认值为键盘逐镖录入，既有调用点无需改动。
     * @param claim 归属裁决（二期 2C，仅 Cricket 有意义）。默认 [DartClaim.NUMBER] 保持一期行为：
     *   X01 与三种标准变体都不需要裁决，既有调用点无需改动。
     */
    fun throwDart(
        dart: Dart,
        source: DartSource = DartSource.DEFAULT,
        claim: DartClaim = DartClaim.NUMBER,
    ) {
        var s = _uiState.value
        if (s.lastDartSource != source) {
            _uiState.update { it.copy(lastDartSource = source) }
            s = _uiState.value
        }
        when (s.config.matchType) {
            MatchType.X01 -> applyX01Dart(s, dart)
            MatchType.CRICKET -> applyCricketDart(s, dart, claim)
            else -> { /* 其他玩法：阶段二实现 */ }
        }
        // 每次投掷后检查是否轮到 AI（人类回合结束时触发 AI 接管）。
        scheduleAiTurnIfNeeded()
    }

    /**
     * 确认并结束当前回合。
     * 用于人类玩家"未投满 3 镖也想换人"（例如故意放弃剩余镖）。
     * 回合内已投的镖按正常规则结算，未投的镖视为放弃。
     */
    fun commitTurn(taps: List<BoardTap> = emptyList()) {
        // 点位随回合入队：一回合一次 commit，正好是「三镖一组」的天然分组。
        val legNumber = _uiState.value.x01Leg?.legNumber ?: 1
        val now = System.currentTimeMillis()
        taps.forEach { tap ->
            pendingHits += PendingHit(
                legNumber = legNumber,
                xMm = tap.xMm,
                yMm = tap.yMm,
                number = tap.dart.number,
                multiplier = tap.dart.multiplier,
                hitAt = now
            )
        }
        val s = _uiState.value
        when (s.config.matchType) {
            MatchType.X01 -> {
                val leg = s.x01Leg ?: return
                if (leg.isFinished) return
                val darts = leg.currentTurnDarts
                if (darts.isEmpty()) return
                val (probeLeg, probeOutcome) = X01Rules.applyTurn(leg, darts)
                val withStats = accumulateX01Turn(s, leg, probeOutcome)
                if (probeOutcome.won) {
                    handleLegWin(withStats, probeLeg, probeOutcome)
                } else {
                    _uiState.update {
                        withStats.copy(
                            x01Leg = probeLeg,
                            message = probeOutcome.message ?: formatOutcomeMessage(probeOutcome)
                        )
                    }
                }
                scheduleAiTurnIfNeeded()
            }
            MatchType.CRICKET -> {
                val leg = s.cricketLeg ?: return
                if (leg.isFinished) return
                if (leg.currentTurnDarts.isEmpty()) return

                // 回合结算：先把本回合的原始事实折入统计，再切换玩家。
                val withStats = accumulateCricketTurn(s)

                // 每镖已即时结算，commit 只负责切换玩家与清空回合。
                // **先收尾本回合**（累加该玩家的回合数）再判定 —— 轮数上限的判据是「全员打满」，
                // 顺序反了会永远差一个回合才结束。
                val committed = CricketRules.endTurn(leg)

                // 判定统一由 M2 返回（M4 §6.4：M4 不得另写一套比较逻辑）。
                val won = CricketRules.isWinningLeg(
                    committed.players,
                    committed.currentPlayerIndex,
                    committed.config
                )

                if (won) {
                    val wonLeg = committed.copy(
                        isFinished = true,
                        winnerIndex = committed.currentPlayerIndex,
                        // 超时终局要能落库说明「这局不是关满赢的」（M9 据此口径）。
                        endedByRoundLimit = CricketRules.isRoundLimitWin(
                            committed.players,
                            committed.currentPlayerIndex,
                            committed.config
                        ),
                    )
                    handleCricketLegWin(withStats, wonLeg)
                } else {
                    val nextIndex = (committed.currentPlayerIndex + 1) % committed.players.size
                    _uiState.update {
                        withStats.copy(
                            cricketLeg = committed.copy(currentPlayerIndex = nextIndex),
                            message = null,
                            cricketTurnEvents = emptyList()
                        )
                    }
                }
                scheduleAiTurnIfNeeded()
            }
            else -> Unit
        }
    }

    /** 撤销当前回合已录入的镖（回合重建）。 */
    fun undoLastDart() {
        val s = _uiState.value
        _uiState.update {
            when (s.config.matchType) {
                MatchType.X01 -> s.copy(
                    x01Leg = s.x01Leg?.let { leg ->
                        if (leg.currentTurnDarts.isNotEmpty())
                            leg.copy(currentTurnDarts = leg.currentTurnDarts.dropLast(1))
                        else leg
                    }
                )
                MatchType.CRICKET -> s.copy(
                    cricketLeg = s.cricketLeg?.let { leg ->
                        if (leg.currentTurnDarts.isEmpty()) {
                            leg
                        } else {
                            // 撤销用**当时记录的** hit 反算，不能用当前状态重算（M4 §6.5「回合事件重建」）：
                            // 此时 marks 已封顶、得分已按变体归属落账，重算会得到 0 标记，
                            // 并把分数还给错误的人（cut_throat 的分记在对手头上）。
                            val playerIndex = leg.currentPlayerIndex
                            val hit = s.cricketTurnEvents.lastOrNull()?.hit
                            val players = leg.players.toMutableList()
                            if (hit != null) {
                                val me = players[playerIndex]
                                players[playerIndex] = me.copy(
                                    marks = me.marks.remove(hit.target, hit.marksGained)
                                )
                                // 得分按**归属**回退：standard 是自己，cut_throat 是各对手。
                                hit.scoreOwnerIds.forEach { ownerId ->
                                    val idx = players.indexOfFirst { it.playerId == ownerId }
                                    if (idx >= 0) {
                                        players[idx] = players[idx].copy(
                                            score = players[idx].score - hit.scoreGained
                                        )
                                    }
                                }
                            }
                            val remaining = leg.currentTurnDarts.dropLast(1)
                            leg.copy(
                                currentTurnDarts = remaining,
                                players = players,
                                // 回合被撤空 ⇒ 改判基线一起清掉，否则下一镖会基于**上一回合**的快照重放。
                                turnStartPlayers = if (remaining.isEmpty()) null else leg.turnStartPlayers,
                            )
                        }
                    },
                    // 逐镖事件与 currentTurnDarts 保持同步出栈（裁决随事件一起出栈）。
                    cricketTurnEvents = if (s.cricketLeg?.currentTurnDarts?.isNotEmpty() == true)
                        s.cricketTurnEvents.dropLast(1) else s.cricketTurnEvents
                )
                else -> s
            }
        }
    }

    /**
     * **改判**当前回合第 [dartIndex] 支镖的归属裁决（二期 2C，M2 §4.9.2⑤）。
     *
     * 窗口 = 投下一镖之前（第三镖的窗口到本回合结束为止），由 UI 侧控制按钮可用性。
     * 实现走 `CricketRules.redeclare` 的「重放本回合序列」，不做任何反向补偿 ——
     * 分数回滚是最容易写错的一类逻辑，重放的成本（≤3 镖）可以忽略。
     */
    fun redeclareCricketDart(dartIndex: Int, claim: DartClaim) {
        val s = _uiState.value
        if (s.config.matchType != MatchType.CRICKET) return
        val leg = s.cricketLeg ?: return
        if (leg.isFinished) return
        if (dartIndex !in leg.currentTurnDarts.indices) return
        // 改判不可改变胜利判定结果：已经打完的回合、或已经结算完的局，不接受改判。
        if (leg.currentTurnDarts.size >= MAX_DARTS_PER_TURN) return

        val (replayed, hits) = CricketRules.redeclare(leg, dartIndex, claim)
        if (hits.isEmpty()) return

        // 重放会改动**整段序列**的结算结果，所以事件表整体重建，而不是只改一项。
        // 「首关」按标记游标从回合基线上逐镖推进推导 —— 不能用改判后的最终板面倒推
        // （最终板面看不出是哪一镖关的）。
        val baseMarks = (leg.turnStartPlayers ?: leg.players)[leg.currentPlayerIndex].marks
        val running = mutableMapOf<CricketTarget, Int>()
        val events = replayed.currentTurnDarts.mapIndexed { index, claimed ->
            val hit = hits.getOrNull(index)
            val target = hit?.target
            val justClosed = if (hit != null && target != null) {
                val before = running[target] ?: baseMarks.marksOf(target)
                running[target] = (before + hit.marksGained).coerceAtMost(MAX_MARKS)
                if (before < MAX_MARKS && running.getValue(target) >= MAX_MARKS) target else null
            } else {
                null
            }
            CricketTurnEvent(claimed.dart, hit, justClosed, claimed.claim)
        }

        val won = CricketRules.isWinningLeg(
            replayed.players,
            replayed.currentPlayerIndex,
            replayed.config
        )
        if (won) {
            handleCricketLegWin(
                s.copy(cricketLeg = replayed, cricketTurnEvents = events),
                replayed.copy(
                    isFinished = true,
                    winnerIndex = replayed.currentPlayerIndex,
                ),
            )
        } else {
            _uiState.update { s.copy(cricketLeg = replayed, cricketTurnEvents = events) }
        }
    }

    /**
     * 录入一支 X01 镖。
     * 关键行为：**满 3 镖后不自动换人**，需玩家手动点击「确认」结束回合；
     * 仅当本镖直接"获胜"或"爆分"时才立即结算（否则 3 镖后仍有确认机会）。
     */
    private fun applyX01Dart(s: GameUiState, dart: Dart) {
        val leg = s.x01Leg ?: return
        if (leg.isFinished) return
        // 已满 3 镖（等待确认）时不再接受新镖。
        if (leg.currentTurnDarts.size >= 3) return

        val turnDarts = leg.currentTurnDarts + dart
        val updatedLeg = leg.copy(currentTurnDarts = turnDarts)

        // 试算：仅在"获胜/爆分"时立即结算；满 3 镖由玩家手动确认。
        val (probeLeg, probeOutcome) = X01Rules.applyTurn(leg, turnDarts)
        val isTurnOver = probeOutcome.won || probeOutcome.result == TurnResult.BUST

        if (!isTurnOver) {
            _uiState.update { s.copy(x01Leg = updatedLeg) }
            return
        }

        val withStats = accumulateX01Turn(s, leg, probeOutcome)
        if (probeOutcome.won) {
            handleLegWin(withStats, probeLeg, probeOutcome)
        } else {
            _uiState.update {
                withStats.copy(
                    x01Leg = probeLeg,
                    message = probeOutcome.message ?: formatOutcomeMessage(probeOutcome)
                )
            }
        }
    }

    /**
     * 录入一支 Cricket 镖。
     * 每镖即时更新玩家 marks 与 score；满 3 镖后需手动点击确认结束回合；
     * 直接获胜时立即结算。
     */
    private fun applyCricketDart(s: GameUiState, dart: Dart, claim: DartClaim) {
        val leg = s.cricketLeg ?: return
        if (leg.isFinished) return
        if (leg.currentTurnDarts.size >= MAX_DARTS_PER_TURN) return

        val prePlayer = leg.currentPlayer
        val (updatedLeg, hit) = CricketRules.applySingleDart(leg, ClaimedDart(dart, claim))

        // C10 首关分区：本镖是否让某目标位首次达到 3 标记。
        val justClosed = hit?.let { h ->
            val nowPlayer = updatedLeg.players[updatedLeg.currentPlayerIndex]
            val wasClosed = prePlayer.marks.marksOf(h.target) >= MAX_MARKS
            val nowClosed = nowPlayer.marks.marksOf(h.target) >= MAX_MARKS
            if (!wasClosed && nowClosed) h.target else null
        }

        // 逐镖事件与 currentTurnDarts 同步增删，回合结算时折入统计。
        // 裁决随事件一起存：撤销时它随事件出栈，改判时它被整体重建。
        val withEvent = s.copy(
            cricketLeg = updatedLeg,
            cricketTurnEvents = s.cricketTurnEvents +
                CricketTurnEvent(dart, hit, justClosed, claim)
        )

        // 检查是否已满足胜利条件。
        // 判定统一由 M2 返回（M4 §6.4：M4 不得另写一套比较逻辑）—— 变体化后
        // cut_throat 是「低分领先」、no_score 根本不比分数，本地再算一遍必然跑偏。
        val won = CricketRules.isWinningLeg(
            updatedLeg.players,
            updatedLeg.currentPlayerIndex,
            updatedLeg.config
        )

        if (won) {
            val wonLeg = updatedLeg.copy(
                isFinished = true,
                winnerIndex = updatedLeg.currentPlayerIndex
            )
            handleCricketLegWin(withEvent, wonLeg)
        } else {
            _uiState.update { withEvent }
        }
    }

    private fun handleLegWin(s: GameUiState, newLeg: X01LegState, outcome: TurnOutcome) {
        val winnerId = outcome.playerId
        val legsWonMap = s.legsWon.toMutableMap()
        legsWonMap[winnerId] = (legsWonMap[winnerId] ?: 0) + 1

        val reached = s.config.mode == MatchMode.CASUAL ||
            (legsWonMap[winnerId] ?: 0) >= s.config.legsToWin

        if (reached) {
            _uiState.update {
                s.copy(
                    x01Leg = newLeg,
                    legsWon = legsWonMap,
                    isMatchFinished = true,
                    winnerPlayerId = winnerId,
                    message = "GAME SHOT"
                )
            }
            persistFinishedMatch(_uiState.value)
        } else {
            // 本局获胜但不是决胜局：记录胜利信息，展示本局胜利页，等待玩家确认进入下一局。
            _uiState.update {
                s.copy(
                    x01Leg = newLeg,
                    legsWon = legsWonMap,
                    showLegSummary = true,
                    lastLegWinnerId = winnerId
                )
            }
        }
    }

    private fun handleCricketLegWin(s: GameUiState, newLeg: CricketLegState) {
        // 局末把本局的分区级事实（关闭数 / 是否关满 / 首关分区）折入统计。
        val base = accumulateCricketLegEnd(s, newLeg)
        val winnerId = newLeg.players[newLeg.winnerIndex ?: 0].playerId
        val legsWonMap = base.legsWon.toMutableMap()
        legsWonMap[winnerId] = (legsWonMap[winnerId] ?: 0) + 1

        val reached = base.config.mode == MatchMode.CASUAL ||
            (legsWonMap[winnerId] ?: 0) >= base.config.legsToWin

        if (reached) {
            _uiState.update {
                base.copy(
                    cricketLeg = newLeg,
                    legsWon = legsWonMap,
                    isMatchFinished = true,
                    winnerPlayerId = winnerId,
                    message = "WINNER"
                )
            }
            persistFinishedMatch(_uiState.value)
        } else {
            _uiState.update {
                base.copy(
                    cricketLeg = newLeg,
                    legsWon = legsWonMap,
                    showLegSummary = true,
                    lastLegWinnerId = winnerId
                )
            }
        }
    }

    /**
     * 本局胜利页 → 进入下一局。
     * 由胜利页的"继续下一局"按钮触发。
     */
    fun continueToNextLeg() {
        val s = _uiState.value
        when (s.config.matchType) {
            MatchType.X01 -> {
                val current = s.x01Leg ?: return
                val nextLeg = X01Rules.newLeg(s.config, s.players, current.legNumber + 1)
                _uiState.update {
                    s.copy(
                        x01Leg = nextLeg,
                        showLegSummary = false,
                        lastLegWinnerId = null,
                        message = null,
                        // 局级累计器随新局重置；facts 跨局保留。
                        legTurns = emptyMap(),
                        legFirstClosed = emptyMap(),
                        cricketTurnEvents = emptyList()
                    )
                }
                scheduleAiTurnIfNeeded()
            }
            MatchType.CRICKET -> {
                val current = s.cricketLeg ?: return
                val nextLeg = CricketRules.newLeg(s.config, s.players, current.legNumber + 1)
                _uiState.update {
                    s.copy(
                        cricketLeg = nextLeg,
                        showLegSummary = false,
                        lastLegWinnerId = null,
                        message = null,
                        legTurns = emptyMap(),
                        legFirstClosed = emptyMap(),
                        cricketTurnEvents = emptyList()
                    )
                }
                scheduleAiTurnIfNeeded()
            }
            else -> Unit
        }
    }

    fun dismissLegSummary() {
        _uiState.update { it.copy(showLegSummary = false, lastLegWinnerId = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // ------------------------------------------------------------ 落库

    /** 比赛结束时把本场事实写入对局历史（同一场只写一次）。 */
    private fun persistFinishedMatch(state: GameUiState) {
        if (!state.isMatchFinished) return
        if (persistedMatchId != null) return
        val matchId = MatchMapper.matchId()
        persistedMatchId = matchId
        val endedAt = System.currentTimeMillis()
        val record = MatchMapper.toMatchEntity(state, matchId, endedAt, localProfileId)
        val players = MatchMapper.toPlayerEntities(state, matchId, localProfileId)
        /*
         * 落库**不随页面销毁被取消**（2026-09-27 反馈：打完一局，数据页却没有）。
         *
         * 收镖 → 结算页弹出 → 退出，是一次连贯动作；结算页上点「返回首页」会立刻
         * 清掉这个 ViewModel，而 `viewModelScope` 的取消来得比 Room 那次写入更快 ——
         * 于是这一场凭空消失，且没有任何报错：用户只知道「数据页是空的」。
         * 对局历史是用户资产，写它这件事不该由「他退出得够不够慢」决定。
         */
        viewModelScope.launch(NonCancellable) {
            repository.saveMatch(record, players)
            flushPendingHits(matchId)
            // 决策④ 主路径：对局落库后立即重算成就，把「本场新解锁」交给胜利页展示。
            // 重算是异步的，用 matchId 兜底 —— 用户抢在重算完成前点了「再来一局」时，
            // 不能让上一场的成就飘到新一局的胜利页上。
            val snapshot = achievementRepository.recalculate(endedAt)
            if (persistedMatchId == matchId) {
                _newlyUnlocked.value = snapshot.items.filter { it.id in snapshot.newlyUnlockedIds }
            }
        }
    }

    /**
     * 落库本场采集到的点位，与整场记录**同一批**（同一个 matchId）。
     *
     * 写完立刻清空：万一用户在胜利页点了「再来一局」，
     * 上一场的点位不能漂到下一场的 matchId 上 —— 那会让收敛曲线凭空多出一截假历史。
     */
    private fun flushPendingHits(matchId: String) {
        if (pendingHits.isEmpty()) return
        val entities = pendingHits.map { h ->
            DartHitEntity(
                matchId = matchId,
                profileId = localProfileId,
                legNumber = h.legNumber,
                xMm = h.xMm,
                yMm = h.yMm,
                number = h.number,
                multiplier = h.multiplier,
                source = DartSource.BOARD_TAP.name,
                hitAt = h.hitAt
            )
        }
        pendingHits.clear()
        viewModelScope.launch { hitRepository.saveHits(entities) }
    }

    // ------------------------------------------------------------ 统计累计
    // 只在「回合结算」时累加、「开局 / 进入下一局」时重置，
    // 因此 undoLastDart 天然安全：撤销只作用于尚未结算的当前回合。

    private fun accumulateX01Turn(
        s: GameUiState,
        legBefore: X01LegState,
        outcome: TurnOutcome
    ): GameUiState {
        val before = legBefore.players.firstOrNull { it.playerId == outcome.playerId }
        val darts = outcome.darts.size
        // 自适应采样：仅统计真人回合（排除 AI 托管回合，M4 §6.1.1②）。
        if (isHumanPlayer(s, outcome.playerId)) {
            aiControllers.values.forEach { it.recordHumanTurn(outcome.scored, darts) }
        }
        // 回合开始时剩余分已可一回合收完 → 记一次收镖尝试（Checkout 率分母）。
        val canCheckout = before != null && before.remaining <= maxCheckoutScore(s.config)
        return s.copy(
            facts = s.facts.update(outcome.playerId) { f ->
                f.copy(
                    dartsThrown = f.dartsThrown + darts,
                    turnsPlayed = f.turnsPlayed + 1,
                    totalScore = f.totalScore + outcome.scored,
                    maxTurnScore = maxOf(f.maxTurnScore, outcome.scored),
                    count180 = f.count180 + if (darts == 3 && outcome.scored == 180) 1 else 0,
                    busts = f.busts + if (outcome.result == TurnResult.BUST) 1 else 0,
                    checkoutAttempts = f.checkoutAttempts + if (canCheckout) 1 else 0,
                    bestCheckout = if (outcome.won) maxOf(f.bestCheckout, outcome.scored)
                    else f.bestCheckout,
                )
            },
            legTurns = s.legTurns + (outcome.playerId to (s.legTurns[outcome.playerId] ?: 0) + 1),
        )
    }

    private fun accumulateCricketTurn(s: GameUiState): GameUiState {
        val leg = s.cricketLeg ?: return s
        val events = s.cricketTurnEvents
        if (events.isEmpty()) return s
        val playerId = leg.players[leg.currentPlayerIndex].playerId
        val score = events.sumOf { it.hit?.scoreGained ?: 0 }
        val marks = events.sumOf { it.hit?.marksGained ?: 0 }
        // 自适应采样：仅统计真人回合（M4 §6.1.1②）。
        if (isHumanPlayer(s, playerId)) {
            aiControllers.values.forEach { it.recordHumanTurn(score, events.size) }
        }
        // C3 分母与 C4 分母同为 dartsThrown，故这里只累计分子。
        val triples = events.count { it.dart.multiplier == 3 }
        val bulls = events.count { it.dart.number == BULL_NUMBER }
        val firstClosedThisLeg = events.firstNotNullOfOrNull { it.justClosed }
        return s.copy(
            facts = s.facts.update(playerId) { f ->
                f.copy(
                    dartsThrown = f.dartsThrown + events.size,
                    turnsPlayed = f.turnsPlayed + 1,
                    totalScore = f.totalScore + score,
                    maxTurnScore = maxOf(f.maxTurnScore, score),
                    marksTotal = f.marksTotal + marks,
                    tripleHits = f.tripleHits + triples,
                    bullHits = f.bullHits + bulls,
                )
            },
            legTurns = s.legTurns + (playerId to (s.legTurns[playerId] ?: 0) + 1),
            legFirstClosed = if (firstClosedThisLeg != null) {
                s.legFirstClosed + (playerId to firstClosedThisLeg)
            } else s.legFirstClosed,
            cricketTurnEvents = emptyList(),
        )
    }

    /** 局末折入分区级事实（C2 关闭数 / C6 关满局 / C10 首关分区）。 */
    private fun accumulateCricketLegEnd(s: GameUiState, leg: CricketLegState): GameUiState {
        val targets = leg.config.cricketTargets
        var facts = s.facts
        leg.players.forEach { p ->
            val closedCount = p.closedCount(targets)
            // 超时局不计入「关满局数」（M2 §4.9.8）：本局不是以「关满」结束的，
            // 即使某位玩家在此刻恰好已关满也要排除，否则 C6 的分母会混进非关满结束的局。
            val closedAll = !leg.endedByRoundLimit && p.hasClosedAll(targets)
            val turns = s.legTurns[p.playerId] ?: 0
            val first = s.legFirstClosed[p.playerId]
            facts = facts.update(p.playerId) { f ->
                f.copy(
                    closedSectionsTotal = f.closedSectionsTotal + closedCount,
                    closedAllSectionLegs = f.closedAllSectionLegs + if (closedAll) 1 else 0,
                    // C6 仅统计关满 7 分区的局，未关满的局不计入分母。
                    turnsInClosedLegs = f.turnsInClosedLegs + if (closedAll) turns else 0,
                    firstClosed = if (first != null) f.firstClosed + first else f.firstClosed,
                )
            }
        }
        return s.copy(facts = facts, legTurns = emptyMap(), legFirstClosed = emptyMap())
    }

    /** Double-Out 时最高可收镖分 170，未开启则 180。 */
    private fun maxCheckoutScore(config: MatchConfig): Int =
        if (config.doubleOut) 170 else 180

    /** 某玩家是否为真人（自适应采样只统计真人）。 */
    private fun isHumanPlayer(state: GameUiState, playerId: String): Boolean =
        state.players.firstOrNull { it.id == playerId }?.isAi == false

    private fun formatOutcomeMessage(outcome: TurnOutcome): String? = when (outcome.result) {
        TurnResult.BUST -> "BUST"
        TurnResult.NO_SCORE -> "NO SCORE"
        else -> null
    }
}

/** Cricket 逐镖事件：与 currentTurnDarts 同步增删，回合结算时折入 [MatchFacts]。 */
data class CricketTurnEvent(
val dart: Dart,
val hit: CricketHitResult?,
/** 本镖是否让某目标位首次关闭（C10 首关分区）。 */
val justClosed: CricketTarget?,
/**
 * 本镖的归属裁决（二期 2C）。
 *
 * 存在事件里而不是另存一张表：撤销时它随事件一起出栈、改判时事件表整体重建，
 * 两条路径都不需要额外的同步逻辑。缺省 [DartClaim.NUMBER] 让 X01 与一期调用点无需改动。
 */
val claim: DartClaim = DartClaim.NUMBER,
)

/** Cricket 计分板的 Bull 分区号。 */
private const val BULL_NUMBER = 25

/** 标记封顶值（关闭门槛）。 */
private const val MAX_MARKS = 3

/** Cricket 每回合最多 3 镖。 */
private const val MAX_DARTS_PER_TURN = 3

/** 对局 UI 状态。 */
data class GameUiState(
    val config: MatchConfig = MatchConfig(),
    val players: List<Player> = emptyList(),
    val x01Leg: X01LegState? = null,
    val cricketLeg: CricketLegState? = null,
    val legsWon: Map<String, Int> = emptyMap(),
    val isMatchFinished: Boolean = false,
    val winnerPlayerId: String? = null,
    val showLegSummary: Boolean = false,
    val lastLegWinnerId: String? = null,
    val message: String? = null,
    /** 本场累计的原始统计事实（只在回合结算时累加，跨局保留）。 */
    val facts: MatchFacts = MatchFacts(),
    /** 本局各玩家已结算的回合数（C6 分母用，换局清空）。 */
    val legTurns: Map<String, Int> = emptyMap(),
    /** 本局各玩家首个关闭的目标位（C10，换局清空）。 */
    val legFirstClosed: Map<String, CricketTarget> = emptyMap(),
    /** Cricket 当前回合的逐镖事件。 */
    val cricketTurnEvents: List<CricketTurnEvent> = emptyList(),
    /** 本场比赛开始时间。 */
    val startedAt: Long = System.currentTimeMillis(),
    /**
     * 最近一支镖的录入来源（M3 `input_source` / M12）。
     * 说明：当前仅内存态；落库为后续批次（需 Room schema 升级，见《PRD_批次4变更清单.md》待办）。
     */
    val lastDartSource: DartSource = DartSource.DEFAULT,
) {
    val isX01: Boolean get() = config.matchType == MatchType.X01
    val isCricket: Boolean get() = config.matchType == MatchType.CRICKET

    /**
     * 当前回合已录入的镖（**仅 X01**）。
     *
     * Cricket 的回合镖带归属裁决与结算明细，不能降级成裸 [Dart] —— 去 [cricketTurnEvents] 取。
     */
    val currentTurnDarts: List<Dart>
        get() = x01Leg?.currentTurnDarts ?: emptyList()

    /**
     * 当前回合已录入的镖数（**两种玩法通用**）。
     *
     * 「本镖是否已被自动结算清空」这类判断必须用它：Cricket 的 [currentTurnDarts] 恒为空，
     * 拿它当判据会让 AI 只投一镖就停手。
     */
    val turnDartsCount: Int
        get() = x01Leg?.currentTurnDarts?.size ?: cricketLeg?.currentTurnDarts?.size ?: 0

    val currentPlayerIndex: Int
        get() = x01Leg?.currentPlayerIndex ?: cricketLeg?.currentPlayerIndex ?: 0

    val currentPlayer: Player?
        get() = players.getOrNull(currentPlayerIndex)

    fun playerById(id: String): Player? = players.firstOrNull { it.id == id }
    fun legsWonOf(id: String): Int = legsWon[id] ?: 0

    /** 当前比分文本，如 "P1 1 : 0 P2"；无胜局时返回 null。 */
    fun legsWonText(): String? {
        if (legsWon.values.none { it > 0 }) return null
        return players.joinToString(" : ") { "${legsWon[it.id] ?: 0}" }
    }
}
