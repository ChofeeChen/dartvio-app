package com.dartvio.app.domain.model

/**
 * 对局配置。PRD 决策点：目标分统一为 301/501/701(P0) + 901/1101(P1)。
 * legsToWin：正式=3（M2 定义），休闲模式=0（单局定胜负）。
 */
data class MatchConfig(
    val matchType: MatchType = MatchType.X01,

    /** X01 目标分：301/501/701/901/1101。 */
    val targetScore: Int = 501,

    /** 比赛模式：休闲 / 多局。 */
    val mode: MatchMode = MatchMode.MULTI_LEG,

    /** 多局模式下，先赢多少局者获胜。休闲模式为 0。 */
    val legsToWin: Int = 3,

    /** 结束规则（Out Mode）：获胜的最后一支镖要满足什么条件（直出/双倍出/大师出）。 */
    val outMode: OutMode = OutMode.DOUBLE_OUT,

    /** 开局规则（In Mode）：第一支有效镖要满足什么条件才开始计分（直入/双倍入/大师入）。 */
    val inMode: InMode = InMode.STRAIGHT_IN,

    /** 牛眼规则（Bull Mode）：外牛眼算 25 分还是 50 分。 */
    val bullMode: BullMode = BullMode.STANDARD_25_50,

    /**
     * 轮数上限（Max Rounds，2026-09-12）：`0` = 无上限（缺省）。
     *
     * **一个字段承载两个玩法**（2026-09-12 由 `maxRounds` / `roundLimit` 两个字段合并）：
     * - X01：即原「最多轮数」，替代从无实现的 `overtimeRule` 空开关；
     * - Cricket：即原 `roundLimit`（M2 §4.9.8），仅 Tactics 会设非 0，其余玩法恒 0。
     *
     * 单位两者一致：「每人各投完一轮」记 1。
     *
     * ⚠️ **超时终局的胜负口径按玩法分派**，留在各自的规则引擎里（合并字段不合并这条规则）：
     * - X01 打满 ⇒ **剩余分最低者获胜**（剩余分越低越接近胜利）；
     * - Cricket 打满 ⇒ **总分最高者获胜**（§4.9.8，不读 [CricketVariant.winCompare]）。
     *
     * 合并理由：两者的语义、单位、候选值（[MAX_ROUNDS_CHOICES]）逐项相同，
     * 差别只在「谁领先」这一条玩法规则上，而它本来就不由字段名承载。
     * 合并后仍互斥：同一局只会有一个玩法在读这个字段。
     */
    val maxRounds: Int = 0,

    /**
     * 「智能难度」开关（M4 §6.1.1⑦）。
     * 开启且为多局模式时，AI 启用同级自适应（遇强则强）；否则 AI 强度恒为档位中值。
     */
    val smartAi: Boolean = true,

    /**
     * Cricket 玩法变体（M2 §4.8 / M4 §6.2），与 matchMode / aiPpr 等同属 Cricket 专属配置。
     * 仅在 matchType = CRICKET 时生效；X01 不适用（设置页也不显示该项）。
     */
    val cricketVariant: CricketVariant = CricketVariant.DEFAULT,

    /**
     * Cricket 目标集：本局要关闭哪些目标位（二期 2A 起用标识而非数字）。
     *
     * 默认 = 标准 7 分区（20→15 递减 + Bull），**顺序即 UI 行序**，与一期完全相同。
     * 二期 2C 起它同时承载玩法：Tactics 用 [CricketTarget.TACTICS_TARGETS]（9 档），
     * Random 用 [CricketTarget.pickRandomTargets] 抽 5 个数字分区（Q7：**不新增 [CricketVariant] 取值**，
     * 玩法完全由目标集区分，落库与协议因此不需要为玩法再开字段）。
     */
    val cricketTargets: List<CricketTarget> = CricketTarget.DEFAULT_TARGETS,

    /**
     * Cricket Overkill 开关（二期 2C，M2 §4.9.9）：`false` = 关闭（缺省，一期行为）。
     *
     * 开启后：本方领先对手 ≥ 200 分时，本可得分的那一镖**只加标记、不计分**。
     * 仅 Tactics 使用；字段可用、默认关闭。
     */
    val overkillEnabled: Boolean = false,
) {
    /**
     * 老字段投影：`doubleOut` / `doubleIn` / `overtimeRule` 三个布尔仍按原语义可读
     * （线上老键、Room 老列、历史统计都靠它们），但**规则判定一律读 [outMode] / [inMode] / [maxRounds]**。
     *
     * 为什么不直接删：老键是给还没升级的端读的，删掉等于要求所有端同时升级。
     * 投影保证「新档位」与「老布尔」永远同源，不会出现两个字段各说一套。
     */
    val doubleOut: Boolean get() = outMode != OutMode.STRAIGHT_OUT
    val doubleIn: Boolean get() = inMode != InMode.STRAIGHT_IN
    val overtimeRule: Boolean get() = maxRounds > 0

    /**
     * 目标集里的数字号位投影（一期口径）。
     *
     * 保留它有两个用处：① 线上老字段 `cricket_numbers` 仍按数字数组发，老客户端读得懂；
     * ② 一期遗留的读取点可以逐条迁移，不用一次性全改。
     * 类别档不是数字，天然落在投影外 —— 这正是二期要解决的问题，不是缺陷。
     */
    val cricketNumbers: List<Int>
        get() = cricketTargets.mapNotNull { (it as? CricketTarget.Number)?.value }

    val displayName: String
        get() = when (matchType) {
            MatchType.X01 -> "X01 - $targetScore"
            MatchType.CRICKET -> "Cricket"
            MatchType.AROUND_THE_CLOCK -> "Around the Clock"
            MatchType.SHANGHAI -> "Shanghai"
            MatchType.HALVE_IT -> "Halve It"
            MatchType.KILLER -> "Killer"
        }

    companion object {
        val X01_301 = MatchConfig(targetScore = 301)
        val X01_501 = MatchConfig(targetScore = 501)
        val X01_701 = MatchConfig(targetScore = 701)
        val X01_901 = MatchConfig(targetScore = 901)
        val X01_1101 = MatchConfig(targetScore = 1101)

        /** Cricket 不需要倍区收尾，所以是直出（与旧版 `doubleOut = false` 等价）。 */
        val CRICKET = MatchConfig(matchType = MatchType.CRICKET, outMode = OutMode.STRAIGHT_OUT)

        /** 合法目标分集合。 */
        val VALID_TARGET_SCORES = listOf(301, 501, 701, 901, 1101)

        /** 合法局数（多局模式）。 */
        val VALID_LEGS_TO_WIN = (1..10).toList()

        /**
         * 轮数上限候选集：`0` = 无上限，其余为每人各投的轮数。
         *
         * **X01 与 Cricket 共用这一份**（2026-09-12 合并）：合并前 UI 侧另有一份逐值相同的
         * `ROUND_LIMIT_CHOICES`（同一个值域维护两处，加一档就会漏改一边）。
         * 取值依据是同一套牌感预期：501 通常 15~20 轮结束。
         */
        val MAX_ROUNDS_CHOICES = listOf(0, 15, 20, 50, 80)
    }
}

/** 参赛选手。 */
data class Player(
    val id: String,
    val name: String,
    val type: PlayerType = PlayerType.HUMAN,
    val aiDifficulty: AiDifficulty? = null,
    /** 头像标识：真人用 HumanAvatar.name，AI 用 AiAvatar.name。 */
    val avatar: String = "HUMAN_1"
) {
    val isAi: Boolean get() = type == PlayerType.AI
}
