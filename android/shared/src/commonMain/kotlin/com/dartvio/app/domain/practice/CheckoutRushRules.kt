package com.dartvio.app.domain.practice

import com.dartvio.app.domain.model.BullMode
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.DartSource
import com.dartvio.app.domain.model.InMode
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.domain.model.TurnResult
import com.dartvio.app.domain.model.X01LegState
import com.dartvio.app.domain.model.X01PlayerState
import com.dartvio.app.domain.rules.X01Rules
import kotlin.random.Random

/**
 * 极速结镖（Checkout Rush）的规则层。PRD M11 新增子模式。
 *
 * ## 它不管什么
 *
 * **这里没有任何结镖/Bust/靶盘判定规则**：所有实际结果都由 [X01Rules.applyTurn] 逐镖裁定，
 * 本文件只负责①选题 ②把 X01 的结算结果翻译成极速结镖的呈现口径（用镖数、Bust 原因）。
 * 任何「类似的独立实现」都会被 4 条红线之一击中（注意力稀缺）：不多写第二套 X01。
 *
 * ## 场景语义
 *
 * 极速结镖模拟的是「比赛已经打到待结镖分数」的那一刻，**不是从 501 分重新开局** ——
 * 因此 [rushLegState] 固定 `inMode = STRAIGHT_IN`（等价于已开分）：
 * 第一镖不会被要求去打 Double In 的开局分。
 */
// --------------------------------------------------------------------------------------
// 枚举
// --------------------------------------------------------------------------------------

/**
 * 单题结果。
 *
 * - [CHECKOUT]：合法结镖；
 * - [BUST]：爆分（超分 / 留下 1 分 / 归零但最后一镖不是 Double）；
 * - [NOT_FINISHED]：三镖投完仍未结镖，也没爆分；
 * - [SKIPPED]：用户主动跳过 —— **不进入成功率分母**（PRD §十.4）；
 * - [ABORTED]：进程被杀 / 异常中断 —— 不形成成绩（PRD §六.6）。
 */
enum class RushResult(val label: String) {
    CHECKOUT("结镖成功"),
    BUST("爆分"),
    NOT_FINISHED("未完成"),
    SKIPPED("跳过"),
    ABORTED("中断"),
    ;

    val isSuccess: Boolean get() = this == CHECKOUT

    /**
     * 是否进入成功率分母：`SKIPPED` / `ABORTED` 都不进。
     *
     * 跳过记的是次数而不是失败 —— 把它算进分母会让「不想做的题不做」变成一种惩罚策略。
     */
    val countsTowardsSuccessRate: Boolean get() = this == CHECKOUT || this == BUST || this == NOT_FINISHED
}

/** Bust 原因（存储与展示共用一套字符串），`null` = 未爆分。 */
enum class BustReason(val label: String, val code: String) {
    /** 超过剩余分。 */
    OVER("超过剩余分", "OVER"),

    /** Double Out 下留下 1 分（D1=2、T1=3 都收不掉）。 */
    LEFT_1("留下 1 分", "LEFT_1"),

    /** 归零但最后一镖不是 Double / Bull。 */
    NOT_DOUBLE("最后一镖不是双区", "NOT_DOUBLE"),
    ;

    companion object {
        fun from(code: String): BustReason? = entries.firstOrNull { it.code == code }
    }
}

/** 难度档。classify 规则见 [CheckoutTargetFactory.difficultyOf]。 */
enum class RushDifficulty(val label: String, val canBeDrawn: Boolean = true) {
    /** 入门：存在 1 镖路线，或 ≤60 分的两镖路线。 */
    ROOKIE("入门"),

    /** 进阶：至少 2 镖（>60 分），或 ≤60 分的三镖路线。 */
    ADVANCED("进阶"),

    /** 挑战：至少 3 镖且 >60 分（高分、易 Bust 路线）。 */
    CHALLENGE("挑战"),

    /** 混合：按产品权重抽取上面三档。 */
    MIXED("混合", canBeDrawn = false),
}

// --------------------------------------------------------------------------------------
// 选题
// --------------------------------------------------------------------------------------

