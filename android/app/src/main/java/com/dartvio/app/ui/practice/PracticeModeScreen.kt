package com.dartvio.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary

/**
 * 训练中心 · **第一级入口**（C1：单级列表 → 两级入口）。
 *
 * 这一页现在**只有两张卡**：单人训练 / 双人对抗训练。
 *
 * 为什么把原来那 6 张卡整体下沉一层：它们有一个共同点 —— **都不需要选对手**。
 * 而对抗练习的第一件事就是「和谁打」，两者不是一类。把七张卡平铺在一级，
 * 会让人以为「对抗练习」也只是又一个单人项目，进而错过它；
 * 反过来，把对抗塞进一级列表里找，又会让只想练单人的人多看一层。
 *
 * 两张卡分别进 `PracticeSoloScreen`（原 6 项，一行逻辑未改）与 `VersusListScreen`（六模式）。
 */
@Composable
fun PracticeModeScreen(
    onBack: (() -> Unit)? = null,
    onSolo: () -> Unit = {},
    onVersus: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    "训练中心",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            navigationIcon = {
                // 作为一级 Tab 时不显示返回按钮
                if (onBack != null) {
                    TextButton(onClick = onBack) { Text("返回", color = Primary) }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                navigationIconContentColor = Primary,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                "想怎么练？",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            EntryCard(
                title = "单人训练",
                desc = "Count Up / 随机结镖 / 99 Darts / 精准工坊 / Cricket MPR / AI 对战",
                accent = Secondary,
                onClick = onSolo,
            )
            Spacer(Modifier.height(12.dp))
            // 对抗练习走「特色」同款渐变：它是本次新增的差异化入口，
            // 但**刻意不给它加「特色」角标** —— 这里的角标要给「精准工坊」（第二级里的差异化功能）留着，
            // 一级入口两张卡都该是平等的选项，标了角标反而像在替用户做决定。
            EntryCard(
                title = "双人对抗训练",
                desc = "Bull 之争 / 倍区竞赛 / 环游三镖……两人轮流投镖，先达成目标者胜",
                accent = Accent,
                featured = true,
                onClick = onVersus,
            )
        }
    }
}

@Composable
private fun EntryCard(
    title: String,
    desc: String,
    accent: Color,
    featured: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val brandStroke = Brush.linearGradient(listOf(Primary, Accent))
    val brandFill = Brush.linearGradient(
        listOf(Primary.copy(alpha = 0.16f), Accent.copy(alpha = 0.16f))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (featured) Modifier.background(brandFill, shape)
                else Modifier.background(MaterialTheme.colorScheme.surface, shape)
            )
            .then(
                if (featured) Modifier.border(2.dp, brandStroke, shape)
                else Modifier.border(1.5.dp, accent, shape)
            )
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp, 56.dp)
                .background(
                    if (featured) Brush.verticalGradient(listOf(Primary, Accent)) else SolidColor(accent),
                    RoundedCornerShape(3.dp)
                )
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                desc,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
