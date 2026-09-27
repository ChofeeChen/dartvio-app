package com.dartvio.app.domain.stats

import com.dartvio.app.domain.model.CricketTarget

/**
 * 统计口径全部来自 M9 §8。
 *
 * 两条硬约束：
 * 1. PPR = 总得分 ÷ (投镖数 ÷ 3)，回合数按「实际投镖数 ÷ 3」推算（对齐代码
 *    X01PlayerState.ppr）。已知偏差：Bust 或提前 Checkout 导致回合不足 3 镖时
 *    分母偏小、PPR 偏高。
 * 2. Cricket 的 Mark 相关指标一律基于**逐镖累加**的 marksTotal，绝不使用封顶 3
 *    的 marks 状态快照求和。
 */

/** X01 个人统计（M9 §8.1）。 */
data class X01Stats(
    val matchCount: Int = 0,
    val formalMatchCount: Int = 0,
    val winCount: Int = 0,
    val formalWinCount: Int = 0,
    val legCount: Int = 0,
    val formalLegCount: Int = 0,
    /** 全部对局 PPR（含 AI 对战） */
    val pprAll: Double = 0.0,
    /** 正式赛 PPR */
    val pprFormal: Double = 0.0,
    /** 平均每镖得分 */
    val scorePerDart: Double = 0.0,
    /** 全部对局胜率（含 AI，UI 上须标注「含 AI 对战」） */
    val winRateAll: Double = 0.0,
    /** 正式赛胜率 */
    val winRateFormal: Double = 0.0,
    /** 最高收尾 */
    val highestCheckout: Int = 0,
    /** 最高连胜（仅正式赛） */
    val longestWinStreak: Int = 0,
    /** 场均镖数 = 总投镖数 ÷ 总局数 */
    val dartsPerLeg: Double = 0.0,
    /** 180 总次数 */
    val total180: Int = 0,
    /** 单回合最高分 */
    val maxTurnScore: Int = 0,
    /** Bust 率（所有真人参与对局） */
    val bustRate: Double = 0.0,
    /** Checkout 率 = 成功收镖局数 ÷ 进入可收镖状态的回合数 */
    val checkoutRate: Double = 0.0,
) {
    val hasData: Boolean get() = matchCount > 0
}

/** Cricket 个人统计（M9 §8.3，指标编号 C1–C10）。 */
data class CricketStats(
    val matchCount: Int = 0,
    val winCount: Int = 0,
    // C1 Mark率 = 逐镖累加 Mark 数 ÷ 总镖数
    val markRate: Double = 0.0,
    // C2 分区关闭率 = 已关闭分区数 ÷ (对局数 × 7)
    val closeRate: Double = 0.0,
    // C3 三倍区命中率 = 三倍区命中镖数 ÷ 总镖数
    val tripleRate: Double = 0.0,
    // C4 Bull命中率 = Bull 命中镖数(Outer+Inner) ÷ 总镖数
    val bullRate: Double = 0.0,
    // C5 平均回合得分 = 总得分 ÷ 总回合数
    val avgTurnScore: Double = 0.0,
    // C6 平均关闭回合数（仅统计关满 7 分区的局）
    val avgTurnsToClose: Double = 0.0,
    // C7 得分效率 = 总得分 ÷ 总镖数（Cricket 版 PPR）
    val scoreEfficiency: Double = 0.0,
    // C8 Cricket 胜率（仅 S 级）
    val winRate: Double = 0.0,
    // C9 最高单回合得分
    val maxTurnScore: Int = 0,
    // C10 首关分区分布：目标位 → 出现次数（2A 起键为标识，数字分区渲染时仍显示号位）
    val firstClosedHistogram: Map<CricketTarget, Int> = emptyMap(),
) {
    val hasData: Boolean get() = matchCount > 0
}

/** 统计页一次性对外暴露的数据。 */
data class StatsSnapshot(
    val x01: X01Stats = X01Stats(),
    val cricket: CricketStats = CricketStats(),
)
