package com.dartvio.app.ui.lobby

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.net.online.OnlineDiagnostics
import com.dartvio.app.net.online.OnlineDiagnostics.Line
import com.dartvio.app.ui.theme.Divider
import com.dartvio.app.ui.theme.Error
import com.dartvio.app.ui.theme.OnPrimary
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.Success
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.launch

/**
 * 联机诊断页（临时保留，用于真机排查）。
 *
 * 与旧的局域网诊断页同名同路由，但内容完全不同：那时诊断的是「手机之间能不能连上」，
 * 现在诊断的是「这台手机到云端这一路上，哪一环断了」。
 *
 * 结果同时写进 logcat（tag `DartVioDiag`）：屏幕上的这份给用户复制，日志里的那份给排查用 ——
 * 真机上没法看变量，只能看日志。
 */
@Composable
fun OnlineDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<Line>() }
    var running by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
            Text(
                "联机诊断",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            DiagHint("逐层检查本机到云端的链路：DNS → TCP → HTTPS → 建房间 → 事件读写 → 大厅快照 → Realtime。")
            Spacer(Modifier.height(12.dp))
            DiagPrimaryButton(
                label = if (running) "检查中…" else "开始检查",
                enabled = !running,
                onClick = {
                    lines.clear()
                    running = true
                    scope.launch {
                        try {
                            OnlineDiagnostics.run(context) { line -> lines.add(line) }
                        } finally {
                            running = false
                        }
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
            if (lines.isNotEmpty()) {
                DiagOutlineButton(
                    label = "复制全部结果",
                    accent = Primary,
                    onClick = { copyToClipboard(context, lines.joinToString("\n") { it.pretty() }) }
                )
            }
            Spacer(Modifier.height(16.dp))

            lines.forEach { line ->
                Text(
                    line.pretty(),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    color = when (line.ok) {
                        null -> TextSecondaryDark
                        true -> Success
                        false -> Error
                    }
                )
                Spacer(Modifier.height(4.dp))
            }
            if (lines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark)
                        .padding(14.dp)
                ) {
                    Text(
                        "还没有结果。点「开始检查」，大约 15 秒。",
                        fontSize = 12.sp,
                        color = TextDisabledDark
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Text(
            "临时诊断功能 · 验收后可删",
            fontSize = 11.sp,
            color = TextDisabledDark,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun DiagHint(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DiagPrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Primary else SurfaceDark)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) OnPrimary else TextDisabledDark
        )
    }
}

@Composable
private fun DiagOutlineButton(
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Divider, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}
