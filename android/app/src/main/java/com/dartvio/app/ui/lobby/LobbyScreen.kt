package com.dartvio.app.ui.lobby

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dartvio.app.domain.credit.CreditScorer
import com.dartvio.app.domain.model.BullMode
import com.dartvio.app.domain.model.InMode
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.domain.room.ArenaState
import com.dartvio.app.domain.room.OfficialArena
import com.dartvio.app.domain.room.Room
import com.dartvio.app.domain.room.RoomFilter
import com.dartvio.app.domain.room.RoomSchedule
import com.dartvio.app.domain.room.RoomStatus
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.Success
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 「大厅」一级页面（M6 F6.1 比赛大厅列表）。
 *
 * V0.1 由 [com.dartvio.app.data.room.LocalRoomRepository] 提供 Mock 房间，
 * 交互（筛选、刷新、加入、观战、创建）全部可走通。
 *
 * 页面顶部固定展示「官方擂台」时间锚点（[ArenaAnchorCard]）：它由真实时钟驱动，
 * 不随 Mock 房间变化。低供给阶段大厅最贵的一次流失是「打开 → 空 → 关掉 App」，
 * 因此空状态不渲染「0 人在线」这类数字，而是给出**一个确定的回来时间 + 一个此刻就能做的动作**。
 *
 * 顶部右上角的 [Icons.Filled.Wifi] 进入联机设置（`lobby/online`）。本页刻意**不**显示联机状态，
 * 也不感知网络：联机与否对大厅只是「数据源换了一个」（见 [LobbyViewModel]），
 * 把连接状态显示在这里会让「列表是空的」和「没连上」两种原因混成一团。
 */
@Composable
fun LobbyScreen(
    onOpenCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onSpectate: (String) -> Unit,
    onStartPractice: () -> Unit,
    onOpenOnline: () -> Unit,
    viewModel: LobbyViewModel = viewModel()
) {
    val rooms by viewModel.rooms.collectAsState()
    // 排序：**可加入的房间永远在最上面**（Dartsmind 口径）。可加入 > 进行中可观战 > 其余，
    // 同级按原有顺序（compareByDescending 是稳定排序，不会打乱仓库给的时间序）。
    val visible = remember(rooms, viewModel.filter) {
        rooms.filter(viewModel.filter::matches)
            .sortedWith(
                compareByDescending<Room> { viewModel.canJoin(it) }
                    .thenByDescending { viewModel.canSpectate(it) }
            )
    }
    var filterExpanded by remember { mutableStateOf(false) }

    // 卡片上的倒计时由真实时钟驱动：预约房那句「还剩 12:34」必须每秒在动，
    // 停着不动的数字会被读成「房间卡住了」，而它其实是这间房唯一的进度信息。
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MILLIS)
            now = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LobbyHeader(onOpenOnline = onOpenOnline)

        LobbyMenuBar(
            onCreateRoom = onOpenCreateRoom,
            filterActive = filterExpanded,
            onToggleFilter = { filterExpanded = !filterExpanded }
        )

        ArenaStartBanner(
            visible = viewModel.arenaJustStarted,
            onTimeout = viewModel::dismissArenaBanner
        )

        PullToRefreshBox(
            isRefreshing = viewModel.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { ArenaAnchorCard(state = viewModel.arenaState) }

                if (filterExpanded) {
                    item { FilterRow(selected = viewModel.filter, onSelect = viewModel::updateFilter) }
                }

                if (visible.isEmpty()) {
                    item {
                        // 创建入口刻意只留菜单栏那一个（「创建比赛」）：
                        // 空态里再放一个「创建房间」是同一个动作的两个入口，
                        // 名字还不一样，读起来像两种房间（2026-09-26 真机反馈）。
                        EmptyRooms(
                            filter = viewModel.filter,
                            onShowAll = viewModel::showAllRooms,
                            onStartPractice = onStartPractice
                        )
                    }
                } else {
                    items(visible, key = { it.id }) { room ->
                        RoomCard(
                            room = room,
                            now = now,
                            joinable = viewModel.canJoin(room),
                            spectatable = viewModel.canSpectate(room),
                            onJoin = { viewModel.joinFromList(room)?.let(onJoinRoom) },
                            onSpectate = { onSpectate(room.id) }
                        )
                    }
                }

                // 列表底部只留一点余量，说明卡不在这里 —— 见下方 [LobbyFooter]。
            }
        }

        // 「关于大厅」钉在内容区底部、**不随列表滚动**：
        // 它挂在 LazyColumn 末尾时，房间里只剩两三条的时候会被顶到屏幕中间，
        // 看上去像另一张房间卡（2026-09-26 真机反馈）。搬到列表外面，
        // 它就永远贴着底部 tab 区，读作「页脚」而不是「一个入口」。
        LobbyFooter()
    }

    viewModel.message?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            containerColor = SurfaceDark,
            title = { Text("无法加入") },
            text = { Text(text, color = TextSecondaryDark) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessage) {
                    Text("知道了", color = Primary)
                }
            }
        )
    }
}

