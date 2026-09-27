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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dartvio.app.data.profile.ProfileStore
import com.dartvio.app.domain.model.AiDifficulty
import com.dartvio.app.domain.model.BullMode
import com.dartvio.app.domain.model.HumanAvatar
import com.dartvio.app.domain.model.InMode
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.domain.model.Player
import com.dartvio.app.domain.profile.LocalProfile
import com.dartvio.app.ui.components.CompactStepper
import com.dartvio.app.ui.components.PrimaryActionButton
import com.dartvio.app.ui.components.SettingGroupCard
import com.dartvio.app.ui.components.SettingRow
import com.dartvio.app.ui.components.SettingsEntryCard
import com.dartvio.app.ui.theme.BackgroundDark
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.TextPrimaryDark

/**
 * X01 设置页的状态。
 *
 * 提到 [SetupSession] 上（而不是留在页面里 `remember { }`）是为了让「游戏详细设置」页往返时
 * 对战模式、**选中的对手**这些不被重置 —— Navigation Compose 跳转时会销毁本页的组合，
 * 页面内的 `remember` 状态会当场丢失，而玩家从详细设置回来时显然期望设置还在。
 *
 * 本人（第 1 席）的昵称与头像来自登录账号档案，本状态只读不改 ——
 * 改昵称与头像的唯一入口是「我的」页。
 */
@Stable
class X01SetupState {

    var scoreIndex by mutableStateOf(
        MatchConfig.VALID_TARGET_SCORES.indexOf(501).coerceAtLeast(0)
    )
    var versus by mutableStateOf(VersusMode.HUMAN_VS_HUMAN)

    /** 对手（多选即人数，见 [OpponentSelection]）；本人固定第 1 席。 */
    var opponents by mutableStateOf(OpponentSelection())

    /**
     * AI 难度：**整局一档、对全部 AI 对手生效**，与对手头像（身份）解耦。
     *
     * 界面上由「游戏详细设置」页的「AI 对手」卡片编辑（[AiOpponentSettingsCard]）——
     * 难度是一局规则，不属于「挑对手」。
     */
    var aiDifficulty by mutableStateOf(AiDifficulty.INTERMEDIATE)

    /** 「游戏详细设置」页负责的字段。 */
    var mode by mutableStateOf(MatchMode.MULTI_LEG)
    /**
     * 默认**先赢 1 局**（2026-09-27 反馈：打完一局本地 X01，数据页却什么都没有）。
     *
     * 统计与历史以**整场**为一条记录，不是一局：默认 3 局时，赢下第一局看到的
     * 「本局获胜」页只是中场，从这里退出的人整场没打完 ⇒ 一条记录都不会落库 ⇒
     * 数据页一片空白，且页面无从解释为什么。
     * 1 局制下「打完一局」=「打完一场」，与绝大多数人心里那一局 501 是同一件事；
     * 想打多局的人自己调（上限 5 局），那时胜利页会明说「打完才计入统计」。
     */
    var legsToWin by mutableStateOf(1)

    // X01 规则档位（三组，2026-09-12 由布尔升级）：默认 = 双倍出 / 直入 / 25-50 牛眼 / 无轮数上限。
    var outMode by mutableStateOf(OutMode.DOUBLE_OUT)
    var inMode by mutableStateOf(InMode.STRAIGHT_IN)
    var bullMode by mutableStateOf(BullMode.STANDARD_25_50)
    var maxRounds by mutableStateOf(0)
    var smartAi by mutableStateOf(true)

    /** 本人昵称 / 头像：取本机档案，界面只展示不可改。 */
    var selfName by mutableStateOf(LocalProfile.DEFAULT_NICKNAME)
    var selfAvatar by mutableStateOf(HumanAvatar.HUMAN_1)

    val targetScore: Int get() = MatchConfig.VALID_TARGET_SCORES[scoreIndex]

    /** **含本人在内**的参战人数 —— 对战页的玩家卡片数量就是它。 */
    val playerCount: Int get() = opponents.playerCount(versus)

