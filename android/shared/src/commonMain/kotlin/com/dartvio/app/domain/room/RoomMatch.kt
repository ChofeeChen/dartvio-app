package com.dartvio.app.domain.room

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.rulesCode
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.Player
import com.dartvio.app.domain.model.TurnResult
import com.dartvio.app.domain.model.X01LegState
import com.dartvio.app.domain.rules.X01Rules

/**
 * 房间内正在进行的**权威对局**（服务端权威，BL-001 硬约束）。
 *
 * ## 为什么需要它
 *
 * 开局那一刻的观战帧（[RoomRules.initialSpectatorSnapshot]）是一张**静态照片**：
 * 第 1 局、无回合历史、双方满目标分。若对局期间没有别的东西推进它，
 * 「观战」就永远是一张 501:501 的封面照 —— 客人重连后看到的也是这张照片。
 *
 * 本类补上缺的那一半：**状态**。它由主机进程持有，每次回合提交向前走一步，
 * 并把自己此刻的样子作为唯一对外表示（[RoomMatchRules.snapshot]）广播出去。
 *
 * ## 边界（本切片有意不做）
 *
 * - **只支持 X01**。[RoomMatchRules.start] 对其它玩法返回 null，调用方据此走「无权威对局」分支。
 *   原因：[SpectatorSnapshot] 只有一个 `score` 字段，而 Cricket 要表达的是「六个分区各封闭到几」，
 *   硬塞进去只会让模型含混 —— 那需要先扩协议，属于独立切片。
 * - **无版本号、无撤回**。提交按「谁轮到谁说话 + 服务端到达顺序」裁定；
 *   契约里的 `matchVersion` 与 `turn_undone` / `turn_locked` 留待接正式后端时再加。
 * - **不持久化**。进程被杀即丢局（与房间状态同一取舍）。
 */
data class RoomMatch(
    val roomId: String,
    val roomName: String,
    val config: MatchConfig,
    /** 出手顺序 = 房间成员顺序，与 [leg] 的玩家下标一一对应。 */
    val members: List<RoomMember>,
    val leg: X01LegState,
    /**
     * 成员 id → 已赢局数。
     *
     * 不能只依赖 [com.dartvio.app.domain.model.X01PlayerState.legsWon]：那个字段是给本地对局用的，
     * 服务端从不更新它（[X01Rules.newLeg] 每次都把它置 0）。记在这里，跨局累计才有一处明确的真相。
     */
    val legsWon: Map<String, Int> = emptyMap(),
    /** 已结算的回合流水（旧 → 新）。一帧最多带走 [RoomMatchRules.MAX_TURNS_IN_FRAME] 条。 */
    val turns: List<SpectatorTurn> = emptyList(),
    val isFinished: Boolean = false,
    val winnerId: String? = null,
    /**
     * 对局版本（M5 T6）：**每次生效的提交 +1**，开局为 0。
     *
     * 它是「我手里的这份比分新不新」的唯一判据。客户端提交时把它带上（`expect_version`），
     * 服务端发现与权威版本不一致就回冲突 —— 说明这份提交是**基于一个已经过去的局面**
     * 做出来的（对手刚投完、或自己上一次提交的回程丢了）。
     *
     * 只在**对局帧**里走（[RoomMatchRules.snapshot]），不进房间快照：房间快照每次成员变化
     * 都要发一份，让所有大厅里的人为对局版本号付带宽没有道理。
     */
    val version: Int = 0,

    /**
     * 已发出的**最大**回合序号（M5 T7）。
     *
     * 撤回会把流水回退一条，但**不回退这个数**：流水是历史、不是指针，
     * 让撤回去掉的那一条的 `seq` 被下一回合复用，两端就会出现「两个不同的回合，同一个 seq」，
     * 而依赖 seq 排序与去重的地方（帧、统计）会静默丢掉其中一条。
     */
    val turnSeq: Int = 0,

    /**
     * 最近一次生效提交的**回退点**（M5 T7）。null = 没有可撤回的回合。
     *
     * 存整份状态而不是「减去这一回合的分」：X01 的一手可能同时改动剩余分、
     * 当前玩家下标、局号（`BUST` 与收镖都会改变走向），
     * 靠「反推」去还原等于把规则再实现一遍，而两份实现迟早会不一致。
     */
    val undoPoint: UndoPoint? = null,

    /**
     * 撤销窗口的截止时刻（**服务端**时钟，M5 T7）。null = 没有待撤回的回合。
     *
     * 时刻由服务端写入：客户端时钟与服务端无关（换设备、改系统时间都能让它偏离），
     * 若由客户端自报「我还有 3 秒」，那就是把裁定权交出去了。
     */
    val lastTurnUndoableUntil: Long? = null,

    /** 锁定时刻（服务端时钟，M5 T7）。非 null = 撤销窗口已关闭，不可再撤。 */
    val lockedAt: Long? = null
) {

    /** 轮到谁投。规则层与帧派生都从这里取，避免「当前玩家」出现两种算法。 */
    val currentPlayerId: String get() = members[leg.currentPlayerIndex].id

    /** 已结算回合数。取 `seq` 而不是 `turns.size`：流水会被截断，序号不会。 */
    val turnCount: Int get() = turns.lastOrNull()?.seq ?: 0
}

