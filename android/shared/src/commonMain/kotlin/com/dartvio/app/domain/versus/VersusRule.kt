// 本文件原本用 internal 标记「只给 versus 包内用」的辅助函数。随 domain/ 下沉到 shared 后，
// internal 对 app 模块不再可见（其中 handicapSummary / bullProximity 等被对局 UI 与仓库直接调用），
// 因此统一改为 public。**它们仍是内部实现细节，新代码请勿直接调用。**
package com.dartvio.app.domain.versus

import com.dartvio.app.domain.model.Dart

/** 每轮每人固定 3 镖（加赛 Bull 时为 1 镖，见 [BattleState.dartsPerRound]）。 */
const val VERSUS_DARTS_PER_ROUND = 3

/** 双人对抗的座位数。写死 2：这是「双人同靶轮流」，不是多人局。 */
const val VERSUS_SEATS = 2

/**
 * 环带。
 *
 * 领域层此前没有这个类型：既有模型用 [Dart.multiplier] 的裸 `Int`（1/2/3）表达倍率、
 * 用半径常量表达环带。对抗练习要按「单倍 / 双倍 / 三倍 / 内外牛 / 脱靶」分类计分，
 * 拿 `multiplier` 表达会把「内牛」与「某个双倍分区」混成同一个值，
 * 所以这里显式建模一次，只在 [BoardHit.toDart] 处换回既有模型。
 *
 * ⚠️ [multiplier] 只用于换算 [Dart]，**不代表模式得分** ——
 * Bull 之争里内牛值 2 分（不是 50 分），倍区竞赛里 D20 值 2 分（不是 40 分）。
 */
enum class Ring(val multiplier: Int) {
    SINGLE(1),
    DOUBLE(2),
    TRIPLE(3),
    OUTER_BULL(1),
    INNER_BULL(2),
    MISS(0),
}

/**
 * 一次命中。[sector] 取 1..20；`null` = 不落在任何扇区（Bull 区或脱靶），
 * 此时 [ring] 必为 [Ring.OUTER_BULL] / [Ring.INNER_BULL] / [Ring.MISS]。
 */
data class BoardHit(val sector: Int?, val ring: Ring) {

    val isMiss: Boolean get() = ring == Ring.MISS
    val isBull: Boolean get() = ring == Ring.OUTER_BULL || ring == Ring.INNER_BULL

    /** 是否命中某个扇区的三倍 / 双倍环（不含 Bull）。 */
    val isTriple: Boolean get() = ring == Ring.TRIPLE && sector != null
    val isDouble: Boolean get() = ring == Ring.DOUBLE && sector != null

    /** 换算成既有判分模型 [Dart]：落库、统计、`Dart.label()` 展示都走它。 */
    fun toDart(): Dart = when (ring) {
        Ring.MISS -> Dart.MISS
        Ring.OUTER_BULL -> Dart.OUTER_BULL
        Ring.INNER_BULL -> Dart.INNER_BULL
        else -> Dart(sector ?: 0, ring.multiplier)
    }

    /** 展示文本（"T20" / "D16" / "BULL" / "25" / "MISS"）。 */
    fun label(): String = toDart().label()

    /** 扇区的**分值**（S20=20 / D16=32 / T20=60）；Bull 与脱靶返回 0（按 [ring] 另判）。 */
    fun sectorScore(): Int = sector?.let { it * ring.multiplier } ?: 0

    companion object {
        val MISS = BoardHit(null, Ring.MISS)
        val OUTER_BULL = BoardHit(null, Ring.OUTER_BULL)
        val INNER_BULL = BoardHit(null, Ring.INNER_BULL)

        fun single(sector: Int) = BoardHit(sector, Ring.SINGLE)
        fun double(sector: Int) = BoardHit(sector, Ring.DOUBLE)
        fun triple(sector: Int) = BoardHit(sector, Ring.TRIPLE)

        /** 由既有 [Dart] 还原（对局数据复用、测试夹具都走它）。 */
        fun of(dart: Dart): BoardHit = when {
            dart.isMiss -> MISS
            dart.isOuterBull -> OUTER_BULL
            dart.isInnerBull -> INNER_BULL
            dart.multiplier == 3 -> triple(dart.number)
            dart.multiplier == 2 -> double(dart.number)
            else -> single(dart.number)
        }
    }
}

