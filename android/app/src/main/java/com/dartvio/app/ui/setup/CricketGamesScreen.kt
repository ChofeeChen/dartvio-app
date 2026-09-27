package com.dartvio.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dartvio.app.data.profile.ProfileStore
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.Player
import com.dartvio.app.ui.components.PrimaryActionButton
import com.dartvio.app.ui.components.SettingGroupCard
import com.dartvio.app.ui.components.SettingStack
import com.dartvio.app.ui.components.SettingsEntryCard
import com.dartvio.app.ui.components.VariantCard
import com.dartvio.app.ui.theme.SurfaceDark

/**
 * Cricket 二级「游戏选择页」。
 *
 * 首页三个主入口（X01 / CRICKET / 训练中心）不变 —— 本页是 CRICKET 之下的第二级：
 * **玩法选择在第一屏**（standard / 无计分 / 生死局 / Tactics / 随机目标），
 * 其余设置收进独立的「游戏设置」页。
 *
 * 为什么要把「玩法」提成独立一级而不是像以前那样塞在设置页第一个 Section：
 * 玩法是**这一局的全部意义所在**（决定目标集、得分归属、是否有 Overkill），
 * 而另外六七个设置段绝大多数人每次都不改 —— 两者在同一页平铺，
 * 结果是「每次都要滑过一堆不看的开关才能点开始」。
 *
 * **本页职责 = 决定「这一局是什么」**：
 *  1. 玩哪种 Cricket（[CricketMode]）
 *  2. **和谁打**（[VersusSettingsCard]，2026-09-12 从二级页前移）
 * 二级「游戏设置」页只管「怎么算」（AI 强度 / 赛制 / 玩法规则）。
 * 这条分界与 X01 完全一致（X01 一级页 = 目标分 + 对战，二级页 = 规则 + AI 对手），
 * 分界依据是**参数的变更频率**：每次开局都可能换的放一级，设一次就不动的放二级。
 *
 * 本页不产生任何新规则：玩法差别全部落在目标集上（见 [CricketMode]）。
 */
@Composable
fun CricketGamesScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onStart: (MatchConfig, List<Player>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val profile = remember(context) { ProfileStore.ensure(context) }
    val state = rememberCricketSetupState(context)
    LaunchedEffect(profile) { state.applySelf(profile) }
    val prefs = remember(context) { SetupDefaultsStore.prefs(context) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Cricket 游戏") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // 底栏两块：**「游戏设置」入口紧贴「开始比赛」上方**，两者一起钉在屏幕底部。
            //
            // 入口卡放底栏而不是随内容滚动，是因为它是这一页唯一的「出口」：
            // 玩法列表会随玩法扩展继续变长，滚到末尾才看得到设置入口等于把设置藏起来。
            // 底栏必须自己处理系统导航条内边距（MainActivity 开了 enableEdgeToEdge，Scaffold 不替 bottomBar 加）。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsEntryCard(
                    title = "游戏设置",
                    // 只列**二级页真有**的东西：对战已在本页，写进这里会让玩家点进去找它。
                    subtitle = "AI 难度 · 赛制 · 玩法规则",
                    highlight = state.summary,
                    onClick = onOpenSettings
                )
                PrimaryActionButton(
                    text = "开始比赛",
                    onClick = {
                        // 开局即落盘：玩家真正打过的配置才是可信的「上次设置」。
                        // 两个作用域分别落：比赛层跨玩法共用一份，玩法层只写当前这一档。
                        SetupDefaultsStore.writeMatch(prefs, state.toMatchDefaults())
                        SetupDefaultsStore.writeRules(
                            prefs,
                            state.cricketMode,
                            state.toRuleDefaults()
                        )
                        onStart(state.config, state.buildPlayers())
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
            SettingStack(title = "选择游戏") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CricketMode.entries.forEach { mode ->
                        VariantCard(
                            title = mode.title,
                            desc = mode.desc,
                            selected = state.cricketMode == mode,
                            // 玩法层设置随玩法切换：把该玩法盘上的那一档交回状态层
                            // （本会话改过的由状态层自己的缓存优先，见 selectMode）。
                            onClick = {
                                state.selectMode(
                                    mode,
                                    diskRules = SetupDefaultsStore.readRules(prefs, mode)
                                )
                            }
                        )
                    }
                }
            }

            when (state.cricketMode) {
                CricketMode.TACTICS -> SettingGroupCard(title = "本局目标（9 档）") {
                    HintText(state.targets.joinToString(" · ") { targetLabel(it) })
                }

                CricketMode.RANDOM -> SettingGroupCard(title = "本局随机目标（5 档）") {
                    HintText(state.targets.joinToString(" · ") { targetLabel(it) })
                    HintText("再点一次「随机目标」即可重新抽签；开局后本局目标不再改变。")
                }

                // 其余玩法的目标集是恒定的，不必占版面向玩家复述一遍。
                else -> Unit
            }

            // ===== 对战（对手选择）=====
            //
            // 摆在**本页**而不是二级「游戏设置」里（2026-09-12 与 X01 对齐）：
            // 「和谁打」是每次开局都可能改的参数，与「玩哪种 Cricket」同属「这一局是什么」；
            // 收进二级页会让玩家在按「开始比赛」之前看不到也改不了它。
            // 位置放在目标预览**之后**：「选了玩法 → 立刻看到它抽出的目标集」这条因果不能被别的卡打断。
            VersusSettingsCard(
                versus = state.versus,
                onVersusChange = { state.versus = it },
                selfName = state.selfName,
                selfAvatar = state.selfAvatar,
                opponents = state.opponents,
                onOpponentsChange = { state.opponents = it }
            )
        }
    }
}

/**
 * 取 Cricket 的游戏选择页 ↔ 游戏设置页**共用**的那一份状态。
 *
 * [SetupSession] 里缓存的实例在整个进程内只有一份，所以：
 *  - 从「游戏设置」返回时，玩家刚改的设置与刚选好的对手都还在（`remember { }` 单独用做不到 ——
 *    Navigation Compose 跳转时会销毁上一个目的地的组合）；
 *  - 「上次用过的玩法 + 该档设置」只在**首次创建**时灌一次（[SetupSession.cricketState] 的 `restore`
 *    参数只对新建实例生效），否则每次回到本页都会把玩家在设置页改的东西冲掉。
 */
@Composable
internal fun rememberCricketSetupState(
    context: android.content.Context,
): CricketSetupState = remember {
    SetupSession.cricketState { state ->
        val prefs = SetupDefaultsStore.prefs(context)
        val last = SetupDefaultsStore.readLastMode(prefs)
        // 两层分别读：比赛层（和谁打）一份，玩法层（怎么算）取「上次用过的玩法」那一档。
        state.restore(
            mode = last,
            matchDefaults = SetupDefaultsStore.readMatch(prefs),
            ruleDefaults = last?.let { SetupDefaultsStore.readRules(prefs, it) }
                ?: CricketRuleDefaults.DEFAULT
        )
    }
}