/** 一次回合提交的裁定结果。 */
sealed interface TurnSubmit {

    /** 已生效。[turn] 是本次结算出来的那条流水，广播与测试都用它。 */
    data class Applied(val match: RoomMatch, val turn: SpectatorTurn) : TurnSubmit

    /** 房间没有进行中的对局（未开局，或玩法不支持联机对局）。 */
    data object NoMatch : TurnSubmit

    /** 现在不轮到你投。 */
    data object NotYourTurn : TurnSubmit

    /** 对局已结束（收镖定胜负之后仍在提交）。 */
    data object Finished : TurnSubmit

    /** 镖本身不合法（数量或落点）。[message] 直接面向用户。 */
    data class InvalidDarts(val message: String) : TurnSubmit
}

/**
 * 一次提交在**权威侧**的处置结果（M5 T6）。
 *
 * 与 [TurnSubmit] 的分工：[TurnSubmit] 是纯规则对「这一回合算不算」的裁定；
 * 这里多包了两件**只有持有状态的一方才知道**的事：
 *
 * - [Applied.replayed] / [Rejected.replayed]：这次提交意图是不是**见过**（幂等重放）；
 * - [VersionConflict]：客户端手里的版本是不是**过期**了。
 *
 * 两者都必须与「读-裁定-写回」在同一把锁内完成（见 `RoomMatchStore.submit`）：
 * 幂等命中若在锁外，两条并发的同 id 提交就能双双通过「没见过」这一步，
 * 从而在同一个回合上结算两次 —— 那正是 T6 要修的现象。
 */
sealed interface TurnOutcome {

    /** 生效。[replayed] = true 时本次**没有**再次结算，[match] 是首次结算出的那份状态。 */
    data class Applied(val match: RoomMatch, val turn: SpectatorTurn, val replayed: Boolean) : TurnOutcome

    /** 未生效。[replayed] = true 表示这是同一次提交意图的重试，答复与首次一致。 */
    data class Rejected(val rejection: TurnRejection, val replayed: Boolean) : TurnOutcome

    /**
     * 客户端预期版本落后于权威版本：**没有结算**，状态一字未动。
     *
     * [current] 是权威版本，客户端应以最新帧为准重新组织这次提交，而不是静默重投 ——
     * 重投会把「基于旧局面录的三支镖」硬塞进新回合里。
     */
    data class VersionConflict(val expected: Int, val current: Int) : TurnOutcome

    /** 房间没有进行中的对局（与 [TurnSubmit.NoMatch] 同义）。 */
    data object NoMatch : TurnOutcome
}

/**
 * 一次生效提交的**回退点**（M5 T7）：这一手打下去之前的对局状态。
 *
 * [legsWon] 也一并留着，因为它决定了「这一手能不能撤」：
 * 撤回只能退掉**这一手的比分**，而赢下一局是跨局的账（[RoomMatch.legsWon]），
 * 退它会连「这局谁赢了」一起改掉 —— 那不是撤回，是重赛。
 */
