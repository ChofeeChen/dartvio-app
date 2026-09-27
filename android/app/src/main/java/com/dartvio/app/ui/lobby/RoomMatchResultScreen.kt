package com.dartvio.app.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.DartVioApp
import com.dartvio.app.data.local.RoomMatchRecorder
import com.dartvio.app.data.room.RoomRepositoryProvider
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.room.LocalUser
import com.dartvio.app.domain.room.MatchFinish
import com.dartvio.app.domain.room.MatchStats
import com.dartvio.app.domain.room.PlayerMatchStats
import com.dartvio.app.domain.room.RoomMatchRules
import com.dartvio.app.domain.room.RoomMatchView
import com.dartvio.app.domain.room.RoomStatus
import com.dartvio.app.domain.room.SpectatorSnapshot
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import java.util.Locale

/**
 * 联机对局的**结算页**（M5 T9）。
 *
 * 它显示的总比分与流水都取自**同一份权威帧**（[SpectatorSnapshot]），
 * 与对局页、观战页同源 —— 结算页自己另算一份「谁赢了几局」，只在两端给出不同答案时才会被发现，
 * 而那时已经没有人知道该信哪一份。
 *
 * ## 谁可以点「再来一局」
 *
 * 只有房主。[RoomRepository.rematch] 在服务端也这样判（重复判定只是为了让按钮不出现，
 * 而不是让它点了没反应 —— 一个假的按钮比没有按钮更让人困惑）。
 *
 * 客人不需要轮询：房间回到等候态这一变化随房间快照到达，本页据此自己回到等候页。
 *
 * ## 落库
 *
 * 打完的这一场由 [RecordFinishedMatch] 落成一行**本地历史**（`source = LAN`）：
 * 它进历史列表与「数据」页的**比赛大厅**口径，但由 `MatchStatsFilter` 挡在战绩之外。
 * 大厅卡片上的房主 PPR、房间等候页里的实力名片，都读这一份记录。
 *
 * ## 不做
 *
 * 不做「查看回放」——帧里只保留最近 [RoomMatchRules.MAX_TURNS_IN_FRAME] 条流水，
 * 回放需要另一份数据源。
 */
