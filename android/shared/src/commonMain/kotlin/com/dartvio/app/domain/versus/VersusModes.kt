package com.dartvio.app.domain.versus

/** 模式分组标签（列表页按它分三段展示）。 */
enum class VersusGroup(val label: String) {
    WARM_UP("热身"),
    SPECIAL("专项"),
    PRESSURE("压力"),
}

/**
 * 一个对抗模式对 UI 的全部描述。
 *
 * 列表页 / 配置页 / 对局页 / 战报页**都不认识具体模式**：它们只读这里，
 * 所以「第 7 个模式」落地时只需要新增一个 [VersusRule] 实现 + 一条 [VersusModes.ALL]，
 * 四个页面一行都不用改。
 */
data class VersusModeInfo(
    val modeKey: String,
    val title: String,
    val desc: String,
    val group: VersusGroup,
    /** V1 是否已接通 UI；`false` = 列表页显示「即将上线」且不可点。 */
    val available: Boolean,
    /** 「规则说明」弹窗的条目（每行一条，不带标点装饰）。 */
    val rulesSummary: List<String>,
)

/**
 * 模式注册表。**六个模式的唯一清单**：`modeKey` ↔ 引擎 ↔ 文案三者的对应关系只在这里出现一次。
 *
 * 顺序 = 列表页展示顺序：先把 V1 能玩的三张放在各分组最前，
 * 让用户第一眼看到的全是能点的卡，而不是被三张「即将上线」挡在前面。
 */
object VersusModes {

    const val BULL_BATTLE = BullBattleRule.MODE_KEY
    const val RING_RACE = RingRaceRule.MODE_KEY
    const val CLOCK_TRIPLE = ClockTripleRule.MODE_KEY
    const val SHANGHAI = ShanghaiRule.MODE_KEY
    const val HALVE_IT = HalveItRule.MODE_KEY
    const val DOUBLES_CLOCK = DoublesClockRule.MODE_KEY

    val ALL: List<VersusModeInfo> = listOf(
        VersusModeInfo(
            modeKey = BULL_BATTLE,
            title = "Bull 之争",
            desc = "只投牛眼，先攒够目标分者胜",
            group = VersusGroup.WARM_UP,
            available = true,
            rulesSummary = listOf(
                "每轮 3 镖全部投向 Bull 区。",
                "外 Bull 记 1 分，内 Bull 记 2 分（统一模式下都记 1 分）。",
                "先达到目标分立即获胜；没有爆分规则，超出也算赢。",
                "目标分可选 10 / 20 / 30 / 50，双方可分别设置（让分）。",
            ),
        ),
        VersusModeInfo(
            modeKey = RING_RACE,
            title = "倍区竞赛",
            desc = "只算目标分区，T3 / D2 / S1 攒分竞速",
            group = VersusGroup.SPECIAL,
            available = true,
            rulesSummary = listOf(
                "目标分区默认 20，可选 19 / 16。",
                "只有命中目标分区的镖计分：三倍 3 分、双倍 2 分、单倍 1 分，打错分区 0 分。",
                "先达到目标分者胜，目标分可选 20 / 30 / 50 / 100，双方可分别设置。",
                "「三倍独尊」让分：被让分的一方只有三倍环计 3 分。",
            ),
        ),
        VersusModeInfo(
            modeKey = CLOCK_TRIPLE,
            title = "环游三镖",
            desc = "按顺时针序逐个分区推进，先走完一圈者胜",
            group = VersusGroup.SPECIAL,
            available = true,
            rulesSummary = listOf(
                "从 1 分区出发，按标准靶顺时针序推进，最后到 20 分区。",
                "每轮 3 镖全部投向当前目标分区，环带不限。",
                "核心规则：3 镖全部命中当前分区才前进一格；否则下一轮继续打这个分区（后退不存在）。",
                "先走完 20 个分区者胜。变体：经典版（1 镖中即进）、双倍版（须命中双倍环）。",
            ),
        ),
        VersusModeInfo(
            modeKey = DOUBLES_CLOCK,
            title = "双倍环游",
            desc = "从 D1 打到 D20，中一镖走一格",
            group = VersusGroup.SPECIAL,
            // 2026-09-27 开放：引擎（`DoublesClockRule`）早已写完并通过单测，
            // 缺的只是把这张卡点亮 + 配置页给它一组控件。
            available = true,
            rulesSummary = listOf(
                "按 D1 → D2 → … → D20 的顺序推进，可选 Bull 收尾。",
                "每轮 3 镖全部投向当前双倍区，1 镖命中即前进一格（最多连进 3 格）。",
                "变体：地狱模式要求 3 镖同中才前进。",
                "先完成者胜；让分方式为强者起点靠后。",
            ),
        ),
        VersusModeInfo(
            modeKey = SHANGHAI,
            title = "上海争霸",
            desc = "第 n 轮打 n 分区，同轮 S+D+T 秒杀",
            group = VersusGroup.PRESSURE,
            available = true,
            rulesSummary = listOf(
                "共 7 轮，第 n 轮打 n 分区，每人每轮 3 镖。",
                "命中当前分区的单倍 1 分、双倍 2 分、三倍 3 分；打错分区 0 分。",
                "秒杀：同一轮内打出同一分区的 S + D + T 立即获胜。",
                "7 轮无人秒杀则总分高者胜；平分加赛 Bull，各 1 镖。",
            ),
        ),
        VersusModeInfo(
            modeKey = HALVE_IT,
            title = "减半挑战",
            desc = "8 轮指定目标，本轮全 0 分总分减半",
            group = VersusGroup.PRESSURE,
            available = true,
            rulesSummary = listOf(
                "8 轮目标依次为：20、16、任意双倍、任意三倍、25、Bull 50、17、15。",
                "「任意双倍」轮每支命中任意双倍区的镖按其分值计入（D16 记 32）。",
                "「25」轮每支外 Bull 记 25 分，「Bull 50」轮每支内 Bull 记 50 分。",
                "惩罚：本轮 3 镖全部 0 分则当前总分减半（向下取整）。",
                "序列完成后总分高者胜；平分加赛 Bull。",
            ),
        ),
    )

    fun infoOf(modeKey: String): VersusModeInfo? = ALL.firstOrNull { it.modeKey == modeKey }

    /** V1 已接通的模式（列表页可点的那些）。 */
    val AVAILABLE: List<VersusModeInfo> = ALL.filter { it.available }

    /**
     * `modeKey` → 引擎。
     *
     * 未知 key 返回 `null` 而不是抛异常：这个 key 会从路由参数和数据库里回来，
     * 「库里的老行 + 新版本删掉了一个模式」时应当退化成不可玩，而不是让整页崩掉。
     */
    fun ruleOf(modeKey: String): VersusRule? = when (modeKey) {
        BULL_BATTLE -> BullBattleRule
        RING_RACE -> RingRaceRule
        CLOCK_TRIPLE -> ClockTripleRule
        SHANGHAI -> ShanghaiRule
        HALVE_IT -> HalveItRule
        DOUBLES_CLOCK -> DoublesClockRule
        else -> null
    }
}
