package com.dartvio.app.domain.rules

import com.dartvio.app.domain.model.ClaimedDart
import com.dartvio.app.domain.model.CricketHitResult
import com.dartvio.app.domain.model.CricketLegState
import com.dartvio.app.domain.model.CricketPlayerState
import com.dartvio.app.domain.model.CricketTarget
import com.dartvio.app.domain.model.CricketVariant
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.DartClaim
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.ScoreSink
import com.dartvio.app.domain.model.TargetCategory
import com.dartvio.app.domain.model.WinCompare

/**
 * Cricket 规则引擎。PRD M2（玩法变体见 §4.8）。
 *
 * 核心规则：
 * 1. 有效分数：20,19,18,17,16,15,Bull(25)。
 * 2. 击中单倍标记 +1、双倍 +2、三倍 +3，标记上限 3（标记满 3 即关闭）。
 * 3. 标记满 3 后再次击中该分数即可得分，但仅在至少一名对手尚未关闭该分数时。
 * 4. 获胜条件：己方所有分数均关闭，再按变体的 [WinCompare] 比较分数（§4.8②③④）。
 * 5. Outer Bull=1 mark, Inner Bull(D25)=2 marks。
 *
 * ⚠️ 变体只改「得分归属」与「胜负比较」两处；关闭规则、回合结构三种变体完全一致。
 * 因此这里不按变体复制分支，只把这两个口径参数化 —— 判定由本层统一返回，
 * M4（GameViewModel / 对局核心）不得另写一套比较逻辑（M4 §6.4）。
 *
 * 二期 2C（M2 §4.9）在此基础上加了三件事，都**不复制变体分支**：
 * - **归属裁决**（[DartClaim]）：一记 T19 可记给「19」或「三倍档」，由 [evaluateDart] 解析；
 * - **死区豁免**：数字号位被全员关闭后，仍可按「倍数 × 面值」得分（[deadZoneExempt]）；
 * - **轮数上限超时终局**（[isRoundLimitWin]）：无人满足获胜条件时总分最高者胜。
 */
object CricketRules {

