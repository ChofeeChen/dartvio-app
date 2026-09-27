package com.dartvio.app.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.data.profile.ProfileStore
import com.dartvio.app.domain.leaderboard.LeaderboardCalculator
import com.dartvio.app.domain.leaderboard.LeaderboardEntry
import com.dartvio.app.domain.leaderboard.LeaderboardPlayer
import com.dartvio.app.domain.leaderboard.LeaderboardSort
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.flow.flowOf
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 本地排行榜（第③期 ③B）。
 *
 * 三条设计取舍：
 * 1. **数据源是原始对局，不是预计算的榜**：与统计页同一个 `observeStatsMatches()`
 *    （**战绩口径**，M5 T10 起排除联机局），口径全部收在 [LeaderboardCalculator] 里现算。
 *    榜单是派生的，落库就会有「榜与明细对不上」的第二套真相。
 * 2. **公示放在列表之外**（列表上方常驻）：未达样本门槛、榜为空时同样必须看到
 *    「统计范围是什么」。这是 PRD A9.10 的硬要求，不能藏在滚动区里。
 * 3. **空态是引导而不是空表**：没有正式赛时不画表头，直接告诉用户怎么上榜。
 */
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as? DartVioApp

    val matchesFlow = remember(app) { app?.matchRepository?.observeStatsMatches() }
    val matches by (matchesFlow ?: flowOf(emptyList<MatchWithPlayers>()))
        .collectAsState(initial = emptyList())

    // 每次进入页面重读一次档案：昵称/头像随时可能被改，缓存副本会过期。
    // profileId 由 ProfileStore 保证「生成一次、此后不变」，读取是幂等的。
    val profile = remember { ProfileStore.ensure(context) }

    var sort by remember { mutableStateOf(LeaderboardSort.PPR) }

    val snapshot = remember(matches, profile, sort) {
        LeaderboardCalculator.compute(
            matches = matches,
            players = listOf(LeaderboardPlayer(profile.profileId, profile.nickname)),
            sort = sort,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    "排行榜",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            navigationIcon = {
                TextButton(onClick = onBack) { Text("返回", color = Primary) }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                navigationIconContentColor = Primary,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )

        RankingNotice()
        SortRow(selected = sort, onSelect = { sort = it })

        if (snapshot.isEmpty) {
            EmptyGuide()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (snapshot.ranked.isNotEmpty()) {
                    itemsIndexed(snapshot.ranked, key = { _, entry -> entry.playerId }) { index, entry ->
                        EntryRow(entry, sort, rank = index + 1)
                    }
                }
                if (snapshot.insufficient.isNotEmpty()) {
                    item(key = "insufficient_header") { InsufficientHeader() }
                    items(snapshot.insufficient, key = { "insufficient_${it.playerId}" }) { entry ->
                        EntryRow(entry, sort, rank = null)
                    }
                }
                // 本版本只有一份本机档案，榜上必然只有一个人。与其让用户对着「第 1 名 / 共 1 人」
                // 猜是不是坏了，不如把原因直接写在榜上。
                if (snapshot.ranked.size + snapshot.insufficient.size == 1) {
                    item(key = "single_player_hint") { SinglePlayerHint() }
                }
            }
        }
    }
}

/** 单人榜的说明：这不是 bug，是本机身份体系当前只有一份档案。 */
@Composable
private fun SinglePlayerHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(SurfaceVariantDark, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "榜上目前只有你一个人",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "排行榜按「本机玩家档案」统计。本版本每台设备只有一份档案，" +
                "同一台设备上的其他席位（含 AI）无法归属到人，因此不会出现在榜上。",
            fontSize = 12.sp,
            color = TextSecondaryDark,
        )
    }
}

/**
 * 统计口径公示（PRD A9.10 硬要求）。
 *
 * 三句话缺一不可，且**必须写死在榜上**而不是放在文档里：
 * 统计范围（谁进榜）、排除范围（谁不进榜）、起始时点（旧数据为什么不参与）。
 * 第三句是升级用户的直接疑问 —— 他们看得见历史对局，却看不见它们出现在榜上。
 */
