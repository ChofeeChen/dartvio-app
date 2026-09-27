package com.dartvio.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.practice.COUNT_UP_DARTS_PER_ROUND
import com.dartvio.app.domain.practice.COUNT_UP_ROUNDS
import com.dartvio.app.domain.practice.CountUpState
import com.dartvio.app.ui.components.DartKeypad
import com.dartvio.app.ui.components.Multiplier
import com.dartvio.app.ui.game.GameBlockGap
import com.dartvio.app.ui.game.GameTopBar
import com.dartvio.app.ui.game.TurnDartsRow
import com.dartvio.app.ui.game.appendDigit
import com.dartvio.app.ui.game.dartOf
import com.dartvio.app.ui.game.isValidDartValue
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.BackgroundDark
import com.dartvio.app.ui.theme.Bust
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceElevated
import com.dartvio.app.domain.achievement.AchievementProgress
import com.dartvio.app.ui.achievement.AchievementUnlockCard
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * Count Up 练习计分页（M11 §6.2）。
 *
 * 规则：8 轮 × 3 镖；每镖按倍率累计；3 镖满自动进入下一轮；
 * 未投镖时按「确认」视为 Bust（本轮 0 分）；可提前结束并结算。
 */
@Composable
fun CountUpScreen(
    onExit: () -> Unit,
    viewModel: CountUpViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val isNewRecord by viewModel.isNewRecord.collectAsStateWithLifecycle()
    val unlockedAchievements by viewModel.newlyUnlocked.collectAsStateWithLifecycle()

    var buffer by remember { mutableStateOf("") }
    var multiplier by remember { mutableStateOf(Multiplier.SINGLE) }
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentRoundIndex, state.finished) {
        buffer = ""
        multiplier = Multiplier.SINGLE
    }

    if (state.finished) {
        CountUpResultScreen(
            state = state,
            bestScore = bestScore,
            isNewRecord = isNewRecord,
            onRetry = { viewModel.reset() },
            onExit = onExit,
            unlockedAchievements = unlockedAchievements
        )
        return
    }

    val inputEnabled = !state.roundLocked

    fun commitBuffer() {
        val value = buffer.toIntOrNull()
        if (value != null && isValidDartValue(value)) {
            viewModel.throwDart(dartOf(value, multiplier))
        }
        buffer = ""
    }

    fun confirm() {
        val value = buffer.toIntOrNull()
        when {
            value != null && isValidDartValue(value) -> {
                viewModel.throwDart(dartOf(value, multiplier))
                buffer = ""
            }
            state.currentDarts.isEmpty() -> viewModel.bust()
            else -> viewModel.finalizeRound()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(GameBlockGap),
        verticalArrangement = Arrangement.spacedBy(GameBlockGap)
    ) {
        GameTopBar(
            title = "Count Up 练习",
            subtitle = "第 ${state.currentRoundNumber} / $COUNT_UP_ROUNDS 轮 · 已投 ${state.dartsThrown} 镖",
            onExit = { showExitDialog = true }
        )

        TotalScoreCard(total = state.totalScore, best = bestScore)

        RoundScoresBoard(scores = state.roundScores, activeIndex = state.currentRoundIndex)

        TurnDartsRow(
            darts = state.currentDarts,
            trailingLabel = "本轮",
            trailingValue = state.currentRoundScore
        )

        BustHint(visible = state.bustFlash)

        DartKeypad(
            modifier = Modifier.weight(1f),
            enabled = inputEnabled,
            multiplier = multiplier,
            onMultiplierChange = { multiplier = it },
            buffer = buffer,
            currentRemaining = null,
            turnDartsCount = state.currentDarts.size,
            onDigit = { digit -> if (inputEnabled) buffer = appendDigit(buffer, digit) },
            onBull25 = { if (inputEnabled) { viewModel.throwDart(Dart.OUTER_BULL); buffer = "" } },
            onBull50 = { if (inputEnabled) { viewModel.throwDart(Dart.INNER_BULL); buffer = "" } },
            onMiss = { if (inputEnabled) { viewModel.throwDart(Dart.MISS); buffer = "" } },
            onConfirm = { if (inputEnabled) confirm() },
            onBackspace = {
                if (buffer.isNotEmpty()) buffer = buffer.dropLast(1)
                else viewModel.undoLastDart()
            }
        )

        Text(
            text = "提示：未投镖时按「确认」= Bust（本轮 0 分）；每轮 $COUNT_UP_DARTS_PER_ROUND 镖后自动进入下一轮。",
            color = TextSecondaryDark,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimaryDark,
            textContentColor = TextSecondaryDark,
            title = { Text("结束练习？") },
            text = { Text("将按当前已完成回合结算本次练习结果。") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.finishEarly()
                }) { Text("结算并查看结果", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onExit()
                }) { Text("直接退出", color = TextSecondaryDark) }
            }
        )
    }
}

