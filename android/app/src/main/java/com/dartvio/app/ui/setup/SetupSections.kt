package com.dartvio.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dartvio.app.domain.model.AiAvatar
import com.dartvio.app.domain.model.AiDifficulty
import com.dartvio.app.domain.model.BullMode
import com.dartvio.app.domain.model.CricketTarget
import com.dartvio.app.domain.model.CricketVariant
import com.dartvio.app.domain.model.HumanAvatar
import com.dartvio.app.domain.model.InMode
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.domain.model.Player
import com.dartvio.app.domain.model.ScoreSink
import com.dartvio.app.domain.profile.LocalProfile
import com.dartvio.app.ui.components.AvatarOption
import com.dartvio.app.ui.components.CompactStepper
import com.dartvio.app.ui.components.OptionChipRow
import com.dartvio.app.ui.components.SegmentedSelector
import com.dartvio.app.ui.components.SettingGroupCard
import com.dartvio.app.ui.components.SettingRow
import com.dartvio.app.ui.components.SettingStack
import com.dartvio.app.ui.components.SwitchSettingRow
import com.dartvio.app.ui.theme.Primary
import kotlin.random.Random

// ============================================================================
// 共用常量
// ============================================================================

/**
 * Tactics 表单缺省预填值（§4.9.8：50 轮，玩家可改）。
 *
 * 只是**表单预填**，不是 [MatchConfig] 的字段缺省（那边的 `maxRounds` 缺省 0 = 无上限）。
 * 档位候选集不再另立一份：与 X01「最多轮数」共用 [MatchConfig.MAX_ROUNDS_CHOICES]
 * （2026-09-12 合并前这里有一份逐值相同的 `ROUND_LIMIT_CHOICES`）。
 */
const val DEFAULT_TACTICS_ROUND_LIMIT: Int = 50

/**
 * 多局模式可选的「先赢局数」档位。
 *
 * 收窄到 1~5：与创建房间页（`CreateRoomViewModel.legsOptions`）的既有口径一致，
 * 6~10 局在实战里几乎不被使用，却让这一行必须排成两行。
 *
 * ⚠️ 这只是**UI 候选集**：`MatchConfig.VALID_LEGS_TO_WIN` 仍是 1~10，
 * 房间配置、历史对局与协议里的 `legsToWin` 一字未动。
 */
val LEGS_TO_WIN_CHOICES: List<Int> = (1..5).toList()

/** 设置页的目标位文案：数字号位照写，类别档用「双倍档 / 三倍档」（§8.11 术语只保留一套）。 */
fun targetLabel(target: CricketTarget): String = when (target) {
    is CricketTarget.Number -> if (target.value == 25) "BULL" else target.value.toString()
    is CricketTarget.Category -> target.display
}

// ============================================================================
// Cricket 玩法（UI 层模型）
// ============================================================================

/**
 * 设置页的 Cricket 玩法（**UI 层模型**，M2 §1.Q7）。
 *
 * 五项选择只存在于 UI：底层仍是 `variant = standard` + **目标集**
 * （Tactics ⇒ 9 档目标集 / Random ⇒ 抽 5 个数字分区）。
 *
 * 绝不为 Tactics / Random 给 [CricketVariant] 加取值 —— 那会让「变体」这一个轴同时表示
 * 「得分怎么算」与「打哪些目标」两件事，`cricket_numbers`、`cricketVariant` 落库、
 * 成就准入与房间协议全都要跟着裂开，而它们本来一个都不用改。
 */