/**
 * 记分键盘允许点选的范围 —— 由规则引擎给出，UI 只照着画。
 *
 * 这是「规则收口在引擎」的关键一环：如果由页面决定哪些键可点，
 * 每个模式就会在 UI 里各留一份规则副本，加模式时必然漏改。
 */
data class KeyboardLayout(
    /** 可点的扇区（1..20）；空 = 本轮不能点扇区（Bull 类模式）。 */
    val sectors: List<Int> = emptyList(),
    /** 可点的环带。 */
    val rings: List<Ring> = listOf(Ring.SINGLE, Ring.DOUBLE, Ring.TRIPLE),
    /** 是否允许牛眼。 */
    val bull: Boolean = false,
    /** 是否允许脱靶（默认可点：故意不让记 MISS 只会逼用户记成别的）。 */
    val miss: Boolean = true,
)

/**
 * 单镖 / 回合结算的 UI 反馈事件。引擎只产出事件，不接触任何 Android 类型，
 * 因此「引擎 → UI」这条路可以在纯 JVM 单测里逐条断言。
 */
sealed class DartEvent {
    data class Scored(val points: Int) : DartEvent()
    data class Advance(val sector: Int) : DartEvent()
    data class Halve(val from: Int, val to: Int) : DartEvent()
    data class Shanghai(val sector: Int) : DartEvent()
    data class Win(val playerIndex: Int) : DartEvent()
}

/** 终局原因，直接作为 `versus_match_records.endReason` 落库。 */
enum class BattleEndReason(val label: String) {
    NORMAL("达成目标"),
    SHANGHAI("上海秒杀"),
    RESIGN("认输"),
    ABORT("中止"),
}

/** 一轮的存档快照（换手时落一条 `versus_round_records`）。 */
data class RoundSnapshot(
    val roundNo: Int,
    val darts: List<BoardHit>,
    val score: Int,
    /** 本轮目标的文字快照；开赛时的规则如果之后被改，战报仍能复现当时的靶子。 */
    val targetSnapshot: String,
)

data class BattlePlayer(
    val id: String,
    val name: String,
    /** 当前得分 / 进度。含义由模式定义（Bull 之争 = 得分，环游类 = 无意义）。 */
    val score: Int = 0,
    /** 环游类：当前目标分区；非环游类恒为 `null`。`25` = 收尾的 Bull 格。 */
    val targetSector: Int? = null,
    /** 环游类：已完成步数（`0` = 还在起点）。 */
    val step: Int = 0,
    /** 加赛 Bull 时本轮记录的距离值；`null` = 本轮还没记。 */
    val playoffScore: Int? = null,
    val history: List<RoundSnapshot> = emptyList(),
)

data class BattleState(
    val modeKey: String,
    val config: BattleConfig,
    val players: List<BattlePlayer>,
    val currentPlayerIndex: Int = 0,
    val roundNo: Int = 1,
    /** 本轮已录的镖（顺序 = 录入顺序）。 */
    val dartsInRound: List<BoardHit> = emptyList(),
    /** 是否处于平分加赛（Bull，各 1 镖）。 */
    val playoff: Boolean = false,
    val finished: Boolean = false,
    val winnerIndex: Int? = null,
    val endReason: BattleEndReason? = null,
) {

    val current: BattlePlayer get() = players[currentPlayerIndex.coerceIn(0, players.lastIndex)]

    /** 本轮还要记几镖。加赛阶段是 1。 */
    val dartsPerRound: Int get() = if (playoff) 1 else VERSUS_DARTS_PER_ROUND

    /** 本轮是否已录满（UI 用它决定何时调 [VersusRule.onRoundEnd]）。 */
    val isRoundComplete: Boolean get() = dartsInRound.size >= dartsPerRound

    /** 已录镖数（0..3）。 */
    val dartsRecordedInRound: Int get() = dartsInRound.size

    /** 当前是「第几镖」，**从 1 起算**，用于「第 2 / 3 镖」这类展示。 */
    val dartNoInRound: Int get() = (dartsInRound.size + 1).coerceAtMost(dartsPerRound)

    /** 本局已录总镖数（含尚未结算的本轮）—— 战报「总镖数」的唯一口径。 */
    val totalDarts: Int
        get() = players.sumOf { player -> player.history.sumOf { it.darts.size } } + dartsInRound.size

    /** 已结算的问卷轮数（history 最长的那个玩家）；加赛轮不计入。 */
    val settledRounds: Int get() = players.maxOfOrNull { it.history.size } ?: 0
}