/** 一道题：目标分 + 该目标可用的具体镖序路线。 */
data class CheckoutTarget(
    val score: Int,
    val routes: List<List<Dart>>,
) {
    /** 首选路线（路线表已按「镖数少 → 多、最高单镖分降序」排好）。 */
    val preferredRoute: List<Dart> get() = routes.firstOrNull() ?: emptyList()

    /** 替代路线（除首选以外的前几条）。 */
    val alternates: List<List<Dart>> get() = routes.drop(1)
}

/**
 * 目标生成器。
 *
 * **合法性的唯一判据是路线表本身**（[X01Rules.checkoutRoutes] 非空），而不是一张手写分数表：
 * PRD §四.4 禁止 M11/UI 维护第二份「不可结镖分数表」。
 *
 * 由此也修掉了历史实现 ([CheckoutSolver.generateTarget]) 里硬编码排除 `1..19` 的问题 ——
 * Double Out 下 1 确实结不掉，但 **2 = D1、3 = S1+D1** 都是合法目标，不能因为 1 就把 2..19 一锅端。
 */
object CheckoutTargetFactory {

    /** 结镖练习的目标区间上限：>170 不可能在 3 镖内完成。 */
    const val MAX_3_DART_SCORE = 170

    /**
     * 全部「至少存在一条 1..3 镖合法路线」的目标分数（有序）。
     *
     * 注意下限是 **2**：Double Out 下 1 分不可结镖，而 1 本来也不会出现在路线表里
     * （路线表只收录以 Double / Bull 收尾的组合，最小和是 D1 = 2）。
     */
    fun candidates(): List<Int> = (2..MAX_3_DART_SCORE).filter { X01Rules.hasCheckoutRoute(it) }

    /**
     * 难度判定：**最短路线的镖数** + 分数高低。
     *
     * - 1 镖 ⇒ 入门；
     * - 2 镖 ⇒ ≤60 分入门（题库里这档需要玩家记住，但技术动作最简单），>60 分进阶；
     * - 3 镖 ⇒ ≤60 分进阶，>60 分挑战（优先高分、易 Bust 路线）。
     *
     * @return 没有合法路线时返回 `null`（调用方应永远先过滤掉这类分数）。
     */
    fun difficultyOf(target: Int): RushDifficulty? {
        val minDarts = X01Rules.checkoutRoutes(target).minOfOrNull { it.size } ?: return null
        return when {
            minDarts == 1 -> RushDifficulty.ROOKIE
            minDarts == 2 -> if (target <= 60) RushDifficulty.ROOKIE else RushDifficulty.ADVANCED
            else -> if (target <= 60) RushDifficulty.ADVANCED else RushDifficulty.CHALLENGE
        }
    }

    /**
     * 混合档权重：入门 40% / 进阶 35% / 挑战 25%。
     *
     * 写在事实旁边而不是 UI 里 —— 权重是**产品配置**，将来调优不应该去改做题页。
     */
    private val MIXED_WEIGHTS: List<Pair<RushDifficulty, Int>> = listOf(
        RushDifficulty.ROOKIE to 40,
        RushDifficulty.ADVANCED to 35,
        RushDifficulty.CHALLENGE to 25,
    )

    /**
     * 出一道题。
     *
     * @param difficulty 难度档；[RushDifficulty.MIXED] 走权重抽取。
     * @param recent 本次会话已经出过的分数：10 题挑战里**尽量不重复**；
     *               抽不出新题（该题池太小）时才允许重复，而不是排除合法目标。
     */
    fun nextTarget(
        difficulty: RushDifficulty = RushDifficulty.MIXED,
        recent: Set<Int> = emptySet(),
        random: Random = Random.Default
    ): CheckoutTarget {
        val picked = if (difficulty == RushDifficulty.MIXED) {
            drawDifficulty(random)
        } else {
            difficulty
        }
        return targetOf(picked, recent, random)
    }

    private fun drawDifficulty(random: Random): RushDifficulty {
        val total = MIXED_WEIGHTS.sumOf { it.second }
        var roll = random.nextInt(total)
        MIXED_WEIGHTS.forEach { (difficulty, weight) ->
            roll -= weight
            if (roll < 0) return difficulty
        }
        return RushDifficulty.ROOKIE
    }