enum class CricketMode(
    val variant: CricketVariant,
    val title: String,
    val desc: String,
) {
    STANDARD(CricketVariant.STANDARD, CricketVariant.STANDARD.label, CricketVariant.STANDARD.hint),
    NO_SCORE(CricketVariant.NO_SCORE, CricketVariant.NO_SCORE.label, CricketVariant.NO_SCORE.hint),
    CUT_THROAT(
        CricketVariant.CUT_THROAT,
        CricketVariant.CUT_THROAT.label,
        CricketVariant.CUT_THROAT.hint
    ),

    // 名称与 hint 取自 M7 §8.11 的冻结文案（可直接引用，不要改写）。
    // 中文释义必须与名字**并列显示**：国内镖馆语境里的「Tactics」指的是 7 区标准 Cricket，
    // 与本模式字面完全重合 —— 等玩家读完 hint 才发现不同就已经晚了。
    TACTICS(
        CricketVariant.STANDARD,
        "Tactics 战术飞镖",
        "标准 Cricket 再加两个目标 —— 双倍档、三倍档，各需 3 个标记；打出双倍 / 三倍区时要二选一记哪个目标。"
    ),

    RANDOM(
        CricketVariant.STANDARD,
        "随机目标",
        "开局随机抽 5 个分区作为本局目标，局中不再改变。"
    );

    /**
     * 是否提供 Overkill 开关。
     *
     * Overkill 只在**得分归属为自己**时才有可压制的分（§4.9.9 作用域 = [ScoreSink.SELF]）。
     * 不计分模式没有分、生死局的分记在对手账上，开关放上去就是「开了也不改变任何结算」。
     */
    val supportsOverkill: Boolean get() = variant.scoreSink == ScoreSink.SELF
}

/**
 * Cricket 设置的可变状态。
 *
 * 抽出来是为了让「Cricket 游戏选择页」「Cricket 游戏设置页」共用同一份语义 ——
 * 否则同一个玩法在两处各组装一次配置，早晚会分叉
 * （最典型的是 Random 的抽签时机：只允许在选中那一刻抽一次）。
 *
 * **状态分两层**（与页面的分层一一对应，见 [SetupDefaultsStore] 的说明）：
 *  - 比赛层：[versus] / [opponents] / [aiDifficulty] / [smartAi] —— 跨玩法共享一份；
 *  - 玩法层：[mode] / [legsToWin] / [tacticsRoundLimit] / [overkillEnabled] —— 随玩法切换。
 */