/**
 * 模式配置。刻意做成**扁平 + 全默认值**的一张表：
 * 六个模式的参数加起来也不过十来个，拆成六个 config 子类后，
 * 「配置跨页传递 + 落库快照 + 让分」这三件事都要各写一遍。
 *
 * 每个模式的**推荐默认值**不放这里，而是放在各自的 `defaultConfig()`：
 * 单张表只有一个默认值，而 Bull 之争的默认目标是 20、倍区竞赛是 30，
 * 让页面各自 `copy` 一遍等于把「默认值」散到 UI 里。
 */
data class BattleConfig(
    val modeKey: String,
    /** 倍区竞赛：目标分区（默认 20）。 */
    val targetSector: Int = 20,
    /** Bull 之争 / 倍区竞赛：先达成的目标分数（双方相同时用它）。 */
    val targetScore: Int = 0,
    /** 让分：双方各自的目标分数；缺省或 ≤0 的位次回落 [targetScore]。 */
    val targetScores: List<Int> = emptyList(),
    /** 让分：环游类各自的起始步数（强者 >0 = 起点靠后）。 */
    val startSteps: List<Int> = emptyList(),
    /** Bull 之争：是否区分内外 Bull（true = 内 2 分 / 外 1 分）。 */
    val splitBull: Boolean = true,
    /**
     * 倍区竞赛「三倍独尊」让分：**仅这些席位**三倍环计 3 分、D/S 均为 0。
     *
     * 按**席位**而不是整局一个布尔：这是「强者侧」的让分手段，
     * 整局开关会让两个人都变成三倍独尊，等于把让分变成改玩法。
     */
    val tripleOnlySeats: List<Int> = emptyList(),
    /** 环游三镖「双倍版」：须命中当前分区的双倍环才算命中。 */
    val doubleOnly: Boolean = false,
    /**
     * 环游类：`true` = 1 镖命中即前进一格（经典版 / 双倍环游默认）；
     * `false` = 整轮 3 镖全部命中才前进（环游三镖核心规则 / 双倍环游地狱模式）。
     */
    val singleHitAdvance: Boolean = false,
    /** 双倍环游：D20 之后还需要收尾 Bull（内/外 Bull 都算）才算完成。 */
    val finishOnBull: Boolean = false,
) {

    fun targetFor(playerIndex: Int): Int =
        targetScores.getOrNull(playerIndex)?.takeIf { it > 0 } ?: targetScore

    fun startStepFor(playerIndex: Int): Int =
        (startSteps.getOrNull(playerIndex) ?: 0).coerceAtLeast(0)

    fun tripleOnlyFor(playerIndex: Int): Boolean = tripleOnlySeats.contains(playerIndex)

    /**
     * 让分是否生效到具体某人（UI 摘要与测试都要问这句话，不要在页面里各判一次）。
     *
     * **双方目标相同 ⇒ 谁都不算被让分**：`targetScores` 为空时 `targetScore` 对两席同时生效，
     * 直接拿「席位值 != targetScore」去比会得出 `0 != 20`、`true` —— 那会让两个席位都挂上「让分」标记，
     * 让分这件事等于没说。所以目标分这一路只在**确实按席位给了不同值**时才成立，
     * 且只认**目标更低**（更轻松）的那一席为被让分方。
     */
    fun handicapped(playerIndex: Int): Boolean {
        if (startStepFor(playerIndex) > 0 || tripleOnlyFor(playerIndex)) return true
        val mine = targetScores.getOrNull(playerIndex)?.takeIf { it > 0 } ?: return false
        return targetScores
            .filterIndexed { index, value -> index != playerIndex && value > 0 }
            .any { other -> mine < other }
    }

    /**
     * 落库快照（`versus_match_records.configJson`）。
     *
     * **刻意不用 JSON 库**：项目红线是不新增依赖，而这里要的只是「能原样读回来 + 可读」。
     * 手写键值对既满足两者，又能在纯 JVM 单测里往返验证；键序固定，所以同配置的快照逐字节相同，
     * 「同配置的个人最佳」可以直接拿它当 key。
     */
    fun toStorageString(): String = listOf(
        "mode=$modeKey",
        "targetSector=$targetSector",
        "targetScore=$targetScore",
        "targetScores=${targetScores.joinToString("/")}",
        "startSteps=${startSteps.joinToString("/")}",
        "splitBull=$splitBull",
        "tripleOnlySeats=${tripleOnlySeats.joinToString("/")}",
        "doubleOnly=$doubleOnly",
        "singleHitAdvance=$singleHitAdvance",
        "finishOnBull=$finishOnBull",
    ).joinToString(";")
}

