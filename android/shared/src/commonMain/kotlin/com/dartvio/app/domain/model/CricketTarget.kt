package com.dartvio.app.domain.model

import kotlin.random.Random

/**
 * Cricket 目标位标识（M2 §4.8⑦ 二期 2A）。
 *
 * ## 为什么要有这一层
 * 一期把目标位写成 `Int`（`CricketMarks.marks: Map<Int, Int>`、`MatchConfig.cricketNumbers`），
 * 于是「目标位是什么」和「它恰好是几号」被绑死：2C 的 tactics 变体会有「双倍档 / 三倍档」
 * 这种**不是某个数字**的目标位，届时所有 `Int` 都要改一遍，而且改漏的地方不会报错 ——
 * 只会静默地少算一个分区。这里先把标识层立起来，二期只加变体、不再动结构。
 *
 * ## ⚠️ token 是冻结契约
 * [token] 是**持久化与协议**的稳定取值（`match_players.firstClosedCsv`、`match_records.targetSetCsv`、
 * 线上 `cricket_targets` 数组）。一经发布**不得修改**，改它等于让历史数据与老客户端一起失效。
 *
 * - 数字分区 → 数字本身（`"20"` `"19"` `"18"` `"17"` `"16"` `"15"` `"25"`），与一期逐字节相同
 * - 类别档 → 前缀（`"D"` / `"T"`）
 * - 复合 token（`"D20"` / `"T20"`）**不识别**，按未知跳过；2C 不引入复合 token
 *   （Q7：tactics / random 不新增 `CricketVariant` 取值，玩法由目标集承载，本层不变）
 *
 * 特别注意 **Bull 的 token 是 `"25"`** 而不是 `"BULL"`：一期落库写的就是 `25`，
 * 保持不动才能让 `firstClosedCsv` 的历史数据继续可读。「Bull」是展示口径（见 [display] 与
 * 各界面自己的板面标签），与 token 是两件事。
 */
sealed interface CricketTarget {

    /** 持久化 / 协议用的稳定 token（**冻结，不得改**，见类型注释）。 */
    val token: String

    /** 数字分区：板面上的一个具体号位（含 Bull = `Number(25)`）。 */
    data class Number(val value: Int) : CricketTarget {
        override val token: String get() = value.toString()
    }

    /** 类别档：2C tactics 启用（如「任意双倍」）；2A 的目标集里不会出现。 */
    data class Category(val kind: TargetCategory) : CricketTarget {
        override val token: String get() = kind.prefix
    }

    /** 展示名（数据层默认口径；各界面可再套自己的板面标签，如 Bull 写 `BULL`）。 */
    val display: String
        get() = when (this) {
            is Number -> value.toString()
            is Category -> kind.label
        }