@Stable
class CricketSetupState(
    initialVersus: VersusMode = VersusMode.HUMAN_VS_HUMAN,
) {
    /** 当前玩法。请走 [selectMode] 改动：Random 需要在选中那一刻抽签。 */
    var cricketMode by mutableStateOf(CricketMode.STANDARD)
        private set

    var versus by mutableStateOf(initialVersus)

    /**
     * 对手（**多选即人数**，见 [OpponentSelection]）。本人固定第 1 席，不从这里选。
     *
     * 换掉原先的「选手姓名输入框 + 单个 AI 头像」：那套写法一次只认 1 名对手，
     * 与「最多 4 人对战」的既有上限对不上，也与 X01 设置页的交互分叉。
     */
    var opponents by mutableStateOf(OpponentSelection())

    /** 本人昵称 / 头像：取自本机档案（登录账号），界面只展示不可改。 */
    var selfName by mutableStateOf(LocalProfile.DEFAULT_NICKNAME)
    var selfAvatar by mutableStateOf(HumanAvatar.HUMAN_1)

    /**
     * AI 难度：**整局一档、对全部 AI 对手生效**（与 [opponents] 里的 AI 头像解耦）。
     *
     * 头像回答「对手是谁」，这一档回答「对手多强」。合成一根轴时（旧版头像自带难度），
     * 设置页那 4 个圆在读法上就是「4 档难度选项」，玩家看不出自己在挑对手。
     *
     * 界面上它归 [AiOpponentSettingsCard]（游戏设置页），不归「对战」卡 —— 见那里的注释。
     */
    var aiDifficulty by mutableStateOf(AiDifficulty.INTERMEDIATE)

    /**
     * 智能难度：AI 在**本档内**跟着真人水平自适应 —— [aiDifficulty] 定档位，本开关定「要不要微调」。
     *
     * X01 一直有它，Cricket 之前漏了。两边同样有 AI 对手，却只有一处能开自适应，属于口径分叉；
     * 现在两个玩法的 AI 设置共用同一张卡（[AiOpponentSettingsCard]）。
     */
    var smartAi by mutableStateOf(true)

    var mode by mutableStateOf(MatchMode.MULTI_LEG)
    // 默认先赢 1 局（与 X01 同一口径，见 X01SetupScreen.legsToWin 注释）：
    // 打完一局就等于打完一场，战绩与统计当场就能看到。
    var legsToWin by mutableStateOf(1)
    var tacticsRoundLimit by mutableStateOf(DEFAULT_TACTICS_ROUND_LIMIT)
    var overkillEnabled by mutableStateOf(false)

    /**
     * Random 的目标集必须**只在选中的那一刻抽一次**（§4.9.5① 局级常量）：
     * 放进 config 的就地计算会让每次重组都重抽，目标集在设置页就自己变了。
     */
    var randomTargets by mutableStateOf(CricketTarget.DEFAULT_TARGETS)
        private set

    /** 玩法层设置的会话内暂存：切走时收进来、切回来原样恢复（见 [selectMode]）。 */
    private val ruleCache = mutableMapOf<CricketMode, CricketRuleDefaults>()

    /**
     * 切换玩法。选中 Random 时立刻抽签并固定下来。
     *
     * **玩法层设置跟着玩法走**：先把当前玩法上刚改的东西收进 [ruleCache]，
     * 再把目标玩法的设置放回来（本会话改过的 > 盘上那一档 > 默认）。
     * 少了这一步，「设置按玩法分别记住」在**同一次会话里就是不成立的** ——
     * 切玩法后改的东西会被写进另一个玩法的档，玩家下次打开发现设置串味了。
     *
     * [diskRules] 由页面从 [SetupDefaultsStore] 读好后传进来（状态层不碰 Context），
     * 这样整段切换逻辑 -- 含 Random 的抽签时机 -- 都能在纯 JVM 单测里覆盖。
     *
     * **比赛层（对手 / AI 强度）不参与切换**：它是跨玩法共享的一份，切玩法不该动它。
     */
    fun selectMode(
        selected: CricketMode,
        diskRules: CricketRuleDefaults? = null,
        random: Random = Random.Default,
    ) {
        if (selected != cricketMode) {
            ruleCache[cricketMode] = toRuleDefaults()
            applyRuleDefaults(ruleCache[selected] ?: diskRules ?: CricketRuleDefaults.DEFAULT)
            cricketMode = selected
        }
        // 重复点同一个玩法卡同样要重抽：Random 卡片上的说明就写着「再点一次即可重新抽签」。
        if (selected == CricketMode.RANDOM) {
            randomTargets = CricketTarget.pickRandomTargets(random)
        }
    }

    /** 当前玩法对应的目标集。 */
    val targets: List<CricketTarget>
        get() = when (cricketMode) {
            CricketMode.TACTICS -> CricketTarget.TACTICS_TARGETS
            CricketMode.RANDOM -> randomTargets
            else -> CricketTarget.DEFAULT_TARGETS
        }

    /**
     * 组装本局配置。
     *
     * 玩法差别全部落在目标集上（Q7）：standard / no_score / cut_throat / tactics 恒定，
     * random 用上面抽好的那一份。
     *
     * `outMode` 固定 [OutMode.STRAIGHT_OUT]（原 `doubleOut = false`）：Cricket 玩法不读这个字段，
     * 取直出是为了与 `MatchConfig.CRICKET` 这个既有常量保持一致，
     * 避免 Cricket 配置里挂着一个只在 X01 才有意义的「双倍出」。
     */
    val config: MatchConfig
        get() = MatchConfig(
            matchType = MatchType.CRICKET,
            mode = mode,
            legsToWin = if (mode == MatchMode.CASUAL) 0 else legsToWin,
            outMode = OutMode.STRAIGHT_OUT,
            cricketVariant = cricketMode.variant,
            cricketTargets = targets,
            // 轮数上限与 X01「最多轮数」是**同一个字段**（2026-09-12 合并）。
            // Cricket 侧只有 Tactics 有意义，其余玩法恒 0（= 无上限）。
            maxRounds = if (cricketMode == CricketMode.TACTICS) tacticsRoundLimit else 0,
            // AI 自适应开关直接进配置：AI 引擎读它决定是否按真人水平在本档内微调。
            smartAi = smartAi,
            // 只有 SELF 归属下 Overkill 才有意义（§4.9.9）；其余玩法即使开着也是空操作，
            // 所以这里直接把开关的结果吞掉，避免把「开着但无效」的配置带进对局。
            overkillEnabled = cricketMode.supportsOverkill && overkillEnabled,
        )

    /** **含本人在内**的参战人数 —— 对战页的玩家卡片数量就是它。 */
    val playerCount: Int
        get() = opponents.playerCount(versus)

    /** 组装参赛名单：第 1 席恒为本人（本机档案），其后是选中的对手。 */
    fun buildPlayers(): List<Player> =
        buildSetupPlayers(versus, opponents, selfName, selfAvatar, aiDifficulty)

    /** 本人身份来自登录账号，进入页面时刷新一次（界面不允许改）。 */
    fun applySelf(profile: LocalProfile) {
        selfName = profile.nickname
        selfAvatar = profile.avatar
    }

    /**
     * 页面首次创建时的一次性恢复：**比赛层一份 + 指定玩法的规则档**。
     *
     * 不复用 [selectMode] 是因为那里有「同档不重放」的保护（玩家重复点当前玩法卡不该重置设置），
     * 而首次恢复恰恰要把盘上那一份放进来 —— 包括上次用的就是默认玩法 STANDARD 的情况。
     *
     * [mode] 为 null（从未保存过）时保留初始玩法，仍然恢复比赛层：对手是跨玩法的，
     * 第一次进 Cricket 就该沿用玩家在别处挑好的对手。
     */
    fun restore(
        mode: CricketMode?,
        matchDefaults: MatchDefaults,
        ruleDefaults: CricketRuleDefaults,
        random: Random = Random.Default,
    ) {
        applyMatchDefaults(matchDefaults)
        if (mode != null) {
            cricketMode = mode
            // 盘上这一档也进缓存：切走再切回来时走缓存，不必再读盘。
            ruleCache[mode] = ruleDefaults
        }
        applyRuleDefaults(ruleDefaults)
        // Random 的目标集不入存储（§4.9.5①），恢复时同样必须在「选中」这一刻重抽。
        if (cricketMode == CricketMode.RANDOM) {
            randomTargets = CricketTarget.pickRandomTargets(random)
        }
    }

    /** 灌入**比赛层**设置（对手 / AI 强度）。跨玩法共享，切玩法时不再调用。 */
    fun applyMatchDefaults(defaults: MatchDefaults) {
        versus = if (defaults.versusAi) VersusMode.HUMAN_VS_AI else VersusMode.HUMAN_VS_HUMAN
        opponents = OpponentSelection.fromKeys(defaults.humanOpponentKeys, defaults.aiOpponentKeys)
        aiDifficulty = defaults.aiDifficulty
        smartAi = defaults.smartAi
    }

    /** 灌入**玩法层**设置（赛制 / 局数 / 轮数上限 / Overkill）。 */
    fun applyRuleDefaults(defaults: CricketRuleDefaults) {
        mode = defaults.matchMode
        legsToWin = defaults.legsToWin
        tacticsRoundLimit = defaults.tacticsRoundLimit
        overkillEnabled = defaults.overkill
    }

    /** 把当前比赛层状态收成可持久化的形态。 */
    fun toMatchDefaults(): MatchDefaults = MatchDefaults(
        versusAi = versus == VersusMode.HUMAN_VS_AI,
        humanOpponentKeys = opponents.humanKeys(),
        aiOpponentKeys = opponents.aiKeys(),
        aiDifficulty = aiDifficulty,
        smartAi = smartAi
    )

    /** 把当前玩法层状态收成可持久化的形态（「保存设置」/「开始比赛」时调用）。 */
    fun toRuleDefaults(): CricketRuleDefaults = CricketRuleDefaults(
        matchMode = mode,
        legsToWin = legsToWin,
        tacticsRoundLimit = tacticsRoundLimit,
        overkill = overkillEnabled
    )

    /** 入口卡摘要：一句话说清「现在会打成什么样」。入口卡上它是唯一的信息来源。 */
    val summary: String
        get() = buildString {
            append(if (mode == MatchMode.CASUAL) "单局" else "先赢 $legsToWin 局")
            append(" · ")
            if (versus.picksAiAvatar) {
                append("VS AI×${opponents.ais.size}")
            } else {
                append("真人 $playerCount 人")
            }
        }
}

