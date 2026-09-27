package com.dartvio.app.ui.practice

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dartvio.app.data.CheckoutRushRepository
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.DartSource
import com.dartvio.app.domain.practice.CheckoutRushOutcome
import com.dartvio.app.domain.practice.CheckoutRushRules
import com.dartvio.app.domain.practice.CheckoutTarget
import com.dartvio.app.domain.practice.CheckoutTargetFactory
import com.dartvio.app.domain.practice.RushAttemptRecord
import com.dartvio.app.domain.practice.RushDifficulty
import com.dartvio.app.domain.practice.RushResult
import com.dartvio.app.domain.rules.X01Rules
import com.dartvio.app.ui.components.InputMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 一题的生命周期阶段。
 *
 * 顺序固定：`COUNTDOWN → THROWING → INPUT → RESULT`，只有 [BACKGROUND] 是旁支
 * （切后台时从计时中的任一阶段切入，等用户选「重新开始本题」或「退出训练」）。
 */
enum class RushPhase {
    /** 3 / 2 / 1 倒计时，**尚未揭示**目标分，throwElapsedMs 未启动。 */
    COUNTDOWN,

    /** 已揭示目标分，投掷计时中。 */
    THROWING,

    /** 已点「完成投掷」：throw 冻结、录入计时中。 */
    INPUT,

    /** 已确认结果，展示单题结果。 */
    RESULT,

    /** 切后台暂停：等待用户选择重开本题或退出。 */
    BACKGROUND,
}

data class RushUiState(
    val phase: RushPhase = RushPhase.COUNTDOWN,
    val kind: RushSessionKind = RushSessionKind.TEN,
    val difficulty: RushDifficulty = RushDifficulty.MIXED,
    val sessionId: String = "",
    /** 第几题（1 起）。 */
    val problemIndex: Int = 0,
    val target: Int = 0,
    val routes: List<List<Dart>> = emptyList(),
    val darts: List<Dart> = emptyList(),
    val countdown: Int = 0,
    /** 主成绩计时（ms）：揭示目标分 → 点击「完成投掷」。 */
    val throwElapsedMs: Long = 0,
    /** 交互分析计时（ms）：开始录入 → 确认结果。**永不参与训练成绩**。 */
    val inputElapsedMs: Long = 0,
    val routeHintUsed: Boolean = false,
    val hintExpanded: Boolean = false,
    val outcome: CheckoutRushOutcome? = null,
    /** 本题开始前，同难度的无提示个人最佳（用于「是否刷新」）。 */
    val bestBeforeMs: Long? = null,
    val isNewBest: Boolean = false,
    /** 当前连续成功数。 */
    val streak: Int = 0,
    val inputMode: InputMode = InputMode.KEYPAD,
    /** 上一题的 attempt id：「再试一次」靠它做 `retryOfAttemptId` 关联。 */
    val lastAttemptId: Long = 0,
    /** 「再试一次」指向的原记录 id；新题为 0。 */
    val pendingRetryOf: Long = 0,
    val timingInvalidated: Boolean = false,
    /** 已录入镖时切换输入方式，先弹确认。 */
    val pendingSwitchConfirm: Boolean = false,
    /** 训练结束（10 题做完 / 自由练习主动结束）→ 交给报告页。 */
    val completedSessionId: String? = null,
) {
    /** 本题实际难度：混合档要按抽到的分数反推，个人最佳才可比。 */
    val actualDifficulty: RushDifficulty
        get() = CheckoutTargetFactory.difficultyOf(target) ?: difficulty
}

/**
 * 极速结镖 ViewModel。
 *
 * ## 计时口径（提示词 §六）—— 两条计时，职责不混
 * - [RushUiState.throwElapsedMs] 是**唯一的训练成绩**：揭示目标分开始，点「完成投掷」冻结，
 *   之后无论录入花多久都不再变化（§十一.10）。
 * - [RushUiState.inputElapsedMs] 只用于分析录入交互效率，**永不参与成绩排名**。
 *
 * ## 为什么计时用 `elapsedRealtime` 而不是 `delay` 累加
 * `delay` 的间隔不保证精确，累加会把调度误差计进成绩；
 * 这里只记录起止时刻、由 ticker 负责刷新显示，误差不会累积。
 */
class CheckoutRushViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = CheckoutRushRepository(app.applicationContext)

    private val _state = MutableStateFlow(RushUiState())
    val state: StateFlow<RushUiState> = _state.asStateFlow()

    /** 最近出过的题目，用于避免连续重复（只在本次会话内有效，不落库）。 */
    private val recent = ArrayDeque<Int>()

    private var throwStartedAt = 0L
    private var inputStartedAt = 0L
    private var ticker: Job? = null

    fun start(kind: RushSessionKind, difficulty: RushDifficulty, sessionId: String) {
        ticker?.cancel()
        recent.clear()
        _state.value = RushUiState(
            kind = kind,
            difficulty = difficulty,
            sessionId = sessionId,
            problemIndex = 0,
        )
        nextProblem()
    }

    // =================================================================================
    // 出题 / 倒计时
    // =================================================================================

    private fun nextProblem(targetOverride: Int? = null, retryOf: Long = 0) {
        val s = _state.value
        val index = s.problemIndex + 1
        // 新题由工厂抽（它连路线一起给，口径与领域层一致）；「再试一次」是同一个目标分重开，
        // 但也走同一条取路线的路径，不另写一份 `routesOf`。
        val picked = targetOverride?.let { CheckoutTarget(it, X01Rules.checkoutRoutes(it)) }
            ?: CheckoutTargetFactory.nextTarget(s.difficulty, recent.toSet())
        val target = picked.score
        val routes = picked.routes
        recent.addLast(target)
        while (recent.size > RECENT_WINDOW) recent.removeFirst()

        _state.value = s.copy(
            phase = RushPhase.COUNTDOWN,
            problemIndex = index,
            target = target,
            routes = routes,
            darts = emptyList(),
            countdown = COUNTDOWN_SECONDS,
            throwElapsedMs = 0,
            inputElapsedMs = 0,
            routeHintUsed = false,
            hintExpanded = false,
            outcome = null,
            bestBeforeMs = null,
            isNewBest = false,
            pendingRetryOf = retryOf,
            timingInvalidated = false,
            pendingSwitchConfirm = false,
        )
        captureBest(_state.value.actualDifficulty)
        startTicker()
    }

    private fun captureBest(difficulty: RushDifficulty) {
        viewModelScope.launch {
            val best = repository.bestUnhintedThrowMs(difficulty)
            _state.value = _state.value.copy(bestBeforeMs = best)
        }
    }

    /** 倒计时结束 → 揭示目标分并启动投掷计时。 */
    private fun reveal() {
        val s = _state.value
        throwStartedAt = now()
        _state.value = s.copy(phase = RushPhase.THROWING, countdown = 0, throwElapsedMs = 0)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                val s = _state.value
                when (s.phase) {
                    RushPhase.COUNTDOWN -> {
                        delay(COUNTDOWN_TICK_MS)
                        if (s.countdown <= 1) reveal() else _state.value = _state.value.copy(countdown = s.countdown - 1)
                    }

                    RushPhase.THROWING -> {
                        delay(TICK_MS)
                        _state.value = _state.value.copy(throwElapsedMs = now() - throwStartedAt)
                    }

                    RushPhase.INPUT -> {
                        delay(TICK_MS)
                        _state.value = _state.value.copy(inputElapsedMs = now() - inputStartedAt)
                    }

                    else -> return@launch
                }
            }
        }
    }

    // =================================================================================
    // 录入
    // =================================================================================

    /** 点击「完成投掷」：冻结 throw 计时，进入录入。 */
    fun onThrowDone() {
        val s = _state.value
        if (s.phase != RushPhase.THROWING) return
        inputStartedAt = now()
        _state.value = s.copy(phase = RushPhase.INPUT, throwElapsedMs = now() - throwStartedAt)
        startTicker()
    }

    fun addDart(dart: Dart) {
        val s = _state.value
        if (s.phase != RushPhase.INPUT) return
        if (s.darts.size >= CheckoutRushRules.MAX_DARTS) return
        _state.value = s.copy(darts = s.darts + dart)
    }

    /** 逐镖撤销（提示词 §八.4）：只在录入阶段可用，结果提交后不可改。 */
    fun undoLastDart() {
        val s = _state.value
        if (s.phase != RushPhase.INPUT || s.darts.isEmpty()) return
        _state.value = s.copy(darts = s.darts.dropLast(1))
    }

    /**
     * 请求切换输入方式。已录入镖时先弹确认（提示词 §八.3）。
     *
     * 这里显式实现「切换保护」而不是照抄 X01 页 —— 既有 X01 页是**直接切换、无提示**，
     * 本模式按要求补上确认；不去改 X01 页，避免动到已验证的 M7 页面。
     */
    fun requestSwitchInput(mode: InputMode) {
        val s = _state.value
        if (mode == s.inputMode) return
        if (s.darts.isNotEmpty()) {
            _state.value = s.copy(pendingSwitchConfirm = true)
        } else {
            _state.value = s.copy(inputMode = mode)
        }
    }

    /** 切换确认：保留已录镖则只换方式，清除则连同镖一起清。 */
    fun resolveSwitchInput(clearDarts: Boolean) {
        val s = _state.value
        if (!s.pendingSwitchConfirm) return
        _state.value = s.copy(
            pendingSwitchConfirm = false,
            darts = if (clearDarts) emptyList() else s.darts,
        )
    }

    fun cancelSwitchInput() {
        _state.value = _state.value.copy(pendingSwitchConfirm = false)
    }

    // =================================================================================
    // 路线提示（§七）
    // =================================================================================

    /** 展开路线：一旦看过即置 `routeHintUsed = true`，**不可恢复**。 */
    fun showRouteHint() {
        val s = _state.value
        _state.value = s.copy(routeHintUsed = true, hintExpanded = true)
    }

    fun collapseRouteHint() {
        // 只收起面板，`routeHintUsed` 保持 true —— 收起不等于没看过。
        _state.value = _state.value.copy(hintExpanded = false)
    }

    // =================================================================================
    // 结果 / 流转
    // =================================================================================

    fun confirmResult() {
        val s = _state.value
        if (s.phase != RushPhase.INPUT) return
        val outcome = CheckoutRushRules.evaluate(s.target, s.darts)
        val inputMs = now() - inputStartedAt
        ticker?.cancel()
        viewModelScope.launch {
            // 个人最佳必须在**插入本题之前**查：这样查到的一定不含本题，
            // 「是否刷新」不会变成自己跟自己比（插入后再查，最快值可能就是刚写进去的这一条）。
            // 结论同时写回 state，报告里的「原纪录」也用这个权威值，而不是异步预取的那一份。
            val bestBefore = repository.bestUnhintedThrowMs(s.actualDifficulty)
            val id = repository.append(
                s.toRecord(outcome, inputMs)
            )
            val newBest = outcome.result.isSuccess &&
                !s.routeHintUsed &&
                !s.timingInvalidated &&
                (bestBefore == null || s.throwElapsedMs < bestBefore)
            _state.value = _state.value.copy(
                phase = RushPhase.RESULT,
                outcome = outcome,
                inputElapsedMs = inputMs,
                lastAttemptId = id,
                bestBeforeMs = bestBefore,
                isNewBest = newBest,
                streak = if (outcome.result.isSuccess) s.streak + 1 else 0,
            )
        }
    }

    /** 跳过：记 SKIPPED，**不进成功率分母**，但单独计次数（§十.4）。 */
    fun skip() {
        val s = _state.value
        if (s.phase !in setOf(RushPhase.COUNTDOWN, RushPhase.THROWING, RushPhase.INPUT)) return
        val throwMs = if (s.phase == RushPhase.THROWING) now() - throwStartedAt else s.throwElapsedMs
        ticker?.cancel()
        persistAsync(
            s.copy(throwElapsedMs = throwMs).toSkippedRecord(),
            nextAfter = { advance() },
        )
    }

    /** 再试一次：同一目标分重开一题，新 attempt 通过 `retryOfAttemptId` 关联原记录（§十.6）。 */
    fun retry() {
        val s = _state.value
        if (s.phase != RushPhase.RESULT) return
        nextProblem(targetOverride = s.target, retryOf = s.lastAttemptId)
    }

    fun next() {
        if (_state.value.phase != RushPhase.RESULT) return
        advance()
    }

    /** 自由练习 / 10 题挑战的「结束训练」。 */
    fun endTraining() {
        ticker?.cancel()
        _state.value = _state.value.copy(completedSessionId = _state.value.sessionId)
    }

    private fun advance() {
        val s = _state.value
        val limit = s.kind.problemCount
        if (limit != null && s.problemIndex >= limit) {
            _state.value = s.copy(completedSessionId = s.sessionId)
        } else {
            nextProblem()
        }
    }

    // =================================================================================
    // 切后台（§六.5）
    // =================================================================================

    /**
     * 应用进入后台：立即把当前题记为 ABORTED 并冻结计时，等用户回来选择。
     *
     * **先落 ABORTED 再等**：进程若在后台被杀，这一题已经有记录（不形成成绩，但也不至于整题消失）；
     * 用户选「重新开始本题」时再开一条新 attempt 通过 `retryOfAttemptId` 关联上去。
     *
     * 后台时间**绝不**计入成绩：本题置 `timingInvalidated = true`，不参与个人最佳比较。
     */
    fun onAppBackground() {
        val s = _state.value
        if (s.phase !in setOf(RushPhase.COUNTDOWN, RushPhase.THROWING, RushPhase.INPUT)) return
        ticker?.cancel()
        val throwMs = if (s.phase == RushPhase.THROWING) now() - throwStartedAt else s.throwElapsedMs
        _state.value = s.copy(phase = RushPhase.BACKGROUND, throwElapsedMs = throwMs)
        persistAsync(s.copy(throwElapsedMs = throwMs).toAbortedRecord())
    }

    /** 后台返回后「重新开始本题」。 */
    fun restartProblem() {
        val s = _state.value
        if (s.phase != RushPhase.BACKGROUND) return
        nextProblem(targetOverride = s.target, retryOf = s.lastAttemptId)
    }

    /** 后台返回后「退出训练」：当前题已记为 ABORTED，直接结束。 */
    fun exitAfterBackground() {
        _state.value = _state.value.copy(completedSessionId = _state.value.sessionId)
    }

    // =================================================================================
    // 落库
    // =================================================================================

    private fun persistAsync(record: RushAttemptRecord, nextAfter: (() -> Unit)? = null) {
        viewModelScope.launch {
            val id = repository.append(record)
            if (nextAfter != null) {
                _state.value = _state.value.copy(lastAttemptId = id)
                nextAfter()
            }
        }
    }

    private fun RushUiState.toRecord(outcome: CheckoutRushOutcome, inputMs: Long) = RushAttemptRecord(
        sessionId = sessionId,
        createdAt = System.currentTimeMillis(),
        target = target,
        difficulty = actualDifficulty,
        darts = outcome.darts,
        inputMode = inputMode.toDartSource(),
        throwElapsedMs = throwElapsedMs,
        inputElapsedMs = inputMs,
        routeHintUsed = routeHintUsed,
        result = outcome.result,
        remainingAfter = outcome.remainingAfter,
        bustReason = outcome.bustReason,
        retryOfAttemptId = pendingRetryOf,
        timingInvalidated = timingInvalidated,
    )

    private fun RushUiState.toSkippedRecord() = RushAttemptRecord(
        sessionId = sessionId,
        createdAt = System.currentTimeMillis(),
        target = target,
        difficulty = actualDifficulty,
        darts = emptyList(),
        inputMode = inputMode.toDartSource(),
        throwElapsedMs = throwElapsedMs,
        inputElapsedMs = 0,
        routeHintUsed = routeHintUsed,
        result = RushResult.SKIPPED,
        remainingAfter = target,
        bustReason = null,
        retryOfAttemptId = pendingRetryOf,
        timingInvalidated = timingInvalidated,
    )

    private fun RushUiState.toAbortedRecord() = toSkippedRecord().copy(
        result = RushResult.ABORTED,
        inputElapsedMs = if (phase == RushPhase.INPUT) now() - inputStartedAt else 0,
    )

    private fun InputMode.toDartSource(): DartSource = when (this) {
        InputMode.BOARD -> DartSource.BOARD_TAP
        InputMode.KEYPAD -> DartSource.DART_BY_DART
        InputMode.AI_VISION -> DartSource.DART_BY_DART
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 3
        const val COUNTDOWN_TICK_MS = 700L
        const val TICK_MS = 50L
        /** 近期题去重窗口：避免同一批题反复出现，但不做成全局洗牌。 */
        const val RECENT_WINDOW = 8
    }
}

/** 新会话 id：一次训练一个，报告页按它回库读。 */
fun newRushSessionId(): String = "rush_" + UUID.randomUUID().toString().take(8)
