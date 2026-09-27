package com.dartvio.app.ui.achievement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.domain.achievement.AchievementDataSource
import com.dartvio.app.domain.achievement.AchievementProgress
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * 成就解锁卡片（决策④ 主路径）。
 *
 * 出现在三处：对局结算页、练习结果页、练习结果弹窗。
 * 设计取舍：
 * - **不是全屏弹窗** —— 决策④明确禁止遮挡式打断，飞镖的节奏感优先于一个成就；
 * - [compact] 模式用于弹窗场景：只占标题 + 一行成就名，
 *   因为随机结镖 / 99 Darts 的结果弹窗本身已有较多统计行，塞入逐条卡片会溢出；
 * - 未解锁时**不渲染任何内容**（返回空），调用方可无条件放置。
 */
@Composable
fun AchievementUnlockCard(
    unlocked: List<AchievementProgress>,
    modifier: Modifier = Modifier,
    title: String = "解锁成就",
    compact: Boolean = false,
    accent: Color = Primary,
) {
    if (unlocked.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "\uD83C\uDFC5", fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${unlocked.size} 项",
                color = TextSecondaryDark,
                fontSize = 11.sp,
            )
        }

        if (compact) {
            Text(
                text = unlocked.joinToString("、") { it.definition.title },
                color = TextPrimaryDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            return@Column
        }

        unlocked.forEach { progress ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = progress.definition.title,
                        color = TextPrimaryDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = progress.definition.description,
                        color = TextSecondaryDark,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                AchievementSourceBadge(progress.definition.dataSource)
            }
        }
    }
}

/**
 * 数据源徽标（决策②的合规要求：每条成就必须标注判定数据的可信度）。
 *
 * 徽标只有三档，且沿用代码中已有的枚举标签，不在 UI 层另起文案，
 * 避免与成就页出现两套说法。
 */
@Composable
fun AchievementSourceBadge(
    source: AchievementDataSource,
    modifier: Modifier = Modifier,
) {
    val color = when (source) {
        AchievementDataSource.FORMAL -> Primary
        AchievementDataSource.CASUAL -> Secondary
        AchievementDataSource.PRACTICE -> TextSecondaryDark
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = source.label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