data class UndoPoint(
    /** 提交者。只有本人能撤回（防「对手替我撤回」与「反复撤回刷分」）。 */
    val playerId: String,
    /** 被撤回回合的 `seq`，用于给两端提示「第几回合被撤了」。 */
    val seq: Int,
    val leg: X01LegState,
    val legsWon: Map<String, Int>,
    val turns: List<SpectatorTurn>
)

/** 一次撤回在**权威侧**的处置结果（M5 T7）。与 [TurnOutcome] 同构：生效 / 不生效 / 为什么。 */
sealed interface UndoResult {

    /** 已回退。[seq] 是被撤掉的回合号；流水已回退，但 [RoomMatch.turnSeq] **不回退**。 */
    data class Applied(val match: RoomMatch, val seq: Int) : UndoResult

    /** 房间没有进行中的对局。 */
    data object NoMatch : UndoResult

    /**
     * 没有可撤回的回合：还没投过、上一手已经换局（[RoomMatch.legsWon] 变了）、或对局已结束。
     *
     * 这三种合为一种，因为界面处置相同（按钮不出现）；拆开只会让调用方写三份一样的分支。
     */
    data object NotUndoable : UndoResult

    /** 窗口已关闭（超时，或对手已经开始操作）。 */
    data object Locked : UndoResult

    /** 上一回合不是你投的。 */
    data object NotYourTurn : UndoResult
}

/**
 * 一次弃权在**权威侧**的处置结果（M5 T8）。
 *
 * 与 [UndoResult] 同构：生效 / 不生效 / 为什么不生效。之所以不复用它，是因为两者的
 * 「不生效」取值完全不同（撤回有窗口问题，弃权没有），合成一个会让两边都多出不可能出现的分支。
 */
sealed interface ForfeitResult {

    /** 已判负。[winnerId] 是对手 —— 不是「当前领先的人」，理由见 [RoomMatchRules.forfeit]。 */
    data class Applied(val match: RoomMatch, val winnerId: String) : ForfeitResult

    /** 房间没有进行中的对局。 */
    data object NoMatch : ForfeitResult

    /** 离线的人不是本局的参与者（例如观战者掉线）：他的离线与本局无关。 */
    data object NotInMatch : ForfeitResult

    /** 对局已经结束，没有可判的胜负。 */
    data object AlreadyOver : ForfeitResult
}

/** 一次提交为什么没生效。与 [TurnSubmit] 的被拒分支一一对应，去掉了「不可能出现在答复里」的那两种。 */
sealed interface TurnRejection {

    data object NotYourTurn : TurnRejection

    data object Finished : TurnRejection

    /** 镖本身不合法。[message] 直接面向用户。 */
    data class InvalidDarts(val message: String) : TurnRejection
}

/**
 * 屏幕前这个人的对局视图：权威帧 + 「我能不能投」。
 *
 * [myTurn] 必须在数据层算：判定要用**线上身份**（帧里 `player.id` 就是连接 id），
 * 而 UI 只认识 [LocalUser.ID]。放进 UI 就等于逼每个界面各自再做一次别名翻译，
 * 而漏翻的表现是「轮到我但键盘不出现」——用户无法自查。
 */