// ============================================================================
// 可复用设置段落
// ============================================================================

/** 说明性小字（不参与任何交互）。 */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * 「对战」段：对战模式（2 项 → 标题同行）+ **对手多选**。
 *
 * 头像行自带尺寸，走 [SettingStack]（标题独占一行）—— 4 个圆头像塞进 58dp 的标题列后面
 * 会被压变形；这也是分档规则里「≥4 项一律独占」的典型例子。
 *
 * 这里**没有**「玩家人数」控件：人数就是选中头像的数量。
 * 两个控件并存时必然出现「选了 4 人却只有 3 个头像被选」这类互相打架的状态，
 * 合并之后「选中几个头像 = 几名对手」是看得到的事实，不需要再看数字。
 *
 * **难度不在这张卡里**（2026-09-12 二次裁决）：见 [AiOpponentSettingsCard]。
 * 头像行只回答「对手是谁」。难度是**一局规则**，跟 Double-Out / 局数 / 轮数上限一样，
 * 属于「游戏设置」而不是「挑对手」；摆在这里时它读起来像「第 5 个头像」。
 *
 * [showVersusSelector] = false 时整张卡只挑对手、标题改「对手」（2026-09-14）：
 * 「AI 对战练习」入口已把对战模式锁死为真人 vs AI，再留一排可点的「真人 vs 真人」
 * 等于允许把练习局改成真人局，所以那边**隐藏**而不是置灰。
 */