/**
 * 顶栏。右上角只留 WiFi 一个入口。
 *
 * 原先旁边还有「房间号加入」（拨号键盘图标）：房间号整套凭证已按 PRD D5 移除 ——
 * 房间一经创建就在大厅公开列出，点卡片即可进来。留着一个**没有输入对象**的入口，
 * 只会把人引向一条已经不存在的路径（2026-09-26 真机反馈）。
 */
@Composable
private fun LobbyHeader(onOpenOnline: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 顶栏避让状态栏：任何页面的头部都不得顶进时间/信号/电量那一条。
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "比赛大厅",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "在线对战 · 房间 · 观战",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onOpenOnline) {
            Icon(Icons.Filled.Wifi, contentDescription = "联机状态", tint = Primary)
        }
    }
}

/**
 * 菜单行（Dartsmind 口径）：**创建比赛**是页面主动作放左侧；
 * 右侧是筛选按钮——现在展开/收起现有的筛选 chips，语义上是「筛选」的统一入口，
 * 后续把 chips 换成筛选面板时这个按钮的位置和含义都不用再动（功能预留）。
 */
@Composable
private fun LobbyMenuBar(
    onCreateRoom: () -> Unit,
    filterActive: Boolean,
    onToggleFilter: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Primary)
                .clickable(onClick = onCreateRoom)
                .padding(horizontal = 20.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = OnPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "创建比赛",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = OnPrimary
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onToggleFilter,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (filterActive) Primary.copy(alpha = 0.18f) else SurfaceVariantDark)
        ) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = "筛选房间",
                tint = if (filterActive) Primary else TextSecondaryDark
            )
        }
    }
}

/** 开赛横幅停留时长：够读完一句话，又不至于挡住下面的房间列表。 */
private const val ARENA_BANNER_MILLIS = 3_000L

/** 大厅时钟的走秒间隔（预约房倒计时 / 到期倒计时的唯一驱动）。 */
private const val TICK_MILLIS = 1_000L

/**
 * 擂台开赛横幅（一次性，3 秒自动收起）。
 *
 * 只在观察到「尚未开赛 → 进行中」的跳变时出现，把时间锚点从「一个未来时刻」兑现成
 * 「此刻正在发生」——这是低供给阶段最便宜的一次召回提示。
 *
 * 刻意**不做成系统通知**（无需通知权限、也不打扰已经离开的用户），也刻意**不可点击**：
 * 它的职责只是通知「到点了」，具体动作仍由大厅本身提供（创建房间 / 加入 / 练几镖）。
 */
