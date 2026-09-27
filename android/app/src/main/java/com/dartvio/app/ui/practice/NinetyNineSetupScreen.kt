package com.dartvio.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.ui.game.GameBlockGap
import com.dartvio.app.ui.game.GameTopBar
import com.dartvio.app.ui.theme.BackgroundDark
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Secondary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * 99 Darts 练习设置页：进入练习前选择一个分区（1-20）。
 */
@Composable
fun NinetyNineSetupScreen(
    onBack: () -> Unit,
    onStart: (Int) -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(20) }
    val best = remember(selected) { NinetyNineStatsStore.best(context, selected) }
    val sessions = remember(selected) { NinetyNineStatsStore.sessions(context, selected) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        GameTopBar(
            title = "99 Darts 练习",
            subtitle = "选择练习分区",
            onExit = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(GameBlockGap)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(GameBlockGap)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(14.dp)
            ) {
                Text(
                    "规则",
                    color = Secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "选择 1-20 中一个分区，连续投 33 轮 × 3 镖，共 99 镖。",
                    color = TextSecondaryDark,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "命中得分：T=3 分 · D=2 分 · S=1 分 · 未命中=0 分",
                    color = TextSecondaryDark,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(GameBlockGap)
            ) {
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(GameBlockGap)
                    ) {
                        for (col in 0 until 5) {
                            val number = row * 5 + col + 1
                            SectorButton(
                                number = number,
                                selected = selected == number,
                                onClick = { selected = number }
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariantDark)
                    .padding(14.dp)
            ) {
                Text(
                    "当前选择：S$selected",
                    color = TextPrimaryDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "历史最佳：$best 分 · 已练习 $sessions 次",
                    color = TextSecondaryDark,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { onStart(selected) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("开始练习", color = OnPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RowScope.SectorButton(
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Primary else SurfaceVariantDark)
            .border(
                1.dp,
                if (selected) Primary else Divider,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$number",
            color = if (selected) OnPrimary else TextPrimaryDark,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