@Composable
private fun TotalScoreCard(total: Int, best: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("累计总分", color = TextSecondaryDark, fontSize = 12.sp)
            Text(
                text = "$total",
                color = Primary,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("历史最佳", color = TextSecondaryDark, fontSize = 12.sp)
            Text(
                text = "${maxOf(best, total)}",
                color = Secondary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RoundScoresBoard(scores: List<Int?>, activeIndex: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(GameBlockGap)) {
        scores.chunked(4).forEachIndexed { rowIndex, rowScores ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GameBlockGap)
            ) {
                rowScores.forEachIndexed { colIndex, score ->
                    val index = rowIndex * 4 + colIndex
                    RoundScoreCell(
                        roundNumber = index + 1,
                        score = score,
                        active = index == activeIndex,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundScoreCell(
    roundNumber: Int,
    score: Int?,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val played = score != null
    val accent = when {
        active -> Primary
        played && score == 0 -> Bust
        played -> Secondary
        else -> Divider
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) SurfaceVariantDark else SurfaceDark)
            .border(1.dp, accent, RoundedCornerShape(10.dp))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("R$roundNumber", color = TextSecondaryDark, fontSize = 10.sp)
        Text(
            text = score?.toString() ?: "—",
            color = if (played) accent else TextSecondaryDark.copy(alpha = 0.5f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BustHint(visible: Boolean) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "BUST · 本轮 0 分",
            color = Bust,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CountUpResultScreen(
    state: CountUpState,
    bestScore: Int,
    isNewRecord: Boolean,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    unlockedAchievements: List<AchievementProgress> = emptyList()
) {
    /*
     * 结果页的两条硬约束（全局 UI 约束 §G3，2026-09-27 真机反馈）：
     * ① **内容区可滚动**：成就卡 / 轮分表会随成绩变高，固定高度的列会把下面的按钮顶出屏幕；
     * ② **主操作钉在内容区之下**并带 `navigationBarsPadding`：手势导航条会盖住最底部那一截，
     *    按钮贴着父容器底边就等于被系统条吃掉一半（用户反馈的「按钮位置太靠下」）。
     * 顶部不再垫 Spacer：内容少时由 `Arrangement.Center` 居中，多时才滚动 ——
     * 垫了固定 Spacer，小屏上第一行就会被顶出可视区（用户反馈的「顶部溢出」）。
     */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .navigationBarsPadding()
            .padding(GameBlockGap)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GameBlockGap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Text(
            text = "练习完成",
            color = TextPrimaryDark,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        if (isNewRecord) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Accent)
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "NEW RECORD",
                    color = BackgroundDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ===== 成就解锁卡片（决策④：练习结果页）=====
        AchievementUnlockCard(unlocked = unlockedAchievements)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${state.totalScore}",
                color = Primary,
                fontSize = 60.sp,
                fontWeight = FontWeight.Bold
            )
            Text("总分", color = TextSecondaryDark, fontSize = 12.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GameBlockGap)
        ) {
            ResultStat("历史最佳", "${maxOf(bestScore, state.totalScore)}", Modifier.weight(1f))
            ResultStat("平均每轮", "${state.averagePerRound}", Modifier.weight(1f))
            ResultStat("单轮最高", "${state.maxRoundScore}", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GameBlockGap)
        ) {
            ResultStat("180+", "${state.count180}", Modifier.weight(1f))
            ResultStat("完成镖数", "${state.dartsThrown}", Modifier.weight(1f))
            ResultStat("完成轮次", "${state.roundsPlayed}/$COUNT_UP_ROUNDS", Modifier.weight(1f))
        }

            RoundScoresBoard(scores = state.roundScores, activeIndex = -1)
        }

        Spacer(Modifier.height(12.dp))

        /*
         * 两个按钮**等宽、同高 52dp、贴在内容区之下**：
         * 「返回」与「再来一次」是同一层级的选择，靠颜色（中性 / 主色）区分主次，
         * 不能靠高度差 —— 高度差会被读成「上面那个更重要」。
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GameBlockGap)
        ) {
            Button(
                onClick = onExit,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
            ) {
                Text("返回", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("再来一次", color = OnPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = TextPrimaryDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextSecondaryDark, fontSize = 10.sp)
    }
}