data class RoomMatchView(
    val snapshot: SpectatorSnapshot,
    val myTurn: Boolean,
    /**
     * 「我」在这份帧里的**线上身份**；尚未握手时为 null。
     *
     * 界面要它只为一件事：在玩家卡片与回合记录的昵称旁标出「（本机）」。
     * 这个判断必须在这里做 —— 帧里的 id 是连接 id，界面只认识 `LocalUser.ID`，
     * 在 UI 里自行比对的结果必然是「永远标不出自己」。
     */
    val selfId: String? = null,
    /**
     * 我此刻还能撤回上一回合的毫秒数（M5 T7）；0 = 不能撤。
     *
     * 在数据层算而不是在 UI 里比 id：帧里的 `player_id` 是**线上身份**（连接 id），
     * 与 UI 认识的 [LocalUser.ID] 是两套编号，而漏掉这次翻译的表现是
     * 「对手的回合结束后我这边冒出一个撤回按钮」。
     */
    val undoWindowMs: Long = 0,

    /**
     * 对局是**怎么**结束的（M5 T8）；正常收镖时为 null（那种结局帧里已经写清楚了）。
     *
     * 它由服务端下发（弃权判负时随 `match_finished` 到达），客户端不自行推断 ——
     * 「谁赢了」一旦有第二个来源，两端迟早会给出不同的结局。
     */
    val finish: MatchFinish? = null
) {

    val currentPlayer: SpectatorPlayer? get() = snapshot.players.firstOrNull { it.isActive }

    /** 对局是否已经打完。含休闲模式（`legsToWin <= 0`）的「单局定胜负」：那一局的收镖就是终局。 */
    val isOver: Boolean
        get() = snapshot.isFinished ||
            (snapshot.legsToWin <= 0 && snapshot.turns.lastOrNull()?.isCheckout == true)

    /** 现在能不能录镖。 */
    val canThrow: Boolean get() = myTurn && !isOver

    /** 现在能不能撤回上一回合（T7）。 */
    val canUndo: Boolean get() = undoWindowMs > 0
}

/** 把权威帧投影成「我」的视图。[wireId] 为 null（尚未握手）时一律视为不是我的回合。 */
fun SpectatorSnapshot.forSelf(wireId: String?): RoomMatchView = RoomMatchView(
    snapshot = this,
    myTurn = wireId != null && players.any { it.isActive && it.id == wireId },
    selfId = wireId,
    // 只有**那一手是我投的**才有窗口：撤回别人的回合等于替对手改分。
    undoWindowMs = undo?.takeIf { it.playerId == wireId }?.windowMs ?: 0
)

/**
 * 房间对局的纯规则（M5 实时同步）。
 *
 * 与 [RoomRules] 同样的取舍：入参是数据、出参是数据，不持有状态、不碰协程、不需要 Android 运行时，
 * 因此边界能被单测完全枚举（见 `RoomMatchRulesTest`），
 * 且**服务端裁定与客户端预校验可以共用同一份实现**（本切片客户端只做「不复制规则」的姿势：见 `RoomMatchScreen`）。
 */
object RoomMatchRules {

    /** 一个回合的镖数上限（PRD M2）。 */
    const val MAX_DARTS_PER_TURN = 3

    /**
     * 一帧里最多带多少条回合流水。
     *
     * 帧是全量快照，每回合整体重发一次；长盘对局（501 打到 15+ 回合很常见）若不带上限，
     * 帧会随时间线性膨胀，而局域网里最贵的资源是「每回合都要重发的这一份」。
     * 30 条足够回答「刚才发生了什么」；更早的历史属于统计（M9）的职责，不该躺在对局帧里。
     */
    const val MAX_TURNS_IN_FRAME = 30

    /**
     * 撤销窗口长度（M5 T7，契约 §11.4 的 `undoWindow=5s`）。
     *
     * 5 秒是被两件事夹出来的：短到「手一抖投错了」能来得及点（真人反应 + 一次点击），
     * 又短到对手不会盯着按钮发呆。它同时也是**服务端**锁定计时器的时长 ——
     * 两端各记一份会漂移，而漂移的表现是「按钮还在，点了却说已锁定」。
     */
    const val UNDO_WINDOW_MS = 5_000L

    /**
     * 本切片是否为该玩法维护权威对局。
     *
     * 单独暴露成一个函数，而不是把判断在别处再写一遍：**建局**（服务端）与**分流**
     * （界面该进对局页还是观战页，经 `RoomRepository.hasAuthoritativeMatch`）必须对
     * 同一份名单负责。名单一旦有两份，「加了玩法支持却仍进观战页」只会表现为
     * 「房间开局后点进去没有键盘」——没人会想到去改导航。
     */
    fun supportsLiveMatch(matchType: MatchType): Boolean = matchType == MatchType.X01

