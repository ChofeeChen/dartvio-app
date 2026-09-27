package com.dartvio.app.ui.practice.versus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.data.VersusRepository
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.data.local.entity.VersusMatchRecordEntity
import com.dartvio.app.data.local.entity.VersusRoundRecordEntity
import com.dartvio.app.domain.versus.BattleEndReason
import com.dartvio.app.ui.game.GameBlockGap
import com.dartvio.app.ui.game.GameTopBar
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * 对抗练习 · 轮后战报。
 *
 * **只读数据库，不读内存状态**：这一页在「再来一局」之后仍可能被回退栈带回，
 * 而那时 `VersusSession` 里的对局状态早已是下一局的；用 `matchId` 回库读是唯一稳定的口径。
 * 也正因为如此，它是唯一能验证「逐轮落库」有没有真的写进去的页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersusReportScreen(
    onExit: () -> Unit,
    onRematch: () -> Unit,
) {
    val context = LocalContext.current
    val matchId = VersusSession.matchId
    val data by produceState<Pair<VersusMatchRecordEntity?, List<VersusRoundRecordEntity>>?>(
        initialValue = null,
        matchId,
    ) {
        value = if (matchId == null) {
            null
        } else {
            val repository = VersusRepository(DartVioDatabase.get(context).versusDao())
            repository.loadMatch(matchId) to repository.loadRounds(matchId)
        }
    }

    Scaffold(
        topBar = {
            GameTopBar(
                title = "战报",
                subtitle = data?.first?.modeLabel ?: "对抗练习",
                onExit = onExit,
            )
        }
    ) { padding ->
        val match = data?.first
        val rounds = data?.second.orEmpty()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(GameBlockGap),
            verticalArrangement = Arrangement.spacedBy(GameBlockGap),
        ) {
            // 明细滚动、按钮钉底：战报再长，「再来一局」也不该被滚出屏幕。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GameBlockGap),
            ) {
                if (match == null) {
                    Text(
                        "没有找到本局记录",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    ResultCard(match)
                    if (match.handicapSummary.isNotBlank()) {
                        Card {
                            Text(
                                "让分　${match.handicapSummary}",
                                fontSize = 12.sp,
                                color = Accent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    // 双方分列：对抗类战报的价值在「同一轮次上两人各打了什么」，
                    // 混成一条时间线会看不出交替出手的节奏。
                    match.playerNames.forEachIndexed { index, name ->
                        val mine = rounds.filter { it.playerIndex == index }
                        PlayerRoundsCard(
                            name = name,
                            isWinner = match.winnerIndex == index,
                            rounds = mine,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    // 只清场次信息，保留模式 / 配置 / 名字 —— 这正是「再来一局（同配置）」的语义。
                    VersusSession.restart()
                    onRematch()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text("再来一局（同配置）", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnPrimary)
            }
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Text("返回列表", fontSize = 14.sp, color = TextSecondaryDark)
            }
        }
    }
}

@Composable
private fun ResultCard(match: VersusMatchRecordEntity) {
    val reason = match.endReason
        .let { runCatching { BattleEndReason.valueOf(it) }.getOrNull() }
    val headline = when {
        match.winnerName.isNotBlank() -> "${match.winnerName} 获胜"
        reason == BattleEndReason.ABORT -> "本局中止"
        else -> "本局结束"
    }
    Card {
        Text(
            headline,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (match.winnerName.isNotBlank()) Accent else TextPrimaryDark,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append("${match.modeLabel}　·　${reason?.label ?: "—"}")
                if (match.wentToPlayoff) append("　·　加赛决胜")
            },
            fontSize = 12.sp,
            color = TextSecondaryDark,
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Metric("回合数", match.roundCount.toString(), Modifier.weight(1f))
            Metric("总镖数", match.totalDarts.toString(), Modifier.weight(1f))
            Metric("时长", formatDuration(match.durationMs), Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlayerRoundsCard(
    name: String,
    isWinner: Boolean,
    rounds: List<VersusRoundRecordEntity>,
) {
    val best = rounds.maxByOrNull { it.roundScore }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isWinner) Accent else TextPrimaryDark,
            )
            if (isWinner) {
                Spacer(Modifier.width(8.dp))
                Text("胜", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent)
            }
        }
        if (rounds.isEmpty()) {
            Text(
                "本局没有记录到这一方的轮次",
                fontSize = 12.sp,
                color = TextDisabledDark,
                modifier = Modifier.padding(top = 6.dp),
            )
            return@Card
        }
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append("${rounds.size} 轮　·　${rounds.sumOf { it.dartCount }} 镖")
                if (best != null) append("　·　最佳单轮 ${best.roundScore}")
            },
            fontSize = 12.sp,
            color = TextSecondaryDark,
        )
        Spacer(Modifier.height(8.dp))
        rounds.forEach { round ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(SurfaceDark, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (round.isPlayoff) "加" else "${round.roundNo}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark,
                    modifier = Modifier.width(26.dp),
                    textAlign = TextAlign.Start,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        round.dartsCsv.replace("|", "　"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                    )
                    if (round.targetSnapshot.isNotBlank()) {
                        Text(round.targetSnapshot, fontSize = 10.sp, color = TextDisabledDark)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "+${round.roundScore}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                    )
                    Text("累计 ${round.runningScore}", fontSize = 10.sp, color = TextSecondaryDark)
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(14.dp))
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
        Text(label, fontSize = 11.sp, color = TextSecondaryDark)
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "—"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}分${seconds}秒" else "${seconds}秒"
}
