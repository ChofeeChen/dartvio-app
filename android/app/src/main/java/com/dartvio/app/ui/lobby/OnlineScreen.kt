package com.dartvio.app.ui.lobby

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.BuildConfig
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.TextDisabledDark

/**
 * 在线状态页（路由 `lobby/online`，由 WiFi 图标进入）。
 *
 * ## 这一页现在只剩一件事：把连接情况说清楚
 *
 * 建房与找房间都搬进了比赛大厅：建房是大厅里的「创建比赛」，找房间是大厅里的房间卡。
 * 房间号那一整套（6 位号输入、复制、凭号加入）按 PRD D5 移除 —— 房间一经创建就在大厅
 * 公开列出，不再需要一个要念给对方听的凭证。
 *
 * 因此这里不再有「进入比赛大厅」的入口（那是同一个大厅的第二个入口，而用户从 WiFi
 * 图标进来想看的是**连接情况**，2026-09-26 真机反馈），也不再有昵称 / 头像
 * （那是「我的」页里的全局档案）。
 *
 * ## 为什么诊断入口还在顶栏
 *
 * 它只有在出问题时才被需要，而那时用户已经在「点不动」的状态里 —— 藏在二级页里等于没有。
 */
@Composable
fun OnlineScreen(
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        OnlineTopBar(onBack, onOpenDiagnostics)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            OnlineSetup()

            Spacer(Modifier.height(24.dp))
            SectionLabel("关于联机")
            Hint(
                "联机只走云端：两台手机各自连互联网即可对战，不需要在同一个 WiFi 下，也不需要账号。\n" +
                    "房间数据以云端事件为准，中途断线重连后会接着上一手继续。"
            )
            Spacer(Modifier.height(28.dp))
        }

        // 版本行钉在页底、**不随内容滚动**。两端一眼可对版本，而版本不一致在联机里
        // 表现为各种说不清的怪现象，不会自己报一句「版本不一致」。
        Text(
            "v${BuildConfig.VERSION_NAME}",
            fontSize = 11.sp,
            color = TextDisabledDark,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        )
    }
}

@Composable
private fun OnlineTopBar(onBack: () -> Unit, onOpenDiagnostics: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        // 页名与页内唯一的主信息保持一致：这一页回答的是「联机链路通不通」，
        // 叫「联机对战」会让人以为这里是选角色 / 开局的地方（2026-09-26 真机反馈）。
        Text(
            "在线状态",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        Text(
            "诊断",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Primary,
            modifier = Modifier
                .clickable(onClick = onOpenDiagnostics)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/**
 * 复制文本到剪贴板并给一个可见反馈。
 *
 * 复制没有视觉变化，不给反馈用户会怀疑「到底复制上没有」，于是反复点 —— 反馈比功能本身更重要。
 */
internal fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("文本", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

@Composable
// 刻意保持 private：`OnlineLinkSetup` 等页面各自有一份同名实现，
// 同包内若这里改成 internal 就会与它们互相冲突（Kotlin 的顶层声明不按文件隔离可见性）。
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
internal fun Hint(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