    /**
     * 一支镖对 Cricket 的 effect。
     *
     * [targets] 由调用方从 `config.cricketTargets` 传入；不在目标集内的号位当作**未命中**
     * 返回 `null`（该镖仍会计入 dartsThrown，与一期一致 —— 打在 3 号上的镖就是浪费掉的一镖）。
     *
     * [variant] 必须由调用方从 `config.cricketVariant` 显式传入。
     * 刻意不给默认值：漏传会静默退回 standard 口径，正是「同一规则两份跑偏」的典型来源。
     *
     * @param claim 本镖的归属裁决（§4.9.2③④，二期 2C）。
     *   **刻意不给默认值**，与 [variant] 同款纪律：漏传会静默退回数字口径，
     *   而 Tactics 局的「记给三倍档」恰恰是玩家主动做的选择，静默丢失就是吃玩家的操作。
     *   类别裁决只在「合格 D/T 且该档在目标集内」时成立，否则**回落数字**（不吞镖）。
     * @param overkillEnabled 是否启用 Overkill（§4.9.9，二期 2C，配置侧缺省 false）。
     *   同样**刻意不给默认值** —— 它是 `MatchConfig` 的字段，规则层只收拆出来的值，
     *   漏传等于悄悄关掉玩家选的开关。
     */
    fun evaluateDart(
        dart: Dart,
        claim: DartClaim,
        overkillEnabled: Boolean,
        shooter: CricketPlayerState,
        opponents: List<CricketPlayerState>,
        targets: List<CricketTarget>,
        variant: CricketVariant
    ): CricketHitResult? {
        if (dart.isMiss) return null

        // 「出镖 → 目标位」的唯一映射点。Dart 的号码是板面号位（Int，X01 也用它），
        // 这里把它翻成目标位标识；目标集里有类别档时，物理镖依旧只落在数字号位上。
        val token = when {
            dart.isInnerBull -> BULL_TOKEN
            dart.isOuterBull -> BULL_TOKEN
            else -> dart.number
        }
        val numberTarget = CricketTarget.Number(token)
        if (numberTarget !in targets) return null

        // ① 裁决解析：类别裁决**只在合格 D/T 且该档在目标集内**时成立，否则回落数字。
        //    「回落」而不是「未命中」：非法裁决不得吞镖（红线 §10.3）。
        val category = categoryFor(dart)
        val effectiveClaim =
            if (claim == DartClaim.CATEGORY && category != null && category in targets) {
                DartClaim.CATEGORY
            } else {
                DartClaim.NUMBER
            }

        val target = if (effectiveClaim == DartClaim.CATEGORY) category!! else numberTarget
        // 类别档恒 +1 标记（§4.9.2①）。写成 dart.multiplier 会让 T20 一镖关掉三倍档 —— 红线 §10.2。
        val marksToAdd = if (effectiveClaim == DartClaim.CATEGORY) 1 else dart.multiplier

        val currentMarks = shooter.marks.marksOf(target)
        val marksGained = (3 - currentMarks).coerceAtLeast(0).coerceAtMost(marksToAdd)

        // 超出标记的命中可用于得分
        val excessMarks = (marksToAdd - marksGained).coerceAtLeast(0)

        // 得分条件：对手中至少一人未关闭该分数
        val openOpponents = opponents.filter { !it.marks.isClosed(target) }

        // ② 死区豁免（§4.9.3③）：**只在裁决为数字时**生效 —— 类别档不产分，没有可放宽的条件。
        val exempt = effectiveClaim == DartClaim.NUMBER &&
            deadZoneExempt(dart, shooter, opponents, targets)

        // ③ 得分：既有公式一字未改，只把「无对手未关 ⇒ 0 分」这一条放宽。
        val amount = if (openOpponents.isEmpty() && !exempt) 0 else excessMarks * faceValue(target)

        // ④ Overkill（§4.9.9）：只压分值，标记照加。
        val suppressed = overkillEnabled &&
            variant.scoreSink == ScoreSink.SELF &&
            amount > 0 &&
            opponents.isNotEmpty() &&
            shooter.score - opponents.maxOf { it.score } >= OVERKILL_LEAD_THRESHOLD
        val effectiveAmount = if (suppressed) 0 else amount

        // 得分归属由变体决定（§4.8⑤）。豁免的归属 = 数字目标下的投掷者自己（SELF 语义，不变）。
        val owners = if (effectiveAmount <= 0) {
            emptyList()
        } else {
            when (variant.scoreSink) {
                ScoreSink.SELF -> listOf(shooter.playerId)
                // 多人局：每个尚未关闭该分区的对手都要加分（1v1 时退化为 §4.6 的参考实现）
                ScoreSink.OPPONENTS -> openOpponents.map { it.playerId }
                ScoreSink.NONE -> emptyList()
            }
        }

        return CricketHitResult(
            target = target,
            dartMultiplier = dart.multiplier,
            marksGained = marksGained,
            scoreGained = if (owners.isEmpty()) 0 else effectiveAmount,
            scoreOwnerIds = owners,
            deadZoneExemptionApplied = exempt,
            scoreSuppressedByOverkill = suppressed,
        )
    }

    /** Overkill 的触发阈值：本方领先对手 ≥ 200 分（§4.9.9，固定值，不可配）。 */
    private const val OVERKILL_LEAD_THRESHOLD = 200

    /**
     * STRICT 严格性下的「合格双倍 / 三倍」（§4.9.7，二期 2C 的**唯一实现点**）。
     *
     * 本期只实现 STRICT，且**不引入任何严格性配置项**（不加字段、不留开关、不加未使用参数）。
     * 规则：仅 **15–20** 上的 D/T 合格；Bull 永远不是「双倍区」（Bull 是独立目标）；
     * 1–14 的 D/T 不计入任何目标。
     *
     * 两处已知边界（本期不会出现，随 SLOP 一起解决）：① 「Tactics + 非全 15–20 目标集」下
     * 会出现「合格但非本局目标」（本局无 19 却打 D19 → 在 [evaluateDart] 里回落数字或未命中）；
     * ② SLOP 下 1–20 任何 D/T 都合格，且 Inner Bull 是否算一个双倍未定。
     *
     * 可见性为 `internal` 而非 `private`：[CricketAi.adjudicate] 需要同一个判据。
     * 让 AI 复用这一处，而不是在 AI 里抄一份 STRICT 规则 —— 抄一份就等于把上面两条边界
     * 变成两个会各自漂移的实现点。
     */
    public fun categoryFor(dart: Dart): CricketTarget.Category? {
        if (dart.multiplier != 2 && dart.multiplier != 3) return null
        if (dart.isInnerBull || dart.isOuterBull) return null
        if (dart.number !in 15..20) return null
        return CricketTarget.Category(
            if (dart.multiplier == 3) TargetCategory.TRIPLES else TargetCategory.DOUBLES
        )
    }