@Composable
private fun ArenaStartBanner(
    visible: Boolean,
    onTimeout: () -> Unit
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(ARENA_BANNER_MILLIS)
            onTimeout()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Row(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, top = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Success.copy(alpha = 0.16f))
                .border(1.dp, Success.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = Success,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "官方擂台开始了 —— 现在创建房间最容易配上人",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 官方擂台时间锚点卡片。
 *
 * 由真实时钟驱动，与 Mock 房间数据无关。进行中时用呼吸点强化「此刻是活的」——
 * 低供给阶段用户最需要被确认的正是这件事。
 */
@Composable
private fun ArenaAnchorCard(state: ArenaState) {
    val live = state is ArenaState.Live
    val accent = if (live) Success else Accent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Schedule,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.headline,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (live) {
                    Spacer(Modifier.width(8.dp))
                    LiveDot(accent)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                state.subline,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 进行中的呼吸指示点。 */
@Composable
private fun LiveDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "arena-live")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "arena-live-alpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = dotAlpha))
    )
}

@Composable
private fun FilterRow(selected: RoomFilter, onSelect: (RoomFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoomFilter.entries.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) Primary else SurfaceVariantDark)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    option.label,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) OnPrimary else TextSecondaryDark
                )
            }
        }
    }
}

private enum class RoomAction { JOIN, SPECTATE, FULL, LOCKED, ENDED }

/** 卡片状态色（同时用于左侧竖条与文字徽标，见 [RoomCard] 注释）。 */
@Composable
private fun statusTone(status: RoomStatus): Color = when (status) {
    RoomStatus.WAITING -> Success
    RoomStatus.PLAYING -> Secondary
    RoomStatus.ENDED -> TextDisabledDark
}

/**
 * 房间卡片。
 *
 * ## 状态怎么表达（等了很久才定的方案）
 *
 * **左侧一条 3dp 竖条 + 一个三字徽标**。只靠颜色不行：深色主题下三种色相在快速滑动时
 * 分辨不出来，色弱用户更是完全读不到；只靠文字也不行：徽标要在房名那一行里抢位置，
 * 状态的**扫读**效率就没了。竖条不占宽度、在列表里连成一条色带，一眼能分出
 * 「上面三间能进、下面两间是记录」，徽标再给出精确的那一句。
 *
 * ## 这一版删掉了什么
 *
 * - **四个圆形空位**：本作是 1v1，画四个永远填不满的空位，读出来的是「这房间缺三个人」；
 *   它同时把卡片撑高一倍，一屏只剩三张卡（2026-09-26 真机反馈）。
 * - **房间号**：它已经不是加入凭证（房间在大厅公开列出），留着只是把人引向一个
 *   没有输入对象的号码。
 *
 * ## 布局为什么是三行
 *
 * 第一行「房名 + 状态 + 人数」：这三件事是**扫列表**时唯一被读的信息，
 * 且人数与徽标都占固定宽度，因此各张卡片的这两列必然对齐；
 * 第二行「规则摘要 + 动作」：动作块与「这是什么局」同层，缩短「我能不能进」的动线；
 * 第三行（有才有）时间与房主实力：不常出现的信息不占用常驻高度。
 */
