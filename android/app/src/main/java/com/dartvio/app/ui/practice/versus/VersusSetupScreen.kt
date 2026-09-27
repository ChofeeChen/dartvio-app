package com.dartvio.app.ui.practice.versus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.data.local.entity.VersusMatchRecordEntity
import com.dartvio.app.data.profile.ProfileStore
import com.dartvio.app.domain.model.AiAvatar
import com.dartvio.app.domain.model.HumanAvatar
import com.dartvio.app.domain.profile.LocalProfile
import com.dartvio.app.domain.versus.BattleConfig
import com.dartvio.app.domain.versus.BullBattleRule
import com.dartvio.app.domain.versus.ClockTripleRule
import com.dartvio.app.domain.versus.DoublesClockRule
import com.dartvio.app.domain.versus.HalveItRule
import com.dartvio.app.domain.versus.RingRaceRule
import com.dartvio.app.domain.versus.ShanghaiRule
import com.dartvio.app.domain.versus.VersusModes
import com.dartvio.app.ui.components.AvatarOption
import com.dartvio.app.ui.components.SegmentedSelector
import com.dartvio.app.ui.setup.VersusMode
import com.dartvio.app.ui.theme.Accent
import com.dartvio.app.ui.theme.Primary
import com.dartvio.app.ui.theme.SurfaceVariantDark
import com.dartvio.app.ui.theme.TextDisabledDark
import com.dartvio.app.ui.theme.TextSecondaryDark

/** 环游类让分的可选「起点靠后」格数。 */
private val CLOCK_HANDICAP_STEPS = listOf(0, 2, 4)

