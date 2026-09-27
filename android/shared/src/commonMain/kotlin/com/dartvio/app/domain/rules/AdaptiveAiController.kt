package com.dartvio.app.domain.rules

import com.dartvio.app.domain.model.AiDifficulty
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode

/**
 * 同级自适应难度控制器（M4 §6.1.1 v1.8）。
 *
 * 目标：AI 强度贴合真人水平——「遇强则强、遇弱则弱」，但**不跨档**。
 *
 * 规则：
 *  ① basePpr = 档位中值；上下限 = 档位 PPR 区间。
 *  ② 每个真人回合结算后，滚动统计最近 N=5 个**真人**回合的实际 PPR
 *     （playerPpr = 真人得分之和 ÷ 真人镖数 × 3，AI 托管回合不计入）。
 *  ③ targetPpr = clamp(basePpr + k × (playerPpr − basePpr), low, high)，k=0.5。
 *  ④ 平滑：aiPpr = 0.7 × 上一值 + 0.3 × targetPpr。
 *  ⑤ 结果始终 clamp 在档位区间内（⑥ 即不跨档）。
 *  ⑦ 关闭开关：休闲模式或用户关闭「智能难度」时，aiPpr 恒为档位中值。
 *
 * 说明：控制器不持有协程，线程安全由调用方（GameViewModel 主线程）保证。
 */
class AdaptiveAiController(
    private val difficulty: AiDifficulty,
    private val enabled: Boolean,
    private val followK: Double = 0.5,
    private val smoothing: Double = 0.3,
    private val windowSize: Int = 5
) {
    private val recentScores = ArrayDeque<Int>()
    private val recentDarts = ArrayDeque<Int>()

    /** 当前生效的 AI PPR（自适应后；关闭时恒为档位中值）。 */
    var currentPpr: Double = difficulty.pprMid.toDouble()
        private set

    /** 取本回合的强度画像。 */
    fun profile(): AiProfile =
        AiProfile(difficulty, if (enabled) currentPpr else difficulty.pprMid.toDouble())

    /**
     * 记录一个真人回合的结算事实。
     * @param scored 该回合得分
     * @param darts 该回合实际投出的镖数
     */
    fun recordHumanTurn(scored: Int, darts: Int) {
        if (!enabled || darts <= 0) return
        recentScores.addLast(scored)
        recentDarts.addLast(darts)
        while (recentScores.size > windowSize) {
            recentScores.removeFirst()
            recentDarts.removeFirst()
        }
        val dartsSum = recentDarts.sum()
        if (dartsSum <= 0) return
        val playerPpr = recentScores.sum().toDouble() / dartsSum * 3.0
        applyTarget(playerPpr)
    }

    private fun applyTarget(playerPpr: Double) {
        val base = difficulty.pprMid.toDouble()
        val target = (base + followK * (playerPpr - base))
            .coerceIn(difficulty.pprLow.toDouble(), difficulty.pprHigh.toDouble())
        currentPpr = ((1 - smoothing) * currentPpr + smoothing * target)
            .coerceIn(difficulty.pprLow.toDouble(), difficulty.pprHigh.toDouble())
    }

    /** 重置采样与当前 PPR（新比赛时调用）。 */
    fun reset() {
        recentScores.clear()
        recentDarts.clear()
        currentPpr = difficulty.pprMid.toDouble()
    }

    companion object {
        /**
         * 按比赛配置构建控制器。
         * 休闲模式或未开启「智能难度」时，自适应关闭（aiPpr 恒为档位中值）。
         */
        fun forMatch(
            config: MatchConfig,
            difficulty: AiDifficulty,
            smartEnabled: Boolean
        ): AdaptiveAiController =
            AdaptiveAiController(
                difficulty = difficulty,
                enabled = smartEnabled && config.mode == MatchMode.MULTI_LEG
            )
    }
}
