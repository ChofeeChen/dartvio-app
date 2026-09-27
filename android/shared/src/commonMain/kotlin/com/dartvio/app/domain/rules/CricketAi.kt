package com.dartvio.app.domain.rules

import com.dartvio.app.domain.model.AiDifficulty
import com.dartvio.app.domain.model.ClaimedDart
import com.dartvio.app.domain.model.CricketLegState
import com.dartvio.app.domain.model.CricketPlayerState
import com.dartvio.app.domain.model.CricketTarget
import com.dartvio.app.domain.model.CricketVariant
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.DartClaim
import com.dartvio.app.domain.model.ScoreSink
import kotlin.random.Random

/**
 * Cricket AI 出镖引擎（简化版）。
 *
 * 策略（M4 §6.6，按变体分派）：
 *  1. 关闭优先 —— 优先关闭自己尚未关闭的分区（从高分开始），三种变体一致。
 *  2. standard / cut_throat：本方全关后打对手尚未关闭的最高分区（standard 为得分，
 *     cut_throat 该镖不进自己账，等同不加分）。
 *  3. no_score：只做关闭优先，不做任何得分目标选择。
 *  4. 按难度取命中概率：命中则给出理想镖，未命中则在其他有效分数中随机。
 *
 * ⛔ 明确不做「故意送分 / 主动避分」的负向策略（§6.6）：AI 行为必须可解释，
 * 否则玩家会把「它为什么往我这儿送分」当成缺陷。
 *
 * 生成结果交给 [CricketRules.applyTurn] 结算，规则一致。
 *
 * 二期 2C（M2 §4.9.2⑥ / §4.9.9）追加两件事：
 * - **归属裁决**（[adjudicate]）：出镖后决定这一镖记给「数字」还是「类别档」；
 * - **Overkill 感知**：大幅领先（≥200）时不再去刷「必然被抑制」的得分号位。
 */
object CricketAi {

    /** Bull 分区号；Bull 打不出三倍，命中时需降为双倍。 */
    private const val BULL = 25

    /** Bull 目标位（与 [BULL] 同值，集中一处免得散落魔数）。 */
    private val BULL_TARGET: CricketTarget.Number = CricketTarget.BULL

    /** 命中时优先打三倍区（效率最高）。 */
    private const val TRIPLE = 3

    /** Overkill 的领先阈值，与 [CricketRules] 内保持一致（§4.9.9，固定值不可配）。 */
    private const val OVERKILL_LEAD_THRESHOLD = 200

    /** 生成 AI 的一个完整回合（最多 3 镖）。 */
    fun generateTurn(
        leg: CricketLegState,
        profile: AiProfile,
        random: Random = Random.Default
    ): List<ClaimedDart> {
        val targets = leg.config.cricketTargets
        val numbers = numericTargets(targets)
        val variant = leg.config.cricketVariant
        val hitChance = profile.hitChance

        val darts = mutableListOf<ClaimedDart>()
        repeat(3) {
            val target = pickTarget(leg, numbers, variant)
            val dart = if (random.nextDouble() < hitChance) {
                // 命中：三倍区效率最高；Bull 无三倍区，降为双倍（内牛）。
                Dart(target.value, if (isBull(target)) 2 else TRIPLE)
            } else {
                // 未命中：在有效分数中随机
                val alt = missAlternatives(numbers).random(random)
                val mult = listOf(1, 1, 2, 3).random(random)
                Dart(alt.value, mult)
            }
            // 裁决按**当前**板面判定：本回合前几镖改动的标记必须被看到，
            // 否则「第 1 镖关上 19、第 2 镖的 T19 该改记三倍档」会被判成数字。
            val me = leg.players[leg.currentPlayerIndex]
            darts.add(ClaimedDart(dart, adjudicate(dart, me, targets)))
        }
        // 若该玩家本轮已可获胜，无需缩短（规则层会立即判定）。
        return darts
    }

    /**
     * 目标集里的数字号位。
     *
     * AI 最终必须产出 [Dart]（板面号位 + 倍率），所以选点只在数字目标里做 ——
     * `filterIsInstance` 天然把类别档排除在外（§4.9.5④：数字目标必须人人可达）。
     */
    private fun numericTargets(targets: List<CricketTarget>): List<CricketTarget.Number> =
        targets.filterIsInstance<CricketTarget.Number>()

    private fun isBull(target: CricketTarget.Number): Boolean = target.value == BULL

