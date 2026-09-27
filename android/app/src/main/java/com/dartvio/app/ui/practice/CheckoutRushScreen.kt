package com.dartvio.app.ui.practice

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.practice.CheckoutRushRules
import com.dartvio.app.domain.practice.CheckoutSolver
import com.dartvio.app.ui.components.BoardTapPad
import com.dartvio.app.ui.components.DartKeypad
import com.dartvio.app.ui.components.InputMode
import com.dartvio.app.ui.components.InputModeSwitch
import com.dartvio.app.ui.components.Multiplier
import com.dartvio.app.ui.game.GameTopBar
import com.dartvio.app.ui.game.TurnDartsRow
import com.dartvio.app.ui.game.appendDigit
import com.dartvio.app.ui.game.dartOf
import com.dartvio.app.ui.game.isValidDartValue
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.BackgroundDark
import com.dartvio.app.ui.theme.Bust
import com.dartvio.app.ui.theme.Error
import com.dartvio.app.ui.theme.Success
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceElevated
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import com.dartvio.app.ui.theme.Warning

/**
 * 极速挑战训练页（提示词 §八）。
 *
 * ## 一屏只做一件事
 * 阶段决定这一屏**能点什么**：倒计时不揭示目标分、投掷阶段录入区禁用（§八.7 防误触）、
 * 只有点过「完成投掷」才进入可录入状态。这样「计时中」和「录入中」不会出现两套控件同时可点。
 *
 * ## 整页不滚动（★）
 * 本页**没有** `verticalScroll`：状态条 / 镖槽 / 主次按钮按内容高度排，
 * 中间的输入区用 `weight(1f)` 吃掉全部剩余高度 —— 这样任何屏高下这一屏都完整可见，
 * 不会出现「要点还得先滑一下」。代价是键盘高度随屏收敛，所以剩余高度不足时
 * 由 `BoxWithConstraints` 自动降档（路线提示只留首选）把空间让给键盘。
 * ★反向约束：`DartKeypad` 的行高是 `weight(1f)`，必须落在**有界高度**父容器里；
 * 一旦回到可滚动布局，整块键盘会塌成 0 高（不报错，只见一片空白）。
 *
 * ## 状态不只用颜色表达（§八.8）
 * 成功 / 爆分 / 未完成 / 计时 / 禁用全部带文字与符号（✓ ✗ —），
 * 颜色只作辅助 —— 单靠颜色在色觉障碍与深色主题下会丢信息。
 */
@Composable
fun CheckoutRushScreen(
    onExit: () -> Unit,
    onFinish: (String) -> Unit,
    viewModel: CheckoutRushViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 首次进入才开题：进程重建后 ViewModel 是新的，但 Store 里的会话还在 ⇒ 接着用同一个会话。
    LaunchedEffect(Unit) {
        if (viewModel.state.value.problemIndex == 0) {
            val sessionId = CheckoutRushSessionStore.sessionId
            if (sessionId == null) {
                onExit()
            } else {
                viewModel.start(CheckoutRushSessionStore.kind, CheckoutRushSessionStore.difficulty, sessionId)
            }
        }
    }

    // 训练结束（10 题做完 / 主动结束）→ 交报告页。
    LaunchedEffect(state.completedSessionId) {
        state.completedSessionId?.let(onFinish)
    }

    // 切后台：暂停本题。用 Activity 的 Lifecycle 而不是 ProcessLifecycleOwner，
    // 免得为这一个页面新增依赖（工程里没有 lifecycle-runtime-compose）。
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onAppBackground()
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        GameTopBar(
            title = "极速挑战",
            subtitle = progressLabel(state),
            onExit = onExit,
        )

        // 小屏（≈640dp 的旧低端机）：状态卡字号降档、键盘内边距收紧，
        // 把省下来的高度还给键盘 —— 宁可字小一点，也不能让键点不准。
        val compact = LocalConfiguration.current.screenHeightDp < 700

        // 整页不滚动：固定块按内容高度，输入区吃掉剩余高度（下游 weight 需要这里的有界高度）。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = RushPagePadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusCard(state = state, compact = compact)

            // 剩余高度不足时自动降档（路线提示只留首选），优先保住键盘的可点尺寸。
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val tight = maxHeight < 300.dp
                Column(modifier = Modifier.fillMaxSize()) {
                    // 结算阶段：结果卡取代输入区，镖槽与主按钮也在下方收起（结果卡自带出口）。
                    if (state.phase != RushPhase.RESULT) {
                        RouteHintBar(
                            state = state,
                            showAlternatives = !tight,
                            onShowHint = viewModel::showRouteHint,
                            onCollapse = viewModel::collapseRouteHint,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (state.phase == RushPhase.RESULT) {
                            ResultPanel(
                                state = state,
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            InputPanel(
                                state = state,
                                viewModel = viewModel,
                                compact = compact,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            if (state.phase != RushPhase.RESULT) {
                TurnDartsRow(
                    darts = state.darts,
                    trailingLabel = "已录",
                    trailingValue = state.darts.sumOf { it.score },
                    slotScoreLabel = { it.label() },
                )
                MainActionButton(state = state, viewModel = viewModel)
                SecondaryActionRow(state = state, viewModel = viewModel)
            }
        }
    }

    if (state.pendingSwitchConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::cancelSwitchInput,
            title = { Text("切换输入方式？", color = TextPrimaryDark) },
            text = {
                Text(
                    "本题已经录入 ${state.darts.size} 镖。切换后可以选择保留或清除已录的镖。",
                    color = TextSecondaryDark,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveSwitchInput(clearDarts = true) }) {
                    Text("清除并切换", color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveSwitchInput(clearDarts = false) }) {
                    Text("保留", color = Accent)
                }
            },
            containerColor = SurfaceDark,
        )
    }

    if (state.phase == RushPhase.BACKGROUND) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("训练已暂停", color = TextPrimaryDark) },
            text = {
                Text(
                    "检测到应用进入后台，本题已中断并记为「中断」，"
                        + "后台时间不会计入成绩，本题也不参与个人最佳比较。",
                    color = TextSecondaryDark,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::restartProblem) { Text("重新开始本题", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::exitAfterBackground) { Text("退出训练", color = TextSecondaryDark) }
            },
            containerColor = SurfaceDark,
        )
    }
}

