package com.dartvio.app.domain.rules

import com.dartvio.app.domain.model.AiDifficulty
import kotlin.random.Random

/**
 * 一次 AI 回合生效的强度画像（M4 §6.1）。
 *
 * [ppr] 为**自适应后**的当前 PPR；命中率、结镖率、180 率、延迟均由它/难度换算。
 * 生成器只依赖本画像，不关心自适应细节。
 */
data class AiProfile(
    val difficulty: AiDifficulty,
    val ppr: Double = difficulty.pprMid.toDouble()
) {
    /** 单镖命中理想目标的概率（由当前 PPR 现算）。 */
    val hitChance: Double get() = AiDifficulty.hitChanceFor(ppr)

    /** 结镖率：处于收尾镖（可直接打到 0）时的命中概率。 */
    val checkoutRate: Double get() = difficulty.checkoutRate

    /** 180 率：本回合形成 180 的概率。 */
    val percent180: Double get() = difficulty.percent180

    /** 取一个本档位区间的单镖显示延迟（毫秒）。 */
    fun nextDelayMs(random: Random): Long =
        if (difficulty.maxDelayMs <= difficulty.minDelayMs) difficulty.minDelayMs
        else random.nextLong(difficulty.minDelayMs, difficulty.maxDelayMs + 1)

    companion object {
        /** 固定档位中值的画像（自适应关闭时使用）。 */
        fun of(difficulty: AiDifficulty): AiProfile = AiProfile(difficulty)
    }
}
