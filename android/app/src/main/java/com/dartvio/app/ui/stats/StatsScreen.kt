package com.dartvio.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dartvio.app.DartVioApp
import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.domain.model.BullMode
import com.dartvio.app.domain.model.CricketTarget
import com.dartvio.app.domain.model.CricketVariant
import com.dartvio.app.domain.model.InMode
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.domain.stats.CricketStats
import com.dartvio.app.domain.stats.PracticeSummary
import com.dartvio.app.domain.stats.StatsSampleData
import com.dartvio.app.domain.stats.StatsTrend
import com.dartvio.app.domain.stats.TrendPoint
import com.dartvio.app.domain.stats.X01Stats
import com.dartvio.app.ui.components.BarChart
import com.dartvio.app.ui.components.TrendLegend
import com.dartvio.app.ui.components.TrendLineChart
import com.dartvio.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M9 第①期：个人统计页（F9.1 X01 / Cricket 双 Tab）+ 比赛历史（F9.2 / F9.3）。
 *
 * 只展示本地已落库数据即可支撑的指标。在线榜（M5）、练习模式（M11）本期不出现空入口。
 */
@Composable
fun StatsScreen(
    onBack: (() -> Unit)? = null,
    onOpenAchievements: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
    viewModel: StatsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf<MatchWithPlayers?>(null) }
    // 玩法筛选（M9 §8.2）：默认「全部」，不改变历史列表默认口径。
    var variantFilter by remember { mutableStateOf<CricketVariant?>(null) }
    // 一级分类：本地对局 / 练习 / 比赛大厅。三类数据不可比，必须分开摆（见 [StatsScope]）。
    var scope by remember { mutableStateOf(StatsScope.LOCAL) }
    // 示例数据开关：Beta 期第一天打开的人手上没有一场对局，一页图表就都白做了。
    var demoMode by remember { mutableStateOf(false) }

    val snapshot = if (scope == StatsScope.ARENA) state.arenaSnapshot else state.snapshot
    val historyMatches = if (scope == StatsScope.ARENA) state.arenaMatches else state.matches
    // 开关换的是**数据源**，不是换一套界面：下面所有卡片照旧从同一份
    // StatsSnapshot / TrendPoint 取数。这样「图表怎么画」的迭代碰不到本地数据，
    // 本地表结构的变化也不会带翻图表 —— 两者靠数据模型解耦，而不是靠开关分支解耦。
    val shownSnapshot = if (demoMode) StatsSampleData.snapshot else snapshot
    val emptyHint = when (scope) {
        StatsScope.ARENA -> "还没有比赛大厅的对局：在大厅里打完一场，数据会出现在这里。"
        StatsScope.PRACTICE -> "还没有练习记录：去训练中心练一组，数据会出现在这里。"
        // 「打完」要说清楚是**打完整场**：一局 501 打完只是中场（默认已改为先赢 1 局，
        // 但选了多局的人仍会在这里），不说的话用户只会看到一片空白而不知道差在哪一步
        // （2026-09-27 真机反馈：打完一局本地 X01，本地对局口径没有数据）。
        StatsScope.LOCAL -> "还没有本地对局：从首页开一局 X01 / Cricket，" +
            "打到整场结束（决胜局收镖）后数据会出现在这里。"
    }

    /*
     * 练习汇总**进分类时才读**：它的数据源是 SP 与几张练习表，没有 Flow 可订阅，
     * 而它的更新时机就是「刚练完回到这一页」—— 这个时机重读一次正好。
     */
    LaunchedEffect(scope) {
        if (scope == StatsScope.PRACTICE) viewModel.refreshPractice()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    "数据",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            navigationIcon = {
                // 作为一级 Tab 时不显示返回按钮
                if (onBack != null) {
                    TextButton(onClick = onBack) {
                        Text("返回", color = Primary)
                    }
                }
            },
            actions = { DemoSwitch(demoMode) { demoMode = it } },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                navigationIconContentColor = Primary,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )

        // 一级分类：先选「在哪打的」，再选「看哪项」。
        // 顺序不能反 —— X01 的 PPR 在本地与大厅里是两个不同的数，
        // 让用户先选玩法，他会以为切到 Cricket 就换了一套数据，其实没换口径。
        ScopeSelector(
            selected = scope,
            onSelect = { scope = it },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        /*
         * 数据来源**常驻一行**：这一页的所有数字都来自本机 SQLite / SP，不上服务器。
         * 「不上」这句话只有写在看得到的地方才有人信，而它正是用户敢不敢打这一局的前提。
         */
        Text(
            "以下数据全部来自本机存储，不上传 · 卸载 App 即删除",
            fontSize = 11.sp,
            color = TextDisabledDark,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(6.dp))

        /*
         * 练习那一栏**没有** X01 / Cricket / 历史 这三个二级 Tab：
         * 它不是某一种玩法的成绩，六个练习项目各有一套自己的数，
         * 套「玩法」分栏只会让人以为这是另一种对局统计。
         */
        if (scope != StatsScope.PRACTICE) {
            SecondaryTabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = Primary,
            ) {
                listOf("X01", "Cricket", "历史").forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
        }

        // 示例模式的**常驻标识**：写在页面顶部而不是只在图表里标一次 ——
        // 让人把自己的战绩和演示数据看混，比空页面更糟，这是这个开关唯一的失败模式。
        if (demoMode) {
            DemoBanner()
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        Column(modifier = Modifier.weight(1f)) {
            // 趋势跟着**当前口径**走（本地 / 大厅）：混起来画的曲线比不画更糟 ——
            // 两种场合的水平本来不同，一条跨口径的折线会把「换了场子」画成「状态起伏」。
            val trend = remember(historyMatches, demoMode) {
                if (demoMode) StatsSampleData.trend() else StatsTrend.x01ThreeDartAvg(historyMatches)
            }
            if (scope == StatsScope.PRACTICE) {
                PracticeTab(state.practice, emptyHint)
            } else when (tab) {
                0 -> X01StatsTab(shownSnapshot.x01, trend, demoMode, emptyHint)
                1 -> CricketStatsTab(shownSnapshot.cricket, emptyHint)
                else -> HistoryTab(
                    matches = historyMatches.filterByVariant(variantFilter),
                    filter = variantFilter,
                    onFilterChange = { variantFilter = it },
                    onOpen = { detail = it },
                )
            }
        }

        // 数据管理入口：与统计同属「我的数据」，从「我的」页整体搬过来 ——
        // 那里剩下的应该只是**身份**，不是一堆数据动作（2026-09-26 反馈）。
        DataToolsRow(
            onOpenAchievements = onOpenAchievements,
            onOpenLeaderboard = onOpenLeaderboard,
        )
    }

    detail?.let { MatchDetailDialog(it) { detail = null } }
}

