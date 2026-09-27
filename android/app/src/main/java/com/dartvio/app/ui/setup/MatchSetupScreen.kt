package com.dartvio.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.data.profile.ProfileStore
import com.dartvio.app.domain.model.*
import com.dartvio.app.domain.profile.LocalProfile
import com.dartvio.app.ui.components.OptionChipRow
import com.dartvio.app.ui.components.PrimaryActionButton
import com.dartvio.app.ui.components.SegmentedSelector
import com.dartvio.app.ui.components.SettingGroupCard
import com.dartvio.app.ui.components.SettingRow
import com.dartvio.app.ui.components.SettingStack
import com.dartvio.app.ui.components.SwitchSettingRow
import com.dartvio.app.ui.components.VariantCard
import com.dartvio.app.ui.theme.*
import kotlin.random.Random

/**
 * 比赛设置页。PRD 决策点2：
 * 1) 休闲模式（单局定胜负）
 * 2) 多局模式（1~5 局胜）
 *
 * 排版口径（2026-09-12）：不再有独立的章节标题，所有设置项收进父卡片
 * （[SettingGroupCard]）；卡片内按**选项数**分档 —— ≤3 项走标题同行（[SettingRow]），
 * ≥4 项走标题独占一行（[SettingStack]）。分档依据见 [SettingRow] 的注释。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchSetupScreen(
    matchType: MatchType,
    onBack: () -> Unit,
    onStart: (MatchConfig, List<Player>) -> Unit,
    /** 初始对战模式；练习入口可预选「真人 vs AI」。 */
    initialVersus: VersusMode = VersusMode.HUMAN_VS_HUMAN,
    /** 非空时显示「项目」切换，供练习入口在 X01 / Cricket 间切换。 */
    selectableTypes: List<MatchType> = emptyList(),
    /** 自定义标题，为空时按项目显示「X01 设置 / Cricket 设置」。 */
    screenTitle: String? = null,
    /**
     * 锁死对战模式（隐藏「对战模式」二选一，只留挑对手）。
     *
     * 「AI 对战练习」入口用它：那一页的对战模式由业务定死为 [VersusMode.HUMAN_VS_AI]，
     * 把两个选项摆在练习页上等于给了一条「把练习局改成真人局」的岔路。
     */
    versusLocked: Boolean = false
) {
    var type by remember(matchType) { mutableStateOf(matchType) }
    var targetScore by remember { mutableStateOf(501) }
    var mode by remember { mutableStateOf(MatchMode.MULTI_LEG) }
    // 默认先赢 1 局：与 X01 设置页同一口径（打完一局 = 打完一场 = 落库），
    // 见 X01SetupScreen 的 legsToWin 注释（2026-09-27 反馈）。
    var legsToWin by remember { mutableStateOf(1) }
    // X01 规则档位（三组）+ 最多轮数：与「X01 详细设置」页共用同一组控件与文案
    // （见 [X01RuleSettingRows]），免得练习页与正式设置页对同一条规则各说一套。
    var outMode by remember { mutableStateOf(OutMode.DOUBLE_OUT) }
    var inMode by remember { mutableStateOf(InMode.STRAIGHT_IN) }
    var bullMode by remember { mutableStateOf(BullMode.STANDARD_25_50) }
    var maxRounds by remember { mutableStateOf(0) }
    // Cricket 也支持「真人 vs AI」（与 X01 对齐）
    var versus by remember { mutableStateOf(initialVersus) }
    // 对手（**点选头像即人数**，见 [OpponentSelection]）。本人固定第 1 席，
    // 昵称与头像取本机档案，不再是可编辑的「玩家 1 / 玩家 2」输入框。
    var opponents by remember { mutableStateOf(OpponentSelection()) }
    var selfName by remember { mutableStateOf(LocalProfile.DEFAULT_NICKNAME) }
    var selfAvatar by remember { mutableStateOf(HumanAvatar.HUMAN_1) }
    // AI 难度：整局一档、对全部 AI 对手统一生效 —— 与头像（只表示「是谁」）解耦，
    // 归下面的「AI 对手」卡片（[AiOpponentSettingsCard]），不与挑对手挤在同一张卡里。
    var aiDifficulty by remember { mutableStateOf(AiDifficulty.INTERMEDIATE) }
    // 智能难度（AI 在本档内随真人水平微调）。X01 与 Cricket 共用同一个开关与同一份文案，
    // 见 [AiOpponentSettingsCard]。
    var smartAi by remember { mutableStateOf(true) }
    val context = LocalContext.current
    LaunchedEffect(context) {
        val profile = ProfileStore.ensure(context)
        selfName = profile.nickname
        selfAvatar = profile.avatar
    }
    // Cricket 玩法（PRD M7 P7.1；二期 2C 扩成五选一）。切到 X01 时该 Section 不显示，值也不参与 config。
    var cricketMode by remember { mutableStateOf(CricketMode.STANDARD) }
    // Random 的目标集必须**只在选中的那一刻抽一次**（§4.9.5① 局级常量）：
    // 放进 config 的就地计算会让每次重组都重抽，目标集在设置页就自己变了。
    var randomTargets by remember { mutableStateOf(CricketTarget.DEFAULT_TARGETS) }
    // 轮数上限只对 Tactics 有意；单独 remember 一份，避免切走再切回时丢掉玩家的改动。
    var tacticsRoundLimit by remember { mutableStateOf(DEFAULT_TACTICS_ROUND_LIMIT) }
    // Overkill 缺省关闭（字段可用、默认关闭）。
    var overkillEnabled by remember { mutableStateOf(false) }

    // 参战人数 = 本人 + 选中的对手。生死局的策略提示按真实人数判断，
    // 不再写死 2 人 —— 选满 3 名对手时那条提示本身就是错的。
    val playerCount = opponents.playerCount(versus)

    val config = MatchConfig(
        matchType = type,
        targetScore = targetScore,
        mode = mode,
        legsToWin = if (mode == MatchMode.CASUAL) 0 else legsToWin,
        outMode = outMode,
        inMode = inMode,
        bullMode = bullMode,
        // 轮数上限与 X01「最多轮数」是**同一个字段**（2026-09-12 合并）：
        // X01 用本页「最多轮数」那一行；Cricket 只有 Tactics 才有意义，其余玩法恒 0（= 无上限）。
        maxRounds = if (type == MatchType.CRICKET) {
            if (cricketMode == CricketMode.TACTICS) tacticsRoundLimit else 0
        } else {
            maxRounds
        },
        cricketVariant = cricketMode.variant,
        // 玩法差别全部落在目标集上（Q7）：standard / no_score / cut_throat / tactics 恒定，
        // random 用上面抽好的那一份。
        cricketTargets = if (type == MatchType.CRICKET) {
            when (cricketMode) {
                CricketMode.TACTICS -> CricketTarget.TACTICS_TARGETS
                CricketMode.RANDOM -> randomTargets
                else -> CricketTarget.DEFAULT_TARGETS
            }
        } else {
            CricketTarget.DEFAULT_TARGETS
        },
        smartAi = smartAi,
        // 只有 SELF 归属下 Overkill 才有意义（§4.9.9）；其余模式即使开着也是空操作，
        // 所以这里直接把开关的结果吞掉，避免把「开着但无效」的配置带进对局。
        overkillEnabled = type == MatchType.CRICKET &&
            cricketMode.supportsOverkill && overkillEnabled,
    )

    /** 构造当前设置下的玩家列表：第 1 席本人，其后是选中的对手。 */
    fun buildPlayers(): List<Player> =
        buildSetupPlayers(versus, opponents, selfName, selfAvatar, aiDifficulty)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle ?: if (type == MatchType.X01) "X01 设置" else "Cricket 设置") },
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
            // 「开始比赛」钉在屏幕底部，不随设置内容滚动。
            // 设置段数会随玩法扩展继续变长（Cricket 已有 6 段），按钮留在滚动区末尾
            // 就等于「必须先滑到底才能开局」—— 那是纯损耗，不是信息。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                PrimaryActionButton(
                    text = "开始比赛",
                    onClick = { onStart(config, buildPlayers()) }
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
            // ===== 项目（仅练习入口可切换；2 项 → 标题同行）=====
            if (selectableTypes.isNotEmpty()) {
                SettingGroupCard {
                    SettingRow(title = "项目") {
                        SegmentedSelector(
                            options = selectableTypes,
                            selected = type,
                            label = { if (it == MatchType.X01) "X01" else "Cricket" },
                            onSelect = { type = it }
                        )
                    }
                }
            }

            // ===== 比赛类型（PRD M7 P7.1）=====
            // Cricket 设置的第一个选择，也是分量最重的一个（它决定目标集与算分方式）；
            // 5 项 → 按分档规则独占一行。只在 Cricket 下出现 ——
            // X01 没有变体概念，显示出来只会让玩家困惑。
            if (type == MatchType.CRICKET) {
                SettingStack(title = "比赛类型") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CricketMode.entries.forEach { option ->
                            VariantCard(
                                title = option.title,
                                desc = option.desc,
                                selected = option == cricketMode,
                                onClick = {
                                    cricketMode = option
                                    // Random 在**选中这一刻**抽签并固定下来（§4.9.5① 局级常量）。
                                    if (option == CricketMode.RANDOM) {
                                        randomTargets = CricketTarget.pickRandomTargets(Random.Default)
                                    }
                                }
                            )
                        }
                    }
                }

                if (cricketMode == CricketMode.CUT_THROAT && playerCount == 2) {
                    // P7.1：只提示、不禁用 —— 2 人生死局策略价值确实低，但玩家仍有权选。
                    Text(
                        "2 人对战时生死局策略价值有限，3 人以上更有趣",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Random 的目标集抽到就要让玩家看见：抽出来的 5 个分区直接决定这局怎么打，
                // 藏到开局才发现等于把「随机」变成了「意外」。
                if (cricketMode == CricketMode.RANDOM) {
                    Text(
                        "本局目标：" + randomTargets.joinToString("、") { targetLabel(it) },
                        fontSize = 13.sp,
                        color = Primary
                    )
                }

                // 玩法规则：轮数上限（Tactics 专属）与 Overkill（SELF 归属专属）归到同一张卡。
                if (cricketMode == CricketMode.TACTICS || cricketMode.supportsOverkill) {
                    SettingGroupCard(title = "玩法规则") {
                        // 轮数上限只对 Tactics 有意义（其余玩法关满即胜，不需要上限）。
                        // 5 项 → 标题独占一行。
                        if (cricketMode == CricketMode.TACTICS) {
                            SettingStack(title = "轮数上限") {
                                OptionChipRow(
                                    options = MatchConfig.MAX_ROUNDS_CHOICES,
                                    selected = tacticsRoundLimit,
                                    label = { if (it == 0) "无上限" else "$it 轮" },
                                    onSelect = { tacticsRoundLimit = it }
                                )
                            }
                            HintText("每人各打满 N 轮仍无人关满时，总分最高者胜")
                        }

                        // Overkill 是标准 Cricket 的规则，所以它跟着「得分归属」走：
                        // SELF 归属（标准 / Tactics / 随机）提供开关；不计分与生死局下它无处生效，
                        // 放一个开了也不改变任何结算的开关只会让人以为坏了（§4.9.9 作用域）。
                        if (cricketMode.supportsOverkill) {
                            SwitchSettingRow(
                                title = "Overkill 超杀保护",
                                desc = "领先 200 分以上时，本可得分的那一镖只加标记、不计分",
                                checked = overkillEnabled,
                                onCheckedChange = { overkillEnabled = it }
                            )
                        }
                    }
                }
            }

            // ===== 对战（与 X01 设置页、Cricket 游戏设置页共用同一个组件）=====
            // 三处共用是为了让「本人固定第 1 席 + 点选头像决定对手人数」只有一种实现，
            // 否则改一次人数口径要同时改三个页面，迟早分叉。
            VersusSettingsCard(
                versus = versus,
                onVersusChange = { versus = it },
                selfName = selfName,
                selfAvatar = selfAvatar,
                opponents = opponents,
                onOpponentsChange = { opponents = it },
                showVersusSelector = !versusLocked
            )

            // ===== AI 对手（难度 / 自适应）=====
            // 本页是「一步到位」的设置页（没有独立的游戏设置页），所以这张卡就地展开，
            // 紧跟「对战」之后 —— 挑谁、多强，读序与别的设置页保持一致。
            AiOpponentSettingsCard(
                versus = versus,
                aiDifficulty = aiDifficulty,
                onAiDifficultyChange = { aiDifficulty = it },
                smartAi = smartAi,
                onSmartAiChange = { smartAi = it },
                opponentsHint = if (versusLocked) {
                    "对手（头像与人数）在上面「对手」卡片里选择"
                } else {
                    "对手（头像与人数）在上面「对战」卡片里选择"
                }
            )

            // ===== X01 专属：目标分（5 项 → 标题独占）+ 计分规则 =====
            if (type == MatchType.X01) {
                SettingGroupCard(title = "目标分与规则") {
                    SettingStack(title = "目标分") {
                        OptionChipRow(
                            options = MatchConfig.VALID_TARGET_SCORES.toList(),
                            selected = targetScore,
                            label = { "$it" },
                            onSelect = { targetScore = it }
                        )
                    }

                    // 牛眼 / 开局 / 结束 / 最多轮数：与 X01 详细设置页同一份实现。
                    X01RuleSettingRows(
                        bullMode = bullMode,
                        onBullModeChange = { bullMode = it },
                        inMode = inMode,
                        onInModeChange = { inMode = it },
                        outMode = outMode,
                        onOutModeChange = { outMode = it },
                        maxRounds = maxRounds,
                        onMaxRoundsChange = { maxRounds = it }
                    )
                }
            }

            // ===== 赛制（比赛模式 2 项 → 标题同行；局数 5 项 → 标题独占）=====
            MatchFormatSettings(
                mode = mode,
                onModeChange = { mode = it },
                legsToWin = legsToWin,
                onLegsToWinChange = { legsToWin = it }
            )
        }
    }
}