// =====================================================================================
// 分块
// =====================================================================================

private fun progressLabel(state: RushUiState): String {
    val total = state.kind.problemCount
    val index = state.problemIndex.coerceAtLeast(0)
    val where = if (total == null) "第 $index 题" else "第 $index / $total 题"
    return "$where · ${state.difficulty.label}"
}

/**
 * 状态条：目标分 + 两条计时 + 一行口径提示，**合并在一张卡里**。
 *
 * 原来这三块是「目标分卡 + 规则小字 + 计时面板」三个纵向块（约 176dp），
 * 训练时要盯的其实就「打几分 / 用了多久」两个数 —— 拆成三块只会把键盘挤出屏幕。
 * 底部那一行按阶段切换：投掷中给「怎么停表」，其余时候给规则口径 ——
 * 两个信息都用同一行，不额外占高。
 */
@Composable
private fun StatusCard(state: RushUiState, compact: Boolean) {
    val running = state.phase == RushPhase.THROWING
    val shape = RoundedCornerShape(12.dp)
    // 底部那行只在「有话要说」时出现：投掷中给停表提示，其余时候给规则口径；
    // 小屏再把规则口径让掉（主按钮文案已经写了「冻结成绩」），把高度还给键盘。
    val caption = when {
        running -> "计时中 · 点「完成投掷」冻结成绩"
        compact -> null
        else -> "Double Out · 标准 Bull（25 / 50）· 直入（已开分）"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (state.phase == RushPhase.COUNTDOWN) {
                    Text("准备", fontSize = 11.sp, color = TextSecondaryDark)
                    Text(
                        "${state.countdown}",
                        fontSize = if (compact) 26.sp else 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Warning,
                    )
                    Text(
                        "目标分将在倒计时结束后揭示",
                        fontSize = 11.sp,
                        color = TextSecondaryDark,
                    )
                } else {
                    Text("目标分", fontSize = 11.sp, color = TextSecondaryDark)
                    Text(
                        "${state.target}",
                        fontSize = if (compact) 26.sp else 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                    )
                    Text(
                        "剩余 ${remainingLabel(state)}",
                        fontSize = 11.sp,
                        color = TextSecondaryDark,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("投掷用时（成绩）", fontSize = 11.sp, color = TextSecondaryDark)
                Text(
                    formatMs(state.throwElapsedMs),
                    fontSize = if (compact) 17.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (running) Success else TextPrimaryDark,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "录入 ${formatMs(state.inputElapsedMs)} · 不计成绩",
                    fontSize = 11.sp,
                    color = TextSecondaryDark,
                )
            }
        }
        if (caption != null) {
            Text(
                caption,
                fontSize = 11.sp,
                color = if (running) Success else TextSecondaryDark,
            )
        }
    }
}

private fun remainingLabel(state: RushUiState): String {
    val outcome = state.outcome
    if (outcome != null) return "${outcome.remainingAfter}"
    val thrown = state.darts.sumOf { it.score }
    return "${(state.target - thrown).coerceAtLeast(0)}"
}

