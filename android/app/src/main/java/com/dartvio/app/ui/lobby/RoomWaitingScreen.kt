package com.dartvio.app.ui.lobby

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale
import com.dartvio.app.DartVioApp
import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.data.room.LocalRoomRepository
import com.dartvio.app.data.room.PlayerCreditStore
import com.dartvio.app.data.room.PlayerProfileStore
import com.dartvio.app.data.room.RoomRepositoryProvider
import com.dartvio.app.domain.credit.CreditScorer
import com.dartvio.app.domain.model.HumanAvatar
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.stats.StatsCalculator
import com.dartvio.app.domain.stats.X01Stats
import com.dartvio.app.domain.room.LocalUser
import com.dartvio.app.domain.room.NicknameRules
import com.dartvio.app.domain.room.Room
import com.dartvio.app.domain.room.RoomMember
import com.dartvio.app.domain.room.RoomSchedule
import com.dartvio.app.domain.room.RoomStatus
import com.dartvio.app.domain.room.RoomVisibility
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Success
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * 房间等候页（M6 F6.3）。
 *
 * 房主：可踢人、切换观战/可见性、在全员准备后开始比赛。
 * 成员：可切换准备状态。
 * 房间解散或对局开始时自动离开本页。
 *
 * 成员掉线时这里会显示**缺席倒计时**（M5 T8）。那只是服务端判负之前的预告：
 * 本页不自己数到 0 就宣布「他输了」—— 结局由服务端作为一帧下发。
 */
/** 缺席倒计时的走秒间隔（M5 T8）。只影响提示；判负由服务端裁定。 */
private const val ABSENCE_TICK_MS = 1_000L