    /**
     * 开局：为房间建立权威对局。
     *
     * @return null 表示本切片不为该玩法维护权威对局，调用方应走「无对局」分支
     *   （观战仍能拿到静态首帧，只是不会推进）。
     */
    fun start(room: Room): RoomMatch? {
        if (!supportsLiveMatch(room.config.matchType)) return null
        if (room.members.isEmpty()) return null

        return RoomMatch(
            roomId = room.id,
            roomName = room.name,
            config = room.config,
            members = room.members,
            leg = X01Rules.newLeg(room.config, players(room.members), legNumber = 1)
        )
    }

    /**
     * 裁定一次回合提交。**这是唯一能让比分前进的入口**。
     *
     * 判定顺序是有意的：先看「还能不能投」，再看「该不该你投」，最后才看镖本身。
     * 反过来（先校验镖）会把「对局已结束」报成「镖非法」，用户会去改镖而不去重开一局。
     */
    fun submit(match: RoomMatch, memberId: String, darts: List<Dart>, now: Long = 0L): TurnSubmit {
        if (match.isFinished) return TurnSubmit.Finished
        if (match.currentPlayerId != memberId) return TurnSubmit.NotYourTurn

        validate(darts)?.let { return it }

        val (nextLeg, outcome) = X01Rules.applyTurn(match.leg, darts)

        // 取 [RoomMatch.turnSeq] 而不是「最后一条流水 +1」：撤回会让流水回退一条，
        // 而序号是历史、不是指针（见该字段的注释）。
        val seq = match.turnSeq + 1
        val turn = SpectatorTurn(
            seq = seq,
            playerName = nameOf(match, memberId),
            // 与本地对局的流水文本同源（`Dart.label()`），这样观战页的「T20 T20 D20」在两种来源下长得一样。
            darts = darts.joinToString(" ") { it.label() },
            scored = outcome.scored,
            remaining = outcome.remainingAfter,
            isBust = outcome.result == TurnResult.BUST,
            isCheckout = outcome.won
        )

        val legsWon = if (outcome.won) {
            match.legsWon + (memberId to (match.legsWon[memberId] ?: 0) + 1)
        } else {
            match.legsWon
        }
        val turns = (match.turns + turn).takeLast(MAX_TURNS_IN_FRAME)
        val matchWon = outcome.won && legsWon.getValue(memberId) >= legsTarget(match.config)

        val advanced = if (outcome.won && !matchWon) {
            // 赢下一局但整场没结束：重开一局。先手固定回 0 号成员，与本地对局（GameViewModel 同样调用
            // X01Rules.newLeg）严格一致 —— 若这里改成「上局赢家先手」，同一份配置在单机与联机下就会
            // 跑出不同的比分，而这种差异只有真机对拍才会被发现。
            match.copy(
                leg = X01Rules.newLeg(match.config, players(match.members), match.leg.legNumber + 1),
                legsWon = legsWon,
                turns = turns
            )
        } else {
            match.copy(
                leg = nextLeg,
                legsWon = legsWon,
                turns = turns,
                isFinished = matchWon,
                winnerId = if (matchWon) memberId else null
            )
        }

        // 版本只在**生效**时 +1（T6）：被拒的提交没有改变任何状态，
        // 若它也递增，客户端会因为一次「不是你的回合」而以为自己手里的帧过期了。
        return TurnSubmit.Applied(
            advanced.copy(
                version = match.version + 1,
                turnSeq = seq,
                // 回退点必须是「这一手之前」的状态，因此取 `match`（旧）而不是 `advanced`（新）。
                undoPoint = UndoPoint(
                    playerId = memberId,
                    seq = seq,
                    leg = match.leg,
                    legsWon = match.legsWon,
                    turns = match.turns
                ),
                // 每一次生效提交都开一个**新的**撤销窗口，并解开上一手的锁定。
                lastTurnUndoableUntil = now + UNDO_WINDOW_MS,
                lockedAt = null
            ),
            turn
        )
    }

