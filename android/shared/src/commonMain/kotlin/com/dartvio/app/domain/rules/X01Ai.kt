package com.dartvio.app.domain.rules

import com.dartvio.app.domain.model.AiDifficulty
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.InMode
import com.dartvio.app.domain.model.MatchConfig
import kotlin.random.Random

/**
 * X01 AI 出镖引擎（简化版，M4）。
 *
 * 设计目标：让 AI 能"自动投掷"推动对局，且强度随难度可感知。
 * 做法：
 *  1. 根据剩余分选择目标（能收尾则优先收尾，否则优先 T20）。
 *  2. 按难度取命中概率 p：命中则给出理想镖，未命中则在相邻格里随机。
 *  3. 每回合生成 1..3 支镖；一旦该镖已获胜/爆分，后续镖不再生成。
 *
 * 规则一致性：三组规则档位（开局 [InMode] / 结束 [com.dartvio.app.domain.model.OutMode] /
 * 牛眼 [com.dartvio.app.domain.model.BullMode]）都从 [MatchConfig] 读取，**不再各自判布尔** ——
 * 否则「引擎按大师出跑、AI 按双倍出瞄」这类分叉会静默发生。
 * 生成结果仍交给 [X01Rules.applyTurn] 结算，AI 不自行修改剩余分，不会绕过规则。
 */
object X01Ai {

    /**
     * 一镖收尾候选，按真实选手的偏好排序：
     * 内牛眼 → 外牛眼 → 各号位双倍（20→1）→ 各号位三倍（20→1）→ 各号位单倍（20→1）。
     *
     * 顺序即优先级：大师出同时有双倍解与三倍解时先取双倍（更稳，
     * 例如剩 40 时取 D20 而不是 T13.33——三倍解只在双倍解不存在时才会被选中）。
     */
    private val FINISH_CANDIDATES: List<Dart> = buildList {
        add(Dart.INNER_BULL)
        add(Dart.OUTER_BULL)
        (20 downTo 1).forEach { add(Dart.double(it)) }
        (20 downTo 1).forEach { add(Dart.triple(it)) }
        (20 downTo 1).forEach { add(Dart.single(it)) }
    }

    /** 减分候选（不用于收尾），按「单镖分越高越靠前」排序。 */
    private val REDUCTION_CANDIDATES: List<Dart> = buildList {
        (20 downTo 1).forEach { add(Dart.triple(it)) }
        (20 downTo 1).forEach { add(Dart.double(it)) }
        (20 downTo 1).forEach { add(Dart.single(it)) }
        add(Dart.OUTER_BULL)
    }

    /**
     * 生成 AI 的一个完整回合（最多 3 镖）。
     *
     * @param remaining 回合开始时剩余分
     * @param profile 本回合强度画像（含自适应后的 PPR）
     * @param config 比赛配置（决定开局 / 结束 / 牛眼三组规则）
     * @param hasOpened 本回合开始时该 AI 是否已开镖（双倍入 / 大师入下决定这一镖怎么瞄）
     * @param random 随机源（便于测试注入固定种子）
     */
    fun generateTurn(
        remaining: Int,
        profile: AiProfile,
        config: MatchConfig,
        hasOpened: Boolean = true,
        random: Random = Random.Default
    ): List<Dart> {
        // 180 率：仅当剩余分 > 180（本回合不可能直接收尾）且**已开镖**时，
        // 按档位概率打出一个极限回合（T20×3）。未开镖时打三支三倍区毫无意义。
        if (hasOpened && remaining > 180 && random.nextDouble() < profile.percent180) {
            return listOf(Dart.triple(20), Dart.triple(20), Dart.triple(20))
        }

        val darts = mutableListOf<Dart>()
        var remainingNow = remaining
        var openedNow = hasOpened

        repeat(3) {
            val mustOpen = !openedNow
            val dart = pickDart(remainingNow, mustOpen, profile, config, random)
            darts.add(dart)

            if (mustOpen && !config.inMode.opens(dart)) {
                // 未开镖且这一镖不满足开镖条件：整支不计 —— 剩余分与开镖状态都不变，
                // 但镖已经投出（与 [X01Rules.applyTurn] 里 simulateTurn 的口径一致）。
                return@repeat
            }
            openedNow = true

            val after = remainingNow - config.bullMode.scoreOf(dart)
            // 提前终止：已爆分或已获胜，则本回合结束。
            val busted = after < 0 ||
                (config.outMode.requiresMultiplierFinish && after == 1) ||
                (after == 0 && !config.outMode.finishes(dart))
            if (busted) return darts
            if (after == 0) return darts

            remainingNow = after
        }
        return darts
    }

