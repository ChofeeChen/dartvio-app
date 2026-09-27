package com.dartvio.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.domain.model.AiAvatar
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.HumanAvatar
import com.dartvio.app.domain.model.Player
import com.dartvio.app.ui.components.Multiplier
import com.dartvio.app.ui.theme.Bust
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.GameShot
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/** 对局页统一间距：页边距、区块间隙、键盘按键间隙全部使用此值，保证视觉均匀。 */
val GameBlockGap = 8.dp

/**
 * X01 玩家卡片高度。
 *
 * 由 162dp 增至 198dp：状态提示条改为「仅在有回合事件时出现」后，常规状态不再占据高度，
 * 其释放的空间（提示条约 28dp + 一个区块间隙 8dp）全部回填到卡片上，
 * 使卡片上下间隙、键盘区域间隙与页边距保持一致。
 */
val PlayerCardHeight = 198.dp

// =====================================================================================
// 通用辅助函数
// =====================================================================================

/**
 * 追加一位数字到输入缓冲（最多 2 位；已满则以新数字重新开始）。
 * 例："" +2 → "2"；"2" +0 → "20"；"20" +3 → "3"。
 */
fun appendDigit(buffer: String, digit: Int): String {
    if (buffer.length >= 2) return "$digit"
    return buffer + digit
}

/** 合法飞镖分值：1..20 或 25（牛眼）。 */
fun isValidDartValue(v: Int): Boolean = (v in 1..20) || v == 25

/** 分值 + 倍率 → Dart。25 视为牛眼（D 为内牛 50，其余为外牛 25）。 */
fun dartOf(value: Int, multiplier: Multiplier): Dart = when {
    value == 25 -> if (multiplier == Multiplier.DOUBLE) Dart.INNER_BULL else Dart.OUTER_BULL
    else -> Dart(value, multiplier.factor)
}

// =====================================================================================
// 通用组件
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameTopBar(
    title: String,
    subtitle: String,
    onExit: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = subtitle,
                    color = TextSecondaryDark,
                    fontSize = 11.sp
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "退出",
                    tint = TextPrimaryDark
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
    )
}

/**
 * 卡片上方的居中小三角：指示当前进行中的玩家。
 * [modifier] 供调用方用 offset 把它画进区块间隙里，从而不占用布局高度（保证卡片上下间隙一致）。
 */
@Composable
fun ActiveTriangle(visible: Boolean, color: Color = Primary, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(7.dp).width(14.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (visible) {
            Canvas(modifier = Modifier.size(14.dp, 7.dp)) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, color = color)
            }
        }
    }
}

/** 三镖显示区：固定一行，3 个槽位 + 本回合累计分。 */
@Composable
fun TurnDartsRow(
    darts: List<Dart>,
    modifier: Modifier = Modifier,
    accent: Color = Secondary,
    trailingLabel: String = "本回合",
    trailingValue: Int = darts.sumOf { it.score }
,
    slotScoreLabel: (Dart) -> String = { "${it.score} 分" }
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GameBlockGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val dart = darts.getOrNull(i)
            DartSlot(
                dart = dart,
                accent = accent,
                scoreLabel = dart?.let(slotScoreLabel),
                modifier = Modifier.weight(1f)
            )
        }
        // 文字与数字位置互换（数字在上、标签在下），并把数字字号加大 30%（18 → 23.4 sp）：
        // 这一格是「本回合已经拿了多少分」，扫一眼要的就是那个数，标签只是说明。
        Column(
            modifier = Modifier.width(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$trailingValue",
                color = if (trailingValue > 0) accent else TextSecondaryDark,
                fontSize = 23.4.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = trailingLabel, color = TextSecondaryDark, fontSize = 9.sp)
        }
    }
}

@Composable
fun DartSlot(
    dart: Dart?,
    modifier: Modifier = Modifier,
    accent: Color = Secondary,
    scoreLabel: String? = null
) {
    val filled = dart != null
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) SurfaceVariantDark else SurfaceDark)
            .border(
                1.dp,
                if (filled) accent.copy(alpha = 0.6f) else SurfaceVariantDark,
                RoundedCornerShape(10.dp)
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = dart?.label() ?: "—",
            color = if (filled) TextPrimaryDark else TextSecondaryDark.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = if (filled) scoreLabel ?: "${dart!!.score} 分" else "待投",
            color = if (filled) accent else TextSecondaryDark.copy(alpha = 0.5f),
            fontSize = 10.sp
        )
    }
}

/**
 * 状态提示条：仅在出现**回合事件**时显示（爆分 / 本回合未得分 / 本局结束）。
 *
 * 常规的“轮到 xx 投掷”提示已移除——当前玩家由卡片上方的三角与高亮边框直接指示，
 * 无事件时本组件不产生任何布局高度，释放的纵向空间回填到玩家卡片上（见 [PlayerCardHeight]）。
 */
@Composable
fun StatusStrip(message: String?) {
    val (text, color) = when (message) {
        "BUST" -> "爆分 · 本回合得分作废" to Bust
        "NO SCORE" -> "本回合未得分" to TextSecondaryDark
        "GAME SHOT", "WINNER" -> "本局结束" to GameShot
        else -> return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/** 玩家卡片上"上一回合得分"的删除线展示。 */
@Composable
fun LastScoreText(score: Int?, fontSize: androidx.compose.ui.unit.TextUnit) {
    if (score == null || score <= 0) return
    Spacer(Modifier.width(5.dp))
    Text(
        text = "$score",
        color = TextDisabledDark,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        textDecoration = TextDecoration.LineThrough,
        maxLines = 1
    )
}

/**
 * 玩家卡片头上的头像 emoji。
 *
 * AI 用机器人头像（emoji 是它的**身份**，强度另由设置页那张「AI 难度」卡决定），真人用真人头像 ——
 * 与设置页里选的是同一套 key，所以「设置页挑的头像」和「对局页看到的头像」必然一致。
 */
fun playerAvatarEmoji(player: Player): String =
    if (player.isAi) {
        AiAvatar.fromKey(player.avatar).emoji
    } else {
        HumanAvatar.fromKey(player.avatar).emoji
    }

/**
 * 「本人」与「对手」之间的短竖条。
 *
 * 卡片本身长得一模一样（同样的大小、同样的配色），没有这根竖条时，
 * 「哪一张是我」只能靠读名字去认。**高度由调用方给**：
 * X01 卡片行是固定高（[PlayerCardHeight]），Cricket 卡片行是撑满剩余空间，
 * 两者的「卡片高度」来源不同，写死会有一边不合适。
 */
@Composable
fun SeatDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(Divider)
    )
}