    /**
     * 死区豁免（§4.9.3③）—— 二期 2C 唯一新增的得分通道。
     *
     * 四个条件**同时**成立才生效：
     * ① 裁决为数字 N（调用点已保证，类别裁决不适用）；
     * ② N 已被**全部玩家**关闭（因 `excess > 0` 已要求本方已关，这里等价于「对手全关」）；
     * ③ 本方**已关闭**对应的类别档（triple ↔ 三倍档 / double ↔ 双倍档）；
     * ④ 该类别档**尚未被全部玩家关闭**（因 ③ 保证本方已关，等价于「至少一名对手未关该档」）。
     *
     * 条件 ③ 尤其不能省：少了它就会出现「**没关三倍档也能在死区号位刷 57 分**」。
     *
     * 触发后得分 = 倍数 × N（T19 ⇒ 57 分）——**这不是新算式**：条件 ② 保证 `marksGained = 0`、
     * `excessMarks = dart.multiplier`，所以既有公式 `excess × faceValue(N)` 天然算出这个值。
     * 归属仍记数字 N 名下，**不给类别档加标记**。
     */
    private fun deadZoneExempt(
        dart: Dart,
        shooter: CricketPlayerState,
        opponents: List<CricketPlayerState>,
        targets: List<CricketTarget>,
    ): Boolean {
        val cat = categoryFor(dart) ?: return false                 // 单倍 / 非 15–20 ⇒ 不适用
        if (cat !in targets) return false                          // 非 Tactics 局天然不生效
        val n = CricketTarget.Number(
            if (dart.isInnerBull || dart.isOuterBull) BULL_TOKEN else dart.number
        )
        if (shooter.marks.marksOf(n) < 3) return false              // ② 本方尚未关 N
        if (opponents.any { !it.marks.isClosed(n) }) return false    // ② N 未全员关闭
        if (!shooter.marks.isClosed(cat)) return false              // ③ 本方未关该类别档
        if (opponents.none { !it.marks.isClosed(cat) }) return false // ④ 该档已全员关闭 ⇒ 不生效
        return true
    }

    /** Bull 的板面号位（与 [CricketTarget.BULL] 同值，集中一处免得散落魔数）。 */
    private const val BULL_TOKEN = 25

    /**
     * 目标位对应的得分面值。
     *
     * 数字分区取数字本身；类别档（双倍档 / 三倍档）**面值恒为 0** —— 这是 2C 冻结口径，
     * 不是「尚未接入」：类别路径只产标记不产分（§4.9.2⑤），打类别档拿分是**故意**不可能的。
     * 类别档想变成「值得打的靶子」，靠的是死区豁免（数字 N 已全员关闭后按倍数 × N 得分）。
     */
    private fun faceValue(target: CricketTarget): Int =
        (target as? CricketTarget.Number)?.value ?: 0

    /**
     * 即时结算一支镖，返回新状态与命中明细（不切换玩家）。
     *
     * 本回合的第一支镖会记录 [CricketLegState.turnStartPlayers] 快照 —— 改判（[redeclare]）
     * 需要一条干净的基线来重放整个回合。
     */
    fun applySingleDart(
        state: CricketLegState,
        claimed: ClaimedDart
    ): Pair<CricketLegState, CricketHitResult?> {
        val config = state.config
        val playerIndex = state.currentPlayerIndex
        val player = state.players[playerIndex]
        val opponents = state.players.filterIndexed { i, _ -> i != playerIndex }

        val hit = evaluateDart(
            claimed.dart, claimed.claim, config.overkillEnabled,
            player, opponents, config.cricketTargets, config.cricketVariant,
        )
            ?: return state.withDart(claimed) to null

        val players = state.players.toMutableList()
        // marks 永远记在投掷者头上；分数则按归属落账。
        players[playerIndex] = player.copy(marks = player.marks.add(hit.target, hit.marksGained))
        applyScore(players, hit)

        return state.copy(
            players = players,
            currentTurnDarts = state.currentTurnDarts + claimed,
            turnStartPlayers = state.turnStartPlayers ?: state.players,
        ) to hit
    }

    /** 追加一支镖并保证回合基线存在（未命中路径也要基线，否则改判会丢掉这条镖）。 */
    private fun CricketLegState.withDart(claimed: ClaimedDart): CricketLegState = copy(
        currentTurnDarts = currentTurnDarts + claimed,
        turnStartPlayers = turnStartPlayers ?: players,
    )

