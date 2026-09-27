package com.dartvio.app.domain.model

/**
 * X01 的三组「规则档位」（2026-09-12 新增）。
 *
 * 为什么不再用布尔：
 *  - `doubleIn` / `doubleOut` 只能表达「开 / 关」，而开局与结束各有**三档**
 *    （直入 / 双倍入 / 大师入、直出 / 双倍出 / 大师出）。要加「大师档」就必须换轴，
 *    否则只能再塞一个布尔 —— 两个布尔能凑出四种组合，其中一种是非法态，读代码的人还得自己推。
 *  - 档位枚举自带**文案**（[label] / [desc]），设置页、规则说明、回写文档都从同一处取，
 *    不会再出现「界面写直出、引擎按双倍出跑」这种分叉。
 *
 * 老字段（`MatchConfig.doubleOut` 等）保留为**派生投影**，见 [MatchConfig] 里的说明。
 */

/** 牛眼规则（Bull Mode）：牛眼区怎么计分。 */
enum class BullMode(val label: String, val desc: String) {
    /** 外牛眼 25 分、内牛眼 50 分（绝大多数比赛的默认口径）。 */
    STANDARD_25_50("25/50", "外牛眼 25 分，内牛眼 50 分"),

    /** 无论内外牛眼都按 50 分计（部分联赛/练习玩法）。 */
    BULL_50_50("50/50", "无论内外牛眼都按 50 分计");

    /**
     * 本口径下单支镖的得分。
     *
     * 只在这里改写牛眼分，**不动 [Dart.score]**：Dart.score 是 Cricket、统计、落库共用的原始口径，
     * 改它会连带把 Cricket 的牛眼也变成 50 分。
     */
    fun scoreOf(dart: Dart): Int = if (this == BULL_50_50 && dart.isOuterBull) 50 else dart.score

    companion object {
        /**
         * 宽容解析（落库 / 协议共用）：null 或未知取值一律回落 [STANDARD_25_50]。
         *
         * 与 [CricketVariant.fromKey] 同一口径 —— 读到不认识的新档位**不抛异常**，
         * 退回本期默认口径继续跑。历史行缺列时也是这条路径。
         */
        fun fromKey(key: String?): BullMode =
            entries.firstOrNull { it.name == key } ?: STANDARD_25_50
    }
}

/** 开局规则（In Mode）：第一支有效镖要满足什么条件，才开始计分。 */
enum class InMode(val label: String, val desc: String) {
    /** 直入：任何落在靶上的镖都可以开始计分。 */
    STRAIGHT_IN("直入", "任何一支落在靶上的镖（单倍/双倍/三倍）都可以开始计分"),

    /** 双倍入：第一支有效镖必须命中双倍区或双倍牛眼。 */
    DOUBLE_IN("双倍入", "第一支有效镖必须命中双倍区（或双倍牛眼）才开始计分"),

    /** 大师入：第一支有效镖必须命中双倍区或三倍区；单倍区不计分。 */
    MASTER_IN("大师入", "第一支有效镖必须命中双倍区或三倍区才开始计分，单倍区不计分");

    /**
     * 这一镖是否满足「开镖」条件。
     *
     * 内牛眼写作 `25 × 2`，[Dart.isDouble] 为 true，因此天然算「双倍」——
     * 三个档位都不需要为牛眼单开分支。
     */
    fun opens(dart: Dart): Boolean = when (this) {
        STRAIGHT_IN -> true
        DOUBLE_IN -> dart.isDouble
        MASTER_IN -> dart.isDouble || dart.isTriple
    }

    /** 开局时是否已经「开着」（只有直入是）。 */
    val opensInitially: Boolean get() = this == STRAIGHT_IN

    /**
     * 计分板上的**短码**（SI / DI / MI）。
     *
     * 为什么另要一套短码：对局页顶部那一行只有三四个字符的位置，而「双倍入」三个字
     * 会把「LEG 1 · R 3」挤没。短码是飞镖圈的通写（Straight In / Double In / Master In），
     * 不需要翻译，也不占地方。
     */
    val code: String
        get() = when (this) {
            STRAIGHT_IN -> "SI"
            DOUBLE_IN -> "DI"
            MASTER_IN -> "MI"
        }

    companion object {
        /** 宽容解析：null 或未知取值一律回落 [STRAIGHT_IN]（最宽松，不会误判成「必须双倍入」）。 */
        fun fromKey(key: String?): InMode =
            entries.firstOrNull { it.name == key } ?: STRAIGHT_IN
    }
}

/** 结束规则（Out Mode）：获胜的最后一支镖要满足什么条件。 */
enum class OutMode(val label: String, val desc: String) {
    /** 直出：任何使分数恰好归零的镖都可以结束比赛。 */
    STRAIGHT_OUT("直出", "任何使分数恰好归零的镖都可以结束比赛，对区域没有要求"),

    /** 双倍出：职业比赛标准；最后一镖必须命中双倍区（或双倍牛眼）。 */
    DOUBLE_OUT("双倍出", "最后一镖必须命中双倍区（或双倍牛眼），且分数恰好归零"),

    /** 大师出：最后一镖必须命中双倍区或三倍区。 */
    MASTER_OUT("大师出", "最后一镖必须命中双倍区或三倍区，且分数恰好归零");

    /** 这一镖是否可以收尾（仅看区域，不看剩余分）。 */
    fun finishes(dart: Dart): Boolean = when (this) {
        STRAIGHT_OUT -> true
        DOUBLE_OUT -> dart.isDouble
        MASTER_OUT -> dart.isDouble || dart.isTriple
    }

    /**
     * 是否「必须按倍区收尾」。
     *
     * 用于「剩余 1 分即爆分」的判定：剩 1 分时，任何倍区收尾都不可能命中
     * （D1 = 2 分、T1 = 3 分），只能靠直出打单倍 1 收掉。
     */
    val requiresMultiplierFinish: Boolean get() = this != STRAIGHT_OUT

    /** 计分板短码（SO / DO / MO），理由见 [InMode.code]。 */
    val code: String
        get() = when (this) {
            STRAIGHT_OUT -> "SO"
            DOUBLE_OUT -> "DO"
            MASTER_OUT -> "MO"
        }

    companion object {
        /** 宽容解析：null 或未知取值一律回落 [STRAIGHT_OUT]（最宽松，不会凭空要求打倍区收尾）。 */
        fun fromKey(key: String?): OutMode =
            entries.firstOrNull { it.name == key } ?: STRAIGHT_OUT
    }
}

/**
 * 对局页顶部那一行要用的**规则短码**，如 `SI-DO`（直入双倍出）。
 *
 * 非 X01 玩法返回空串：那一格本来就只关心「进镖 / 出镖怎么算」，
 * 而写死一个 `X01` 之外的标签是撒谎 —— 空着，让调用方去省略这一项。
 */
fun MatchConfig.rulesCode(): String = when (matchType) {
    MatchType.X01 -> "${inMode.code}-${outMode.code}"
    else -> ""
}
