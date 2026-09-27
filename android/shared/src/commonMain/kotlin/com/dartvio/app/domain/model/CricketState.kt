package com.dartvio.app.domain.model

import com.dartvio.app.platform.PlatformTime

/**
 * Cricket 标记进度：0=未开(Closed 未标记), 1..3 标记数。3 表示 Closed。
 * PRD M2：击中单倍 +1、双倍 +2、三倍 +3，上限 3。
 *
 * 二期 2A：key 从「数字」改为 [CricketTarget]（M2 §4.8⑦）。
 * Tactics 变体会引入「双倍档 / 三倍档」这类**非数字**目标，所以这一层不再假设目标位是几号；
 * `Int` 版本的 `marksOf(20)` 在二期会直接编译失败 —— 这正是重构想要的效果：
 * **把「其实依赖了数字」这件事从沉默变成编译错误**。
 */
data class CricketMarks(
    /** key = 目标位，value = 标记数 0..3。 */
    val marks: Map<CricketTarget, Int> = emptyMap()
) {
    fun marksOf(target: CricketTarget): Int = marks[target] ?: 0

    fun isClosed(target: CricketTarget): Boolean = marksOf(target) >= 3

    fun add(target: CricketTarget, count: Int): CricketMarks {
        val current = marksOf(target)
        val next = (current + count).coerceAtMost(3)
        return copy(marks = marks + (target to next))
    }

    /**
     * 回退一次标记（撤销上一镖）。
     *
     * 保留 `count` 参数而不是「减 1」：撤销的是一整镖，而一镖可能加 2~3 个标记
     * （双倍 / 三倍），减 1 会让记分板与真实板面错位。
     */
    fun remove(target: CricketTarget, count: Int): CricketMarks {
        val current = marksOf(target)
        val next = (current - count).coerceAtLeast(0)
        return copy(marks = marks + (target to next))
    }
}

/**
 * 一记镖的**归属裁决**（M2 §4.9.2③④，二期 2C）。
 *
 * 只能选一边，不可兼得 —— 这正是 Tactics 的核心操作：
 * 同一个 T19，既可以记给「19」（数字路径，可能产分），
 * 也可以记给「三倍档」（类别路径，+1 标记、0 分）。
 *
 * 裁决是**这次投掷的属性**，所以撤销该镖即连带撤销裁决（见 [ClaimedDart]）。
 */
enum class DartClaim {
    /** 记给数字号位（缺省；一期行为）。 */
    NUMBER,

    /** 记给类别档（双倍档 / 三倍档）。不成立时一律回落 [NUMBER]（红线：不得静默吞镖）。 */
    CATEGORY,
}

/**
 * 镖 + 它的归属裁决。
 *
 * 裁决与镖**绑定在一起**而不是另存一张表：撤销 / 改判都是「按序列重放」，
 * 序列里每一项必须自带裁决才能重放出正确结果（红线：不得为 rollback 写反向补偿）。
 */
data class ClaimedDart(
    val dart: Dart,
    val claim: DartClaim = DartClaim.NUMBER,
)

/** Cricket 单玩家状态。 */
data class CricketPlayerState(
    val playerId: String,
    val marks: CricketMarks = CricketMarks(),
    /** 累计得分。 */
    val score: Int = 0,
    val legsWon: Int = 0,
    /**
     * 本局**该玩家已完成**的回合数（二期 2C）。
     *
     * 轮数上限的计数单位是「**每人各算一轮**」（M2 §4.9.8）：`maxRounds = 50`
     * 表示每人各 50 个回合 × 3 镖，而不是全场共 50 个回合 ——
     * 所以这个计数器必须**挂到玩家身上**，全场计数在 2 人局会差 2 倍、4 人局差 4 倍。
     */
    val turnsPlayed: Int = 0,
) {
    /**
     * 该玩家是否已关闭所有分数（含 Bull），用于判定获胜。
     */
    fun hasClosedAll(targets: List<CricketTarget>): Boolean =
        targets.all { marks.isClosed(it) }

    /** 已关闭数量。 */
    fun closedCount(targets: List<CricketTarget>): Int =
        targets.count { marks.isClosed(it) }
}

/**
 * 一次 Cricket 投掷的结算明细，用于 UI 反馈（"3 Marks" / "+20" / "+20 → 对手"）。
 */