    /**
     * **改判**本回合第 [dartIndex] 支镖的归属裁决（§4.9.2③，二期 2C）。
     *
     * 实现是「**以新 claim 重放本回合截至该镖的序列**」，不做任何反向补偿：
     * 从回合开始的快照重放，天然得到正确结果，也不会出现「撤销 A 把 B 的账改错」这类漂移。
     *
     * 返回重放后的新状态与该回合的命中明细（未命中位置为 `null`，与 [currentTurnDarts] 对齐）。
     * 索引越界 / 回合未开始（无基线）时原样返回。
     */
    fun redeclare(
        state: CricketLegState,
        dartIndex: Int,
        claim: DartClaim
    ): Pair<CricketLegState, List<CricketHitResult?>> {
        val base = state.turnStartPlayers ?: return state to emptyList()
        val darts = state.currentTurnDarts
        if (dartIndex !in darts.indices) return state to emptyList()

        // 回到回合起点，再按新裁决重放整段序列。
        var replayed = state.copy(
            players = base,
            currentTurnDarts = emptyList(),
            turnStartPlayers = base,
        )
        val hits = mutableListOf<CricketHitResult?>()
        darts.forEachIndexed { index, claimed ->
            val next = if (index == dartIndex) claimed.copy(claim = claim) else claimed
            val (advanced, hit) = applySingleDart(replayed, next)
            replayed = advanced
            hits.add(hit)
        }
        return replayed to hits
    }

    /**
     * 收尾当前回合：**给当前玩家计一个回合数**，并清空回合缓冲与改判基线。
     *
     * 轮数上限的计数点（§4.9.8 的「每人各算一轮」）就这一处 ——
     * 放在规则层而不是 M4，是为了让「单镖即时结算」与「整回合结算」两条路径不会各数一套。
     */
    fun endTurn(state: CricketLegState): CricketLegState {
        val index = state.currentPlayerIndex
        val players = state.players.toMutableList()
        val me = players.getOrNull(index)
            ?: return state.copy(currentTurnDarts = emptyList(), turnStartPlayers = null)
        players[index] = me.copy(turnsPlayed = me.turnsPlayed + 1)
        return state.copy(players = players, currentTurnDarts = emptyList(), turnStartPlayers = null)
    }

    /**
     * 结算一名玩家的一个完整回合，返回新状态与命中明细。
     */
    fun applyTurn(
        state: CricketLegState,
        darts: List<ClaimedDart>
    ): Triple<CricketLegState, List<CricketHitResult>, Boolean> {
        val config = state.config
        val playerIndex = state.currentPlayerIndex

        // 逐镖基于**当前**玩家列表推进：cut_throat 的得分会改对手的分，
        // 后续镖必须看到最新状态（旧实现每镖重建对手快照，多人局下会算漏）。
        val players = state.players.toMutableList()
        val hits = mutableListOf<CricketHitResult>()

        for (claimed in darts) {
            val shooter = players[playerIndex]
            val opponents = players.filterIndexed { i, _ -> i != playerIndex }
            val hit = evaluateDart(
                claimed.dart, claimed.claim, config.overkillEnabled,
                shooter, opponents, config.cricketTargets, config.cricketVariant,
            ) ?: continue
            players[playerIndex] = shooter.copy(marks = shooter.marks.add(hit.target, hit.marksGained))
            applyScore(players, hit)
            hits.add(hit)
        }

        // 先计数、再判定：轮数上限的判据是「全员打满」，顺序反了会永远差一个回合。
        val committed = endTurn(state.copy(players = players))
        val won = isWinningLeg(committed.players, playerIndex, config)
        val nextIndex = if (won) playerIndex else (playerIndex + 1) % committed.players.size

        val newState = committed.copy(
            currentPlayerIndex = nextIndex,
            isFinished = won,
            winnerIndex = if (won) playerIndex else null,
            endedByRoundLimit = won && isRoundLimitWin(committed.players, playerIndex, config),
        )

        return Triple(newState, hits, won)
    }