@Composable
private fun RankingNotice() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .background(SurfaceDark, RoundedCornerShape(14.dp))
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "统计口径",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Primary,
        )
        Text(
            "· 仅统计本机正式赛（多局模式 + 全真人）的 X01 对局",
            fontSize = 12.sp,
            color = TextSecondaryDark,
        )
        Text(
            "· AI 对战与练习模式不参与排名",
            fontSize = 12.sp,
            color = TextSecondaryDark,
        )
        Text(
            "· 排行榜自本版本起统计：升级前的历史对局无法归属到具体玩家，不参与排名",
            fontSize = 12.sp,
            color = TextSecondaryDark,
        )
    }
}

/** 排序维度切换（③B 建议值见 [LeaderboardSort]，待 PRD 确认）。 */
@Composable
private fun SortRow(selected: LeaderboardSort, onSelect: (LeaderboardSort) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "排序",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LeaderboardSort.entries.forEach { option ->
            val isSelected = option == selected
            Text(
                option.label,
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

/** 空态引导：不画表头、不画空行，直接给「怎么上榜」。 */
@Composable
private fun EmptyGuide() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .background(SurfaceVariantDark, RoundedCornerShape(16.dp))
            .border(1.dp, Divider, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "还没有可用于排名的正式赛",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "打一场正式赛就能上榜",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Primary,
        )
        Text(
            "· 在设置页选「多局模式」且全部为真人（不含 AI），即为正式赛",
            fontSize = 12.sp,
            color = TextSecondaryDark,
        )
        Text(
            "· 正式赛累计满 ${LeaderboardCalculator.MIN_RANKED_MATCHES} 场才会进入主榜，" +
                "不足 ${LeaderboardCalculator.MIN_RANKED_MATCHES} 场先记在「数据不足」",
            fontSize = 12.sp,
            color = TextSecondaryDark,
        )
    }
}

/** 样本不足区的小标题：说明为什么这些人没进主榜，而不是静默丢弃。 */
@Composable
private fun InsufficientHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
    ) {
        Text(
            "数据不足",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "正式赛不足 ${LeaderboardCalculator.MIN_RANKED_MATCHES} 场，暂不进主榜",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 一个榜位。[rank] 为 null 表示出现在「数据不足」区，不参与名次编号。 */
@Composable
private fun EntryRow(entry: LeaderboardEntry, sort: LeaderboardSort, rank: Int?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(14.dp))
            .border(1.dp, if (rank == 1) Primary else Divider, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(rank)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "正式赛 ${entry.formalMatches} 场 · ${entry.wins} 胜",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 当前排序维度之外的补充量：切维度时用户才知道「为什么他排前面」。
            Text(
                secondaryLine(entry, sort),
                fontSize = 11.sp,
                color = TextSecondaryDark,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                metricValue(entry, sort),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (rank == 1) Primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                sort.label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RankBadge(rank: Int?) {
    val background = when (rank) {
        1 -> Primary
        2 -> Secondary
        3 -> Accent
        else -> SurfaceVariantDark
    }
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(background, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank?.toString() ?: "—",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (rank != null && rank <= 3) OnPrimary else TextSecondaryDark,
        )
    }
}

// ------------------------------------------------------------------ 展示口径

/** 主数值：当前排序维度对应的那个数。 */
private fun metricValue(entry: LeaderboardEntry, sort: LeaderboardSort): String = when (sort) {
    LeaderboardSort.PPR -> formatPpr(entry.ppr)
    LeaderboardSort.WIN_RATE -> "${(entry.winRate * 100).roundToInt()}%"
    LeaderboardSort.HIGHEST_CHECKOUT -> "${entry.highestCheckout}"
    LeaderboardSort.MATCH_COUNT -> "${entry.formalMatches}"
}

/** 补充数值：把另外三个维度一并列出，避免用户切来切去才能比较。 */
private fun secondaryLine(entry: LeaderboardEntry, sort: LeaderboardSort): String {
    val parts = mutableListOf<String>()
    if (sort != LeaderboardSort.PPR) parts += "PPR ${formatPpr(entry.ppr)}"
    if (sort != LeaderboardSort.WIN_RATE) parts += "胜率 ${(entry.winRate * 100).roundToInt()}%"
    if (sort != LeaderboardSort.HIGHEST_CHECKOUT) parts += "最高收镖 ${entry.highestCheckout}"
    return parts.joinToString(" · ")
}

/**
 * PPR 保留两位小数。
 *
 * 显式指定 [Locale.US]：部分地区的小数点是逗号，`String.format` 用默认地区会输出
 * "12,34"，在一列数字里既难读又会被误认成千分位。
 */
private fun formatPpr(value: Double): String = String.format(Locale.US, "%.2f", value)