    val config: MatchConfig
        get() = MatchConfig(
            matchType = MatchType.X01,
            targetScore = targetScore,
            mode = mode,
            legsToWin = if (mode == MatchMode.CASUAL) 0 else legsToWin,
            outMode = outMode,
            inMode = inMode,
            bullMode = bullMode,
            maxRounds = maxRounds,
            smartAi = smartAi
        )

    /** 本人身份来自登录账号，进入页面时刷新一次（界面不允许改）。 */
    fun applySelf(profile: LocalProfile) {
        selfName = profile.nickname
        selfAvatar = profile.avatar
    }

    /** 「游戏详细设置」页返回时回填它改过的字段。 */
    fun applyConfig(config: MatchConfig) {
        MatchConfig.VALID_TARGET_SCORES.indexOf(config.targetScore)
            .takeIf { it >= 0 }
            ?.let { scoreIndex = it }
        mode = config.mode
        legsToWin = config.legsToWin.coerceAtLeast(1)
        outMode = config.outMode
        inMode = config.inMode
        bullMode = config.bullMode
        maxRounds = config.maxRounds
        smartAi = config.smartAi
    }

    /** 组装参赛名单：第 1 席恒为本人（本机档案），其后是选中的对手。 */
    fun buildPlayers(): List<Player> =
        buildSetupPlayers(versus, opponents, selfName, selfAvatar, aiDifficulty)
}

/**
 * X01 设置页（主设置）。PRD：
 *  - 紧凑分数选择（左右箭头，301/501/701/901/1101）
 *  - 玩家组成：真人 vs 真人 / 真人 vs AI，**最多 4 人对战**（本人 + 最多 3 名对手）
 *  - 对手由「点选头像」直接决定人数，**不再有独立的「玩家人数」控件**
 *  - 底部「游戏设置」入口 → 游戏详细设置页；入口卡紧贴「开始比赛」上方
 *
 * 排版口径（2026-09-12）：取消独立章节标题，设置项收进父卡片（[SettingGroupCard]）；
 * 卡片内 ≤3 项走标题同行（[SettingRow]），≥4 项走标题独占一行（见 [VersusSettingsCard]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun X01SetupScreen(
    state: X01SetupState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onStart: (MatchConfig, List<Player>) -> Unit,
) {
    val context = LocalContext.current
    val profile = remember(context) { ProfileStore.ensure(context) }
    LaunchedEffect(profile) { state.applySelf(profile) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "X01 设置",
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
            // 「游戏设置」入口紧贴「开始比赛」上方，两者一起钉在屏幕底部（不随设置内容滚动）。
            // 底栏必须自己处理系统导航条内边距：MainActivity 开了 enableEdgeToEdge，
            // Scaffold 不替 bottomBar 加，否则按钮会被手势条压住。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsEntryCard(
                    title = "游戏设置",
                    subtitle = "模式 / 局数 / 计分规则 / AI 对手",
                    highlight = "${state.targetScore} · ${state.playerCount} 人",
                    onClick = onOpenSettings
                )
                PrimaryActionButton(
                    text = "开始比赛",
                    onClick = { onStart(state.config, state.buildPlayers()) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ===== 目标分（单个步进器 → 标题同行）=====
            SettingGroupCard {
                SettingRow(title = "目标分") {
                    CompactStepper(
                        valueLabel = "${state.targetScore}",
                        subtitle = targetSubtitle(state.targetScore),
                        onPrevious = {
                            state.scoreIndex = (state.scoreIndex - 1 +
                                MatchConfig.VALID_TARGET_SCORES.size) %
                                MatchConfig.VALID_TARGET_SCORES.size
                        },
                        onNext = {
                            state.scoreIndex = (state.scoreIndex + 1) %
                                MatchConfig.VALID_TARGET_SCORES.size
                        }
                    )
                }
            }

            // ===== 对战（模式 2 项 → 标题同行；对手头像 4 个 → 标题独占）=====
            // AI 难度**不在这里**：它归「游戏设置」（下一页的「AI 对手」卡片）。
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

private fun targetSubtitle(score: Int): String = when (score) {
    301 -> "快速局"
    501 -> "标准局（推荐）"
    701 -> "长局"
    901 -> "进阶长局"
    else -> "超长局"
}
