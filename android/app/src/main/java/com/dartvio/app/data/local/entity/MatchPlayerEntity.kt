package com.dartvio.app.data.local.entity

import androidx.room.Entity

/**
 * 一场比赛中单个玩家的原始投镖事实。
 *
 * 设计原则：**只落原始事实，不落计算后的指标**。所有 PPR / Mark率 / 得分效率
 * 等指标都在 [com.dartvio.app.domain.stats.StatsCalculator] 里按 M9 §8 口径现算，
 * 这样口径调整时无需数据迁移。
 *
 * 字段名保持玩法中立（generic），按所属 match 的 gameType 解释：
 * - X01：使用 [dartsThrown] / [totalScore] / [busts] / [count180] / [bestCheckout]
 *        / [checkoutAttempts] / [maxTurnScore]
 * - Cricket：使用 [dartsThrown] / [totalScore] / [marksTotal] / [tripleHits]
 *        / [bullHits] / [maxTurnScore] / [closedAllSectionLegs] / [turnsInClosedLegs]
 *        / [closedSectionsTotal] / [firstClosedCsv]
 *
 * ⚠️ [marksTotal] 必须是**逐镖累加**的获得 Mark 数（每镖 0–3）。
 * 不得由 CricketMarks.marks（封顶 3 的状态快照）求和得到 —— 见 M9 §8.3.1 C1。
 */
@Entity(tableName = "match_players", primaryKeys = ["matchId", "playerId"])
data class MatchPlayerEntity(
    val matchId: String,
    val playerId: String,
    val name: String,
    val isAi: Boolean,
    val aiDifficulty: String?,
    /** 出手顺序 0 起 */
    val orderIndex: Int,
    val isWinner: Boolean,
    val legsWon: Int,

    // ---- 通用投镖事实 ----
    /** 实际投出的镖数（M9 多处以它为分母） */
    val dartsThrown: Int,
    /** 回合数（X01 = 一次三镖出手；Cricket 同理） */
    val turnsPlayed: Int,
    /** 累计得分 */
    val totalScore: Int,
    /** 单回合得分最大值（C9 / X01 回合最高分） */
    val maxTurnScore: Int,
    /** 比赛结束时剩余分（X01） */
    val remaining: Int,

    // ---- X01 专属 ----
    val busts: Int,
    val count180: Int,
    /** 最高收尾：成功终结一局的回合得分中的最大值 */
    val bestCheckout: Int,
    /** 进入可收镖状态（剩余分可一回合打完）的回合数 */
    val checkoutAttempts: Int,

    // ---- Cricket 专属 ----
    /** ⚠️ 逐镖累加的获得 Mark 数，不可用封顶的 marks 求和 */
    val marksTotal: Int,
    /** 命中三倍区的镖数（C3 分子，分母为 dartsThrown） */
    val tripleHits: Int,
    /** 命中 Bull（Outer + Inner）的镖数（C4 分子，分母为 dartsThrown） */
    val bullHits: Int,
    /** 关满全部 7 个分区的局数（C6 分子） */
    val closedAllSectionLegs: Int,
    /** 上述「关满 7 分区」的局所花费的回合数合计（C6 分母） */
    val turnsInClosedLegs: Int,
    /** 累计关闭的分区数（C2 分子） */
    val closedSectionsTotal: Int,
    /**
     * 每局首个关闭的目标位，逗号分隔（C10）。
     *
     * 存的是 [com.dartvio.app.domain.model.CricketTarget] 的 `token`，**列类型仍是 TEXT**
     * （M2 §4.8⑦ 红线 4：不得改列类型、不得改字段名）——
     * 数字分区写出的仍是 `"20"` / `"25"`，与一期逐字节相同；类别档写 `"D"` / `"T"`。
     *
     * 读端一律走 [com.dartvio.app.domain.model.CricketTarget.parse]，
     * **未知 token 跳过、绝不抛异常**（红线 1）：老版本读到新版本写下的类别档 token 时
     * 少算一条即可，而不是让统计页打不开。
     */
    val firstClosedCsv: String,
)