    /** 兼容重载：按档位中值构建画像（自适应关闭 / 旧调用方）。 */
    fun generateTurn(
        remaining: Int,
        difficulty: AiDifficulty,
        config: MatchConfig,
        random: Random = Random.Default
    ): List<Dart> = generateTurn(remaining, AiProfile.of(difficulty), config, random = random)

    /** 选择一支镖。 */
    private fun pickDart(
        remaining: Int,
        mustOpen: Boolean,
        profile: AiProfile,
        config: MatchConfig,
        random: Random
    ): Dart {
        val ideal = if (mustOpen) openingDart(config) else idealDart(remaining, config)
        // 收尾镖（可直接打到 0 且满足结束规则）使用档位结镖率；普通镖使用由当前 PPR 换算的命中率。
        val isFinish = !mustOpen &&
            config.bullMode.scoreOf(ideal) == remaining &&
            config.outMode.finishes(ideal)
        val hitChance = if (isFinish) profile.checkoutRate else profile.hitChance

        return if (random.nextDouble() < hitChance) {
            ideal
        } else {
            // 未命中：在理想目标附近随机偏一档（更真实）。
            missAround(ideal, remaining, config, random)
        }
    }

    /**
     * 开镖镖：双倍入打 D20，大师入打 T20（两个都满足各自的开镖条件，且减分最多）。
     * 直入不需要开镖，理论上不会走到这里，兜底同样取 T20。
     */
    private fun openingDart(config: MatchConfig): Dart =
        if (config.inMode == InMode.DOUBLE_IN) Dart.double(20) else Dart.triple(20)

    /** 理想镖：能收尾则收尾，否则在「不爆分」的前提下尽可能减分。 */
    private fun idealDart(remaining: Int, config: MatchConfig): Dart {
        oneDartFinish(remaining, config)?.let { return it }

        if (config.outMode.requiresMultiplierFinish) {
            // 两镖收尾：41..60 先打掉尾数留 40 以内偶数；奇数剩余分先减 1 调奇偶。
            if (remaining in 41..60) return Dart.single(remaining - 40)
            if (remaining in 2..40) return Dart.single(remaining - 1)
        } else {
            if (remaining == 25 && config.bullMode.scoreOf(Dart.OUTER_BULL) == 25) {
                return Dart.OUTER_BULL
            }
        }
        return reductionDart(remaining, config)
    }

    /** 一镖收尾：分数刚好等于剩余分、且满足结束规则（双倍出只认双倍/牛眼，大师出另认三倍）。 */
    private fun oneDartFinish(remaining: Int, config: MatchConfig): Dart? =
        FINISH_CANDIDATES.firstOrNull {
            config.bullMode.scoreOf(it) == remaining && config.outMode.finishes(it)
        }

    /**
     * 减分镖：打掉尽量多的分，且**绝不主动爆分**。
     *
     * 倍区收尾规则下还要避免把剩余分打到 1：1 分是死局（D1=2、T1=3 都收不掉，下一镖必爆），
     * 所以可打的最大分收敛到 `remaining - 2`。
     */
    private fun reductionDart(remaining: Int, config: MatchConfig): Dart {
        val maxSafe = if (config.outMode.requiresMultiplierFinish) remaining - 2 else remaining
        if (maxSafe <= 0) return Dart.MISS
        return REDUCTION_CANDIDATES.firstOrNull { config.bullMode.scoreOf(it) <= maxSafe }
            ?: Dart.MISS
    }

    /** 未命中时，围绕理想目标生成一个合理的偏差镖。 */
    private fun missAround(
        ideal: Dart,
        remaining: Int,
        config: MatchConfig,
        random: Random
    ): Dart {
        // 单镖不超过剩余分（避免直接爆分太频繁）——保留少量爆分以贴近真实。
        val candidates = if (ideal.isBull) {
            listOf(Dart.single(20), Dart.single(19), Dart.single(18), Dart.OUTER_BULL)
        } else {
            val base = ideal.number.coerceIn(1, 20)
            val around = listOf(base - 1, base + 1, base - 2, base + 2, 20, 19, 18, 1)
                .filter { it in 1..20 }
            val mult = when {
                ideal.multiplier == 3 -> listOf(3, 1, 1, 2)
                ideal.multiplier == 2 -> listOf(2, 1, 1)
                else -> listOf(1, 1, 2)
            }
            around.flatMap { n -> mult.map { m -> Dart(n, m) } }
        }

        // 优先选择不超过剩余分的候选（若总是不行则允许爆分）。
        val safe = candidates.filter { config.bullMode.scoreOf(it) <= remaining }
        val pool = safe.ifEmpty { candidates }
        return pool[random.nextInt(pool.size)]
    }

    /** 估算剩余分能否一镖收尾（供 UI 提示，预留）。 */
    fun canCheckout(remaining: Int, config: MatchConfig): Boolean =
        oneDartFinish(remaining, config) != null
}