@Composable
fun RoomWaitingScreen(
    roomId: String,
    onLeave: () -> Unit,
    /** 右上角 WiFi：这一页要停留几十秒等人，「没连上」与「没人来」必须当场能分开。 */
    onOpenOnline: () -> Unit,
    onMatchStarted: (String) -> Unit
) {
    // 等候页是「已进入某个房间」的页面，因此在这里取一次当前仓库即可：
    // 房间一旦建立，数据源不会在停留期间改变（退出联机会先离开本页）。
    val repo = RoomRepositoryProvider.current
    val room by repo.observeRoom(roomId).collectAsState(initial = repo.room(roomId))
    var showRules by remember { mutableStateOf(false) }

    // 成员掉线的缺席倒计时（T8）：起点由服务端下发，之后在本地每秒走一格。
    // 它只是一句提示 —— 判负由服务端说了算，本页不自己宣布结局（红线）。
    val absence by repo.observeAbsence(roomId).collectAsState(initial = emptyMap())
    var absenceLeftMs by remember { mutableStateOf(0L) }
    LaunchedEffect(absence) {
        // 同时有多人掉线时取**最晚**到点的那个：先到点的会被先处置，
        // 屏幕这一行留着的应该是最后一件会发生的事。
        absenceLeftMs = absence.values.maxOrNull() ?: 0L
    }
    LaunchedEffect(absenceLeftMs) {
        if (absenceLeftMs > 0) {
            delay(ABSENCE_TICK_MS)
            absenceLeftMs = (absenceLeftMs - ABSENCE_TICK_MS).coerceAtLeast(0)
        }
    }

    // 房间被解散（房主离开）
    LaunchedEffect(room) {
        if (room == null) onLeave()
    }

    // 对局开始 → 进入比分视图
    LaunchedEffect(room?.status) {
        if (room?.status == RoomStatus.PLAYING) onMatchStarted(roomId)
    }

    val current = room ?: return
    val isHost = current.creatorId == LocalUser.ID
    val self = current.members.firstOrNull { it.id == LocalUser.ID }

    // ---------- 卡片上的人是谁（昵称 + 实力）----------
    //
    // 昵称以**本机档案**为准：房间里面那条成员记录是加入那一刻的快照，
    // 用户在「我的」里改过名字之后它不会自己变。卡片上写着旧名字，陌生人就认错人 ——
    // 而这张卡唯一的用途正是让陌生人决定要不要进来（2026-09-26 真机反馈）。
    val context = LocalContext.current
    val selfName = remember(context) { PlayerProfileStore.nickname(context) }

    // 实力名片（PPR + 信用）。房主是本机时直接读本机已打完的对局；
    // 客人那一边只能拿到房主建房时写进索引行的那一份（在线模式），拿不到就整块不显示。
    val app = context.applicationContext as? DartVioApp
    // 大厅口径优先：进**这一类房间**的人要看的是「他在这里打过什么水平」。
    // 本地训练口径只在大厅还没记录时兜底，且那时要标出来源 ——
    // 两个口径不是一个数，糊在一起会让同一个人在不同场合报出不同的 PPR。
    val arenaFlow = remember(app) { app?.matchRepository?.observeArenaMatches() }
    val statsFlow = remember(app) { app?.matchRepository?.observeStatsMatches() }
    val arenaMatches by (arenaFlow ?: flowOf(emptyList<MatchWithPlayers>()))
        .collectAsState(initial = emptyList())
    val statsMatches by (statsFlow ?: flowOf(emptyList<MatchWithPlayers>()))
        .collectAsState(initial = emptyList())
    val selfPpr = pprOf(StatsCalculator.compute(arenaMatches).x01)
    val localPpr = pprOf(StatsCalculator.compute(statsMatches).x01)
    val selfCredit = PlayerCreditStore.credit(context)

    // 非房主准备就绪后，模拟房主开局，便于验证「房间 → 对局 → 观战」全链路。
    // 以 isReady 为 key：取消准备会取消等待中的协程，不会误开局。
    //
    // 只在单机 Mock 下成立：那时「房主」只是大厅里被演示出来的角色，没有人会去点「开始比赛」，
    // 于是这一段替它把链路走通。联机时房间里有真正的房主，开局权只属于它 ——
    // 客人发出去的 start_match 会被服务端正确地拒绝（只有房主能开局），
    // 除了在日志里留下一条假故障之外毫无作用。
    val simulateHostStart = repo === LocalRoomRepository
    LaunchedEffect(self?.isReady, isHost, simulateHostStart) {
        if (!simulateHostStart || isHost || self?.isReady != true) return@LaunchedEffect
        delay(2600)
        val latest = repo.room(roomId) ?: return@LaunchedEffect
        if (latest.status == RoomStatus.WAITING && latest.members.size >= 2) {
            repo.startMatch(roomId)
        }
    }

    fun leave() {
        repo.leaveRoom(roomId, LocalUser.ID)
        onLeave()
    }

    BackHandler { leave() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 顶栏避让状态栏：与其它房类页面的 RoomTopBar 保持同一基线。
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { leave() }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "离开房间",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    current.name,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    /*
                     * 房间名与房主昵称**分开两行**（2026-09-27 真机反馈）：
                     * 上面大字是「这一局叫什么」，这一行是「谁开的」。
                     * 两者此前被混着用 —— 房间名「茄子的房间1」里夹着人名，
                     * 于是成员列表与房主名看起来是两个不同的称呼，读的人要自己猜是不是同一个人。
                     */
                    if (isHost) "房主：你（$selfName）" else "房主：${current.creatorName}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenOnline) {
                Icon(
                    Icons.Filled.Wifi,
                    contentDescription = "在线状态",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            // ⋮ = 本房规则（PRD A10）。与大厅顶栏的 ⋮ 是同一个语义位置、
            // 不同内容：那里回答「X01 是什么」，这里回答「这一局怎么打」。
            IconButton(onClick = { showRules = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "本房规则",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // 「房间号卡片」在这里被删除（PRD D5 / A11）：房间一经创建就在大厅公开列出，
            // 任何人点 [加入] 即可进来，不再有一个需要念给对方听的凭证。
            // 保留它只会把人引向一条已经不存在的路径 —— 复制一个没人能输入的地方的号。
            if (isHost && current.status == RoomStatus.WAITING && current.members.size < 2) {
                WaitingHint("5 分钟内没人加入，房间会自动解散")
                Spacer(Modifier.height(12.dp))
            }

            val scheduleText = RoomSchedule.cardLabel(current.startsAt, System.currentTimeMillis())
            if (scheduleText != null) {
                WaitingHint("本场预约：$scheduleText")
                Spacer(Modifier.height(12.dp))
            }

            SettingsCard(
                room = current,
                isHost = isHost,
                onToggleSpectators = { repo.setAllowSpectators(roomId, it) },
                onToggleVisibility = {
                    repo.setVisibility(
                        roomId,
                        if (it) RoomVisibility.PUBLIC else RoomVisibility.PRIVATE
                    )
                }
            )

            Spacer(Modifier.height(12.dp))
            val cardPpr = selfPpr ?: localPpr
            HostStatsCard(
                title = if (isHost) "你的实力名片" else "房主实力名片",
                ppr = current.hostPpr ?: if (isHost) cardPpr else null,
                // 标出口径：这一行走的是本地训练的数，不是在大厅里打出来的。
                pprSourceNote = if (current.hostPpr == null && selfPpr == null && localPpr != null) "本地" else null,
                credit = current.hostCredit ?: if (isHost) selfCredit else null
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "成员",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${current.members.size}/${Room.MAX_MEMBERS}",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )
                Spacer(Modifier.weight(1f))
                val readyCount = current.members.count { it.isReady }
                Text(
                    "$readyCount 人已准备",
                    fontSize = 12.sp,
                    color = if (current.allReady) Success else TextSecondaryDark
                )
            }

            Spacer(Modifier.height(10.dp))

            current.members.forEach { member ->
                MemberRow(
                    member = member,
                    // 自己这一行永远显示**当前档案昵称**：房间里的成员记录是加入那一刻的快照，
                    // 在「我的」里改过名字之后它不会自己更新。
                    displayName = if (member.id == LocalUser.ID) selfName else null,
                    /*
                     * PPR：自己永远用本机实算的数（房间里的成员记录是加入那一刻的快照，
                     * 打了两局之后它不会自己涨）；房主那一行的 PPR 还可能在索引行上
                     * （`hostPpr`）—— 建房时 PPR 是异步补写的，事件里那份是 0。
                     *
                     * 拿不到就不显示：写「PPR 0.0」会被读成「这个人很菜」，
                     * 而真实含义只是「他还没打过正式赛」。
                     */
                    ppr = when {
                        member.id == LocalUser.ID -> selfPpr ?: localPpr
                        member.ppr != null -> member.ppr
                        member.isCreator -> current.hostPpr
                        else -> null
                    },
                    canKick = isHost && member.id != LocalUser.ID,
                    onKick = { repo.kickMember(roomId, member.id) }
                )
                Spacer(Modifier.height(8.dp))
            }

            // 重名提示**不拦**（允许同名），只提醒：大厅里靠短码区分，
            // 但同一个房间内喊的是名字，两个同名的人会让「该你投了」有两个应答者。
            val duplicateNames = NicknameRules.duplicates(current.members)
            if (duplicateNames.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                WaitingHint("房间里有重名（${duplicateNames.joinToString("、")}），建议其中一位改一下")
            }

            // 缺席提示压过其它等待提示：它比「等人加入」更紧急，
            // 而混在下面会让用户以为是「房间卡住了」。
            if (absenceLeftMs > 0) {
                Spacer(Modifier.height(12.dp))
                WaitingHint(
                    "有成员掉线，${(absenceLeftMs + ABSENCE_TICK_MS - 1) / ABSENCE_TICK_MS} 秒后判负"
                )
            } else if (current.members.size < 2) {
                Spacer(Modifier.height(12.dp))
                // 房间号已经不是邀请方式（PRD D5）：房间此刻正挂在大厅上等人。
                WaitingHint("等待其他玩家加入…房间已在比赛大厅公开列出")
            } else if (!current.allReady) {
                Spacer(Modifier.height(12.dp))
                WaitingHint("等待所有成员准备")
            }
            Spacer(Modifier.height(20.dp))
        }

        BottomAction(
            room = current,
            isHost = isHost,
            selfReady = self?.isReady == true,
            onToggleReady = { repo.setReady(roomId, LocalUser.ID, !(self?.isReady ?: false)) },
            onStart = { repo.startMatch(roomId) }
        )
    }

    if (showRules) {
        RoomRulesSheet(room = current, onDismiss = { showRules = false })
    }
}

/**
 * 本房规则（PRD A10）。
 *
 * 与大厅顶栏 ⋮ 的「玩法规则」分工：那边讲 X01 是什么，这边讲**这一局**怎么打 ——
 * 用户点 ⋮ 时想知道的是后者，把科普放在这里会让真正的问题（这局是不是双倍结束）被埋掉。
 *
 * **只读**：改配置没有对应的事件类型（`SETTINGS_CHANGED` 只承载公开性与观战开关），
 * 为「改目标分」新造一个事件要动协议，而协议一分叉，老端会把这类房间读成打不开。
 * 因此本期只做展示，可改的能力与协议扩展一起进 P1（PRD 已标注为偏差）。
 */
@Composable
private fun RoomRulesSheet(room: Room, onDismiss: () -> Unit) {
    val config = room.config
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "本房规则",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = TextSecondaryDark)
                }
            }
            Spacer(Modifier.height(8.dp))
            RuleLine("玩法", config.displayName)
            RuleLine(
                "赛制",
                if (config.mode == MatchMode.CASUAL) "休闲单局" else "先到 ${config.legsToWin.coerceAtLeast(1)} 局"
            )
            RuleLine("结镖（OUT）", config.outMode.label)
            RuleLine("开镖（IN）", config.inMode.label)
            RuleLine(
                "回合上限",
                if (config.maxRounds > 0) "${config.maxRounds} 轮" else "无上限"
            )
            RuleLine("观战", if (room.allowSpectators) "允许" else "不允许")
            RuleLine("人数", "${room.members.size}/${Room.MAX_MEMBERS}")
            Spacer(Modifier.height(10.dp))
            Text(
                "规则由房主创建房间时设定，开局后不再更改。",
                fontSize = 12.sp,
                color = TextSecondaryDark
            )
        }
    }
}