@Composable
fun RoomMatchResultScreen(
    roomId: String,
    onExit: () -> Unit,
    onBackToRoom: (String) -> Unit
) {
    val repo = RoomRepositoryProvider.current
    val view by repo.observeMatch(roomId).collectAsState(initial = null)
    val room by repo.observeRoom(roomId).collectAsState(initial = repo.room(roomId))

    /**
     * 回到等候页的一次性闸门。
     *
     * `WAITING` 是一个**稳定**状态，而房间快照会被反复推送（成员变化、重连补帧）。
     * 不设闸门，每次快照到达都会把已经站在等候页的人再推一次 ——
     * 表现为「刚回到等候页，又被弹回结算页」。
     */
    var returned by remember { mutableStateOf(false) }
    LaunchedEffect(room?.status) {
        if (room?.status == RoomStatus.WAITING && !returned) {
            returned = true
            onBackToRoom(roomId)
        }
    }

    // 没有帧就什么都不显示：单机 Mock 没有权威对局（`observeMatch` 恒 null），
    // 而联机下「对局页已经判定结束」与「结算帧还没到」之间只有几十毫秒。
    val current = view ?: return
    val snapshot = current.snapshot
    val isHost = room?.creatorId == LocalUser.ID

    RecordFinishedMatch(roomId = roomId, view = current, startedAt = room?.createdAt, config = room?.config)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        RoomTopBar(title = snapshot.roomName, onBack = onExit)

        WinnerBanner(
            name = winnerNameOf(snapshot, current.finish),
            forfeited = current.finish?.reason == MatchFinish.REASON_FORFEIT
        )

        LegsSummary(snapshot)

        Spacer(Modifier.height(10.dp))
        StatsCard(snapshot)

        Spacer(Modifier.height(12.dp))
        TurnHistory(snapshot, Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (isHost) {
                Button(
                    onClick = { repo.rematch(roomId) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
                ) {
                    Text("再来一局", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "等待房主开始下一场…",
                    fontSize = 13.sp,
                    color = TextSecondaryDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    repo.leaveRoom(roomId, LocalUser.ID)
                    onExit()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceVariantDark,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("退出房间")
            }
        }
    }
}

/**
 * 把这一场已结束的联机局落进本地历史（M5 T10 的写入侧）。
 *
 * 为什么放在结算页：它是**唯一**同时握着「权威帧 + 房间配置」的位置 ——
 * 帧里的事实、房间的规则，两边凑齐才能落出一行完整的历史。
 *
 * 幂等由 `matchId`（结果导出）+ DAO 的 REPLACE 保证，所以旋转屏幕、
 * 重回结算页都不会多出一场记录（见 [RoomMatchRecorder]）。
 *
 * 写失败一律静默：这一行只服务「数据页能不能看到它」，
 * 而结算页的职责是显示结果 —— 一次统计没记下来不该变成一次报错。
 */
@Composable
private fun RecordFinishedMatch(
    roomId: String,
    view: RoomMatchView,
    startedAt: Long?,
    config: MatchConfig?
) {
    val context = LocalContext.current
    var recordedKey by remember { mutableStateOf<String?>(null) }
    val key = "$roomId:${view.snapshot.version}:${view.finish?.winnerId}"
    LaunchedEffect(key, view.isOver) {
        if (!view.isOver || recordedKey == key) return@LaunchedEffect
        recordedKey = key
        val app = context.applicationContext as? DartVioApp ?: return@LaunchedEffect
        val cfg = config ?: return@LaunchedEffect
        runCatching {
            RoomMatchRecorder.record(
                repository = app.matchRepository,
                roomId = roomId,
                config = cfg,
                view = view,
                startedAt = startedAt,
                localProfileId = app.localProfileId
            )
        }
    }
}

/** 胜者横幅。弃权与正常收镖用同一句句式，只是补一句原因 —— 结局只有一种，说法不该有两种。 */
@Composable
private fun WinnerBanner(name: String, forfeited: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "$name 获胜",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (forfeited) {
            Spacer(Modifier.height(4.dp))
            Text(
                "对手掉线超时判负",
                fontSize = 12.sp,
                color = TextSecondaryDark
            )
        }
    }
}

/** 总比分（局数）。局数是这场比赛唯一真正的结果，剩余分只是**最后一局**的残留。 */
@Composable
private fun LegsSummary(snapshot: SpectatorSnapshot) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(SurfaceVariantDark, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        snapshot.players.forEach { player ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    player.name,
                    fontSize = 12.sp,
                    color = TextSecondaryDark,
                    maxLines = 1
                )
                Text(
                    "${player.legsWon}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * 赛后统计卡（M2 R4）。
 *
 * 数据与流水**同源**（都来自同一份帧），因此它也继承了帧的限制：最多 30 条流水。
 * 达到上限时这里如实写「基于最近 30 轮」，而不是把早先的轮次按剩余分反推出来 ——
 * 一个看着完整的错误分母，比一句「可能不完整」更难发现。
 */
@Composable
private fun StatsCard(snapshot: SpectatorSnapshot) {
    val report = MatchStats.of(snapshot)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(SurfaceVariantDark, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "统计",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (report.truncated) {
                Text(
                    "基于最近 ${RoomMatchRules.MAX_TURNS_IN_FRAME} 轮",
                    fontSize = 11.sp,
                    color = TextDisabledDark
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            report.players.forEachIndexed { index, player ->
                if (index > 0) Spacer(Modifier.width(16.dp))
                PlayerStatsColumn(player, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PlayerStatsColumn(stats: PlayerMatchStats, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stats.playerName,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text("${stats.legsWon} 局", fontSize = 11.sp, color = TextSecondaryDark)
        Spacer(Modifier.height(6.dp))
        // 3 镖平均永远有值（没投过就是 0.0），其余"没打出来"的一律「—」而不是 0 ——
        // 0 会被读成"投过但一个都没有"，「—」才是"没有这个数据"。
        StatRow("3 镖平均", oneDecimal(stats.threeDartAvg))
        StatRow("First 9", stats.first9Avg?.let(::oneDecimal) ?: DASH)
        StatRow("100+", "${stats.count100}")
        StatRow("140+", "${stats.count140}")
        StatRow("180", "${stats.count180}")
        StatRow("High Finish", stats.highFinish?.toString() ?: DASH)
        StatRow("最快一局", stats.bestLegTurns?.let { "$it 轮" } ?: DASH)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = TextSecondaryDark, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 定死小数位：默认 `Locale` 在部分机型是阿拉伯语系，会输出阿拉伯数字。 */
private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

private const val DASH = "—"

/**
 * 胜者是谁（纯函数，便于单测）。
 *
 * 取值顺序是有意的：**先信服务端下发的 [MatchFinish]**，其次才是帧里的局数，最后才是最后一条收镖。
 * 反过来（先自己算）会让「弃权判负」在帧里找不到对应的一手镖 —— 那一手根本没投出来，
 * 帧里不会留下任何痕迹，靠流水只能推出「对局已结束」这种什么都没说的句子。
 */
fun winnerNameOf(snapshot: SpectatorSnapshot, finish: MatchFinish?): String {
    finish?.winnerId
        ?.takeIf { it.isNotBlank() }
        ?.let { id -> snapshot.players.firstOrNull { it.id == id }?.name }
        ?.let { return it }

    // 赛制终结：局数达到目标的那一位。休闲模式（legsToWin <= 0）不参与这一步 ——
    // 那里 `legsWon(1) >= 0` 恒真，会把第一个人永远算成胜者。
    if (snapshot.legsToWin > 0) {
        snapshot.players.firstOrNull { it.legsWon >= snapshot.legsToWin }?.name?.let { return it }
    }

    // 单局定胜负：最后一条收镖的人。
    return snapshot.turns.lastOrNull()?.takeIf { it.isCheckout }?.playerName ?: "本局结束"
}