@Composable
private fun RoomCard(
    room: Room,
    now: Long,
    joinable: Boolean,
    spectatable: Boolean,
    onJoin: () -> Unit,
    onSpectate: () -> Unit
) {
    val action = when {
        joinable -> RoomAction.JOIN
        room.status == RoomStatus.ENDED -> RoomAction.ENDED
        room.status == RoomStatus.WAITING -> RoomAction.FULL
        spectatable -> RoomAction.SPECTATE
        else -> RoomAction.LOCKED
    }
    val tone = statusTone(room.status)
    val timeText = RoomSchedule.cardLabel(room.startsAt, now)
    val hostText = hostStatsLabel(room)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
    ) {
        // 状态竖条：不占布局宽度，却能在整列卡片上连成一条色带。
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .background(tone)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    room.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                StatusBadge(room.status)
                Spacer(Modifier.width(6.dp))
                // 固定宽度：各张卡片的「x/2 人」靠它右对齐成一列。
                Box(modifier = Modifier.width(SEATS_WIDTH), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        room.seatsLabel,
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    room.config.summary(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (action != RoomAction.ENDED) {
                    Spacer(Modifier.width(8.dp))
                    RoomActionButton(
                        action = action,
                        onJoin = onJoin,
                        onSpectate = onSpectate
                    )
                }
            }

            // 房主那一行现在**几乎总是有**（昵称兜底后恒非空），因此第三行常驻；
            // 这是有意的：扫列表时「房间名 / 规则 / 谁开的」是同一眼要看完的三件事。
            if (timeText != null || hostText != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (timeText != null) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(timeText, fontSize = 11.sp, color = TextSecondaryDark, maxLines = 1)
                    }
                    if (timeText != null && hostText != null) {
                        Text(" · ", fontSize = 11.sp, color = TextDisabledDark)
                    }
                    if (hostText != null) {
                        Text(
                            hostText,
                            fontSize = 11.sp,
                            color = TextSecondaryDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** 「x/2 人」这一列的宽度：所有卡片共用，因此这一列必然对齐。 */
private val SEATS_WIDTH = 48.dp

/**
 * 第三行：**房主昵称 + 实力**（`房主：茄子 · PPR 42.6 · 信用 96 极好`）。
 *
 * 房主昵称是 2026-09-27 才补上的：此前这一行只有 PPR / 信用，
 * 而房间名（「茄子的房间1」）与房主昵称（「茄子」）是**两件不同的事** ——
 * 前者是这一局叫什么，后者是谁开的。房间名可以被房主随手写成任何东西，
 * 于是在大厅里「这间房是谁的」只能靠猜房间名里有没有夹着名字。
 * 现在两个都写，且**分开两行**：混在一行里会让扫列表的人把房间名读成人名。
 *
 * 实力部分刻意**不**用 0 兜底：「PPR 0.0 · 信用 0」会被读成「这个人很差」，
 * 而真实含义只是「他还没打过 / 这一端拿不到数据」——错误的具体比留白糟糕得多。
 */
private fun hostStatsLabel(room: Room): String? {
    val ppr = room.hostPpr?.takeIf { it > 0.0 }
    val credit = room.hostCredit
    // 昵称**永远有**（房间一定有房主），因此这一行只要有房间就显示 ——
    // 「谁开的这间房」是决定要不要点进去的第一手信息，不能因为它没打过比赛就跟着消失。
    val parts = mutableListOf<String>()
    if (ppr != null) parts += "PPR ${"%.1f".format(Locale.US, ppr)}"
    if (credit != null) parts += "信用 $credit ${CreditScorer.tier(credit)}"
    val stats = parts.joinToString(" · ")
    return if (stats.isEmpty()) "房主：${room.creatorName}" else "房主：${room.creatorName} · $stats"
}

@Composable
private fun StatusBadge(status: RoomStatus) {
    val label = when (status) {
        RoomStatus.WAITING -> "等候中"
        RoomStatus.PLAYING -> "对局中"
        RoomStatus.ENDED -> "已结束"
    }
    val color = statusTone(status)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/**
 * 动作块：可加入 = 实心「加入比赛」（页面里最醒目的可点元素）；
 * 进行中可观战 = 眼睛图标；已满 / 禁观战 = 灰态。同一位置同一尺寸，只是内容与色态不同。
 */
@Composable
private fun RoomActionButton(
    action: RoomAction,
    onJoin: () -> Unit,
    onSpectate: () -> Unit
) {
    val enabled = action == RoomAction.JOIN || action == RoomAction.SPECTATE
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    action == RoomAction.JOIN -> Primary
                    enabled -> Secondary.copy(alpha = 0.16f)
                    else -> SurfaceVariantDark
                }
            )
            .then(
                if (enabled && action != RoomAction.JOIN) {
                    Modifier.border(1.dp, Secondary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled) {
                if (action == RoomAction.SPECTATE) onSpectate() else onJoin()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (action == RoomAction.SPECTATE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Visibility,
                    contentDescription = "观战",
                    tint = Secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text("观战", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Secondary)
            }
        } else {
            Text(
                when (action) {
                    RoomAction.JOIN -> "加入比赛"
                    RoomAction.FULL -> "已满"
                    else -> "禁观战"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (action == RoomAction.JOIN) OnPrimary else TextDisabledDark
            )
        }
    }
}

/**
 * 空结果占位（两种语义，不要混为一谈）。
 *
 * - 筛选后为空 → 是**筛选问题**，动作是回到「全部」；
 * - 全部为空 → 是**供给问题**，动作是「此刻单人能做的事」。
 *
 * 全部为空时刻意**不显示在线人数**：低供给下把「空」渲染成数字只会加速流失，
 * 而「成为第一个创建房间的人吧」会把孤独用户直接推进空等候室。
 *
 * 「现在还没有人开局 / 官方擂台进行中……」这两行已删（2026-09-26 反馈）：
 * 打开大厅看到一片空白，用户本来就知道「没人」—— 再写一遍只是把这件事说得更响，
 * 而且「官方擂台」是他不认识的词，一句解释不清的概念出现在空页面上，只会让人怀疑
 * 自己漏看了什么功能。留一个立刻能做的动作就够了。
 */
@Composable
private fun EmptyRooms(
    filter: RoomFilter,
    onShowAll: () -> Unit,
    onStartPractice: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (filter == RoomFilter.ALL) {
            // 只留「先去练几镖」：创建房间的入口在页面顶部菜单栏（「创建比赛」），
            // 这里再给一个就是同一个动作两个入口。
            OutlineAction("先去练几镖", Secondary, onStartPractice)
        } else {
            EmptyCopy(
                title = "没有符合条件的房间",
                hint = "换个筛选条件，或自己开一局"
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineAction("看全部房间", Secondary, onShowAll)
            }
        }
    }
}

@Composable
private fun EmptyCopy(title: String, hint: String) {
    Text(
        title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(6.dp))
    Text(
        hint,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun FilledAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnPrimary)
    }
}

@Composable
private fun OutlineAction(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, accent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun LobbyFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 左右与房间列表同宽（20dp）；底部贴 tab 区，只留一点点呼吸。
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceVariantDark)
            .padding(14.dp)
    ) {
        Text(
            "关于大厅",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "未联机时房间与观战数据为本地模拟，用于验证完整交互流程；" +
                "联机后这里显示的就是云端上的真实房间，" +
                "点右上角 WiFi 图标可随时查看连接情况。\n" +
                "「官方擂台」为固定时段约定（${OfficialArena.WINDOW_LABEL}），" +
                "由本地时钟驱动，不依赖后端。",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 房间设置摘要，如「X01 - 501 · 3 局胜 · 双倍出」。 */
fun MatchConfig.summary(): String = buildString {
    append(displayName)
    append(" · ")
    append(
        when (mode) {
            MatchMode.CASUAL -> "休闲局"
            MatchMode.MULTI_LEG -> "$legsToWin 局胜"
        }
    )
    if (matchType == MatchType.X01) {
        // 规则档位从**档位枚举**取文案（2026-09-12）：老布尔只有两态，
        // 会把「大师出 / 50-50」显示成「双出 / 25-50」。结束档是 X01 的核心参数，始终显示；
        // 其余三档只在偏离缺省时才显示，免得默认局也挂一串没人看的字。
        append(" · ").append(outMode.label)
        if (inMode != InMode.STRAIGHT_IN) append(" · ").append(inMode.label)
        if (bullMode != BullMode.STANDARD_25_50) append(" · 牛眼 ").append(bullMode.label)
        if (maxRounds > 0) append(" · 最多 ").append(maxRounds).append(" 轮")
    }
}