    /**
     * 归属裁决（M2 §4.9.2⑥，二期 2C）：
     * `某类别档尚未关闭 && (本次对应的数字目标已关闭 || 本方全部数字目标已关闭) ⇒ CATEGORY，否则 NUMBER`。
     *
     * 「合格 D/T」的判据**复用规则层**（[CricketRules.categoryFor]），不在这里重写一份 ——
     * 两处各写一份正是「同一规则两份跑偏」的来源。
     */
    private fun adjudicate(
        dart: Dart,
        me: CricketPlayerState,
        targets: List<CricketTarget>,
    ): DartClaim {
        val category = CricketRules.categoryFor(dart) ?: return DartClaim.NUMBER
        if (category !in targets) return DartClaim.NUMBER
        if (me.marks.isClosed(category)) return DartClaim.NUMBER

        // 「对应数字已关」或「本方数字目标全关」时才值得把这一镖转给类别档（拿 1 个有效标记）。
        val numberTarget = CricketTarget.Number(dart.number)
        val numbersAllClosed = targets
            .filterIsInstance<CricketTarget.Number>()
            .all { me.marks.isClosed(it) }
        return if (me.marks.isClosed(numberTarget) || numbersAllClosed) {
            DartClaim.CATEGORY
        } else {
            DartClaim.NUMBER
        }
    }

    /**
     * 选择目标位（M4 §6.6 按变体分派）。
     *
     * ★兜底值必须取自当前有效目标集，**不得硬编码 20**（§6.6 缺陷修复）：
     * 随机局 / 目标集被裁剪的变体局里 20 可能非法，硬编码会直接产出无效镖。
     * 这也正是 §2A 把 `Int` 换成 [CricketTarget] 后**更难写错**的原因：
     * 目标集里有什么，是这里唯一能看到的东西。
     */
    private fun pickTarget(
        leg: CricketLegState,
        numbers: List<CricketTarget.Number>,
        variant: CricketVariant
    ): CricketTarget.Number {
        val playerIndex = leg.currentPlayerIndex
        val me = leg.players[playerIndex]

        // 关闭优先：三种变体一致。
        val mine = numbers
            .filter { !isBull(it) && !me.marks.isClosed(it) }
            .maxByOrNull { it.value }
        if (mine != null) return mine

        // no_score：关满即胜，此后不再追求任何得分目标，只需回一个合法落点。
        if (variant == CricketVariant.NO_SCORE) return fallbackTarget(numbers)

        // ★Overkill 感知（§8.6.3）：自己大幅领先时，「对手未关的号位」打进去也**必然被抑制**
        //   （得 0 分），再去刷它就是「产出必然被抑制的选点」。此时退回合法落点，
        //   与本 AI「优先关门」的既有策略同向，不引入任何新的负向行为。
        if (overkillSuppressesScoring(leg, me)) return fallbackTarget(numbers)

        // standard / cut_throat：对手尚未关闭的最高分区。
        val opponents = leg.players.filterIndexed { i, _ -> i != playerIndex }
        val oppOpen = numbers
            .filter { n -> opponents.any { !it.marks.isClosed(n) } && !isBull(n) }
            .maxByOrNull { it.value }
        return oppOpen ?: fallbackTarget(numbers)
    }

    /**
     * 现在得分是否会被 Overkill 全部压掉。
     *
     * 与 [CricketRules.evaluateDart] 的触发条件逐条对齐（开关 / 归属 / 领先 ≥200），
     * 但**只用于选点倾向**：真正的压分仍只发生在规则层那一处，这里判错也不会算错分。
     */
    private fun overkillSuppressesScoring(leg: CricketLegState, me: CricketPlayerState): Boolean {
        val config = leg.config
        if (!config.overkillEnabled) return false
        if (config.cricketVariant.scoreSink != ScoreSink.SELF) return false
        val opponents = leg.players.filter { it.playerId != me.playerId }
        if (opponents.isEmpty()) return false
        return me.score - opponents.maxOf { it.score } >= OVERKILL_LEAD_THRESHOLD
    }

    /**
     * 兜底目标：目标集内最大的非 Bull 分区（Bull 打不出三倍，不适合当"标准靶"）；
     * 目标集只剩 Bull 时退回 Bull 本身。任何情况下都不返回目标集之外的值。
     */
    private fun fallbackTarget(numbers: List<CricketTarget.Number>): CricketTarget.Number =
        numbers.filter { !isBull(it) }.maxByOrNull { it.value }
            ?: numbers.maxByOrNull { it.value }
            ?: BULL_TARGET

    /**
     * 未命中时的随机落点池：排除 Bull（打不出三倍区，随机倍率下会产生无效镖）；
     * 目标集只剩 Bull 时退回全集，避免 `random()` 抛空集合异常。
     */
    private fun missAlternatives(numbers: List<CricketTarget.Number>): List<CricketTarget.Number> =
        numbers.filter { !isBull(it) }.ifEmpty { numbers }

    /** 兼容重载：按档位中值构建画像（自适应关闭 / 旧调用方）。 */
    fun generateTurn(
        leg: CricketLegState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): List<ClaimedDart> = generateTurn(leg, AiProfile.of(difficulty), random)
}