    /**
     * 撤回上一回合（M5 T7）。**只能由服务端调用**：客户端不得本地回滚比分（红线）。
     *
     * 判定顺序是有意的：先问「有没有可撤的东西」，再问「是不是你投的」，最后才问「还来不来得及」。
     * 把窗口放在最后，是因为前两条无论窗不窗口都不该撤；反过来写会让「对手替我撤回」
     * 在窗口外报成「已锁定」—— 一个会诱导用户反复重试的错误归因。
     */
    fun undo(match: RoomMatch, memberId: String, now: Long): UndoResult {
        val point = match.undoPoint
            ?: return UndoResult.NotUndoable

        // 已结束的对局不撤：胜负一旦落定就是历史，撤回它等于重赛。
        if (match.isFinished) return UndoResult.NotUndoable

        // 上一手赢下了一局（`legsWon` 变了）：撤回只能退这一手的比分，改不了跨局的账。
        if (point.legsWon != match.legsWon) return UndoResult.NotUndoable

        if (point.playerId != memberId) return UndoResult.NotYourTurn

        // 窗口到点、或已被对手的操作提前锁死。
        if (match.lockedAt != null || (match.lastTurnUndoableUntil ?: Long.MIN_VALUE) <= now) {
            return UndoResult.Locked
        }

        return UndoResult.Applied(
            match.copy(
                leg = point.leg,
                legsWon = point.legsWon,
                turns = point.turns,
                // 撤回不允许连着撤两次：回退点一并清空，下一手提交才会再开出新窗口。
                undoPoint = null,
                lastTurnUndoableUntil = null,
                lockedAt = null,
                version = match.version + 1,
                // turnSeq **不动**：序号是历史，撤掉的那一条不会被下一手复用。
                turnSeq = match.turnSeq
            ),
            seq = point.seq
        )
    }

    /**
     * 关闭撤销窗口（M5 T7）：对手开始操作、或窗口到点时由服务端调用。
     *
     * @return 锁定后的对局；**null 表示本来就没有开着的窗口**，调用方据此决定是否广播 ——
     *   「本来就没窗口还广播一次 turn_locked」会让所有端各推一帧无变化的快照。
     */
    fun lock(match: RoomMatch, now: Long): RoomMatch? {
        if (match.undoPoint == null || match.lockedAt != null) return null
        return match.copy(lockedAt = now, lastTurnUndoableUntil = null)
    }

    /**
     * 判定某成员**弃权**（M5 T8）：对局以对手获胜结束。
     *
     * ## 胜者为什么取「另一个参与者」而不是「当前领先的人」
     *
     * 弃权是惩罚性的。判给领先者，等于承认「落后的一方拔网线」是个划算的选择 ——
     * 那正是要靠这条规则堵住的行为。而两人对局里「不是弃权者的那一个」是唯一确定的对手，
     * 不需要任何比较，也就没有「领先多少才算领先」这种可以争的余地。
     *
     * ## 比分一字不改
     *
     * 弃权改的是**结局**，不是已经投出来的分：把剩余分改成 0 会让回看与统计看到一份
     * 从未发生过的比分（而 T10 的落库口径还悬着，先不制造需要额外解释的数字）。
     */
    fun forfeit(match: RoomMatch, loserId: String): ForfeitResult {
        if (match.isFinished) return ForfeitResult.AlreadyOver
        if (match.members.none { it.id == loserId }) return ForfeitResult.NotInMatch

        val winner = match.members.firstOrNull { it.id != loserId }
            ?: return ForfeitResult.NotInMatch

        return ForfeitResult.Applied(
            match.copy(
                isFinished = true,
                winnerId = winner.id,
                // 判负同样是一次权威变更：持旧版本的客户端应据此刷新，而不是继续提交。
                version = match.version + 1
            ),
            winner.id
        )
    }

