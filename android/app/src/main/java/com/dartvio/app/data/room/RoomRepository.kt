package com.dartvio.app.data.room

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.room.JoinResult
import com.dartvio.app.domain.room.Room
import com.dartvio.app.domain.room.RoomDraft
import com.dartvio.app.domain.room.RoomJoinSource
import com.dartvio.app.domain.room.RoomMatchView
import com.dartvio.app.domain.room.RoomVisibility
import com.dartvio.app.domain.room.SpectatorSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 房间数据源（M6 比赛大厅与房间）。
 *
 * 所有方法都是「语义操作」，不暴露传输细节：
 * V0.1 由 [LocalRoomRepository] 在内存中模拟；
 * 联网后换成 OnlineRoomRepository（云端在线），UI 与 ViewModel 不需要改动。
 */
interface RoomRepository {

    /** 大厅公开房间列表（不含私密房间）。 */
    val publicRooms: StateFlow<List<Room>>

    /** 按房间号读取房间（私密房间也可读）。 */
    fun room(roomId: String): Room?

    /** 订阅单个房间的变更（等候页用）。房间被关闭时发射 null。 */
    fun observeRoom(roomId: String): Flow<Room?>

    /** 订阅观战快照（观战页用）。房间不可观战时发射 null。 */
    fun observeSpectator(roomId: String): Flow<SpectatorSnapshot?>

    /**
     * 订阅**联机对局**视图（对局页用）：权威帧 + 「现在是不是该我投」。
     *
     * 不与 [observeSpectator] 合并：观战者只需要比分，而参与者还需要知道「能不能录镖」——
     * 那个判断要用**线上身份**去比对（帧里的 id 是连接 id），只有数据层知道本机在线上是谁。
     * 放进 UI 就意味着每个界面都要自己再做一次身份翻译，而漏翻的表现是「轮到我了但键盘不出现」。
     *
     * 单机 Mock 没有权威对局，恒发 null（见 [LocalRoomRepository.observeMatch]）。
     */
    fun observeMatch(roomId: String): Flow<RoomMatchView?>

    /**
     * 订阅房间里的**缺席倒计时**（M5 T8）：成员线上 id → 剩余毫秒。
     *
     * 与 [observeRoom] 分开，是因为两者的更新节奏完全不同：房间快照只在成员进出时变，
     * 而这个数字每秒都在走。合进房间快照会让整个等候页每秒重绘一次 —— 而它上面
     * 真正每秒变的只有这一行字。
     *
     * **它不判定结局**：判负由服务端说了算，结果只会作为一帧（[RoomMatchView.finish]）到达。
     * 客户端自己数到 0 就宣布「我赢了」，是红线里最要避开的那件事。
     */
    fun observeAbsence(roomId: String): Flow<Map<String, Long>>

    /**
     * 提交本回合的飞镖（1..3 支），由主机裁定后广播回来。
     *
     * 参数是「我投了什么」，不是「现在应该几分」—— 算分只发生在权威侧（BL-001）。
     * 裁定结果不通过返回值回来，而是作为一帧新比分经 [observeMatch] / [observeSpectator] 到达，
     * 因此界面**不需要**在提交后自己改本地比分：它只需要等帧。
     *
     * @param clientTurnId 这次「提交意图」的 id（M5 T6）：**重试必须复用同一个**，
     *   服务端据此认出「这是同一个意图又来了一次」并直接回首次的结果，
     *   而不是把它当成一次新提交再结算一遍（弱网连点两次就会多走一回合）。
     *   一次新的提交（另一个回合、或改过镖之后）必须用新的 id。
     */
    fun submitTurn(roomId: String, darts: List<Dart>, clientTurnId: String)

    /**
     * 提交答复流（M5 T6）：区分「生效 / 版本冲突 / 别的拒绝」。
     *
     * 它与帧互补而不是替代：帧回答「现在几分」，这条流回答「我这一次提交的下场」。
     * 界面据此决定是**清掉草稿**（冲突）还是**留着草稿重试**（超时或被拒）——
     * 两者处置相反，而只靠等帧无法区分。
     */
    val submitAcks: SharedFlow<TurnAck>