    companion object {
        /** Bull 的数字号位。一期代码里的 `BULL = 25` 就是它。 */
        val BULL: Number = Number(25)

        /**
         * 默认目标集 = 标准 7 分区，**顺序与一期逐字节相同**：20→15 递减，Bull 收尾。
         *
         * 顺序是有意义的（UI 行序、`targetSetCsv` 空串语义），不要改成升序。
         */
        val DEFAULT_TARGETS: List<CricketTarget> =
            listOf(20, 19, 18, 17, 16, 15, 25).map { Number(it) }

        /**
         * Tactics 目标集（二期 2C，M2 §4.9.1）：7 个数字分区 + 双倍档 + 三倍档，共 **9** 档。
         *
         * **顺序固定** = 数字（20→15）→ 双倍档 → 三倍档 → Bull。这个顺序同时是三处的口径：
         * UI 行序、`targetSetCsv` 编码顺序（`"20,19,18,17,16,15,D,T,25"`）、`hasClosedAll` 遍历顺序。
         *
         * ⚠️ **Bull 排在最后**，不能写成 `DEFAULT_TARGETS + listOf(D, T)` —— 那样 Bull 会插到
         * 两个类别档前面，行序与落盘串都会与冻结文本不符。20→13 / 20→12 档本期不做，
         * 但目标是**参数**：规则层不得把这 9 个写死。
         */
        val TACTICS_TARGETS: List<CricketTarget> =
            listOf(20, 19, 18, 17, 16, 15).map { Number(it) } +
                listOf(Category(TargetCategory.DOUBLES), Category(TargetCategory.TRIPLES)) +
                listOf(BULL)

        /** Random 变体每局抽取的目标个数（M2 §4.9.4）。 */
        const val RANDOM_TARGET_COUNT = 5

        /**
         * Random 变体的目标集抽取（二期 2C，M2 §4.9.4 / §4.9.5）。
         *
         * 池 = 标准 7 分区（**不引入 1–14**），固定抽 [count] 个，**不强制包含任何目标**
         * （含 Bull 也不强制），抽完**按默认顺序规范化**（UI 行序 / 落盘串需要稳定顺序，
         * 否则同一场抽签会因为顺序不同写出不同的 `targetSetCsv`）。
         *
         * 不变式（§4.9.5）：
         * - ① **局级常量** —— 只在开局抽一次，局中不重抽（调用点保证）；
         * - ② 不得因关闭而重抽（同上）；
         * - ③ **非空保证** —— `count <= 0`、或抽签异常导致空集时回落 [DEFAULT_TARGETS]；
         *    「空目标集」会让 `hasClosedAll` 对任何玩家立刻成立，即开局瞬间结束；
         * - ④ 可达性 —— 抽出来的都是数字分区，而 AI 的选点只在数字目标里做，天然可达；
         * - ⑤ 规模 ≥ 4 —— 调用点用 `count = RANDOM_TARGET_COUNT`（5），满足。
         */
        fun pickRandomTargets(
            random: Random,
            count: Int = RANDOM_TARGET_COUNT,
        ): List<CricketTarget> {
            if (count <= 0) return DEFAULT_TARGETS
            val picked = DEFAULT_TARGETS.shuffled(random).take(count).toSet()
            // 规范化回默认顺序；`filter` 而非 `sortedBy`，避免把顺序口径再写一份。
            return DEFAULT_TARGETS.filter { it in picked }.ifEmpty { DEFAULT_TARGETS }
        }

        /**
         * 解析 token。
         *
         * 规则（2A 冻结，见提示词 §2.2）：
         * - 去空白、转大写后 `toIntOrNull()` 成功 ⇒ [Number]
         * - 否则**整串等于** `D` / `T` ⇒ [Category]
         * - 其余一律 `null`（含 `"D20"` / `"T20"` 这类复合 token，以及空串、未知前缀）
         *
         * 用「整串等于」而不是「前缀匹配」是刻意的：`"D20"` 若被当成 `DOUBLES`，
         * 2A 就会把一个**含义尚未冻结**的 token 静默收成已定义语义 ——
         * 宁可跳过（数据少一条）也不要算错（数据看着正常但含义是错的）。
         */
        fun parse(raw: String?): CricketTarget? {
            val token = raw?.trim()?.uppercase().orEmpty()
            if (token.isEmpty()) return null
            token.toIntOrNull()?.let { return Number(it) }
            return TargetCategory.entries.firstOrNull { it.prefix == token }?.let { Category(it) }
        }
    }
}

/** 类别档前缀（2C tactics 的识别位）。 */
enum class TargetCategory(val prefix: String, val label: String) {
    DOUBLES("D", "双倍档"),
    TRIPLES("T", "三倍档"),
}

/**
 * 目标集 → CSV（`match_records.targetSetCsv`）。
 *
 * **默认目标集写空串**：变体上线前的历史行存的就是 `''`，新的 standard 行必须逐字节一致，
 * 否则「旧行 == 新行」这个不变式在数据里就不成立，任何按列值分叉的统计都会踩到。
 */
fun encodeCricketTargets(targets: List<CricketTarget>): String =
    if (targets == CricketTarget.DEFAULT_TARGETS) "" else targets.joinToString(",") { it.token }

/**
 * CSV → 目标集（`match_records.targetSetCsv` 的读取口；`match_players.firstClosedCsv`
 * 存的是「首关分区」流水，不需要回落语义，走 [CricketTarget.parse] 逐条跳过）。
 *
 * 空串、缺列、以及**全部 token 都无法识别**一律回落 [CricketTarget.DEFAULT_TARGETS]：
 * - 空串是历史行的正常形态，「语义 = 默认 7 分区」（提示词 §4.1）；
 * - 一个都认不出来时若返回空集，`hasClosedAll` 会对任何玩家立刻成立 ——
 *   「关满即胜」会在开局瞬间判定，比少算一条严重得多。
 *
 * 单个无法识别的 token 跳过即可（不抛异常），所以部分可识别时会保留可识别的部分。
 */
fun parseCricketTargets(raw: String?): List<CricketTarget> =
    raw.orEmpty()
        .split(',')
        .mapNotNull { CricketTarget.parse(it) }
        .ifEmpty { CricketTarget.DEFAULT_TARGETS }