/** 一级分类选择器（本地训练 / 比赛大厅）。 */
@Composable
private fun ScopeSelector(
    selected: StatsScope,
    onSelect: (StatsScope) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StatsScope.entries.forEach { item ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (item == selected) Primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(item) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item == selected) OnPrimary else TextSecondaryDark
                )
            }
        }
    }
}

/**
 * 页面底部的一排数据动作（成就墙 / 排行榜 / 清除本地数据）。
 *
 * 三个都是**结果性**动作，不是浏览动作，因此钉在内容区底部而不是混进统计列表：
 * 它们在 X01 / Cricket / 历史 三个 Tab 下的行为完全一致，
 * 放进 Tab 内容里就得写三遍，还会在切换 Tab 时跟着消失。
 */
@Composable
private fun DataToolsRow(
    onOpenAchievements: () -> Unit,
    onOpenLeaderboard: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as? DartVioApp
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DataToolChip("成就墙", modifier = Modifier.weight(1f), onClick = onOpenAchievements)
        DataToolChip("排行榜", modifier = Modifier.weight(1f), onClick = onOpenLeaderboard)
        DataToolChip(
            label = "清除本地数据",
            modifier = Modifier.weight(1f),
            danger = true,
            onClick = { showClearDialog = true }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = SurfaceDark,
            title = { Text("清除本地数据？") },
            text = { Text("将删除全部对局历史、练习统计与成就进度，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    scope.launch {
                        app?.matchRepository?.clearAll()
                        app?.achievementRepository?.clearAll()
                    }
                }) { Text("清除", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消", color = TextSecondaryDark) }
            }
        )
    }
}

@Composable
private fun DataToolChip(
    label: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, if (danger) Error.copy(alpha = 0.5f) else Divider, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = if (danger) Error else TextSecondaryDark
        )
    }
}

// ------------------------------------------------------------------ X01 Tab