    /**
     * 一局胜负判定的**唯一实现**（M2 §4.8⑤，M4 §6.4 要求 M4 不得另写）。
     *
     * 判定链：先看该玩家是否已关闭全部分区 → 再按变体的 [WinCompare] 与对手分比较。
     * - GE（standard）：本方分 ≥ 所有对手分（原口径）
     * - LE（cut_throat）：本方分 ≤ 所有对手分，含等号（低分领先）
     * - NONE（no_score）：关满即胜，不读任何分数
     *
     * 「≥ / ≤ 所有对手」是 1v1 口径的自然推广（§4.8 只给了 1v1 参考实现）：
     * cut_throat 必须低过**每一个**对手才算领先，否则多人局下会被判成同时获胜。
     *
     * 二期 2C 追加**轮数上限超时终局**（§4.9.8）：全员打满 [MatchConfig.maxRounds]
     * 且无人满足上述获胜条件时，总分最高者胜。终局判定仍然只在这一处，没有第二个实现点。
     */
    fun isWinningLeg(
        players: List<CricketPlayerState>,
        playerIndex: Int,
        config: MatchConfig
    ): Boolean =
        satisfiesWinCondition(players, playerIndex, config) ||
            isRoundLimitWin(players, playerIndex, config)

    /**
     * 常规获胜条件（关满目标集 + 变体比较口径），**不含**超时终局。
     *
     * 拆出来是为了让 [isRoundLimitWin] 能判断「是否有人已经正常获胜」而不与自己互相递归。
     */
    private fun satisfiesWinCondition(
        players: List<CricketPlayerState>,
        playerIndex: Int,
        config: MatchConfig
    ): Boolean {
        val me = players.getOrNull(playerIndex) ?: return false
        if (!me.hasClosedAll(config.cricketTargets)) return false

        val others = players.filterIndexed { i, _ -> i != playerIndex }
        if (others.isEmpty()) return true

        return when (config.cricketVariant.winCompare) {
            WinCompare.NONE -> true
            WinCompare.GE -> me.score >= others.maxOf { it.score }
            WinCompare.LE -> me.score <= others.minOf { it.score }
        }
    }

    /**
     * 轮数上限超时终局（§4.9.8，二期 2C）。
     *
     * `config.maxRounds = 0`（缺省）⇒ 无上限，恒 false，与 2A 行为逐位相同。
     * 字段是 X01「最多轮数」与本玩法「轮数上限」**合并后的同一个**
     * [MatchConfig.maxRounds]（2026-09-12）；合并的是字段，不是胜负口径 ——
     * 超时判胜在这一处、X01 在 `X01Rules`，两边各按自己的领先方向取人。
     * 计数单位是「每人各算一轮」，所以判据是**全员**打满（见 `roundLimitReached`）。
     * 有人已经正常获胜时不接管（否则会把一个已经成立的胜利判成超时）。
     *
     * ⚠️ **已知口径缺口（已在 §6 交付摘要申报，未自行决定）**：冻结文本写「平分 ⇒ 并列」，
     * 但结果承载 `CricketLegState.winnerIndex` 只能放一个索引，`isWinningLeg` 也只能回答
     * 「**我**是否获胜」。本实现取「最高分者胜」，并列时由**轮到的那一方**获胜；
     * 要真正表达并列需要给结果面引入 `isDraw` / `winnerIndexes`，那会牵动对局结果页与落库，
     * 不属于「实装」的授权范围。
     */
    fun isRoundLimitWin(
        players: List<CricketPlayerState>,
        playerIndex: Int,
        config: MatchConfig
    ): Boolean {
        if (config.maxRounds <= 0) return false
        if (players.any { it.turnsPlayed < config.maxRounds }) return false
        if (players.indices.any { satisfiesWinCondition(players, it, config) }) return false
        val me = players.getOrNull(playerIndex) ?: return false
        return me.score >= players.maxOf { it.score }
    }

    /** 初始化一局 Cricket。 */
    fun newLeg(
        config: MatchConfig,
        players: List<com.dartvio.app.domain.model.Player>,
        legNumber: Int
    ): CricketLegState {
        return CricketLegState(
            config = config,
            players = players.map { CricketPlayerState(playerId = it.id) },
            currentPlayerIndex = 0,
            legNumber = legNumber
        )
    }

    /**
     * 把这一镖的得分记到归属玩家账上。
     *
     * standard 记自己、cut_throat 记各未关闭对手、no_score 无人可记 ——
     * 三种变体共用这一条落账路径，避免「谁的账」散落在多个分支里。
     */
    private fun applyScore(
        players: MutableList<CricketPlayerState>,
        hit: CricketHitResult
    ) {
        hit.scoreOwnerIds.forEach { ownerId ->
            val idx = players.indexOfFirst { it.playerId == ownerId }
            if (idx >= 0) {
                val owner = players[idx]
                players[idx] = owner.copy(score = owner.score + hit.scoreGained)
            }
        }
    }
}