@Composable
fun VersusSettingsCard(
    versus: VersusMode,
    onVersusChange: (VersusMode) -> Unit,
    selfName: String,
    selfAvatar: HumanAvatar,
    opponents: OpponentSelection,
    onOpponentsChange: (OpponentSelection) -> Unit,
    modifier: Modifier = Modifier,
    showVersusSelector: Boolean = true
) {
    SettingGroupCard(modifier = modifier, title = if (showVersusSelector) "对战" else "对手") {
        if (showVersusSelector) {
            SettingRow(title = "对战模式") {
                SegmentedSelector(
                    options = VersusMode.entries,
                    selected = versus,
                    label = { if (it == VersusMode.HUMAN_VS_HUMAN) "真人 vs 真人" else "真人 vs AI" },
                    onSelect = onVersusChange
                )
            }
        }

        if (versus.picksAiAvatar) {
            SettingStack(title = "AI 对手（已选 ${opponents.ais.size}）") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AiAvatar.ALL.forEach { ai ->
                        AvatarOption(
                            emoji = ai.emoji,
                            // 头像是「对手身份」，不是难度 —— 难度在下面单独一张卡里选。
                            caption = ai.label,
                            selected = opponents.ais.contains(ai),
                            enabled = OpponentSelection.canToggleAi(opponents, ai),
                            onClick = { onOpponentsChange(opponents.toggleAi(ai)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            SettingStack(title = "真人对手（已选 ${opponents.humans.size}）") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HumanAvatar.ALL.forEach { human ->
                        AvatarOption(
                            emoji = human.emoji,
                            caption = human.label,
                            selected = opponents.humans.contains(human),
                            enabled = OpponentSelection.canToggleHuman(opponents, human),
                            onClick = { onOpponentsChange(opponents.toggleHuman(human)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 本人固定参战 —— 头像行选的是「对手」，把总数挑明，免得玩家去找已经被删掉的「玩家人数」。
        HintText(
            buildString {
                append("本人 ${selfAvatar.emoji} $selfName 固定参战；")
                append("点选头像即增减对手，含本人最多 $MAX_PLAYERS 人")
                if (versus.picksAiAvatar) {
                    // 把「难度不在这里」说明白：否则玩家会在这张卡里翻不到难度，
                    // 以为设置被删了。
                    append("。AI 头像只表示对手身份，强度在「AI 对手」卡片里选择")
                } else {
                    append("。真人对手不设置难度")
                }
            }
        )
    }
}

/** AI 难度行的标题。4 项 ⇒ 一律走 [SettingStack]（标题独占一行）。 */
const val AI_DIFFICULTY_TITLE = "AI 难度（对全部 AI 对手）"

/**
 * AI 难度的档位行（[SettingStack] 形态，4 项）。
 *
 * 抽成独立 composable，是为了让「X01 详细设置」「Cricket 游戏设置」「Cricket 练习」三处
 * 共用同一份标题、档位与压暗口径 —— 难度只有一处定义（[AiDifficulty]），
 * 界面就不该有第二种说法。
 *
 * [enabled] = false 时置灰不可点，但**已选中的档位仍保持亮**（见 [OptionChipRow]）：
 * 真人 vs 真人 下若连选中态一起压暗，玩家会以为难度被重置了。
 */
@Composable
fun AiDifficultySetting(
    enabled: Boolean,
    selected: AiDifficulty,
    onSelect: (AiDifficulty) -> Unit,
    modifier: Modifier = Modifier,
    title: String = AI_DIFFICULTY_TITLE
) {
    SettingStack(title = title, modifier = modifier) {
        OptionChipRow(
            options = AiDifficulty.entries,
            selected = selected,
            label = { it.displayName },
            onSelect = onSelect,
            enabled = enabled
        )
    }
}

/** 「智能难度」开关的标题与说明 —— X01 与 Cricket 共用同一份文案。 */
const val SMART_AI_TITLE = "智能难度（同级自适应）"
const val SMART_AI_DESC = "AI 随真人水平动态校准强度（只在所选难度档位内微调，不跨档）"

/** 真人对战时整卡压暗的系数：与 X01 详细设置页的既有口径一致。 */
private const val AI_CARD_DISABLED_ALPHA = 0.4f

/**
 * 「AI 对手」卡片：**AI 相关设置的唯一入口**
 * （X01 详细设置页 / Cricket 游戏设置页 / Cricket 练习页三处共用）。
 *
 * 为什么从「对战」卡搬出来（2026-09-12 二次裁决）：
 *  - 那张卡的职责是**挑对手**（谁坐第 2/3/4 席），难度答的却是「这一局多难」——
 *    与 Double-Out / 局数 / 轮数上限同一类，要和它们放在一起才有人想起来调；
 *  - 摆到头像行下方时，它读起来像「第 5 个头像」，而不是一局规则。
 *
 * **三处共用同一张卡**（而不是各写一段）：难度行、自适应开关、压暗口径与文案只要有一处分叉，
 * 玩家就会看到「同一个设置在两个页面说法不同」，这比少一个开关更糟 ——
 * X01 有「智能难度」而 Cricket 没有，就是这种分叉。
 *
 * 真人 vs 真人 时**整卡压暗而不是隐藏**：灰着留在原位，玩家才看得出「切到真人 vs AI 才有 AI 可调」；
 * 整块消失会被当成设置漏做。压暗之外还留一句说明原因的话 —— 只靠灰度会被当成图层没加载出来。
 *
 * [opponentsHint] 由各页给出：去哪个卡挑对手在页面之间并不一样
 * （Cricket 设置页 / 练习页就在上面，X01 详细设置页在**上一页**）。
 */
@Composable
fun AiOpponentSettingsCard(
    versus: VersusMode,
    aiDifficulty: AiDifficulty,
    onAiDifficultyChange: (AiDifficulty) -> Unit,
    smartAi: Boolean,
    onSmartAiChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    opponentsHint: String = "对手（头像与人数）在「对战」卡片里选择"
) {
    val enabled = versus.picksAiAvatar

    SettingGroupCard(
        modifier = modifier.alpha(if (enabled) 1f else AI_CARD_DISABLED_ALPHA),
        title = "AI 对手"
    ) {
        AiDifficultySetting(
            enabled = enabled,
            selected = aiDifficulty,
            onSelect = onAiDifficultyChange
        )

        // 自适应开关与难度档**同卡同置灰**：它同样是「只在有 AI 对手时才有意义」的设置，
        // 真人对战时留着可点会让人以为它能改变什么。
        SwitchSettingRow(
            title = SMART_AI_TITLE,
            desc = SMART_AI_DESC,
            checked = smartAi,
            onCheckedChange = onSmartAiChange,
            enabled = enabled
        )

        HintText(
            if (enabled) {
                "$opponentsHint；本局全部 AI 对手统一按这一档强度"
            } else {
                "当前为「真人 vs 真人」，本卡不生效 —— 真人对手没有难度可设"
            }
        )
    }
}

/**
 * X01 三组计分规则档位的**公共行组**：
 * 牛眼规则（Bull Mode）/ 开局规则（In Mode）/ 结束规则（Out Mode）+ 最多轮数（Max Rounds）。
 *
 * 抽出来是因为「X01 详细设置页」与「X01 练习页」都要有这四项，
 * 而它们一旦各写一遍，文案（尤其是每档的说明）就会分叉 ——
 * 规则说明本身就是这份设置的主要价值，说岔了等于没做。
 *
 * 调用方负责外面那层 `SettingGroupCard`（两页的卡片标题不同：一个是「计分规则」，
 * 一个是「目标分与规则」），本函数只负责往卡片里放行。
 *
 * 每行结构 = 「标题 + 档位选择」+ 一行**当前档位的说明**：
 * 档位说明是选项定义的一部分（例如「大师入：第一支有效镖必须命中双倍区或三倍区」），
 * 只把档位名摆出来、不给说明，玩家就得靠猜 —— 这正是原来那个「未定义开关」的病根。
 */
@Composable
fun X01RuleSettingRows(
    bullMode: BullMode,
    onBullModeChange: (BullMode) -> Unit,
    inMode: InMode,
    onInModeChange: (InMode) -> Unit,
    outMode: OutMode,
    onOutModeChange: (OutMode) -> Unit,
    maxRounds: Int,
    onMaxRoundsChange: (Int) -> Unit
) {
    // 牛眼规则：2 项 → 标题同行
    SettingRow(title = "牛眼规则") {
        OptionChipRow(
            options = BullMode.entries,
            selected = bullMode,
            label = { it.label },
            onSelect = onBullModeChange
        )
    }
    HintText(bullMode.desc)

    // 开局规则：3 项 → 标题同行
    SettingRow(title = "开局规则") {
        OptionChipRow(
            options = InMode.entries,
            selected = inMode,
            label = { it.label },
            onSelect = onInModeChange
        )
    }
    HintText(inMode.desc)

    // 结束规则：3 项 → 标题同行
    SettingRow(title = "结束规则") {
        OptionChipRow(
            options = OutMode.entries,
            selected = outMode,
            label = { it.label },
            onSelect = onOutModeChange
        )
    }
    HintText(outMode.desc)

    // 最多轮数：数值档位，走步进器（与本页「局数」同一种手感，比 5 个胶囊更省宽度）。
    SettingRow(title = "最多轮数") {
        CompactStepper(
            valueLabel = if (maxRounds == 0) "无上限" else "最多 $maxRounds 轮",
            subtitle = "打满后剩余分最低者获胜",
            valueFontSize = 18.sp,
            onPrevious = { onMaxRoundsChange(stepMaxRounds(maxRounds, -1)) },
            onNext = { onMaxRoundsChange(stepMaxRounds(maxRounds, +1)) }
        )
    }
    // 说明直接取自需求口径：**达到上限后分数更低者获胜**（不是判平）。
    HintText("设定比赛的最大轮数，防止无限拖延；达到轮数上限后，分数更低的选手获胜")
}

/** 在 [MatchConfig.MAX_ROUNDS_CHOICES] 上前后挪一格（`0` = 无上限，排在最前）。 */
private fun stepMaxRounds(current: Int, delta: Int): Int {
    val choices = MatchConfig.MAX_ROUNDS_CHOICES
    val index = choices.indexOf(current).takeIf { it >= 0 } ?: 0
    return choices[(index + delta).coerceIn(0, choices.lastIndex)]
}

/**
 * 「赛制」段：比赛模式（2 项 → 标题同行）+ 局数（5 项 → 标题独占）。
 *
 * 局数是 5 项，按分档规则必须独占一行；不要为了「省一行」把它硬塞进 [SettingRow]。
 */
@Composable
fun MatchFormatSettings(
    mode: MatchMode,
    onModeChange: (MatchMode) -> Unit,
    legsToWin: Int,
    onLegsToWinChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    casualHint: String = "休闲模式：打完一局即结束，显示「再来一局 / 退出」"
) {
    SettingGroupCard(modifier = modifier, title = "赛制") {
        SettingRow(title = "比赛模式") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModeCard(
                    title = "休闲模式",
                    desc = "单局定胜负",
                    selected = mode == MatchMode.CASUAL,
                    onClick = { onModeChange(MatchMode.CASUAL) },
                    modifier = Modifier.weight(1f)
                )
                ModeCard(
                    title = "多局模式",
                    desc = "多局定胜负",
                    selected = mode == MatchMode.MULTI_LEG,
                    onClick = { onModeChange(MatchMode.MULTI_LEG) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (mode == MatchMode.MULTI_LEG) {
            SettingStack(title = "局数（先达者胜）") {
                OptionChipRow(
                    options = LEGS_TO_WIN_CHOICES,
                    selected = legsToWin,
                    label = { "$it 局" },
                    onSelect = onLegsToWinChange
                )
            }
        } else {
            HintText(casualHint)
        }
    }
}

/**
 * Cricket 二级页（「游戏设置」）的表单：**只放「这一局怎么算」**。
 *
 * 2026-09-12 分层统一：**「对战」卡（对手选择）已经前移到游戏选择页**，
 * 本表单不再包含它 —— 一级页 = 决定「这一局是什么」（玩法 + 对手），
 * 二级页 = 决定「怎么算」（AI 强度 + 赛制 + 玩法规则）。
 * 理由与 X01 对齐：对手是每次都可能改的参数，藏进二级页会让玩家在
 * 按下「开始比赛」之前看不到也改不了它；而赛制/轮数上限这类设一次就不动的参数才该收在这一层。
 *
 * **不含**玩法（standard / Tactics / Random …）本身的选择 —— 那是页面的主选择，
 * 由游戏选择页放在列表首屏。
 */
@Composable
fun CricketSettingsForm(
    state: CricketSetupState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 对手控件在上一页，所以这张卡必须指名去哪改 —— 否则玩家在本页翻不到对手会以为设置丢了。
        AiOpponentSettingsCard(
            versus = state.versus,
            aiDifficulty = state.aiDifficulty,
            onAiDifficultyChange = { state.aiDifficulty = it },
            smartAi = state.smartAi,
            onSmartAiChange = { state.smartAi = it },
            opponentsHint = "对手（头像与人数）在上一页「对战」卡片里选择"
        )

        MatchFormatSettings(
            mode = state.mode,
            onModeChange = { state.mode = it },
            legsToWin = state.legsToWin,
            onLegsToWinChange = { state.legsToWin = it }
        )

        val showRulesCard = state.cricketMode == CricketMode.TACTICS ||
            state.cricketMode.supportsOverkill ||
            state.cricketMode == CricketMode.CUT_THROAT

        if (showRulesCard) {
            SettingGroupCard(title = "玩法规则") {
                if (state.cricketMode == CricketMode.TACTICS) {
                    SettingStack(title = "轮数上限") {
                        OptionChipRow(
                            options = MatchConfig.MAX_ROUNDS_CHOICES,
                            selected = state.tacticsRoundLimit,
                            label = { if (it == 0) "无上限" else "$it 轮" },
                            onSelect = { state.tacticsRoundLimit = it }
                        )
                    }
                    HintText("每人各打满 N 轮仍无人关满时，总分最高者胜")
                }

                if (state.cricketMode.supportsOverkill) {
                    SwitchSettingRow(
                        title = "Overkill 超杀保护",
                        desc = "领先 200 分以上时，本可得分的那一镖只加标记、不计分",
                        checked = state.overkillEnabled,
                        onCheckedChange = { state.overkillEnabled = it }
                    )
                }

                if (state.playerCount <= 2) {
                    // P7.1：只提示、不禁用 —— 2 人生死局策略价值确实低，但玩家仍有权选。
                    // 3 人以上不提示：那时这条策略提示本身就是错的。
                    HintText("2 人对战时生死局策略价值有限，3 人以上更有趣")
                }
            }
        }
    }
}

/**
 * 紧凑模式卡（标题 + 一行描述）。走「标题同行」时被压在约 118dp 宽里，
 * 所以内边距比一般卡片小、文案也必须短。
 */
@Composable
private fun ModeCard(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                if (selected) Primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            desc,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