/**
 * 对抗练习的规则引擎接口。
 *
 * **必须是纯函数**：输入 state + hit，输出新 state，不读写数据库、不碰 Android 类型。
 * 这条约束换来两件事：全部六个模式能在毫秒级纯 JVM 单测里覆盖（含秒杀 / 减半 / 加赛分支），
 * 以及对局页彻底不理解规则 —— 加第 7 个模式不需要动任何页面。
 */
interface VersusRule {

    val modeKey: String

    /** 该模式的推荐开赛配置（默认目标分 / 默认变体）。 */
    fun defaultConfig(): BattleConfig

    fun newState(config: BattleConfig, playerNames: List<String>): BattleState

    /**
     * 记分键盘允许点选的范围。
     *
     * ⚠️ 参数是**状态**而非常见的「只看 config」：环游类的目标分区随回合（甚至随镖）推移，
     * 只看配置根本表达不出「这一轮只能点 18 分区」，而键盘上留一排点了必然记 0 分的死键，
     * 是这套 UI 最不能有的东西。
     */
    fun inputFilter(state: BattleState): KeyboardLayout

    /**
     * 当前目标的一句话口径（对局页顶部展示，也是「规则说明」弹窗的标题）。
     *
     * 放在引擎里而不是页面里：这句话本身就是规则（「先达 20 分」/「打 18 分区」/「任意双倍」），
     * 页面自己拼就会在加第 7 个模式时漏改一处文案，甚至写出与引擎判定相反的说明。
     */
    fun targetCaption(state: BattleState): String =
        state.current.targetSector?.let { "目标 $it 分区" }
            ?: state.config.targetScore.takeIf { it > 0 }?.let { "先达 $it 分" }
            ?: "—"

    /**
     * 「进度」的统一口径：战报曲线、领先方高亮、落库的 `runningScore` 都用它。
     *
     * 默认 = 得分；**环游类模式必须覆盖成「已走步数」** —— 那类模式的 `score` 恒为 0，
     * 不覆盖就会出现「战报曲线全程贴地、双方永远并列」这种看起来像 bug 的战报。
     */
    fun progressOf(state: BattleState, playerIndex: Int): Int =
        state.players.getOrNull(playerIndex)?.score ?: 0

    /** 进度的展示文本（席位卡上的大字）。默认「N 分」，环游类是「N/20」。 */
    fun progressText(state: BattleState, playerIndex: Int): String =
        "${progressOf(state, playerIndex)} 分"

    /** 单镖录入（含「这一镖就定胜负」的即时判定）。只有记满才会由 UI 接着调 [onRoundEnd]。 */
    fun onDart(state: BattleState, hit: BoardHit): Pair<BattleState, DartEvent?>

    /**
     * 回合结算：落快照 / 计分惩罚 / 推进 / 终局判定 / 换人。
     *
     * 返回事件（而不是只返回 state）是**必须的**：[DartEvent.Halve] 与 [DartEvent.Shanghai]
     * 这两个反馈天生只可能在本轮三镖都记完之后才知道，若不允许这里产出事件，
     * 对局页就只能自己重算一遍规则去判断该提示什么 —— 规则立刻出现第二份副本。
     */
    fun onRoundEnd(state: BattleState): Pair<BattleState, DartEvent?>

    /**
     * 认输：把出手权交给对手并终局（`endReason = RESIGN`）。
     *
     * 默认实现已够所有模式使用（先把本轮已录的镖结算掉，再判负），
     * 各模式不需要各写一遍。
     */
    fun resign(state: BattleState, loserIndex: Int): BattleState {
        if (state.finished) return state
        val settled = if (state.dartsInRound.isEmpty()) state else onRoundEnd(state).first
        if (settled.finished) return settled
        val winner = (settled.players.indices - loserIndex).firstOrNull() ?: return settled
        return settled.finish(winner, BattleEndReason.RESIGN)
    }

