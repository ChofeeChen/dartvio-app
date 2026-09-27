package com.dartvio.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dartvio.app.domain.achievement.AchievementProgress
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.practice.NinetyNineState
import com.dartvio.app.domain.practice.SectorHit
import com.dartvio.app.ui.achievement.AchievementUnlockCard
import com.dartvio.app.ui.game.GameBlockGap
import com.dartvio.app.ui.game.GameTopBar
import com.dartvio.app.ui.game.TurnDartsRow
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.BackgroundDark
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * 99 Darts 练习页。
 *
 * 33 轮 × 3 镖 = 99 镖；命中指定分区的 T/D/S 分别得 3/2/1 分。
 * 每镖即时记录，单轮满 3 镖自动进入下一轮，支持撤销上一镖。
 */
@Composable
fun NinetyNineScreen(
    sector: Int,
    onExit: () -> Unit,
    viewModel: NinetyNineViewModel = viewModel(),
) {
    LaunchedEffect(sector) { viewModel.start(sector) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val best by viewModel.bestPoints.collectAsStateWithLifecycle()
    val isNewBest by viewModel.lastIsNewBest.collectAsStateWithLifecycle()
    val unlockedAchievements by viewModel.newlyUnlocked.collectAsStateWithLifecycle()

    var showExitDialog by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished) showResult = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        GameTopBar(
            title = "99 Darts · S$sector",
            subtitle = "第 ${state.currentRoundNumber}/33 轮 · ${state.dartsThrown}/99 镖",
            onExit = { showExitDialog = true }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(GameBlockGap),
            verticalArrangement = Arrangement.spacedBy(GameBlockGap)
        ) {
            ScoreCard(state = state, best = best)

            TurnDartsRow(
                darts = state.currentRoundThrows.map { it.toDart(viewModel.sector) },
                trailingLabel = "本轮",
                trailingValue = state.currentRoundPoints,
                accent = Secondary,
                slotScoreLabel = { dart -> if (dart.isMiss) "0 分" else "${dart.multiplier} 分" }
            )

            NineKeypad(
                sector = viewModel.sector,
                enabled = !state.finished,
                canUndo = state.dartsThrown > 0,
                onHit = { viewModel.record(it) },
                onUndo = { viewModel.undo() },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showResult) {
        NinetyNineResultDialog(
            state = state,
            best = best,
            isNewBest = isNewBest,
            unlockedAchievements = unlockedAchievements,
            onRestart = {
                viewModel.restart()
                showResult = false
            },
            onExit = {
                showResult = false
                onExit()
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimaryDark,
            textContentColor = TextSecondaryDark,
            title = { Text("退出练习？") },
            text = { Text("本轮进度不会保存。") },
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
private fun ScoreCard(state: NinetyNineState, best: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricColumn("总分", "${state.totalPoints}", Primary)
            MetricColumn("命中率", "${state.hitRatePercent}%", Secondary)
            MetricColumn("最佳", "$best", Accent)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatChip("T", state.tripleCount, Accent, Modifier.weight(1f))
            StatChip("D", state.doubleCount, Secondary, Modifier.weight(1f))
            StatChip("S", state.singleCount, Primary, Modifier.weight(1f))
            StatChip("MISS", state.missCount, TextSecondaryDark, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondaryDark, fontSize = 11.sp)
        Text(value, color = color, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceVariantDark)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("$count", color = TextPrimaryDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NineKeypad(
    sector: Int,
    enabled: Boolean,
    canUndo: Boolean,
    onHit: (SectorHit) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GameBlockGap)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(GameBlockGap)
        ) {
            HitKey(
                label = "T$sector",
                sub = "3 分",
                color = Accent,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { onHit(SectorHit.TRIPLE) }
            HitKey(
                label = "D$sector",
                sub = "2 分",
                color = Secondary,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { onHit(SectorHit.DOUBLE) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(GameBlockGap)
        ) {
            HitKey(
                label = "S$sector",
                sub = "1 分",
                color = Primary,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { onHit(SectorHit.SINGLE) }
            HitKey(
                label = "MISS",
                sub = "0 分",
                color = TextSecondaryDark,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) { onHit(SectorHit.MISS) }
        }
        OutlinedButton(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondaryDark)
        ) {
            Text("撤销上一镖")
        }
    }
}

@Composable
private fun HitKey(
    label: String,
    sub: String,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) SurfaceVariantDark else SurfaceDark)
            .border(
                1.5.dp,
                if (enabled) color.copy(alpha = 0.7f) else Divider,
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = if (enabled) color else TextDisabledDark,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Text(sub, color = TextSecondaryDark, fontSize = 12.sp)
    }
}

@Composable
private fun NinetyNineResultDialog(
    state: NinetyNineState,
    best: Int,
    isNewBest: Boolean,
    onRestart: () -> Unit,
    onExit: () -> Unit,
    unlockedAchievements: List<AchievementProgress> = emptyList()
) {
    AlertDialog(
        onDismissRequest = { },
        containerColor = SurfaceDark,
        titleContentColor = TextPrimaryDark,
        textContentColor = TextSecondaryDark,
        title = {
            Text(
                text = "练习完成 · S${state.sector}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (isNewBest) {
                    Text(
                        "新纪录！",
                        color = Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                StatLine("总分", "${state.totalPoints} 分")
                StatLine("命中率", "${state.hitRatePercent}%（${state.hitCount}/99）")
                StatLine("T 命中", "${state.tripleCount}（${state.tripleRatePercent}%）")
                StatLine("D 命中", "${state.doubleCount}（${state.doubleRatePercent}%）")
                StatLine("S 命中", "${state.singleCount}")
                StatLine("未命中", "${state.missCount}")
                StatLine("最高单轮", "${state.maxRoundPoints} 分")
                StatLine("满分轮数", "${state.perfectRounds}")
                StatLine("平均每轮", "%.1f 分".format(state.averagePerRound))
                StatLine("历史最佳", "$best 分")
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
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("返回", color = OnPrimary)
            }
        },
        dismissButton = {
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
            ) {
                Text("再来一次", color = TextPrimaryDark)
            }
        }
    )
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondaryDark, fontSize = 13.sp)
        Text(value, color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun SectorHit.toDart(sector: Int): Dart = when (this) {
    SectorHit.TRIPLE -> Dart(sector, 3)
    SectorHit.DOUBLE -> Dart(sector, 2)
    SectorHit.SINGLE -> Dart(sector, 1)
    SectorHit.MISS -> Dart.MISS
}
