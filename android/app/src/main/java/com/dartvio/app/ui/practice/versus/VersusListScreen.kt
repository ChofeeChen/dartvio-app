package com.dartvio.app.ui.practice.versus

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.data.VersusRepository
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.domain.versus.VersusModeInfo
import com.dartvio.app.domain.versus.VersusModes
import com.dartvio.app.ui.beta.UnavailableFeatureDialog
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.TextDisabledDark

/**
 * 对抗练习 · 模式列表页（C5 第一级入口：先选玩法，再进配置）。
 *
 * **这一页不认识任何具体玩法**：卡片内容全部来自 [VersusModes.ALL]，
 * 因此 V2 给「上海争霸 / 减半挑战」接上引擎时，只需要把它们 `available` 改成 `true`，
 * 这一页一行都不用改 —— 「第 7 个模式」的扩展点就落在注册表上。
 *
 * `available = false` 的模式**仍然列出**（灰卡 + 「即将上线」）而不是隐藏：
 * 六个玩法是产品对外承诺的一部分，藏起来会让人以为只有三个。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersusListScreen(
    onBack: () -> Unit,
    onPickMode: (String) -> Unit,
) {
    val context = LocalContext.current
    val finishedCount by produceState(initialValue = 0, context) {
        value = VersusRepository(DartVioDatabase.get(context).versusDao()).countFinished()
    }
    var rulesOf by remember { mutableStateOf<VersusModeInfo?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "对抗练习",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回", color = Primary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            /*
             * 顶部那句「两人轮流投镖，先达成目标者胜」删掉了（2026-09-27 反馈）。
             *
             * 它说的是**六个模式的公约数**，而对第一次进来的人来说，公约数等于什么都没说：
             * 每张模式卡下面都写着自己那句 desc，读卡片就够了。这类「总起一句」的文案
             * 只在页面只有一种东西时才有用，这里恰好相反。
             */
            if (finishedCount > 0) {
                Text(
                    "本机已完成 $finishedCount 场",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Accent,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            /*
             * 分组标题（热身 / 专项 / 压力）去掉了（2026-09-27 反馈），改成一整列。
             *
             * 这三个词是**我们内部的分档**，不是玩家的语言：进这一页的人要的是
             * 「今天练什么」，而「专项」既没说清练哪块，也没说清和「热身」差在哪 ——
             * 唯一的实际效果是让人先花几秒猜词。分组本身保留在数据里
             * （[VersusModes.ALL] 的顺序仍按它编排），只是不再往屏幕上写。
             */
            VersusModes.ALL.forEach { info ->
                VersusModeCard(
                    info = info,
                    onOpen = { onPickMode(info.modeKey) },
                    onRules = { rulesOf = info },
                )
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    rulesOf?.let { info ->
        VersusRulesDialog(info = info, onDismiss = { rulesOf = null })
    }
}

@Composable
private fun VersusModeCard(
    info: VersusModeInfo,
    onOpen: () -> Unit,
    onRules: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    // 未开放的模式**也要能点开**：点下去毫无反应，别人只会以为这是 bug。
    // 明确回一句「规则已写好、本期未开放」，他给的反馈才是有用的反馈。
    var showLocked by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.5.dp, if (info.available) Primary else TextDisabledDark, shape)
            .clickable { if (info.available) onOpen() else showLocked = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp, 48.dp)
                .background(
                    if (info.available) Primary else TextDisabledDark,
                    RoundedCornerShape(3.dp),
                )
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    info.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (info.available) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        TextDisabledDark
                    },
                )
                if (!info.available) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "即将上线",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDisabledDark,
                        modifier = Modifier
                            .background(
                                TextDisabledDark.copy(alpha = 0.14f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                info.desc,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRules, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("规则", fontSize = 13.sp, color = Accent, fontWeight = FontWeight.Bold)
        }
    }

    if (showLocked) {
        UnavailableFeatureDialog(
            title = info.title,
            note = "这个模式的规则引擎已经写好，本期尚未开放。\n点右侧「规则」可以查看完整玩法说明。",
            onDismiss = { showLocked = false },
        )
    }
}

/**
 * 「规则说明」弹窗。
 *
 * 条目直接取 `VersusModeInfo.rulesSummary`（**在注册表里维护**，不在页面里拼），
 * 所以规则文案不会在页面这一层与引擎判定分叉。
 */
@Composable
private fun VersusRulesDialog(info: VersusModeInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(info.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                info.rulesSummary.forEach { line ->
                    Row {
                        Text("· ", color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(line, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了", color = Primary) }
        },
    )
}