/**
 * 对抗练习 · 对局配置页。
 *
 * 与列表页同一条约定：**只认 [VersusModes] 注册表与 [BattleConfig] 这张扁平配置表**，
 * 不 `when` 具体模式之外的东西；模式专属控件由 `modeKey` 三个常量决定，
 * 加第 7 个模式时这里只多一个分支，而不是多一整页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersusSetupScreen(
    modeKey: String,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val info = VersusModes.infoOf(modeKey)
    val rule = VersusModes.ruleOf(modeKey)

    // 未知 key（库里的老行 / 未来删掉的模式）：退化成不可玩，而不是让整页崩掉。
    if (info == null || rule == null) {
        VersusUnknownMode(onBack = onBack)
        return
    }

    /*
     * 「谁在打」改成了 X01 设置页那一套（2026-09-27 反馈）：**对战模式 + 四选一对手**，
     * 原来那两个「玩家 1 / 玩家 2」输入框取消。
     *
     * 为什么不再让双方各输一个名字：
     * ① 对抗练习是**这台手机上两人轮流投镖**，名字只是卡片上的标签，不影响任何规则；
     * ② 两个空输入框把「默认是谁」这个问题丢给了用户 —— 而默认答案（本人 + 一个对手）
     *    本来就足够好，让用户输入只是为了让他们事后猜自己输的是第几席。
     *
     * 四选一（而不是 X01 的「最多三选」）：对抗练习固定 1v1（[VERSUS_SEATS]），
     * 于是「选几个」这个问题不存在，只剩「选谁」。
     */
    val context = LocalContext.current
    val profile = remember(context) { ProfileStore.ensure(context) }
    var versus by remember { mutableStateOf(VersusMode.HUMAN_VS_HUMAN) }
    var humanPick by remember { mutableStateOf(HumanAvatar.HUMAN_2) }
    var aiPick by remember { mutableStateOf(AiAvatar.AI_1) }
    var config by remember(modeKey) { mutableStateOf(rule.defaultConfig()) }
    var showRules by remember { mutableStateOf(false) }

    val selfName = profile.nickname.ifBlank { LocalProfile.DEFAULT_NICKNAME }
    val opponentName = if (versus.picksAiAvatar) aiPick.label else humanPick.label
    val names = listOf(selfName, opponentName)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        info.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回", color = Primary) }
                },
                actions = {
                    TextButton(onClick = { showRules = true }) {
                        Text("规则", color = Accent, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        /*
         * 「开始对抗」钉在页面底部（与 X01 设置页同一套做法）。
         *
         * 此前它跟在滚动列表最后：设置项一多，按钮就被推到屏幕外，
         * 而用户改完最后一项的下一个动作就是找按钮 —— 那一刻按钮不在原地。
         * 主操作的**位置**不该随内容长度变化。
         */
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp)
                    .navigationBarsPadding()
            ) {
                Button(
                    onClick = {
                        VersusSession.prepare(modeKey, config, names)
                        onStart()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text("开始对抗", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
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

            SetupSection("谁在打") {
                Text(
                    "本人 ${profile.avatar.emoji} $selfName 固定占第 1 席（在「我的」页改昵称）",
                    fontSize = 12.sp,
                    color = TextSecondaryDark,
                )
                Spacer(Modifier.height(10.dp))
                SegmentedSelector(
                    options = VersusMode.entries,
                    selected = versus,
                    label = { if (it == VersusMode.HUMAN_VS_HUMAN) "真人 vs 真人" else "真人 vs AI" },
                    onSelect = { versus = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "对手（四选一）",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (versus.picksAiAvatar) {
                        AiAvatar.ALL.forEach { ai ->
                            AvatarOption(
                                emoji = ai.emoji,
                                caption = ai.label,
                                selected = ai == aiPick,
                                onClick = { aiPick = ai },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        HumanAvatar.ALL.forEach { human ->
                            AvatarOption(
                                emoji = human.emoji,
                                caption = human.label,
                                selected = human == humanPick,
                                onClick = { humanPick = human },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    // 把「AI 这一边现在怎么投」说清楚：对抗练习目前是**一台手机上两人轮流录入**，
                    // 机器人那一席的镖暂由你代投 —— 自动投镖还没做，不说就是骗人。
                    if (versus.picksAiAvatar) {
                        "选 AI：对手记为机器人，它的镖暂时也由你录入（自动投镖开发中）"
                    } else {
                        "选真人：两个人在同一台手机上轮流投镖"
                    },
                    fontSize = 11.sp,
                    color = TextDisabledDark,
                )
            }

            SetupSection("本局规则") {
                when (modeKey) {
                    VersusModes.BULL_BATTLE -> {
                        Text(
                            "先达目标分者胜",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        ChoiceRow(
                            label = "玩家 1 目标分",
                            choices = BullBattleRule.TARGET_CHOICES,
                            selected = config.targetFor(0),
                            onSelect = { config = config.withTarget(0, it) },
                        )
                        Spacer(Modifier.height(8.dp))
                        ChoiceRow(
                            label = "玩家 2 目标分",
                            choices = BullBattleRule.TARGET_CHOICES,
                            selected = config.targetFor(1),
                            onSelect = { config = config.withTarget(1, it) },
                        )
                        Spacer(Modifier.height(8.dp))
                        SwitchRow(
                            label = "区分内外 Bull（内 2 分 / 外 1 分）",
                            checked = config.splitBull,
                            onChecked = { config = config.copy(splitBull = it) },
                        )
                    }

                    VersusModes.RING_RACE -> {
                        ChoiceRow(
                            label = "目标分区",
                            choices = RingRaceRule.SECTOR_CHOICES,
                            selected = config.targetSector,
                            onSelect = { config = config.copy(targetSector = it) },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "只有命中目标分区的镖计分：T 3 分 / D 2 分 / S 1 分。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        ChoiceRow(
                            label = "玩家 1 目标分",
                            choices = RingRaceRule.TARGET_CHOICES,
                            selected = config.targetFor(0),
                            onSelect = { config = config.withTarget(0, it) },
                        )
                        Spacer(Modifier.height(8.dp))
                        ChoiceRow(
                            label = "玩家 2 目标分",
                            choices = RingRaceRule.TARGET_CHOICES,
                            selected = config.targetFor(1),
                            onSelect = { config = config.withTarget(1, it) },
                        )
                        Spacer(Modifier.height(8.dp))
                        SwitchRow(
                            label = "让分：玩家 2 仅三倍环计分",
                            checked = config.tripleOnlyFor(1),
                            onChecked = { config = config.withTripleOnly(1, it) },
                        )
                    }

                    VersusModes.CLOCK_TRIPLE -> {
                        Text(
                            "从 1 分区出发顺时针推进，先走完一圈者胜。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "变体",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VariantChip(
                                text = "核心（3 镖全中才进）",
                                selected = !config.doubleOnly && !config.singleHitAdvance,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    config = config.copy(doubleOnly = false, singleHitAdvance = false)
                                },
                            )
                            VariantChip(
                                text = "经典（1 镖中即进）",
                                selected = !config.doubleOnly && config.singleHitAdvance,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    config = config.copy(doubleOnly = false, singleHitAdvance = true)
                                },
                            )
                            VariantChip(
                                text = "双倍版",
                                selected = config.doubleOnly,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    config = config.copy(doubleOnly = true, singleHitAdvance = true)
                                },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        ChoiceRow(
                            label = "让分：玩家 2 起点靠后",
                            choices = CLOCK_HANDICAP_STEPS,
                            selected = config.startStepFor(1),
                            onSelect = { config = config.copy(startSteps = listOf(0, it)) },
                        )
                    }

                    VersusModes.DOUBLES_CLOCK -> {
                        Text(
                            "按 D1 → D2 → … → D20 顺序推进，每轮 3 镖。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "变体",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VariantChip(
                                text = "标准（1 镖中即进）",
                                selected = config.singleHitAdvance,
                                modifier = Modifier.weight(1f),
                                onClick = { config = config.copy(singleHitAdvance = true) },
                            )
                            VariantChip(
                                text = "地狱（3 镖同中才进）",
                                selected = !config.singleHitAdvance,
                                modifier = Modifier.weight(1f),
                                onClick = { config = config.copy(singleHitAdvance = false) },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        SwitchRow(
                            label = "走完 D20 后还要收尾 Bull",
                            checked = config.finishOnBull,
                            onChecked = { config = config.copy(finishOnBull = it) },
                        )
                        Spacer(Modifier.height(8.dp))
                        ChoiceRow(
                            label = "让分：玩家 2 起点靠后",
                            choices = CLOCK_HANDICAP_STEPS,
                            selected = config.startStepFor(1),
                            onSelect = { config = config.copy(startSteps = listOf(0, it)) },
                        )
                    }

                    VersusModes.SHANGHAI -> {
                        Text(
                            "共 ${ShanghaiRule.ROUNDS} 轮，第 n 轮打 n 分区；" +
                                "同轮打出 S + D + T 立即秒杀获胜。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${ShanghaiRule.ROUNDS} 轮无人秒杀则总分高者胜，平分加赛 Bull。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    VersusModes.HALVE_IT -> {
                        Text(
                            "8 轮目标：" + HalveItRule.SEQUENCE.joinToString(" → ") { it.label } + "。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "本轮 3 镖全部 0 分 → 当前总分减半（向下取整）。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val summary = handicapSummaryOf(config, names)
            if (summary.isNotBlank()) {
                SetupSection("让分") {
                    Text(summary, fontSize = 13.sp, color = Accent, fontWeight = FontWeight.Bold)
                }
            }

            // 底部留白：给 bottomBar 里的「开始对抗」让出位置，
            // 否则最后一个设置段会贴在按钮上沿（视觉上像两段属于同一块）。
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            title = { Text(info.title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
                TextButton(onClick = { showRules = false }) { Text("知道了", color = Primary) }
            },
        )
    }
}

/**
 * 让分摘要（落库 + 战报展示共用）。
 *
 * 放在这里而不是页面内部：配置页要预览、战报要回显，两处各拼一遍必然写出不一样的话术。
 */
fun handicapSummaryOf(config: BattleConfig, names: List<String>): String {
    val parts = mutableListOf<String>()
    if (config.targetFor(0) != config.targetFor(1)) {
        parts += "${names.getOrNull(0) ?: "玩家1"}：目标 ${config.targetFor(0)} 分"
        parts += "${names.getOrNull(1) ?: "玩家2"}：目标 ${config.targetFor(1)} 分"
    }
    if (config.startStepFor(1) > 0) {
        parts += "${names.getOrNull(1) ?: "玩家2"}：起点靠后 ${config.startStepFor(1)} 格"
    }
    if (config.tripleOnlyFor(1)) {
        parts += "${names.getOrNull(1) ?: "玩家2"}：仅三倍环计分"
    }
    return parts.joinToString("　·　")
}

/** 改名成「只给第 [seat] 席设置目标分」：两席相同则收成单值，避免落库快照里出现冗余。 */
private fun BattleConfig.withTarget(seat: Int, value: Int): BattleConfig {
    val a = if (seat == 0) value else targetFor(0)
    val b = if (seat == 1) value else targetFor(1)
    return if (a == b) {
        copy(targetScore = a, targetScores = emptyList())
    } else {
        copy(targetScore = a, targetScores = listOf(a, b))
    }
}

/** 「三倍独尊」按席位开关（见 `BattleConfig.tripleOnlySeats` 的注释）。 */
private fun BattleConfig.withTripleOnly(seat: Int, on: Boolean): BattleConfig {
    val seats = tripleOnlySeats.toMutableList()
    if (on) {
        if (!seats.contains(seat)) seats += seat
    } else {
        seats.remove(seat)
    }
    return copy(tripleOnlySeats = seats.sorted())
}

@Composable
private fun SetupSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    choices: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { value ->
                val isSelected = value == selected
                if (isSelected) {
                    Button(
                        onClick = { onSelect(value) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text(value.toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelect(value) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    ) {
                        Text(value.toString(), fontSize = 15.sp, color = TextSecondaryDark)
                    }
                }
            }
        }
    }
}

@Composable
private fun VariantChip(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    // 横向三选一：必须 `maxLines = 1`，否则窄屏上文字换行会把按钮撑高、整行错位。
    val label = @Composable {
        Text(
            text,
            fontSize = 11.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) com.dartvio.app.ui.theme.OnPrimary else TextSecondaryDark,
        )
    }
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            content = { label() },
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            content = { label() },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersusUnknownMode(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("对抗练习", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回", color = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("这个玩法当前不可用", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "可能是本版本移除了该玩法。已保存的历史战报不受影响。",
                fontSize = 13.sp,
                color = TextDisabledDark,
            )
        }
    }
}
