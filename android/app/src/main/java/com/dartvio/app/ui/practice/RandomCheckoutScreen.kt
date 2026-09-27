package com.dartvio.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import com.dartvio.app.domain.achievement.AchievementProgress
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.practice.CheckoutResult
import com.dartvio.app.domain.practice.CheckoutSolver
import com.dartvio.app.domain.practice.RandomCheckoutState
import com.dartvio.app.ui.achievement.AchievementUnlockCard
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
import com.dartvio.app.ui.theme.GameShot
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * 随机结镖练习页（M11 §6.3）。
 *
 * 复用本页键盘与结算框架：系统给出随机目标（20-170），玩家在 3 镖内完成双结。
 */
@Composable
fun RandomCheckoutScreen(
    onExit: () -> Unit,
    viewModel: RandomCheckoutViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val attempts by viewModel.attempts.collectAsStateWithLifecycle()
    val successes by viewModel.successes.collectAsStateWithLifecycle()
    val unlockedAchievements by viewModel.newlyUnlocked.collectAsStateWithLifecycle()
    val successRate by remember(successes, attempts) {
        mutableStateOf(if (attempts == 0) 0 else successes * 100 / attempts)
    }

    var buffer by remember { mutableStateOf("") }
    var multiplier by remember { mutableStateOf(Multiplier.SINGLE) }
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.target) {
        buffer = ""
        multiplier = Multiplier.SINGLE
    }

    val inputEnabled = !state.isFinished

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(GameBlockGap),
        verticalArrangement = Arrangement.spacedBy(GameBlockGap)
    ) {
        GameTopBar(
            title = "随机结镖练习",
            subtitle = "今日 $attempts 次 · 成功率 ${successRate}%",
            onExit = { showExitDialog = true }
        )

        TargetCard(state)

        if (state.showAnswer && state.bestRoute.isNotEmpty()) {
            AnswerCard(state.bestRoute)
        }

        TurnDartsRow(
            darts = state.darts,
            trailingLabel = "剩余",
            trailingValue = state.remaining
        )

        DartKeypad(
            modifier = Modifier.weight(1f),
            enabled = inputEnabled,
            multiplier = multiplier,
            onMultiplierChange = { multiplier = it },
            buffer = buffer,
            currentRemaining = state.remaining,
            turnDartsCount = state.darts.size,
            onDigit = { digit ->
                if (inputEnabled && !state.isFinished) buffer = appendDigit(buffer, digit)
            },
            onBull25 = {
                if (inputEnabled) { viewModel.throwDart(Dart.OUTER_BULL); buffer = "" }
            },
            onBull50 = {
                if (inputEnabled) { viewModel.throwDart(Dart.INNER_BULL); buffer = "" }
            },
            onMiss = {
                if (inputEnabled) { viewModel.throwDart(Dart.MISS); buffer = "" }
            },
            onConfirm = {
                if (!inputEnabled) return@DartKeypad
                val value = buffer.toIntOrNull()
                if (value != null && isValidDartValue(value)) {
                    viewModel.throwDart(dartOf(value, multiplier))
                }
                buffer = ""
            },
            onBackspace = {
                if (buffer.isNotEmpty()) buffer = buffer.dropLast(1)
            }
        )

        ActionRow(
            onSkip = { viewModel.skip() },
            onToggleAnswer = { viewModel.toggleAnswer() },
            answerShown = state.showAnswer,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (state.isFinished) {
        ResultDialog(
            state = state,
            onRetry = { viewModel.retry() },
            onNext = { viewModel.nextTarget() },
            unlockedAchievements = unlockedAchievements
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimaryDark,
            textContentColor = TextSecondaryDark,
            title = { Text("退出练习？") },
            text = { Text("当前进度不会保存，统计已自动记录。") },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; onExit() }) {
                    Text("退出", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("继续练习", color = TextSecondaryDark)
                }
            }
        )
    }
}

@Composable
private fun TargetCard(state: RandomCheckoutState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("目标", color = TextSecondaryDark, fontSize = 12.sp)
            Text(
                text = "${state.target}",
                color = Primary,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("剩余", color = TextSecondaryDark, fontSize = 12.sp)
            Text(
                text = "${state.remaining}",
                color = if (state.remaining == 0 && state.result == CheckoutResult.SUCCESS) GameShot else TextPrimaryDark,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AnswerCard(route: List<Dart>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariantDark)
            .padding(12.dp)
    ) {
        Text(
            text = "参考路线",
            color = Secondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = CheckoutSolver.formatRoute(route),
            color = TextPrimaryDark,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ActionRow(
    onSkip: () -> Unit,
    onToggleAnswer: () -> Unit,
    answerShown: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GameBlockGap)
    ) {
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondaryDark)
        ) {
            Text("跳过此题")
        }
        OutlinedButton(
            onClick = onToggleAnswer,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary)
        ) {
            Text(if (answerShown) "隐藏答案" else "查看答案")
        }
    }
}

@Composable
private fun ResultDialog(
    state: RandomCheckoutState,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    unlockedAchievements: List<AchievementProgress> = emptyList()
) {
    val success = state.result == CheckoutResult.SUCCESS
    val title = when (state.result) {
        CheckoutResult.SUCCESS -> "结镖成功"
        CheckoutResult.FAIL_BUST -> "BUST"
        CheckoutResult.FAIL_NO_CHECKOUT -> "未完成"
        else -> ""
    }
    val color = if (success) GameShot else Bust

    AlertDialog(
        onDismissRequest = { },
        containerColor = SurfaceDark,
        titleContentColor = color,
        textContentColor = TextSecondaryDark,
        title = {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "目标 ${state.target}  ·  用了 ${state.darts.size} 镖",
                    color = TextPrimaryDark,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "你的路线：${state.darts.joinToString(" → ") { it.label() }}",
                    color = TextSecondaryDark,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                if (state.bestRoute.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "参考：${CheckoutSolver.formatRoute(state.bestRoute)}",
                        color = Accent,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
                // ===== 成就解锁卡片（决策④：弹窗场景用 compact，避免撑高对话框）=====
                if (unlockedAchievements.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    AchievementUnlockCard(
                        unlocked = unlockedAchievements,
                        compact = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("下一题", color = OnPrimary)
            }
        },
        dismissButton = {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
            ) {
                Text("再试一次", color = TextPrimaryDark)
            }
        }
    )
}