@Composable
private fun X01StatsTab(
    stats: X01Stats,
    trend: List<TrendPoint>,
    demo: Boolean = false,
    emptyHint: String = "",
) {
    if (!stats.hasData) {
        EmptyHint(emptyHint.ifBlank { "还没有 X01 对局记录，打完一场就会出现在这里。" })
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 趋势放在**最上面**：一串静态指标不会告诉任何人「我在变好还是变差」，
        // 而这是打开数据页的人真正想知道的事。先看走势，再看现在的水平是多少。
        if (trend.size >= 2) {
            item {
                SectionTitle("进步曲线（最近 ${trend.size} 场 · 三镖均）")
            }
            item {
                TrendCard(trend)
            }
        }
        // 示例模式下再摆两块**不同画法**的图：
        // 折线看走势、柱状看分段量级、占比条看构成 —— 同一份数字换三种问法，
        // 每种问法配它该有的图，这是「按用途选图表」而不是「按喜好选图表」。
        if (demo) {
            item { SectionTitle("分布 · 近 8 周场次") }
            item { WeeklyBarsCard() }
            item { SectionTitle("占比 · 42 场的胜负平") }
            item { OutcomeShareCard() }
            item { Footnote("这三块图的数值都是示例，仅用于演示图表形态。") }
        }
        item {
            SectionTitle("核心指标")
        }
        item {
            MetricBarList(
                listOf(
                    MetricRowData("全部对局 PPR", fmt2(stats.pprAll), barCap(stats.pprAll, 100.0)),
                    MetricRowData("正式赛 PPR", fmt2(stats.pprFormal), barCap(stats.pprFormal, 100.0)),
                    MetricRowData("全部胜率", fmtPct(stats.winRateAll), barPct(stats.winRateAll)),
                    MetricRowData("正式赛胜率", fmtPct(stats.winRateFormal), barPct(stats.winRateFormal)),
                    MetricRowData("最高收尾", stats.highestCheckout.toString(), barCap(stats.highestCheckout.toDouble(), 170.0)),
                    MetricRowData("最高连胜", stats.longestWinStreak.toString(), barCap(stats.longestWinStreak.toDouble(), 10.0)),
                    // 场均镖数 / Bust 率是「越低越好」：柱长表示表现（表现越好柱越长），
                    // 直接按数值画会变成「越差柱越长」，同一屏两种语义没人读得懂。
                    MetricRowData("场均镖数", fmt2(stats.dartsPerLeg), barLower(stats.dartsPerLeg, 40.0)),
                    MetricRowData("180 次数", stats.total180.toString(), barCap(stats.total180.toDouble(), 30.0)),
                    MetricRowData("最高单回合", stats.maxTurnScore.toString(), barCap(stats.maxTurnScore.toDouble(), 180.0)),
                    MetricRowData("平均每镖得分", fmt2(stats.scorePerDart), barCap(stats.scorePerDart, 4.0)),
                    MetricRowData("Bust 率", fmtPct(stats.bustRate), barLower(stats.bustRate, 1.0)),
                    MetricRowData("Checkout 率", fmtPct(stats.checkoutRate), barPct(stats.checkoutRate)),
                )
            )
        }
        item { CategoryDivider() }
        item {
            SectionTitle("对局统计")
        }
        item {
            // 数量类指标没有天然满值（打 3 场和打 300 场都正常）：
            // 柱长按**组内最大值**归一，回答的是「这些数字里哪个是我量的主体」。
            val counts = listOf(
                stats.matchCount, stats.formalMatchCount,
                stats.winCount, stats.formalWinCount,
                stats.legCount, stats.formalLegCount
            )
            val maxCount = counts.max().toDouble()
            MetricBarList(
                listOf(
                    MetricRowData("总场次", stats.matchCount.toString(), barRel(stats.matchCount.toDouble(), maxCount)),
                    MetricRowData("正式赛场次", stats.formalMatchCount.toString(), barRel(stats.formalMatchCount.toDouble(), maxCount)),
                    MetricRowData("胜场", stats.winCount.toString(), barRel(stats.winCount.toDouble(), maxCount)),
                    MetricRowData("正式赛胜场", stats.formalWinCount.toString(), barRel(stats.formalWinCount.toDouble(), maxCount)),
                    MetricRowData("总局数", stats.legCount.toString(), barRel(stats.legCount.toDouble(), maxCount)),
                    MetricRowData("正式赛局数", stats.formalLegCount.toString(), barRel(stats.formalLegCount.toDouble(), maxCount)),
                )
            )
        }
        item { Footnote("PPR = 总得分 ÷ (投镖数 ÷ 3)。回合不足 3 镖时（Bust / 提前收镖）分母偏小，PPR 会略偏高。") }
        item { Footnote("「全部胜率」含 AI 对战与本地休闲赛；「正式赛」仅统计多局全真人对局。") }
        item { Footnote("柱长 = 该项的表现（越长越好），按参考满值折算：胜率按 100%、最高收尾按 170、PPR 按 100、180 按 30；数量类按组内最大值归一。") }
    }
}