    /**
     * 再来一局（M5 T9）：**同一批成员、同一份配置**，开新的一场。
     *
     * 清零的是「这一场」的账（局数、流水、胜负、版本、撤销窗口），
     * 保留的是「这个房间」的东西（成员与配置）。
     *
     * ## 版本为什么归零
     *
     * 一场新的对局从 0 开始计数，而客户端手里那份帧（上一场的最后版本）会随新帧被替换。
     * 若沿用旧版本号，所有人下一次提交都会撞一次冲突 —— 于是「再来一局」的第一手
     * 必然投不进去，而报错文案会把人引向「网络不好」。
     *
     * ## 为什么不再读一次房间
     *
     * 成员与配置由 [start] 在开局那一刻从房间读一次（见 [RoomMatch.members]）。
     * 这里**不重新读**：结算页上站着的可能已经不是刚才那批人（有人中途加入），
     * 而「再来一局」换掉对手，比「少一个人就开不了局」更让人措手不及。
     * 成员真的变了，由房主在等候页重新开局时读（那时 [start] 会读一次）。
     */
    fun rematch(match: RoomMatch): RoomMatch = match.copy(
        leg = X01Rules.newLeg(match.config, players(match.members), legNumber = 1),
        legsWon = emptyMap(),
        turns = emptyList(),
        isFinished = false,
        winnerId = null,
        version = 0,
        turnSeq = 0,
        undoPoint = null,
        lastTurnUndoableUntil = null,
        lockedAt = null
    )

    /**
     * 权威状态 → 观战帧。
     *
     * 这是对局**唯一**的对外表示：观战页、对局页、重连补帧看到的都是它，
     * 因此「主机算的分」与「客人看到的分」不存在第二处来源。
     */
    fun snapshot(match: RoomMatch): SpectatorSnapshot = SpectatorSnapshot(
        roomId = match.roomId,
        roomName = match.roomName,
        configName = match.config.displayName,
        leg = match.leg.legNumber,
        legsToWin = match.config.legsToWin,
        rulesShort = match.config.rulesCode(),
        players = match.members.mapIndexed { index, member ->
            // getOrNull 而不是 []：帧是「发出去的东西」，任何一处越界都只应该让这一格退回目标分，
            // 而不是让整场对局在广播时崩掉（成员与 leg.players 由 start/submit 保证同序同长）。
            val remaining = match.leg.players.getOrNull(index)?.remaining ?: match.config.targetScore
            SpectatorPlayer(
                id = member.id,
                name = member.name,
                avatar = member.avatar,
                legsWon = match.legsWon[member.id] ?: 0,
                score = remaining,
                isActive = index == match.leg.currentPlayerIndex
            )
        },
        turns = match.turns,
        version = match.version,
        // 窗口只在「没被锁定」时下发：锁定后帧里就不该再有「可以撤回」的暗示，
        // 否则客户端要靠自己的计时器判断按钮该不该消失 —— 那正是两端不一致的开端。
        undo = match.undoPoint?.takeIf { match.lockedAt == null }?.let { point ->
            SpectatorUndo(playerId = point.playerId, seq = point.seq, windowMs = UNDO_WINDOW_MS)
        }
    )

    // ===== 内部 =====

    /** 镖的合法性。数量与落点分开报，用户才知道该改哪里。 */
    private fun validate(darts: List<Dart>): TurnSubmit.InvalidDarts? = when {
        darts.isEmpty() ->
            TurnSubmit.InvalidDarts("一个回合至少需要 1 支镖")

        darts.size > MAX_DARTS_PER_TURN ->
            TurnSubmit.InvalidDarts("一个回合最多 $MAX_DARTS_PER_TURN 支镖，收到 ${darts.size} 支")

        else -> darts.firstOrNull { !X01Rules.isValidDart(it) }?.let { dart ->
            TurnSubmit.InvalidDarts("落点非法：${dart.number} x ${dart.multiplier}")
        }
    }

    /**
     * 赢几局算赢下整场。
     *
     * `legsToWin <= 0` 是休闲模式的编码（见 [MatchConfig.legsToWin]），语义是「一局定胜负」，
     * 因此取 1 而不是 0 —— 取 0 会让 `legsWon(1) >= 0` 恒真，第一局收镖就结束，看起来像对的，
     * 实际上把「休闲模式可以连打几局」这件事悄悄改掉了。
     */
    private fun legsTarget(config: MatchConfig): Int =
        if (config.legsToWin <= 0) 1 else config.legsToWin

    private fun players(members: List<RoomMember>): List<Player> = members.map {
        Player(id = it.id, name = it.name, avatar = it.avatar)
    }

    private fun nameOf(match: RoomMatch, memberId: String): String =
        match.members.firstOrNull { it.id == memberId }?.name ?: LocalUser.DEFAULT_NAME
}