    private fun targetOf(
        difficulty: RushDifficulty,
        recent: Set<Int>,
        random: Random
    ): CheckoutTarget {
        val pool = candidates().filter { difficultyOf(it) == difficulty }
        require(pool.isNotEmpty()) { "难度 ${difficulty.label} 没有可用目标（路线表为空？）" }
        val fresh = pool.filter { it !in recent }
        val score = (fresh.ifEmpty { pool }).random(random)
        return CheckoutTarget(score, X01Rules.checkoutRoutes(score))
    }
}

// --------------------------------------------------------------------------------------
// 结算
// --------------------------------------------------------------------------------------

/** 一题的结算结果。 */
data class CheckoutRushOutcome(
    val target: Int,
    /** 实际参与结算的镖（至多 [CheckoutRushRules.MAX_DARTS] 支，顺序即投掷顺序）。 */
    val darts: List<Dart>,
    val result: RushResult,
    /** 结算后剩余分：爆分时按 X01 语义**回滚到目标分**。 */
    val remainingAfter: Int,
    val bustReason: BustReason?,
    /** 实际用镖数：结镖时为结镖那一镖的序号，爆分时为爆分那一镖的序号，未完成时为 3（或实际录入数）。 */
    val dartsUsed: Int,
)

object CheckoutRushRules {

    /** 一题最多接受的镖数。第 1/2 镖合法归零即终局，后面的镖不再收（PRD §三.9）。 */
    const val MAX_DARTS = 3

    /**
     * MVP 允许的录入方式（PRD §三.1）：`input_source` 只允许**逐镖键盘**与**靶面点选**。
     *
     * 白名单而不是黑名单：`DartSource.QUICK_TOTAL`（快速总分）是既存枚举值，
     * 靠「随时记得排除它」迟早会被忘掉 —— 这里显式列出允许的来源，顺手挡住
     * HARDWARE_VISION / PHONE_VISION / VOICE / AI_GENERATED 这些本模式不该走的来源。
     */
    val SUPPORTED_INPUT_MODES: Set<DartSource> = setOf(DartSource.DART_BY_DART, DartSource.BOARD_TAP)

    private const val RUSH_PLAYER_ID = "checkout_rush"
    private const val RUSH_PLAYER_NAME = "练习者"

    /**
     * 单题的对局配置：**固定 Double Out + 标准 Bull + 直入**（已开分）。
     *
     * 直入不是偷懒：`inMode = STRAIGHT_IN` 让 [X01Rules] 里的「未开镖不计分 / Double In 首镖检查」
     * 整段不生效 —— 这正是 PRD §三.6 要的「等价于已经完成开分」的传递方式，**没有新增标志位**。
     */
    fun rushConfig(target: Int): MatchConfig = MatchConfig(
        targetScore = target,
        outMode = OutMode.DOUBLE_OUT,
        inMode = InMode.STRAIGHT_IN,
        bullMode = BullMode.STANDARD_25_50,
        maxRounds = 0,
    )

    /**
     * 单题的对局状态：一人、剩余分 = 目标分、`hasOpened = true`（已开分）。
     *
     * 不走 [X01Rules.newLeg]：`newLeg` 会把 `remaining` 设成 `config.targetScore`，
     * 这里语义上正好相同，但多一层回调不如直接构造清楚；`hasOpened` 显式写 true，
     * 免得将来 Config 默认值一改就悄悄变成「要打 Double In」。
     */
    fun rushLegState(target: Int): X01LegState = X01LegState(
        config = rushConfig(target),
        players = listOf(X01PlayerState(playerId = RUSH_PLAYER_ID, remaining = target, hasOpened = true)),
        currentPlayerIndex = 0
    )

    /** 仅用于「逐镖试算」的直出状态：让单镖的真实扣分暴露出来，而不触发 Double Out 的额外爆分条件。 */
    private fun straightLeg(remaining: Int): X01LegState = X01LegState(
        config = MatchConfig(
            targetScore = remaining,
            outMode = OutMode.STRAIGHT_OUT,
            inMode = InMode.STRAIGHT_IN,
            bullMode = BullMode.STANDARD_25_50,
            maxRounds = 0,
        ),
        players = listOf(X01PlayerState(playerId = RUSH_PLAYER_ID, remaining = remaining, hasOpened = true)),
        currentPlayerIndex = 0
    )

