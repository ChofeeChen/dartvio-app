package com.dartvio.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页。PRD M11：提供比赛类型入口。
 *
 * 练习模式与统计入口已上移至底部导航栏，首页只保留最高频的「快速开局」
 * 与「最近对局」摘要，避免纵向堆叠。
 */
@Composable
fun HomeScreen(
    onSelectGame: (MatchType) -> Unit,
    onOpenPractice: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Spacer(Modifier.height(40.dp))

        Text(
            "DartVio",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Primary
        )
        Text(
            "智能飞镖计分",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        /*
         * Beta 试用包的那张「部分功能为演示 / 未实现」说明卡已移到「我的」页
         * （2026-09-27 反馈）：首页的用途是**开一局**，版本声明属于身份与设置那一层，
         * 摆在开局的路上只会让第一次打开 App 的人先看到一句免责声明。
         */

        Spacer(Modifier.height(36.dp))

        Text(
            "快速开局",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GameTypeCardCompact(
                title = "X01",
                desc = "301 / 501 / 701 / 901 / 1101",
                accent = Primary,
                modifier = Modifier.weight(1f).height(118.dp),
                onClick = { onSelectGame(MatchType.X01) }
            )
            GameTypeCardCompact(
                title = "Cricket",
                desc = "标记与得分策略对战",
                accent = Secondary,
                modifier = Modifier.weight(1f).height(118.dp),
                onClick = { onSelectGame(MatchType.CRICKET) }
            )
        }

        Spacer(Modifier.height(16.dp))

        TrainingCenterCard(onClick = onOpenPractice)

        // 「最近对局」整块移除：它跳的就是底部「数据」tab，
        // 同一个目的地在首页再放一个入口，只会让用户怀疑两者看到的东西不一样
        // （2026-09-26 反馈）。首页现在只剩「开一局」与「去训练」两件事。

        // 底部版本行整体移除：版本号统一留在「我的」页底部，首页不再重复。
        Spacer(Modifier.height(16.dp))
    }
}

/** 训练中心入口：练习模式已从底部 Tab 移出，统一收敛到首页这一张卡。 */
@Composable
private fun TrainingCenterCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.5.dp, Accent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "训练中心",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Count Up · 随机结镖 · 99 Darts · AI 对战",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("›", fontSize = 22.sp, color = Accent)
    }
}

/** 最近一场对局摘要。 */
@Composable
private fun RecentMatchCard(match: MatchWithPlayers, onClick: () -> Unit) {
    val ordered = match.players.sortedBy { it.orderIndex }
    val first = ordered.getOrNull(0)
    val second = ordered.getOrNull(1)
    val dateText = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        .format(Date(match.match.endedAt))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.5.dp, Accent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                match.match.matchTypeLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                dateText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (second != null) {
                "${first?.name ?: "玩家"}  ${first?.legsWon ?: 0} : " +
                    "${second.legsWon}  ${second.name}"
            } else {
                first?.name ?: "对局记录"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            buildString {
                append("${match.match.playerCount} 人")
                append(" · ${match.match.legCount} 局")
                append(" · ${match.match.legsToWin} 局制")
                if (match.match.containsAi) append(" · 含 AI")
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyRecentCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, Divider, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "还没有对局记录，打一局试试",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 紧凑型卡片：两个玩法并排一行，高度一致，节省首页纵向空间。 */
@Composable
private fun GameTypeCardCompact(
    title: String,
    desc: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(
                if (enabled) MaterialTheme.colorScheme.surface else SurfaceVariantDark,
                RoundedCornerShape(16.dp)
            )
            .border(1.5.dp, accent, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(28.dp, 6.dp)
                .background(accent, RoundedCornerShape(3.dp))
        )
        Column {
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else TextDisabledDark
            )
            Text(
                desc,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