data class CricketHitResult(
    /** 命中的目标位（不含目标集内不含的号位 —— 那种镖直接返回 null，不产生结算明细）。 */
    val target: CricketTarget,
    val dartMultiplier: Int,
    /** 用于标记的 marks 增量。 */
    val marksGained: Int,
    /**
     * 该镖产生的分数，**每个归属玩家各记这么多**（M2 §4.8⑤）。
     * standard 是记给自己的分；cut_throat 是记给对手的分；no_score 恒为 0。
     */
    val scoreGained: Int,
    /**
     * 得分归属玩家 id；空列表表示这一镖没有产生任何得分。
     * - standard（ScoreSink.SELF）→ 投掷者自己
     * - cut_throat（ScoreSink.OPPONENTS）→ 所有尚未关闭该分区的对手（多人局每人各得）
     * - no_score（ScoreSink.NONE）→ 空
     *
     * §4.8⑤ 要求返回值带这个标识，UI 才能把「+N」和「+N → 对手」分开提示，
     * 否则生死局玩家会误以为自己进了分。
     */
    val scoreOwnerIds: List<String> = emptyList(),
    /**
     * 本镖是否走了**死区豁免**拿分（M2 §4.9.3③，二期 2C）。
     *
     * 语义：数字号位 N 已被全员关闭、本方已关对应类别档、且该档仍有对手未关 ——
     * 此时按「倍数 × N」计分，**归属仍是数字 N 名下，不给类别档加标记**。
     * UI 必须在结算明细里标出来源（§8.7），否则玩家会以为是算错了。
     */
    val deadZoneExemptionApplied: Boolean = false,
    /**
     * 本镖的得分是否被 **Overkill** 压制（M2 §4.9.9，二期 2C，缺省关闭）。
     *
     * 语义：本就该得分，但本方已领先 ≥ 200 分 ⇒ 该镖只加标记、不计分。
     * **标记照加**是刻意的：否则关门进度会落后于规则，残局可能卡死。
     * 与 [deadZoneExemptionApplied] 是两个独立布尔（语义不重叠，理论上可组合）。
     */
    val scoreSuppressedByOverkill: Boolean = false,
) {
    /** 分数是否记到了别人账上 —— UI 提示「→ 对手」的判据。 */
    fun isScoredForOpponent(shooterId: String): Boolean =
        scoreGained > 0 && scoreOwnerIds.isNotEmpty() && shooterId !in scoreOwnerIds
}

/** 一局 Cricket 的完整状态（可 JSON 快照持久化，支持回合重建）。 */
data class CricketLegState(
    val config: MatchConfig,
    val players: List<CricketPlayerState>,
    val currentPlayerIndex: Int,
    val currentTurnDarts: List<ClaimedDart> = emptyList(),
    val legNumber: Int = 1,
    val isFinished: Boolean = false,
    val winnerIndex: Int? = null,
    /**
     * 本回合**开始时**的玩家快照（二期 2C 改判用）。
     *
     * 改判一律「以新 claim 重放本回合截至该镖的序列」（红线：不得写反向补偿），
     * 所以需要一个干净的基线；没有它就只能基于「已被本回合改过的状态」重放，结果必然错。
     * 首次投掷时记录、回合结束时清空。可空，不落库。
     */
    val turnStartPlayers: List<CricketPlayerState>? = null,
    /**
     * 本局是否由**轮数上限**终局（M2 §4.9.8，二期 2C）。
     *
     * 落库为 `match_records.endedByRoundLimit`；M9 侧据此说明「超时局的
     * `closedAllSectionLegs` 不计入」（超时局的赢家通常并没有关满，语义正确）。
     */
    val endedByRoundLimit: Boolean = false,
) {
    val currentPlayer: CricketPlayerState get() = players[currentPlayerIndex]

    /**
     * 是否所有玩家都已打满 [MatchConfig.maxRounds] 个回合（轮数上限的**唯一判据**）。
     *
     * `maxRounds = 0` 表示无上限（缺省），恒为 false。计数单位是「每人各算一轮」，
     * 所以这里要求**全员**打满 —— 只看当前玩家会让先手方提前结束本局。
     */
    val roundLimitReached: Boolean
        get() = config.maxRounds > 0 && players.all { it.turnsPlayed >= config.maxRounds }
}

/** 对局整体状态（跨 leg）。 */
data class MatchState(
    val config: MatchConfig,
    val players: List<Player>,
    val currentLegIndex: Int = 0,
    val isFinished: Boolean = false,
    val winnerPlayerId: String? = null,
    val startedAtMillis: Long = PlatformTime.nowMillis()
)
