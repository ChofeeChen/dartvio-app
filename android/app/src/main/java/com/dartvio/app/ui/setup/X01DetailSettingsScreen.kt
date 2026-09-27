package com.dartvio.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.domain.model.AiDifficulty
import com.dartvio.app.domain.model.BullMode
import com.dartvio.app.domain.model.InMode
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.ui.components.CompactStepper
import com.dartvio.app.ui.components.PrimaryActionButton
import com.dartvio.app.ui.components.SegmentedSelector
import com.dartvio.app.ui.components.SettingGroupCard
import com.dartvio.app.ui.components.SettingRow
import com.dartvio.app.ui.components.SwitchSettingRow
import com.dartvio.app.ui.theme.BackgroundDark
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceDark
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextPrimaryDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/**
 * X01 游戏详细设置页。补充 X01 必需设置并提供「规则入口」。
 * 保持干净整洁、逻辑清晰：分组卡片化，避免凌乱。
 *
 * 排版口径（2026-09-12）：取消独立章节标题，标题进卡片（[SettingGroupCard]）；
 * 卡片内 ≤3 项走标题同行（[SettingRow]），≥4 项走标题独占一行（[SettingStack]）。
 *
 * [versus] 由调用方从 [X01SetupState] 传入：本页的「AI 对手」段只在 VS AI 时才生效，
 * 真人 vs 真人 时必须置灰（详见段内注释）。
 *
 * [aiDifficulty] 也走调用方传值（而不是本页 `remember`）：难度属于**会话状态**，
 * 主设置页组装参赛名单时要用它，本页只负责显示与回写。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun X01DetailSettingsScreen(
    initialConfig: MatchConfig,
    versus: VersusMode,
    aiDifficulty: AiDifficulty,
    onAiDifficultyChange: (AiDifficulty) -> Unit,
    onBack: () -> Unit,
    onApply: (MatchConfig) -> Unit
) {
    var mode by remember { mutableStateOf(initialConfig.mode) }
    // 局数候选集与其它设置页统一为 1~5（见 LEGS_TO_WIN_CHOICES）：
    // 从房间或旧数据带进来的 6~10 会被收进上界，否则步进器一按「+」就会跳到 5，看起来像坏了。
    var legsToWin by remember {
        mutableStateOf(
            initialConfig.legsToWin.coerceIn(
                LEGS_TO_WIN_CHOICES.first(),
                LEGS_TO_WIN_CHOICES.last()
            )
        )
    }
    // X01 三组规则档位 + 最多轮数：初值取自上一页带过来的配置（X01SetupState 持久化）。
    var outMode by remember { mutableStateOf(initialConfig.outMode) }
    var inMode by remember { mutableStateOf(initialConfig.inMode) }
    var bullMode by remember { mutableStateOf(initialConfig.bullMode) }
    var maxRounds by remember { mutableStateOf(initialConfig.maxRounds) }
    var smartAi by remember { mutableStateOf(initialConfig.smartAi) }
    var showRules by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "X01 详细设置",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark
                )
            )
        },
        containerColor = BackgroundDark,
        bottomBar = {
            // 「保存设置」钉在屏幕底部，不随设置内容滚动（与两个「开始比赛」口径一致）。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                PrimaryActionButton(
                    text = "保存设置",
                    onClick = {
                        onApply(
                            initialConfig.copy(
                                mode = mode,
                                legsToWin = if (mode == MatchMode.CASUAL) 0 else legsToWin,
                                outMode = outMode,
                                inMode = inMode,
                                bullMode = bullMode,
                                maxRounds = maxRounds,
                                smartAi = smartAi
                            )
                        )
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ===== 比赛模式（模式 2 项、局数是单个步进器 → 均走标题同行）=====
            SettingGroupCard(title = "比赛模式") {
                SettingRow(title = "模式") {
                    SegmentedSelector(
                        options = MatchMode.entries,
                        selected = mode,
                        label = { if (it == MatchMode.CASUAL) "休闲（单局）" else "多局定胜负" },
                        onSelect = { mode = it }
                    )
                }
                if (mode == MatchMode.MULTI_LEG) {
                    SettingRow(title = "局数") {
                        CompactStepper(
                            valueLabel = "先赢 $legsToWin 局",
                            subtitle = "多局模式获胜条件",
                            // 文案型取值用小字号：步进器默认的 26sp 是给「501」这种纯数字定的，
                            // 「先赢 4 局」是 5 个全角字形，26sp 会顶满整行、与旁边的 12sp 标题差出三档。
                            valueFontSize = 18.sp,
                            onPrevious = { legsToWin = (legsToWin - 1).coerceAtLeast(1) },
                            onNext = {
                                legsToWin = (legsToWin + 1).coerceAtMost(LEGS_TO_WIN_CHOICES.last())
                            }
                        )
                    }
                }
            }

            // ===== 计分规则 =====
            SettingGroupCard(title = "计分规则") {
                // 原本三项都是「开/关」：Double-Out、Double-In、Over Time。
                // 现在开局与结束各是三档（直入/双倍入/大师入、直出/双倍出/大师出），
                // 原来的 Over Time 是一句无实现、也无上限值的空开关，已换成有实际落点的「最多轮数」。
                // 四项由 [X01RuleSettingRows] 统一渲染，与 X01 练习页同一份实现。
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

            // ===== AI 对手 =====
            // 与 Cricket 的两个设置页**共用同一张卡**（[AiOpponentSettingsCard]）：
            // 难度行、自适应开关、压暗口径与文案只有一处定义，
            // 玩家就不会在 X01 与 Cricket 里看到同一个设置的两种说法。
            // 本页没有「挑对手」的控件（那在上一页），所以提示指向上一页。
            AiOpponentSettingsCard(
                versus = versus,
                aiDifficulty = aiDifficulty,
                onAiDifficultyChange = onAiDifficultyChange,
                smartAi = smartAi,
                onSmartAiChange = { smartAi = it },
                opponentsHint = "对手（头像与人数）在上一页「对战」卡片里选择"
            )

            // ===== 规则入口 =====
            SettingGroupCard(title = "规则") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceVariantDark)
                        .clickable { showRules = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "X01 规则说明",
                        color = TextPrimaryDark,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "查看", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showRules) {
        RulesDialog(
            bullMode = bullMode,
            inMode = inMode,
            outMode = outMode,
            maxRounds = maxRounds,
            onDismiss = { showRules = false }
        )
    }
}

/**
 * 规则说明弹窗：三组档位的说明**从当前设置现取**，所以要把当前值传进来 ——
 * 写死一份文案就会和设置页分叉（设置里写着大师入、说明里还写着双倍入）。
 */
@Composable
private fun RulesDialog(
    bullMode: BullMode,
    inMode: InMode,
    outMode: OutMode,
    maxRounds: Int,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("知道了", color = Primary)
            }
        },
        title = {
            Text("X01 规则", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                // 文案**由枚举现取**（而不是写死一遍）：改了档位说明，弹窗自动跟着变，
                // 不会出现「设置里写着大师入、说明里还写着双倍入」。
                text = "• 每人从目标分（如 501）开始向下扣分，每回合投 3 镖。\n" +
                    "• 牛眼规则：${bullMode.label} —— ${bullMode.desc}。\n" +
                    "• 开局规则：${inMode.label} —— ${inMode.desc}。\n" +
                    "• 结束规则：${outMode.label} —— ${outMode.desc}。\n" +
                    "• 最多轮数：" +
                    (if (maxRounds == 0) "无上限" else "$maxRounds 轮（打满后分数更低的选手获胜）") +
                    "。\n" +
                    "• 爆分（扣成负分、或收尾不符合结束规则）本回合得分作废。\n" +
                    "• 多局模式先赢设定局数者获胜。",
                color = TextSecondaryDark,
                fontSize = 13.sp
            )
        },
        containerColor = SurfaceDark
    )
}