    /**
     * 中止：中途退出时用。**保留已落库的逐轮数据**，只在状态里标 `ABORT`，
     * 所以「中途退出不丢数据」这条要求由它 + 逐轮落库共同满足。
     */
    fun abort(state: BattleState): BattleState =
        if (state.finished) state else state.finish(null, BattleEndReason.ABORT)
}

// =====================================================================================
// 共用纯函数工具（各模式实现都在同一包内使用）
// =====================================================================================

/** 开局：座位固定 [VERSUS_SEATS] 席（这是双人对抗，不是多人局）。 */
public fun versusBaseState(
    modeKey: String,
    config: BattleConfig,
    playerNames: List<String>,
): BattleState = BattleState(
    modeKey = modeKey,
    config = config.copy(modeKey = modeKey),
    players = List(VERSUS_SEATS) { index ->
        BattlePlayer(
            id = "p$index",
            name = playerNames.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "玩家${index + 1}",
            step = config.startStepFor(index),
        )
    },
)

/**
 * 环游类的开局：把起始步数翻译成「当前目标分区」。
 *
 * 让分在这里落地：起点靠后 = `step` 直接落在序列的后面几格，而不是「先白送几格」——
 * 后者会让对手看到分数凭空跳动，前者一眼就懂「他从 D10 开始」。
 */
public fun clockBaseState(
    modeKey: String,
    config: BattleConfig,
    playerNames: List<String>,
    order: List<Int>,
): BattleState {
    val base = versusBaseState(modeKey, config, playerNames)
    return base.copy(
        players = base.players.map { player ->
            val step = player.step.coerceIn(0, order.lastIndex)
            player.copy(step = step, targetSector = order[step])
        }
    )
}

public fun BattleState.updatePlayer(
    index: Int,
    block: (BattlePlayer) -> BattlePlayer,
): BattleState = copy(players = players.mapIndexed { i, p -> if (i == index) block(p) else p })

/**
 * 记一镖进本轮缓冲。**已录满 / 已终局时的重复调用一律忽略** ——
 * 引擎必须能吞掉 UI 的重放与连点，否则重复回调会凭空多出几镖。
 */
public fun BattleState.withDart(hit: BoardHit): BattleState =
    if (finished || isRoundComplete) this else copy(dartsInRound = dartsInRound + hit)

/** 把本轮缓冲写进当前玩家的 history 并清空缓冲。 */
public fun BattleState.commitRound(roundScore: Int, targetSnapshot: String): BattleState {
    val snapshot = RoundSnapshot(roundNo, dartsInRound, roundScore, targetSnapshot)
    return updatePlayer(currentPlayerIndex) { it.copy(history = it.history + snapshot) }
        .copy(dartsInRound = emptyList())
}

/** 换手。轮号只在回到第 0 席时 +1，所以「第 N 轮」对双方是同一个 N。 */
public fun BattleState.passTurn(): BattleState {
    if (players.isEmpty()) return this
    val next = (currentPlayerIndex + 1) % players.size
    return copy(currentPlayerIndex = next, roundNo = if (next == 0) roundNo + 1 else roundNo)
}

public fun BattleState.finish(winnerIndex: Int?, reason: BattleEndReason): BattleState =
    copy(finished = true, winnerIndex = winnerIndex, endReason = reason)

/** 本轮已录镖的得分合计。 */
public fun BattleState.roundScoreOf(points: (BoardHit) -> Int): Int = dartsInRound.sumOf(points)

/** 加赛 Bull 的「离 Bull 近」近似：内牛 50 > 外牛 25 > 其它 0（其它分区一律视为没靠近）。 */
public fun bullProximity(hit: BoardHit): Int = when (hit.ring) {
    Ring.INNER_BULL -> 50
    Ring.OUTER_BULL -> 25
    else -> 0
}

/** 让分摘要（战报与配置页共用一句话口径）。 */
public fun BattleConfig.handicapSummary(index: Int): String? = when {
    !handicapped(index) -> null
    targetScores.getOrNull(index)?.takeIf { it > 0 } != null ->
        "目标 ${targetFor(index)} 分"
    startStepFor(index) > 0 -> "起点第 ${startStepFor(index) + 1} 格"
    tripleOnlyFor(index) -> "三倍独尊"
    else -> null
}