@Composable
private fun RuleLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondaryDark)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsCard(
    room: Room,
    isHost: Boolean,
    onToggleSpectators: (Boolean) -> Unit,
    onToggleVisibility: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, Divider, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            room.config.summary(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "房主创建房间时设定，对局开始后不可更改",
            fontSize = 11.sp,
            color = TextSecondaryDark
        )

        if (isHost) {
            Spacer(Modifier.height(12.dp))
            HostSwitchRow(
                title = "允许观战",
                checked = room.allowSpectators,
                onChange = onToggleSpectators
            )
            Spacer(Modifier.height(8.dp))
            HostSwitchRow(
                title = "公开房间",
                subtitle = "关闭后仅房间号可见",
                checked = room.visibility == RoomVisibility.PUBLIC,
                onChange = onToggleVisibility
            )
        }
    }
}

@Composable
private fun HostSwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, color = TextSecondaryDark)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnPrimary,
                checkedTrackColor = Primary,
                uncheckedThumbColor = TextSecondaryDark,
                uncheckedTrackColor = Divider
            )
        )
    }
}

@Composable
private fun MemberRow(
    member: RoomMember,
    canKick: Boolean,
    onKick: () -> Unit,
    displayName: String? = null,
    /** 这一位的 PPR；`null` = 拿不到，整块不显示（不写 0）。 */
    ppr: Double? = null
) {
    val emoji = runCatching { HumanAvatar.fromKey(member.avatar).emoji }
        .getOrDefault("\uD83D\uDC64")

    val nameLine = if (displayName != null || member.id == LocalUser.ID) {
        "${displayName ?: member.name}（你）"
    } else {
        member.name
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceVariantDark)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(SurfaceDark)
                .border(1.dp, if (member.id == LocalUser.ID) Primary else Divider, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    nameLine,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (member.isCreator) {
                    Spacer(Modifier.width(6.dp))
                    Tag("房主", Accent)
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (member.isReady) "已准备" else "未准备",
                    fontSize = 11.sp,
                    color = if (member.isReady) Success else TextDisabledDark
                )
                // PPR 放在准备状态同一行而不是昵称下面：昵称那一行留给「（你）」「房主」标签，
                // 而准备状态与实力都是**同行扫一眼**的信息，分两行会拉高这张卡。
                val value = ppr?.takeIf { it > 0.0 }
                if (value != null) {
                    Text("  ·  ", fontSize = 11.sp, color = TextDisabledDark)
                    Text("PPR ${fmt1(value)}", fontSize = 11.sp, color = TextSecondaryDark)
                }
            }
        }
        if (canKick) {
            IconButton(onClick = onKick) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "移出房间",
                    tint = TextSecondaryDark,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/**
 * 房主的实力名片（PPR + 信用分）。
 *
 * 两个数字都拿不到时**整块不显示**：写「PPR 0.0 · 信用 0」会被读成「这个人很差」，
 * 而真实含义只是「他还没打过正式赛 / 这一端拿不到数据」（见 CreditScorer 的注释）。
 */
@Composable
private fun HostStatsCard(title: String, ppr: Double?, credit: Int?, pprSourceNote: String? = null) {
    if (ppr == null && credit == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 12.sp, color = TextSecondaryDark)
        Spacer(Modifier.weight(1f))
        if (ppr != null) {
            Text(
                if (pprSourceNote != null) "$pprSourceNote PPR ${fmt1(ppr)}" else "PPR ${fmt1(ppr)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (ppr != null && credit != null) {
            Text("  ·  ", fontSize = 12.sp, color = TextDisabledDark)
        }
        if (credit != null) {
            Text(
                "信用 $credit ${CreditScorer.tier(credit)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
        }
    }
}

/**
 * 这一份统计里最能代表「实力」的那个 PPR：正式赛优先，其次全部已打完的对局。
 *
 * 只看正式赛（`pprFormal`）会让只打过休闲局的人永远拿不到数 —— 可他明明打完了几场。
 * 0 一律当作「没有」：留下一个 0.0 会被读成「他很菜」（见 [HostStatsCard]）。
 */
private fun pprOf(x01: X01Stats): Double? =
    x01.pprFormal.takeIf { it > 0.0 } ?: x01.pprAll.takeIf { it > 0.0 }

/** 定死小数位：默认 Locale 在部分机型是阿拉伯语系，会输出阿拉伯数字。 */
private fun fmt1(value: Double): String = String.format(Locale.US, "%.1f", value)

@Composable
private fun WaitingHint(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = TextSecondaryDark,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .padding(14.dp)
    )
}

@Composable
private fun BottomAction(
    room: Room,
    isHost: Boolean,
    selfReady: Boolean,
    onToggleReady: () -> Unit,
    onStart: () -> Unit
) {
    val canStart = isHost && room.members.size >= 2 && room.allReady

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        val enabled = if (isHost) canStart else true
        val label = if (isHost) {
            when {
                room.members.size < 2 -> "等待玩家加入"
                !room.allReady -> "等待全部准备"
                else -> "开始比赛"
            }
        } else {
            if (selfReady) "取消准备" else "准备"
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        !enabled -> SurfaceVariantDark
                        isHost -> Primary
                        selfReady -> SurfaceVariantDark
                        else -> Primary
                    }
                )
                .then(
                    if (!isHost && selfReady) Modifier.border(1.dp, Primary, RoundedCornerShape(14.dp))
                    else Modifier
                )
                .clickable(enabled = enabled) { if (isHost) onStart() else onToggleReady() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    !enabled -> TextDisabledDark
                    !isHost && selfReady -> Primary
                    else -> OnPrimary
                }
            )
        }
    }
}
