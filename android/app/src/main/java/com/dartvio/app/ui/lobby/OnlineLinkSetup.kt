package com.dartvio.app.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.data.room.OnlineRoomLink
import com.dartvio.app.net.online.OnlineConfig
import com.dartvio.app.net.online.StreamStatus
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Error
import com.dartvio.app.ui.theme.Success
import com.dartvio.app.ui.theme.TextSecondaryDark
import com.dartvio.app.ui.theme.Warning

/**
 * 在线状态页的内容（WiFi 图标进入；大厅页与房间等候页共用）。
 *
 * ## 这一页只回答一个问题：链路通不通
 *
 * 建房与找房间都在比赛大厅里完成，房间一经创建就在大厅公开列出 ——
 * 这里再放一个「进入比赛大厅」的按钮，是同一个大厅的第二个入口，
 * 而用户从 WiFi 图标进来想看的是**连接情况**（2026-09-26 真机反馈）。
 * 昵称 / 头像同理：那是「我在大厅里长什么样」，属于「我的」页里的全局档案，
 * 不属于一个信号页。
 */
@Composable
internal fun OnlineSetup() {
    val context = LocalContext.current
    val status by OnlineRoomLink.status(context).collectAsState()

    if (!OnlineConfig.isConfigured) {
        // 局域网联机已移除（D1），因此「没配后端」就是「联机不可用」，没有退路可说。
        // 早先那句「同一 WiFi 下的局域网联机不受影响」会把人引向一个已经不存在的入口。
        Hint("这一版没有配置在线后端，联机对战暂不可用；单机模式与本地模拟不受影响。")
        Spacer(Modifier.height(12.dp))
    }

    StatusRow(status)
}

/**
 * 连接状态。
 *
 * 刻意只描述**本机**的连接（连上了 / 重连中），不说「对手在不在」：
 * 对手在不在是事件流的事，混在一起会让用户以为「重连中」等于「对方掉线了」。
 */
@Composable
private fun StatusRow(status: StreamStatus) {
    val (label, color) = when (status) {
        StreamStatus.CONNECTING -> "连接中…" to Accent
        StreamStatus.LIVE -> "已连接" to Success
        StreamStatus.RECONNECTING -> "掉线了，正在重连" to Warning
        // 实时推送没连上但轮询在扛：必须直说「能玩，只是慢」——
        // 只写「连接中」的话，用户会一直等一个不会到来的「已连接」，然后判定联机不可用。
        StreamStatus.POLLING_ONLY -> "可用 · 同步较慢" to Warning
        StreamStatus.NOT_CONFIGURED -> "未配置在线后端" to Error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("在线状态", fontSize = 13.sp, color = TextSecondaryDark)
        Spacer(Modifier.weight(1f))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
