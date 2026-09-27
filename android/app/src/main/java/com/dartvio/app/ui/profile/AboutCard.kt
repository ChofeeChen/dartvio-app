package com.dartvio.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.BuildConfig
import com.dartvio.app.data.beta.BetaFeedbackAck
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * 「关于 DartVio」入口卡 + 版本详情弹窗（2026-09-27 反馈：版本信息放哪里）。
 *
 * ## 放这里，不放「我的」页底部
 *
 * 主流 App（微信、抖音、小红书）的口径一致：**版本号住在「设置 → 关于」里**，
 * 「我的」页最多留一行小字。原因是这两个地方的用途不同 ——
 * 「我的」页回答「我是谁」，而「这是哪个包、这一版改了什么」是一次**排查**，
 * 用户只在要报问题、要确认更新时才去找它，那正是设置页该承载的事。
 *
 * 于是：
 * - 「我的」页底部保留一行「v0.1.17 · Build 17」（报问题时随手可抄）；
 * - 完整信息（构建类型 + 本版更新说明）收在这张卡片里，由设置页承载。
 *
 * ## 本版更新说明从哪来
 *
 * 取自 [BetaFeedbackAck.items] 里 `shippedIn` 等于当前版本的那些条目 ——
 * 它就是「我的反馈」页给用户核对的那份清单，**同一份数据两个用处**，
 * 不另开一处维护，也就不会出现「关于页说改了 A、反馈页说改了 B」。
 */
@Composable
fun AboutVersionCard() {
    var showDetail by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(16.dp))
            .border(1.dp, Divider, RoundedCornerShape(16.dp))
            .clickable { showDetail = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 与主色竖条同款：与「我的」页的入口卡逐项一致，这张卡才能看起来是同一列里的。
        Box(
            modifier = Modifier
                .size(6.dp, 40.dp)
                .background(Accent, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "关于 DartVio",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "v${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE} · ${buildTypeLabel()}",
                fontSize = 12.sp,
                color = TextSecondaryDark
            )
        }
        Text("详情", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Primary)
    }

    if (showDetail) {
        VersionDetailDialog(onDismiss = { showDetail = false })
    }
}

/** 构建类型：**用户能拿它判断自己手上的是哪一种包**。 */
private fun buildTypeLabel(): String = when {
    BuildConfig.BETA_DEMO -> "Beta 试用版"
    BuildConfig.FULL_ENTRIES -> "开发包"
    else -> "正式版"
}

@Composable
private fun VersionDetailDialog(onDismiss: () -> Unit) {
    val changes = BetaFeedbackAck.items.filter { it.shippedIn == "v${BuildConfig.VERSION_NAME}" }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text("DartVio v${BuildConfig.VERSION_NAME}") },
        text = {
            Column {
                Text(
                    "Build ${BuildConfig.VERSION_CODE} · ${buildTypeLabel()}",
                    fontSize = 13.sp,
                    color = TextSecondaryDark
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "本版更新",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                if (changes.isEmpty()) {
                    Text(
                        "这一版没有需要回报的改动。",
                        fontSize = 13.sp,
                        color = TextSecondaryDark
                    )
                } else {
                    changes.forEach { item ->
                        Text(
                            "· ${item.what}",
                            fontSize = 13.sp,
                            color = TextSecondaryDark,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "全部数据保存在本机 · 卸载 App 即删除",
                    fontSize = 11.sp,
                    color = TextDisabledDark
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = Primary) }
        }
    )
}