/**
 * 数据页右上角的**示例数据开关**。
 *
 * 为什么是开关而不是「页面里加一段演示」：开关关掉之后页面必须**彻底变回自己的数据**，
 * 演示内容不能留下任何痕迹（不写库、不进统计、不上传），否则用户会以为自己打过那 42 场。
 */
@Composable
private fun DemoSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "示例",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (checked) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun DemoBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Warning.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "下面是示例数据，不是你的战绩 —— 关掉右上角「示例」开关即恢复。",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Warning,
        )
    }
}

/**
 * 进步曲线：**连续进步这件事本身必须先被看见**。
 *
 * 人选的就是趋势 —— 「我练了三周到底有没有长进」；
 * 一串静态指标（PPR 42.6）回答不了它，因为它缺的就是「以前是多少」。
 *
 * 标题里写明**三镖均**：不写清楚，用户会以为这条线是 PPR，
 * 然后对着两条不同口径的线相信自己比数据差了一截。
 */
@Composable
private fun TrendCard(points: List<TrendPoint>) {
    val values = points.map { it.value }
    // 首尾差在**一场的起伏**之内就别下结论：这是噪声不是进步。
    val delta = values.last() - values.first()
    val flat = kotlin.math.abs(delta) < 2.0
    val tone = when {
        flat -> MaterialTheme.colorScheme.onSurfaceVariant
        delta > 0 -> Success
        else -> Error
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        TrendLineChart(
            values = values,
            color = tone,
            guideColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TrendLegend(values = values)
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                points.size < 3 -> "再多打几场，曲线会开始说话。"
                flat -> "整体持平：这段时间的波动还在同一水平上。"
                delta > 0 -> "比起点高 ${fmt2(delta)} 分 —— 方向对了，稳住这个节奏。"
                else -> "比起点低 ${fmt2(-delta)} 分 —— 先看换来的是不是更稳的手感。"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = tone,
        )
    }
}