/**
 * 路线提示条。
 *
 * 收起态是一行（按钮 + 已用提示的口径说明），展开态是卡片 ——
 * [showAlternatives] 为 false 时只留首选路线：小屏或键盘吃紧时，
 * 宁可少看两条替代路线，也不能把键盘压到点不准。
 */
@Composable
private fun RouteHintBar(
    state: RushUiState,
    showAlternatives: Boolean,
    onShowHint: () -> Unit,
    onCollapse: () -> Unit,
) {
    if (!state.hintExpanded) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RushSecondaryButton(
                text = if (state.routeHintUsed) "查看路线（已用）" else "查看路线",
                onClick = onShowHint,
            )
            if (state.routeHintUsed) {
                Text(
                    "已看过提示，成绩不计入无提示个人最佳",
                    fontSize = 11.sp,
                    color = Warning,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        return
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, shape)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("路线提示", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCollapse) { Text("收起", color = Accent) }
        }
        state.routes.firstOrNull()?.let { route ->
            Text(
                "首选：${CheckoutSolver.formatRoute(route)}",
                fontSize = 12.sp,
                color = TextPrimaryDark,
                maxLines = 1,
            )
        }
        if (showAlternatives) {
            state.routes.drop(1).take(2).forEach { route ->
                Text(
                    "替代：${CheckoutSolver.formatRoute(route)}",
                    fontSize = 12.sp,
                    color = TextSecondaryDark,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 录入区：键盘 / 靶盘**常驻**，只在 INPUT 阶段可点（§八.7 防误触）。
 *
 * 常驻而不是「投掷阶段隐藏」有两个理由：①各阶段版面高度一致，
 * 点「完成投掷」后下方的镖槽与主按钮不会跳位；②禁用态本身就是「现在不能录」的说明，
 * 比留一块空白好 —— 空白会让人以为键盘坏了。
 *
 * ★高度必须由调用方给定界（本页是外层 `weight(1f)`）：
 * 键盘行高是 `weight(1f)`，放进无界高度容器会整板塌成 0 高。
 */
@Composable
private fun InputPanel(
    state: RushUiState,
    viewModel: CheckoutRushViewModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val canInput = state.phase == RushPhase.INPUT
    var buffer by remember { mutableStateOf("") }
    var multiplier by remember { mutableStateOf(Multiplier.SINGLE) }

    // 换题 / 换阶段时清掉键盘缓冲，避免上一题的数字带到下一题。
    LaunchedEffect(state.problemIndex, state.phase) {
        buffer = ""
        multiplier = Multiplier.SINGLE
    }

    if (state.inputMode == InputMode.BOARD) {
        BoardTapPad(
            onTap = { viewModel.addDart(it.dart) },
            enabled = canInput,
            // 靶盘自己按 min(宽, 高) 取正方形，填满即最大化。
            modifier = modifier.fillMaxSize(),
        )
    } else {
        DartKeypad(
            modifier = modifier.fillMaxSize(),
            // 小屏收紧键盘内边距（默认 8dp），行距不变、按键更高一点。
            contentPadding = PaddingValues(if (compact) 4.dp else 8.dp),
            enabled = canInput,
            buffer = buffer,
            multiplier = multiplier,
            onMultiplierChange = { multiplier = it },
            turnDartsCount = state.darts.size,
            onDigit = { buffer = appendDigit(buffer, it) },
            onBull25 = { commit(viewModel, 25, Multiplier.SINGLE); buffer = "" },
            onBull50 = { commit(viewModel, 25, Multiplier.DOUBLE); buffer = "" },
            onMiss = { viewModel.addDart(Dart.MISS); buffer = "" },
            onConfirm = { commit(viewModel, buffer.toIntOrNull(), multiplier); buffer = "" },
            onBackspace = { buffer = "" },
            modeSwitch = { modifier ->
                InputModeSwitch(
                    current = state.inputMode,
                    onSelect = { viewModel.requestSwitchInput(it) },
                    modifier = modifier,
                )
            },
        )
    }
}

private fun commit(viewModel: CheckoutRushViewModel, value: Int?, multiplier: Multiplier) {
    val v = value ?: return
    if (!isValidDartValue(v)) return
    viewModel.addDart(dartOf(v, multiplier))
}

@Composable
private fun MainActionButton(
    state: RushUiState,
    viewModel: CheckoutRushViewModel,
) {
    when (state.phase) {
        RushPhase.THROWING -> RushPrimaryButton("完成投掷（冻结成绩）", onClick = viewModel::onThrowDone)
        RushPhase.INPUT -> RushPrimaryButton(
            text = if (state.darts.isEmpty()) "确认结果（未录镖 = 未完成）" else "确认结果",
            onClick = viewModel::confirmResult,
        )
        else -> RushPrimaryButton(text = phaseIdleLabel(state), onClick = {}, enabled = false)
    }
}

/** 次操作行（撤销 / 跳过 / 结束）—— 与结果卡自带的出口不同时出现，避免一屏两组出口。 */
@Composable
private fun SecondaryActionRow(
    state: RushUiState,
    viewModel: CheckoutRushViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RushSecondaryButton(
            text = "撤销一镖",
            enabled = state.phase == RushPhase.INPUT && state.darts.isNotEmpty(),
            onClick = viewModel::undoLastDart,
            modifier = Modifier.weight(1f),
        )
        RushSecondaryButton(
            text = "跳过本题",
            enabled = state.phase in setOf(RushPhase.COUNTDOWN, RushPhase.THROWING, RushPhase.INPUT),
            onClick = viewModel::skip,
            modifier = Modifier.weight(1f),
        )
        RushSecondaryButton(
            text = "结束训练",
            onClick = viewModel::endTraining,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun phaseIdleLabel(state: RushUiState): String = when (state.phase) {
    RushPhase.COUNTDOWN -> "倒计时中…"
    RushPhase.RESULT -> "本题已结算"
    RushPhase.BACKGROUND -> "训练已暂停"
    RushPhase.INPUT -> "确认结果"
    RushPhase.THROWING -> "完成投掷"
}

@Composable
private fun ResultPanel(
    state: RushUiState,
    viewModel: CheckoutRushViewModel,
    modifier: Modifier = Modifier,
) {
    val outcome = state.outcome ?: return
    val shape = RoundedCornerShape(12.dp)
    val (symbol, color, headline) = when (outcome.result) {
        com.dartvio.app.domain.practice.RushResult.CHECKOUT ->
            Triple("✓", Success, "结镖成功")
        com.dartvio.app.domain.practice.RushResult.BUST ->
            Triple("✗", Bust, "爆分：${outcome.bustReason?.label ?: "超出可结范围"}")
        com.dartvio.app.domain.practice.RushResult.NOT_FINISHED ->
            Triple("—", Warning, "三镖未完成")
        com.dartvio.app.domain.practice.RushResult.SKIPPED ->
            Triple("○", TextSecondaryDark, "已跳过")
        com.dartvio.app.domain.practice.RushResult.ABORTED ->
            Triple("○", TextSecondaryDark, "已中断")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark, shape)
            .padding(14.dp)
    ) {
        Text("$symbol $headline", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(8.dp))
        ResultLine("目标分", "${outcome.target}")
        ResultLine("投掷用时（成绩）", formatMs(state.throwElapsedMs))
        ResultLine("实际用镖数", "${outcome.dartsUsed} 镖（最多 ${CheckoutRushRules.MAX_DARTS}）")
        ResultLine(
            "实际路线",
            if (outcome.darts.isEmpty()) "未录入"
            else outcome.darts.joinToString(" → ") { it.label() },
        )
        ResultLine(
            "推荐路线",
            state.routes.firstOrNull()?.let { CheckoutSolver.formatRoute(it) } ?: "—",
        )
        ResultLine("是否使用提示", if (state.routeHintUsed) "已使用（不计入无提示最佳）" else "未使用")
        ResultLine(
            "同难度无提示个人最佳",
            bestLabel(state),
        )
        ResultLine("当前连续成功", "${state.streak}")
        if (state.timingInvalidated) {
            Text(
                "本题曾切后台，不参与个人最佳比较",
                fontSize = 11.sp,
                color = Warning,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RushSecondaryButton("再试一次", onClick = viewModel::retry, modifier = Modifier.weight(1f))
            RushSecondaryButton("下一题", onClick = viewModel::next, modifier = Modifier.weight(1f))
            RushSecondaryButton("结束训练", onClick = viewModel::endTraining, modifier = Modifier.weight(1f))
        }
    }
}

private fun bestLabel(state: RushUiState): String {
    val before = state.bestBeforeMs
    return when {
        state.isNewBest -> "刷新！${formatMs(state.throwElapsedMs)}（原 ${before?.let(::formatMs) ?: "无记录"}）"
        before == null -> "暂无记录"
        else -> formatMs(before)
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 12.sp, color = TextSecondaryDark)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimaryDark)
    }
}

/** 毫秒 → `x.x s`。手写拼接而不是 `String.format`，避免受系统Locale 影响出现逗号小数点。 */
private fun formatMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    return "${safe / 1000}.${(safe % 1000) / 100} s"
}
