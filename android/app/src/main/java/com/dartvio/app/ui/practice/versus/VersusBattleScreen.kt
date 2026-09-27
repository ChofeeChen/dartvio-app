package com.dartvio.app.ui.practice.versus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.data.VersusRepository
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.data.local.entity.VersusMatchRecordEntity
import com.dartvio.app.domain.versus.BattleEndReason
import com.dartvio.app.domain.versus.BattleState
import com.dartvio.app.domain.versus.BoardHit
import com.dartvio.app.domain.versus.DartEvent
import com.dartvio.app.domain.versus.RoundSnapshot
import com.dartvio.app.domain.versus.VersusModes
import com.dartvio.app.ui.game.GameBlockGap
import com.dartvio.app.ui.game.GameTopBar
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Bust
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 换人时键盘锁定的时长（需求：换人提示期间锁定，避免上一人的误触记到下一人头上）。 */
private const val HANDOVER_LOCK_MS = 2000L

/**
 * 对抗练习 · 对局页。
 *
 * **这一页不理解任何一条规则**：目标怎么判、得几分、什么时候结束，全部由 [VersusRule] 回答，
 * 页面只做三件事 —— 把状态画出来、把点击交给引擎、把引擎给的快照落库。
 * 加第 7 个模式时这一页一行都不用改。
 *
 * 落库策略是**逐轮写**（不是终局一次性写）：中途退出时已录轮次全部保留，
 * 「中途退出不丢数据」由它 + `VersusRule.abort` 共同满足。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersusBattleScreen(
    onExit: () -> Unit,
    onFinish: () -> Unit,
) {
    val rule = VersusSession.rule
    val config = VersusSession.config
    val info = VersusSession.info
    val names = VersusSession.playerNames

    // 进程被杀后回到这一页：会话是内存单例，此时为空。已落库的轮次不受影响，只是这局打不下去。
    if (rule == null || config == null || info == null) {
        VersusStaleSession(onExit = onExit)
        return
    }

    val context = LocalContext.current
    val repository = remember(context) { VersusRepository(DartVioDatabase.get(context).versusDao()) }
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(rule.newState(config, names)) }
    var match by remember { mutableStateOf<VersusMatchRecordEntity?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(false) }
    var lockJob by remember { mutableStateOf<Job?>(null) }
    var askExit by remember { mutableStateOf(false) }
    var resignStep by remember { mutableStateOf<Int?>(null) }
    var confirmResign by remember { mutableStateOf<Int?>(null) }

    /** 撤销栈：只存「本轮尚未结算」的历史状态（见 [VersusBattleScreen] 注释）。 */
    val undoStack = remember { mutableStateListOf<BattleState>() }

    LaunchedEffect(Unit) {
        val startedAt = System.currentTimeMillis()
        val created = repository.startMatch(
            matchId = VersusRepository.newMatchId(),
            modeKey = config.modeKey,
            modeLabel = info.title,
            config = config,
            playerNames = state.players.map { it.name },
            handicapSummary = handicapSummaryOf(config, state.players.map { it.name }),
            startedAt = startedAt,
        )
        match = created
        VersusSession.markStarted(created.matchId, startedAt)
    }

    // ---------------- 落库（全部在同一条协程里串行，避免「存轮次」与「终局回填」交错） ----------------

    suspend fun persistRound(seat: Int, snapshot: RoundSnapshot, final: BattleState) {
        val m = match ?: return
        repository.saveRound(
            match = m,
            playerIndex = seat,
            playerName = final.players[seat].name,
            roundNo = snapshot.roundNo,
            darts = snapshot.darts,
            roundScore = snapshot.score,
            runningScore = rule.progressOf(final, seat),
            targetSnapshot = snapshot.targetSnapshot,
            isPlayoff = final.playoff,
            recordedAt = System.currentTimeMillis(),
        )
        // 回读父行：`appendRound` 把累计轮次 / 镖数写回了数据库，内存里的行已经过期。
        match = repository.loadMatch(m.matchId) ?: m
    }

    suspend fun persistFinish(final: BattleState, reason: BattleEndReason) {
        val m = match ?: return
        val fresh = repository.loadMatch(m.matchId) ?: m
        repository.finishMatch(
            match = fresh,
            winnerIndex = final.winnerIndex ?: VersusMatchRecordEntity.NO_WINNER,
            winnerName = final.winnerIndex?.let { final.players[it].name } ?: "",
            endReason = reason,
            wentToPlayoff = final.playoff,
            endedAt = System.currentTimeMillis(),
        )
    }

    // ---------------- 引擎交互 ----------------

    fun onHit(hit: BoardHit) {
        if (state.finished || locked) return
        val seat = state.currentPlayerIndex
        val historyBefore = state.players[seat].history.size

        undoStack.add(state)
        var (next, event) = rule.onDart(state, hit)
        if (next.isRoundComplete && !next.finished) {
            val (settled, endEvent) = rule.onRoundEnd(next)
            next = settled
            event = endEvent ?: event
        }
        state = next

        // 本轮已结算 ⇒ 撤销栈失效：撤销到上一轮会让内存状态与已落库的轮次行不一致。
        val committed = next.players[seat].history.size > historyBefore
        if (committed) undoStack.clear()

        banner = event?.let { eventText(it, next) }
        val snapshot = if (committed) next.players[seat].history.lastOrNull() else null

        scope.launch {
            if (snapshot != null) persistRound(seat, snapshot, next)
            if (next.finished) {
                persistFinish(next, next.endReason ?: BattleEndReason.NORMAL)
                onFinish()
                return@launch
            }
            if (committed) {
                // 换人：锁定键盘并提示，避免上一人的连点记到下一人头上。
                locked = true
                banner = "换人：${next.players[next.currentPlayerIndex].name} 请准备"
                lockJob?.cancel()
                lockJob = scope.launch {
                    delay(HANDOVER_LOCK_MS)
                    locked = false
                    banner = null
                }
            }
        }
    }

    fun onUndo() {
        if (locked || state.finished) return
        val previous = undoStack.removeLastOrNull() ?: return
        state = previous
        banner = "已撤销本镖"
    }

    fun doResign(loser: Int) {
        val seat = state.currentPlayerIndex
        val before = state.players[seat].history.size
        val next = rule.resign(state, loser)
        state = next
        banner = "${next.players[loser].name} 认输"
        val snapshot = if (next.players[seat].history.size > before) {
            next.players[seat].history.lastOrNull()
        } else {
            null
        }
        scope.launch {
            if (snapshot != null) persistRound(seat, snapshot, next)
            persistFinish(next, BattleEndReason.RESIGN)
            onFinish()
        }
    }

    fun doAbort() {
        val next = rule.abort(state)
        state = next
        scope.launch {
            persistFinish(next, BattleEndReason.ABORT)
            onExit()
        }
    }

    // ---------------- 布局 ----------------

    Scaffold(
        topBar = {
            GameTopBar(
                title = info.title,
                subtitle = rule.targetCaption(state),
                onExit = { askExit = true },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(GameBlockGap),
            verticalArrangement = Arrangement.spacedBy(GameBlockGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(GameBlockGap),
            ) {
                state.players.forEachIndexed { index, player ->
                    SeatCard(
                        name = player.name,
                        progress = rule.progressText(state, index),
                        isCurrent = index == state.currentPlayerIndex && !state.finished,
                        isWinner = state.winnerIndex == index,
                        handicapped = config.handicapped(index),
                        compact = state.players.size >= 4,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 一行说清「这一轮打到哪了」：镖数 / 已录的镖 / 引擎给的反馈（+N 分、换人……）。
            RoundBar(
                dartNo = state.dartNoInRound,
                dartsPerRound = state.dartsPerRound,
                labels = state.dartsInRound.map { it.label() },
                banner = banner,
            )

            VersusBoardInput(
                layout = rule.inputFilter(state),
                targetSector = state.current.targetSector
                    ?: if (config.modeKey == VersusModes.BULL_BATTLE) 25 else null,
                dartsInRound = state.dartsInRound,
                enabled = !locked && !state.finished && match != null,
                onHit = ::onHit,
                modifier = Modifier.weight(1f),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(GameBlockGap)) {
                OutlinedButton(
                    onClick = ::onUndo,
                    enabled = undoStack.isNotEmpty() && !locked && !state.finished,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Text("撤销", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { resignStep = 0 },
                    enabled = !state.finished,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Text("认输", fontSize = 13.sp, color = Bust)
                }
                OutlinedButton(
                    onClick = { askExit = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Text("结束", fontSize = 13.sp, color = TextSecondaryDark)
                }
            }
        }
    }

    // ---------------- 弹窗 ----------------

    if (askExit) {
        val finished = state.finished
        AlertDialog(
            onDismissRequest = { askExit = false },
            title = { Text(if (finished) "返回列表" else "结束本局？", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (finished) "本局已结束，可以直接返回。" else "已录的轮次会保留，本局记为「中止」。",
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askExit = false
                    if (finished) onExit() else doAbort()
                }) { Text(if (finished) "返回" else "结束并保留", color = Bust) }
            },
            dismissButton = {
                TextButton(onClick = { askExit = false }) { Text("继续本局", color = Primary) }
            },
        )
    }

    // 认输走两步：先选「谁认输」，再由**对手**确认 —— 需求要求认输需对方确认，
    // 单步确认会出现「输的一方自己点一下就判负」，在双人对投场景里容易被误触。
    if (resignStep != null) {
        AlertDialog(
            onDismissRequest = { resignStep = null },
            title = { Text("谁认输？", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.players.forEachIndexed { index, player ->
                        Button(
                            onClick = {
                                resignStep = null
                                confirmResign = index
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                        ) {
                            Text(player.name, fontSize = 15.sp, color = TextPrimaryDark)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { resignStep = null }) { Text("取消", color = Primary) }
            },
        )
    }

    if (confirmResign != null) {
        val loser = confirmResign!!
        val opponent = state.players.getOrNull(1 - loser)?.name ?: "对手"
        AlertDialog(
            onDismissRequest = { confirmResign = null },
            title = { Text("请 $opponent 确认", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "确认「${state.players[loser].name}」认输？本局判其负。",
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmResign = null
                    doResign(loser)
                }) { Text("确认认输", color = Bust) }
            },
            dismissButton = {
                TextButton(onClick = { confirmResign = null }) { Text("取消", color = Primary) }
            },
        )
    }
}

@Composable
private fun SeatCard(
    name: String,
    progress: String,
    isCurrent: Boolean,
    isWinner: Boolean,
    handicapped: Boolean,
    compact: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = when {
        isWinner -> Accent
        isCurrent -> Primary
        else -> Divider
    }
    Column(
        modifier = modifier
            .fillMaxHeight() // 同行玩家卡等高：有没有标签行不影响卡片大小
            .background(SurfaceDark, shape)
            .border(if (isCurrent || isWinner) 2.dp else 1.dp, borderColor, shape)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            name,
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryDark,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        // 比分是这张卡的主角，字号给足；4 人并排时宽度减半，字号同步降档防溢出。
        // 「出手」不再做成标签 —— 红色描边已经标了当前玩家，重复的词只添噪声。
        Text(
            progress,
            fontSize = if (compact) 18.sp else 28.sp,
            fontWeight = FontWeight.Bold,
            color = if (isWinner) Accent else TextPrimaryDark,
            maxLines = 1,
        )
        if (handicapped || isWinner) {
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (handicapped) {
                    Tag("让分", Accent)
                }
                if (isWinner) {
                    Tag("胜", Accent)
                }
            }
        }
    }
}

/**
 * 一行说清「这一轮打到哪了」：镖数 / 已录的镖 / 引擎反馈（+N 分、换人……）。
 * 旧版是迷你盘 + 三行文字，占高却不承重；横排之后这一块只有一行的高度。
 */
@Composable
private fun RoundBar(
    dartNo: Int,
    dartsPerRound: Int,
    labels: List<String>,
    banner: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariantDark, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "本轮 $dartNo/$dartsPerRound 镖",
            fontSize = 12.sp,
            color = TextSecondaryDark,
            maxLines = 1,
        )
        Text(
            labels.joinToString("　").ifBlank { "尚未记录" },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (labels.isEmpty()) TextDisabledDark else Accent,
            maxLines = 1,
        )
        if (banner != null) {
            // 反馈占满剩余宽度并右对齐：短文案（+1 分）贴右，长文案自截断，不挤前面的镖序。
            Text(
                banner,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Accent,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 引擎事件 → 一句话提示。文案集中在这里，避免在页面各处各拼一遍。 */
private fun eventText(event: DartEvent, state: BattleState): String = when (event) {
    is DartEvent.Scored -> "+${event.points} 分"
    is DartEvent.Advance -> "前进 → ${event.sector} 分区"
    is DartEvent.Halve -> "本轮 0 分 · 总分减半 ${event.from} → ${event.to}"
    is DartEvent.Shanghai -> "Shanghai！${event.sector} 分区 S+D+T 秒杀"
    is DartEvent.Win -> "${state.players.getOrNull(event.playerIndex)?.name ?: "该玩家"} 获胜"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersusStaleSession(onExit: () -> Unit) {
    Scaffold(
        topBar = { GameTopBar(title = "对抗练习", subtitle = "配置已失效", onExit = onExit) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("本局配置已失效", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
            Spacer(Modifier.height(8.dp))
            Text(
                "应用被回收后无法续局。已记录的轮次已保存，可从列表重新开始。",
                fontSize = 13.sp,
                color = TextDisabledDark,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onExit, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                Text("返回列表", color = OnPrimary)
            }
        }
    }
}