/** 柱状：每周场次是**独立的量**，段与段之间没有中间态，所以不该画成折线。 */
@Composable
private fun WeeklyBarsCard() {
    val rows = StatsSampleData.weeklyMatches
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        BarChart(
            labels = rows.map { it.first },
            values = rows.map { it.second },
            color = Primary,
            guideColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            rows.joinToString("　") { (label, value) -> "${label} ${value.toInt()}" },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 占比条：胜 / 负 / 平三部分。
 *
 * 不用饼图的理由写在 [StatsSampleData.outcomeShare] 里 —— 这里补一条 UI 侧的：
 * 饼图把「标签」赶到图外面去，横条可以让标签、条、数字在同一行读完。
 */
@Composable
private fun OutcomeShareCard() {
    val rows = StatsSampleData.outcomeShare
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { (label, ratio) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(24.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(ratio.toFloat().coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(4.dp))
                            .background(Success)
                    )
                }
                Text(
                    fmtPct(ratio),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

// -------------------------------------------------------------- Cricket Tab

@Composable
private fun CricketStatsTab(stats: CricketStats, emptyHint: String = "") {
    if (!stats.hasData) {
        EmptyHint(emptyHint.ifBlank { "还没有 Cricket 对局记录，打完一场就会出现在这里。" })
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("投镖质量") }
        item {
            MetricBarList(
                listOf(
                    MetricRowData("C1 Mark 率", fmtPct(stats.markRate), barPct(stats.markRate)),
                    MetricRowData("C2 分区关闭率", fmtPct(stats.closeRate), barPct(stats.closeRate)),
                    MetricRowData("C3 三倍区命中率", fmtPct(stats.tripleRate), barPct(stats.tripleRate)),
                    MetricRowData("C4 Bull 命中率", fmtPct(stats.bullRate), barPct(stats.bullRate)),
                    MetricRowData("C7 得分效率", fmt2(stats.scoreEfficiency), barCap(stats.scoreEfficiency, 2.0)),
                )
            )
        }
        item { CategoryDivider() }
        item { SectionTitle("回合与胜负") }
        item {
            val countMax = maxOf(stats.matchCount, stats.winCount).toDouble()
            MetricBarList(
                listOf(
                    MetricRowData("C5 平均回合得分", fmt2(stats.avgTurnScore), barCap(stats.avgTurnScore, 60.0)),
                    // 关满 7 个分区最快 7 轮、最慢常见上限 21 轮：越低越好，柱长表示表现。
                    MetricRowData("C6 平均关闭回合数", fmt2(stats.avgTurnsToClose), barLower(stats.avgTurnsToClose, 21.0)),
                    MetricRowData("C9 最高单回合得分", stats.maxTurnScore.toString(), barCap(stats.maxTurnScore.toDouble(), 180.0)),
                    MetricRowData("C8 Cricket 胜率", fmtPct(stats.winRate), barPct(stats.winRate)),
                    MetricRowData("对局场次", stats.matchCount.toString(), barRel(stats.matchCount.toDouble(), countMax)),
                    MetricRowData("胜场", stats.winCount.toString(), barRel(stats.winCount.toDouble(), countMax)),
                )
            )
        }
        if (stats.firstClosedHistogram.isNotEmpty()) {
            item { CategoryDivider() }
            item { SectionTitle("C10 首关分区分布") }
            item {
                val histMax = stats.firstClosedHistogram.values.max().toDouble()
                MetricBarList(
                    stats.firstClosedHistogram.entries
                        .sortedByDescending { it.value }
                        .map { (target, count) ->
                            MetricRowData("首个关闭 ${labelOf(target)}", "${count} 次", barRel(count.toDouble(), histMax))
                        }
                )
            }
        }
        item { Footnote("C1 Mark 率采用逐镖累加的获得 Mark 数（每镖 0–3），不是封顶 3 的标记状态求和。") }
        item { Footnote("C3 分母为总镖数：键盘录入不记录瞄准意图，「瞄准后命中率」属 M8 硬件专属指标，本期不展示。") }
        item { Footnote("C6 仅统计成功关满全部 7 个分区的局。C8 仅统计 S 级（多局全真人）对局。") }
        item { Footnote("柱长 = 该项的表现（越长越好）：率类按 100%、C7 按 2.0、C5 按 60、C9 按 180 折算；分布类按组内最大值归一。") }
    }
}

// ------------------------------------------------------------------ 练习 Tab

/**
 * 练习口径：**练了多少 + 各项目练到什么水平**。
 *
 * 与 X01 / Cricket 那两栏刻意不同形：练习没有「一场比赛」的概念，
 * 也就没有 PPR、胜率、局数这些比分指标。这里能回答的是
 * 「我最近练得勤不勤」「99 Darts 打到多少分了」—— 两个都是真问题，
 * 但都不是把练习塞进对局表能算出来的。
 */
@Composable
private fun PracticeTab(summary: PracticeSummary, emptyHint: String) {
    if (!summary.hasAny) {
        EmptyHint(emptyHint.ifBlank { "还没有练习记录，练一组就会出现在这里。" })
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 坚持程度排在最前：练飞镖这件事，**频率比水平更早决定水平**。
        item { SectionTitle("练习坚持") }
        item {
            val maxCount = maxOf(summary.sessions, summary.daysTotal, summary.streakCurrent, summary.streakBest).toDouble()
            MetricBarList(
                listOf(
                    MetricRowData("练习次数", "${summary.sessions} 次", barRel(summary.sessions.toDouble(), maxCount)),
                    MetricRowData("练习天数", "${summary.daysTotal} 天", barRel(summary.daysTotal.toDouble(), maxCount)),
                    MetricRowData("当前连续", "${summary.streakCurrent} 天", barRel(summary.streakCurrent.toDouble(), maxCount)),
                    MetricRowData("最长连续", "${summary.streakBest} 天", barRel(summary.streakBest.toDouble(), maxCount)),
                )
            )
        }

        item { CategoryDivider() }
        item { SectionTitle("个人最佳") }
        item {
            MetricBarList(
                listOf(
                    MetricRowData(
                        "99 Darts 最高分",
                        if (summary.ninetyNineBestScore > 0) "${summary.ninetyNineBestScore} · ${summary.ninetyNineBestSector}区" else "—",
                        if (summary.ninetyNineBestScore > 0) barCap(summary.ninetyNineBestScore.toDouble(), 999.0) else null
                    ),
                    MetricRowData(
                        "Count Up 最高分",
                        if (summary.countUpBestScore > 0) "${summary.countUpBestScore} 分" else "—",
                        if (summary.countUpBestScore > 0) barCap(summary.countUpBestScore.toDouble(), 1000.0) else null
                    ),
                    MetricRowData(
                        "Cricket MPR 最佳",
                        if (summary.mprBest > 0f) fmt2(summary.mprBest.toDouble()) else "—",
                        if (summary.mprBest > 0f) barCap(summary.mprBest.toDouble(), 3.0) else null
                    ),
                    MetricRowData("落点诊断累计", "${summary.impactDarts} 镖", barCap(summary.impactDarts.toDouble(), 1000.0)),
                )
            )
        }

        if (summary.hasRush) {
            item { CategoryDivider() }
            item { SectionTitle("极速结镖") }
            item {
                val rushMax = maxOf(summary.rushAttempts, summary.rushCheckouts, summary.ninetyNineCompleted).toDouble()
                MetricBarList(
                    listOf(
                        MetricRowData("已判题目", "${summary.rushAttempts} 题", barRel(summary.rushAttempts.toDouble(), rushMax)),
                        MetricRowData("成功结镖", "${summary.rushCheckouts} 题", barRel(summary.rushCheckouts.toDouble(), rushMax)),
                        MetricRowData("成功率", fmtPct(summary.rushHitRate), barPct(summary.rushHitRate)),
                        MetricRowData("99 完成次数", "${summary.ninetyNineCompleted} 次", barRel(summary.ninetyNineCompleted.toDouble(), rushMax)),
                    )
                )
            }
        }

        if (summary.hasVersus) {
            item { CategoryDivider() }
            item { SectionTitle("对抗练习") }
            item {
                val versusMax = maxOf(summary.versusFinished, summary.versusWins, summary.mprSessions).toDouble()
                MetricBarList(
                    listOf(
                        MetricRowData("已完成", "${summary.versusFinished} 场", barRel(summary.versusFinished.toDouble(), versusMax)),
                        MetricRowData("胜场", "${summary.versusWins} 场", barRel(summary.versusWins.toDouble(), versusMax)),
                        MetricRowData("胜率", fmtPct(summary.versusWinRate), barPct(summary.versusWinRate)),
                        MetricRowData("MPR 场次", "${summary.mprSessions} 场", barRel(summary.mprSessions.toDouble(), versusMax)),
                    )
                )
            }
        }

        item {
            Footnote(
                "练习与对局是两套口径：练习数据不写对局表，因此没有 PPR / 胜率 —— " +
                    "把练习塞进那些指标的分母，只会得到一个看着好看、但谁也解释不清的数。"
            )
        }
        item { Footnote("99 Darts / Count Up / MPR 为历史最佳（本机保存），结镖与对抗练习来自本机数据库。") }
        item { Footnote("柱长 = 该项的表现（越长越好）：率类按 100%、MPR 按 3 折算；次数/天数类按组内最大值归一。") }
    }
}

// ------------------------------------------------------------------ 历史 Tab

/**
 * 玩法筛选只作用于 Cricket 局：X01 没有变体概念，落库恒为 standard，
 * 若不加 gameType 判断，「标准」会误把 X01 局也算进来。
 */
private fun List<MatchWithPlayers>.filterByVariant(variant: CricketVariant?): List<MatchWithPlayers> {
    if (variant == null) return this
    return filter { item ->
        item.match.gameType == MatchType.CRICKET.name &&
            CricketVariant.fromKey(item.match.cricketVariant) == variant
    }
}

@Composable
private fun HistoryTab(
    matches: List<MatchWithPlayers>,
    filter: CricketVariant?,
    onFilterChange: (CricketVariant?) -> Unit,
    onOpen: (MatchWithPlayers) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        VariantFilterRow(filter, onFilterChange)
        if (matches.isEmpty()) {
            EmptyHint(if (filter == null) "还没有对局记录。" else "这个玩法下还没有对局记录。")
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(matches) { item -> HistoryRow(item) { onOpen(item) } }
        }
    }
}

/** 「全部 / 标准 / 不计分 / 生死局」筛选条（M9 §8.2），默认全部。 */
@Composable
private fun VariantFilterRow(selected: CricketVariant?, onSelect: (CricketVariant?) -> Unit) {
    val options: List<CricketVariant?> = listOf(null) + CricketVariant.entries
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Text(
                option?.label ?: "全部",
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) OnPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        if (isSelected) Primary else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(50),
                    )
                    .border(1.dp, if (isSelected) Primary else Divider, RoundedCornerShape(50))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun HistoryRow(item: MatchWithPlayers, onClick: () -> Unit) {
    val self = item.players.firstOrNull { it.orderIndex == 0 }
    val result = when {
        self == null -> "—"
        self.isWinner -> "胜"
        else -> "负"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, if (self?.isWinner == true) Primary else Divider, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            result,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (self?.isWinner == true) Primary else TextDisabledDark,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.match.matchTypeLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // 玩法标签（M7 P7.4：历史需可辨识本局玩法）；X01 无变体，不显示。
                if (item.match.gameType == MatchType.CRICKET.name) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        CricketVariant.fromKey(item.match.cricketVariant).label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnPrimary,
                        modifier = Modifier
                            .background(Primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                // 联机标签（M5 T10）：联机局进历史（可回看）但不计入战绩。
                // 它必须**一眼看得出**是什么来路 —— 历史里突然多一场却没有任何标记，
                // 看起来就像数据出错，而不是「我昨天联机打过一场」。
                if (item.match.isLan) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "联机",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(Divider, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                // 含 AI 标签（M9：AI 局计入统计，但需可辨识）
                if (item.match.containsAi) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "含 AI",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnPrimary,
                        modifier = Modifier
                            .background(Accent, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                buildString {
                    append(formatTime(item.match.endedAt))
                    append(" · ")
                    append(item.players.joinToString(" ") { "${it.name} ${it.legsWon}" })
                    // 联机场次带上房间号：房间名随手改、房号会复用，但「哪一场」得有个可追溯的标识。
                    if (item.match.isLan && item.match.roomId.isNotBlank()) {
                        append(" · 房间 ${item.match.roomId}")
                    }
                    if (item.match.isFormal.not() && !item.match.isLan) append(" · 不计入胜率")
                    if (item.match.isLan) append(" · 联机不计入战绩")
                    if (item.match.forfeited) append(" · 对手掉线判负")
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MatchDetailDialog(item: MatchWithPlayers, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = {
            Text(
                buildString {
                    append(item.match.matchTypeLabel)
                    if (item.match.gameType == MatchType.CRICKET.name) {
                        append(" · ")
                        append(CricketVariant.fromKey(item.match.cricketVariant).label)
                    }
                    append(" · ")
                    append(if (item.match.isFormal) "正式赛" else "非正式")
                    if (item.match.containsAi) append(" · 含 AI")
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("结束时间：${formatTime(item.match.endedAt)}")
                Text("时长：${item.match.durationMs / 1000 / 60} 分 ${item.match.durationMs / 1000 % 60} 秒")
                Text("总局数：${item.match.legCount}（先赢 ${item.match.legsToWin} 局）")
                if (item.match.gameType == MatchType.CRICKET.name) {
                    Text("玩法：${CricketVariant.fromKey(item.match.cricketVariant).label}")
                }
                if (item.match.startScore > 0) {
                    // 规则口径从**档位列**读（不再用老布尔）：老布尔只有两态，会把「大师出 / 50-50」
                    // 显示成「双倍出 / 25-50」。默认档不显示，避免每行都挂一串没人关心的默认值。
                    val rules = buildString {
                        if (item.match.x01OutMode != OutMode.DOUBLE_OUT) {
                            append(" · ").append(item.match.x01OutMode.label)
                        }
                        if (item.match.x01InMode != InMode.STRAIGHT_IN) {
                            append(" · ").append(item.match.x01InMode.label)
                        }
                        if (item.match.x01BullMode != BullMode.STANDARD_25_50) {
                            append(" · 牛眼 ").append(item.match.x01BullMode.label)
                        }
                        if (item.match.maxRounds > 0) {
                            append(" · 最多 ").append(item.match.maxRounds).append(" 轮")
                        }
                    }
                    Text("起始分：${item.match.startScore}$rules")
                }
                Text("总镖数：${item.match.totalDarts}")
                Spacer(Modifier.height(4.dp))
                item.players.sortedBy { it.orderIndex }.forEach { p ->
                    Text(
                        buildString {
                            append(if (p.isWinner) "★ " else "   ")
                            append("${p.name}：${p.legsWon} 局 · ")
                            append("PPR ${fmt2(pprOf(p.totalScore, p.dartsThrown))} · ")
                            append("${p.dartsThrown} 镖 · ")
                            append("最高回合 ${p.maxTurnScore}")
                            if (p.marksTotal > 0) append(" · Mark ${p.marksTotal}")
                            if (p.bestCheckout > 0) append(" · 收尾 ${p.bestCheckout}")
                        },
                        fontSize = 12.sp,
                        fontWeight = if (p.isWinner) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        },
    )
}

// ------------------------------------------------------------------ 通用组件

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * 一行指标：名称居左、数值居右、中间一根**双色柱**（2026-09-27 反馈）。
 *
 * 此前「一张卡片一个指标」：一屏只装得下四个数，扫一眼要读完一摞卡片，
 * 而且数字之间没有可比的视觉量 —— 42.6 好不好，得先知道满分是多少。
 * 现在同一类指标排成行，柱长就是「表现」：满柱 = 参考满值，
 * 用户不读数字也能从柱子长短看出自己在什么水平。
 *
 * [bar] 为 0..1 的比例；**null 表示这一行不画柱**（空值「—」，或没有
 * 可信的参考满值 —— 给一个拍的满值，柱子就成了装饰）。
 */
private data class MetricRowData(val label: String, val value: String, val bar: Float? = null)

@Composable
private fun MetricBarList(items: List<MetricRowData>) {
    Column {
        items.forEach { row -> MetricRowView(row) }
    }
}

@Composable
private fun MetricRowView(row: MetricRowData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            row.label,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(112.dp),
        )
        if (row.bar != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Primary.copy(alpha = 0.14f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(row.bar.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Primary)
                )
            }
            Spacer(Modifier.width(10.dp))
        } else {
            // 不画柱的行也要占同样宽度：数值列保持右对齐在同一列上。
            Spacer(Modifier.weight(1f))
        }
        Text(
            row.value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(104.dp),
        )
    }
}

/** 类别之间的**细分割线**：比卡片墙轻得多，但足以让「这是另一类指标」被看见。 */
@Composable
private fun CategoryDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(Divider.copy(alpha = 0.6f))
    )
}

// -------- 柱长换算（纯函数：比例必须可单测、可复算，不能散在组合代码里） --------

/** 率类（0..1）：柱长即百分比。 */
private fun barPct(v: Double): Float = v.toFloat().coerceIn(0f, 1f)

/** 有参考满值的量类（PPR 按 100、最高收尾按 170……）：超出按满柱截断。 */
private fun barCap(v: Double, cap: Double): Float = (v / cap).toFloat().coerceIn(0f, 1f)

/** 越低越好的量（场均镖数、Bust 率）：柱长 = 1 − v/worst，保持「越长越好」的同一语义。 */
private fun barLower(v: Double, worst: Double): Float = (1 - v / worst).toFloat().coerceIn(0f, 1f)

/** 数量类（没有天然满值）：按**组内最大值**归一，回答组内相对量级。 */
private fun barRel(v: Double, groupMax: Double): Float =
    if (groupMax <= 0.0) 0f else (v / groupMax).toFloat().coerceIn(0f, 1f)

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp),
        )
    }
}

@Composable
private fun Footnote(text: String) {
    Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ------------------------------------------------------------------ 格式化

private fun fmt2(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun fmtPct(value: Double): String = String.format(Locale.US, "%.1f%%", value * 100)

private fun pprOf(totalScore: Int, dartsThrown: Int): Double =
    if (dartsThrown == 0) 0.0 else totalScore.toDouble() / (dartsThrown / 3.0)

/**
 * C10 直方图的目标位标签。
 *
 * 数字分区照号位显示；Bull 沿用一期写法「Bull」而**不是** `display`（"25"）——
 * 与对局内的板面标签同理：`display` 是数据层默认展示名，统计页的用词是一期既有口径，
 * 2A 不改变用户看得见的文案。类别档（2C）走 `display`。
 */
private fun labelOf(target: CricketTarget): String =
    if (target is CricketTarget.Number && target.value == 25) "Bull" else target.display

private fun formatTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