    /**
     * 裁定一题。
     *
     * 输入是**实际投出的有序镖序** —— 键盘逐镖与靶面点选都产出同一种东西，
     * 因此二者的结果不可能不一致（PRD §十一.8 由测试兜住）。
     *
     * 未投出的镖**不补 MISS**：用户投了两镖就写上两镖，空位永远不会被补成 0 分。
     */
    fun evaluate(target: Int, darts: List<Dart>): CheckoutRushOutcome {
        val effective = darts.take(MAX_DARTS)
        if (effective.isEmpty()) {
            return CheckoutRushOutcome(
                target = target,
                darts = emptyList(),
                result = RushResult.NOT_FINISHED,
                remainingAfter = target,
                bustReason = null,
                dartsUsed = 0
            )
        }

        val (_, outcome) = X01Rules.applyTurn(rushLegState(target), effective)
        val result = when {
            outcome.won -> RushResult.CHECKOUT
            outcome.result == TurnResult.BUST -> RushResult.BUST
            else -> RushResult.NOT_FINISHED
        }
        return CheckoutRushOutcome(
            target = target,
            darts = effective,
            result = result,
            remainingAfter = outcome.remainingAfter,
            bustReason = bustReasonOf(target, effective),
            dartsUsed = dartsUsedOf(target, effective)
        )
    }

    /**
     * 实际用镖数：**结镖/爆分发生在第几镖**。
     *
     * 不能直接用 `darts.size` —— 第 1 镖已经 D20 结掉 40 分时，用镖数是 1 而不是录入了几镖
     * （第 1 镖结镖后上层就不再收镖，但如果 UI 多录了一镖也不会被算进去）。
     */
    fun dartsUsedOf(target: Int, darts: List<Dart>): Int {
        val effective = darts.take(MAX_DARTS)
        if (effective.isEmpty()) return 0
        val state = rushLegState(target)
        for (i in effective.indices) {
            val (_, outcome) = X01Rules.applyTurn(state, effective.take(i + 1))
            if (outcome.won || outcome.result == TurnResult.BUST) return i + 1
        }
        return effective.size
    }

    /**
     * 爆分原因。
     *
     * **不复制 X01 的扣分 / 爆分算法**：单镖的真实得分仍然由 [X01Rules.applyTurn] 算
     * （含 Bull Mode 换算），这里只借用 [OutMode.requiresMultiplierFinish] / [OutMode.finishes]
     * 这两个**已有谓词**做分类。
     *
     * 之所以要在「直出」状态下逐镖试算：Double Out 下一旦爆分，X01 会把 `scored` 清零、
     * 剩余分回滚，**看不出到底是超了 10 分、还是正好卡在 1 分、还是归零但没收双**。
     * 换成直出后，唯一的爆分条件只剩「扣成负数」 ⇒ 识别出 OVER；余下的两种原因
     * 用 Double Out 自己的谓词对着「扣完还剩多少」判定：
     * `after == 1` ⇒ 留下 1 分；`after == 0 且不是双区/牛眼` ⇒ 没收双。
     */
    fun bustReasonOf(target: Int, darts: List<Dart>): BustReason? {
        val effective = darts.take(MAX_DARTS)
        var remaining = target
        for (dart in effective) {
            val (_, single) = X01Rules.applyTurn(straightLeg(remaining), listOf(dart))
            if (single.result == TurnResult.BUST) return BustReason.OVER // 直出只剩「超分」这一种爆分
            val after = remaining - single.scored
            when {
                OutMode.DOUBLE_OUT.requiresMultiplierFinish && after == 1 -> return BustReason.LEFT_1
                after == 0 && !OutMode.DOUBLE_OUT.finishes(dart) -> return BustReason.NOT_DOUBLE
                after == 0 -> return null // 合法结镖，不由本函数负责
                else -> remaining = after
            }
        }
        return null
    }

    /** 该镖序是否已经分出胜负（结镖或爆分）—— 第 1/2 镖结镖后不得再接受飞镖。 */
    fun isTerminal(target: Int, darts: List<Dart>): Boolean {
        val effective = darts.take(MAX_DARTS)
        if (effective.isEmpty()) return false
        val (_, outcome) = X01Rules.applyTurn(rushLegState(target), effective)
        return outcome.won || outcome.result == TurnResult.BUST
    }
}