    /**
     * 撤回上一回合（M5 T7）。
     *
     * 撤回的结果作为**一帧新比分**到达（剩余分回退回去了），与提交走的是同一条路径 ——
     * 界面因此不需要处理「撤回了多少分」，它只需要等帧。
     *
     * 只有**撤不成**才需要一句话（过了窗口 / 那一手不是你投的），那时答复复用 [submitAcks]
     * 的 [TurnAck.Rejected]：界面的处置与提交被拒完全一样（留一句提示，什么都不改），
     * 为此另开一条答复流只会多一处「忘了 collect」的地方。
     *
     * 只允许撤回**自己投的最后一手**（不做跨回合撤回）。
     */
    fun undoTurn(roomId: String)

    /**
     * 告诉主机「我开始操作这一回合了」（M5 T7，契约 §11.4 的 `player_activated`）。
     *
     * 它关闭**上一手**的撤销窗口：对手已经照着新比分开始录镖了，这时候再把上一手撤回去，
     * 等于让他的输入作废。只靠 5 秒超时覆盖不了这种情况 —— 超时只覆盖「对手在发呆」。
     */
    fun notifyTurnActivated(roomId: String)

    /**
     * 这个房间此刻有没有**权威对局**，也就是「进去能录镖吗」。
     *
     * 由数据源回答，而不是让界面猜：答案同时取决于两件只有数据源知道的事 ——
     * 本机是不是联机（单机 Mock 没有权威对局）、该玩法的对局是否被支持
     * （见 [RoomMatchRules.supportsLiveMatch]）。
     *
     * 界面据此在「对局页」与「观战页」之间分流，判错的两种表现用户都直接看得到：
     * 点进去一个不会响应的键盘，或者明明能打却被按在只读比分板上。
     */
    fun hasAuthoritativeMatch(roomId: String): Boolean

    /** 创建房间，返回新房间（创建者自动成为房主并已准备）。 */
    fun createRoom(draft: RoomDraft, creatorName: String, creatorAvatar: String): Room

    /** 通过房间号加入。 */
    /**
     * 加入房间。
     *
     * @param source 从哪儿进来的（大厅 / 房间号）。只有在线数据源用它做进房来源统计，
     *       局域网那两套直接忽略 —— 它们不存在"发现渠道"这个问题。
     */
    fun joinRoom(
        roomId: String,
        name: String,
        avatar: String,
        source: RoomJoinSource = RoomJoinSource.CODE
    ): JoinResult

    /** 离开房间；房主离开则房间关闭。 */
    fun leaveRoom(roomId: String, memberId: String)

    /** 设置准备状态。 */
    fun setReady(roomId: String, memberId: String, ready: Boolean)

    /** 房主开关：是否允许观战（M6 F6.8）。 */
    fun setAllowSpectators(roomId: String, allowed: Boolean)

    /** 房主开关：公开 / 私密（M6 F6.7）。 */
    fun setVisibility(roomId: String, visibility: RoomVisibility)

    /** 房主踢人。 */
    fun kickMember(roomId: String, memberId: String)

    /** 房主开始对局。 */
    fun startMatch(roomId: String)

    /**
     * 房主发起**再来一局**（M5 T9）：房间回到等候态，对局局数归零，成员与配置保留。
     *
     * 结算页上只有房主能点它 —— 客人点了会让房主那边「还没看结果就被拉回等候页」。
     * 客人端不需要轮询：房间回到等候态这一变化本身就随房间快照到达（[observeRoom]），
     * 界面据此自己回到等候页。
     *
     * 单机 Mock 恒空（没有权威对局，也就没有「上一场」可再来）。
     */
    fun rematch(roomId: String)

    /** 下拉刷新（联网后为重新拉取列表）。 */
    suspend fun refresh()

    /** 生成一个尚未占用的 6 位房间号。 */
    fun newRoomId(): String

    /**
     * 本机**此刻是否还占着一间没结束的房间**（一人一房，PRD D10 / A6）。
     *
     * 默认 null = 「数据源不跟踪这件事」：单机 Mock 与局域网那两套没有跨会话的房间占用
     * 可言，界面据此不做限制；只有在线数据源真的记录它。
     * 用默认实现而不是给三个实现都补一遍，是为了不让一条界面规则牵动三个数据源。
     */
    fun activeRoomId(): String? = null
}
