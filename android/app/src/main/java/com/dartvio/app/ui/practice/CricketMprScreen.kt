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
import com.dartvio.app.domain.practice.CRICKET_MPR_DARTS_PER_ROUND
import com.dartvio.app.domain.practice.CRICKET_MPR_ROUNDS
import com.dartvio.app.domain.practice.CRICKET_MPR_TARGETS
import com.dartvio.app.domain.practice.CricketMprState
import com.dartvio.app.domain.practice.MprDart
import com.dartvio.app.domain.practice.cricketTargetLabel
import com.dartvio.app.domain.practice.formatMpr
import com.dartvio.app.domain.practice.mprRating
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
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.domain.achievement.AchievementProgress
import com.dartvio.app.ui.achievement.AchievementUnlockCard
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * Cricket MPR 挑战（M11 Cricket 练习）。
 *
 * 规则：10 轮 × 3 镖；统计 15-20 与 Bull 的标记数，
 * S=1 / D=2 / T=3，Outer Bull=1 / Inner Bull=2；
 * 分区不封顶，MPR = 总标记 ÷ 已完成轮数，实时折算展示。
 */
@Composable
fun CricketMprScreen(
    onExit: () -> Unit,
    viewModel: CricketMprViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bestMpr by viewModel.bestMpr.collectAsStateWithLifecycle()
    val isNewBest by viewModel.isNewBest.collectAsStateWithLifecycle()
    val unlockedAchievements by viewModel.newlyUnlocked.collectAsStateWithLifecycle()

    var buffer by remember { mutableStateOf("") }
    var multiplier by remember { mutableStateOf(Multiplier.SINGLE) }
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.roundsCompleted, state.finished) {
        buffer = ""
        multiplier = Multiplier.SINGLE
    }

    if (state.finished) {
        CricketMprResultScreen(
            state = state,
            bestMpr = bestMpr,
            isNewBest = isNewBest,
            onRetry = { viewModel.reset() },
            onExit = onExit,
            unlockedAchievements = unlockedAchievements
        )
        return
    }

    fun commitBuffer() {
        val value = buffer.toIntOrNull()
        if (value != null && isValidDartValue(value)) {
            viewModel.record(dartOf(value, multiplier))
        }
        buffer = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // 顶栏与其他训练页同一套留白：bar 贴边、内容再统一缩进 ——
        // 之前顶栏被一起缩进，标题相对别的页面横移了一格。
        GameTopBar(
            title = "Cricket MPR 挑战",
            subtitle = "第 ${state.currentRoundNumber} / $CRICKET_MPR_ROUNDS 轮 · 已投 ${state.dartsThrown} 镖",
            onExit = { showExitDialog = true }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(GameBlockGap),
            verticalArrangement = Arrangement.spacedBy(GameBlockGap)
        ) {
        MprSummaryCard(
            mpr = state.mpr,
            bestMpr = bestMpr,
            totalMarks = state.totalMarks,
            hits = state.hits,
            dartsThrown = state.dartsThrown
        )

        CricketTargetBoard(
            marks = state.marksByTarget,
            currentRoundDarts = state.currentRoundDarts
        )

        TurnDartsRow(
            darts = state.currentRoundDarts.map { it.dart },
            trailingLabel = "本轮标记",
            trailingValue = state.currentRoundMarks
        )

        DartKeypad(
            modifier = Modifier.weight(1f),
            enabled = true,
            multiplier = multiplier,
            onMultiplierChange = { multiplier = it },
            buffer = buffer,
            currentRemaining = null,
            turnDartsCount = state.currentRoundDarts.size,
            onDigit = { digit -> buffer = appendDigit(buffer, digit) },
            onBull25 = { viewModel.record(Dart.OUTER_BULL); buffer = "" },
            onBull50 = { viewModel.record(Dart.INNER_BULL); buffer = "" },
            onMiss = { viewModel.record(Dart.MISS); buffer = "" },
            onConfirm = { commitBuffer() },
            onBackspace = {
                if (buffer.isNotEmpty()) buffer = buffer.dropLast(1)
                else viewModel.undo()
            }
        )

        Text(
            text = "提示：仅 15-20 与 Bull 计入标记，分区不封顶；" +
                "每轮 $CRICKET_MPR_DARTS_PER_ROUND 镖，共 $CRICKET_MPR_ROUNDS 轮。",
            color = TextSecondaryDark,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimaryDark,
            textContentColor = TextSecondaryDark,
            title = { Text("结束挑战？") },
            text = { Text("将按当前已完成轮次结算本次 MPR。") },
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
private fun MprSummaryCard(
    mpr: Float,
    bestMpr: Float,
    totalMarks: Int,
    hits: Int,
    dartsThrown: Int
) {
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
            Text("当前 MPR", color = TextSecondaryDark, fontSize = 12.sp)
            Text(
                text = formatMpr(mpr),
                color = Primary,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "评级 · ${mprRating(mpr)}",
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("总标记", color = TextSecondaryDark, fontSize = 12.sp)
            Text(
                text = "$totalMarks",
                color = Secondary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "命中 $hits / $dartsThrown 镖",
                color = TextSecondaryDark,
                fontSize = 11.sp
            )
            Text(
                text = "历史最佳 ${formatMpr(bestMpr)}",
                color = Secondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 7 个计分分区的累计标记板，两行展示（4 + 3）。 */
@Composable
private fun CricketTargetBoard(
    marks: Map<Int, Int>,
    currentRoundDarts: List<MprDart>
) {
    Column(verticalArrangement = Arrangement.spacedBy(GameBlockGap)) {
        CRICKET_MPR_TARGETS.chunked(4).forEach { rowTargets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GameBlockGap)
            ) {
                rowTargets.forEach { target ->
                    val hitThisRound = currentRoundDarts
                        .filter { it.dart.number == target }
                        .sumOf { it.marks }
                    TargetCell(
                        label = cricketTargetLabel(target),
                        marks = marks[target] ?: 0,
                        active = hitThisRound > 0,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - rowTargets.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TargetCell(
    label: String,
    marks: Int,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = when {
        active -> Accent
        marks > 0 -> Secondary
        else -> Divider
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) SurfaceVariantDark else SurfaceDark)
            .border(1.dp, accent, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TextSecondaryDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "$marks",
            color = if (marks > 0) accent else TextSecondaryDark.copy(alpha = 0.5f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CricketMprResultScreen(
    state: CricketMprState,
    bestMpr: Float,
    isNewBest: Boolean,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    unlockedAchievements: List<AchievementProgress> = emptyList()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            // 与 Count Up 结果页同一套约束（全局 UI 约束 §G3）：内容可滚动、
            // 按钮钉在内容区之下且避开系统导航条，顶部不垫固定 Spacer。
            .navigationBarsPadding()
            .padding(GameBlockGap),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GameBlockGap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Text(
            text = if (state.isFullSession) "挑战完成" else "挑战结束",
            color = TextPrimaryDark,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        if (isNewBest) {
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
                text = formatMpr(state.mpr),
                color = Primary,
                fontSize = 60.sp,
                fontWeight = FontWeight.Bold
            )
            Text("MPR · ${mprRating(state.mpr)}", color = TextSecondaryDark, fontSize = 12.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GameBlockGap)
        ) {
            ResultStat("历史最佳", formatMpr(maxOf(bestMpr, state.mpr)), Modifier.weight(1f))
            ResultStat("总标记", "${state.totalMarks}", Modifier.weight(1f))
            ResultStat("命中率", "${state.hitRate}%", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GameBlockGap)
        ) {
            ResultStat("单轮最高", "${state.bestRoundMarks}", Modifier.weight(1f))
            ResultStat("完成镖数", "${state.dartsThrown}", Modifier.weight(1f))
            ResultStat("完成轮次", "${state.roundsCompleted}/$CRICKET_MPR_ROUNDS", Modifier.weight(1f))
        }

            CricketTargetBoard(marks = state.marksByTarget, currentRoundDarts = emptyList())
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GameBlockGap)
        ) {
            Button(
                onClick = onExit,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
            ) {
                Text("返回", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
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
