package com.dartvio.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dartvio.app.data.profile.ProfileStore
import com.dartvio.app.ui.components.PrimaryActionButton
import com.dartvio.app.ui.theme.BackgroundDark
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.TextPrimaryDark

/**
 * Cricket「游戏设置」—— 从游戏选择页跳进来的**独立页面**。
 *
 * 为什么从底部弹层改成独立页（2026-09-12 体验裁决）：
 * 弹层展开后要上滑才能看到其余设置，**滑上来的内容会盖住原本那一屏**，
 * 玩家分不清「我是在改设置，还是已经离开了这一页」，返回键/下滑关闭的语义也不直观。
 * 独立页有明确的标题、明确的「保存设置」出口，改完返回即可看到入口卡上的摘要已更新。
 *
 * **本页职责 = 决定「这一局怎么算」**：AI 强度 / 赛制 / 玩法规则（[CricketSettingsForm]）。
 * 「和谁打」（对战模式 + 对手）在**上一页游戏选择页** —— 与 X01 完全同构
 * （X01 一级页 = 目标分 + 对战，二级页 = 规则 + AI 对手）。
 * 分界依据是**参数的变更频率**：每次开局都可能改的放一级页，设一次就不动的放本页。
 *
 * 本页**只负责编辑**：状态实例由 [SetupSession] 持有，因此返回后改动仍在；
 * 落盘只发生在「保存设置」（以及游戏选择页上点「开始比赛」）两处，
 * 且按 [SetupDefaultsStore] 的两个作用域分别写。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CricketSettingsScreen(
    state: CricketSetupState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val profile = remember(context) { ProfileStore.ensure(context) }
    LaunchedEffect(profile) { state.applySelf(profile) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "游戏设置",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimaryDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = BackgroundDark,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                PrimaryActionButton(
                    text = "保存设置",
                    onClick = {
                        val prefs = SetupDefaultsStore.prefs(context)
                        // 本页会改 AI 强度（比赛层）与规则（玩法层），两层分别落盘。
                        SetupDefaultsStore.writeMatch(prefs, state.toMatchDefaults())
                        SetupDefaultsStore.writeRules(
                            prefs,
                            state.cricketMode,
                            state.toRuleDefaults()
                        )
                        onBack()
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 设置分两个作用域，页面必须把这件事说清，否则玩家会按错的预期去理解：
            // 哪些是「这一档的」、哪些是「所有玩法共用的」，只能靠这句话区分。
            Text(
                text = "当前玩法：${state.cricketMode.title}",
                color = TextPrimaryDark,
                fontWeight = FontWeight.Bold
            )
            HintText("赛制与玩法规则按玩法分别记住；对手与 AI 强度为全部玩法共用。")

            CricketSettingsForm(state)
        }
    }
}
