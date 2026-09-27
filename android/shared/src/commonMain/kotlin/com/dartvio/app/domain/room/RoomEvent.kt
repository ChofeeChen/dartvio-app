package com.dartvio.app.domain.room

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.MatchConfig

/**
 * 在线房间的**事件类型**。
 *
 * 每个取值都是房间历史上「发生过的一件事」，而不是「房间此刻的样子」——
 * 见 [RoomEventReplay] 里关于为什么选事件流而不是状态同步的说明。
 */
enum class RoomEventType(val key: String) {

    /** 房间被创建。[RoomEventPayload.RoomCreated] */
    ROOM_CREATED("room_created"),

    /** 有人加入。 */
    MEMBER_JOINED("member_joined"),

    /** 有人离开；房主离开即解散。 */
    MEMBER_LEFT("member_left"),

    /** 切换准备状态。 */
    MEMBER_READY("member_ready"),

    /** 房主踢人。 */
    MEMBER_KICKED("member_kicked"),

    /** 房主开局。 */
    MATCH_STARTED("match_started"),

    /** 提交一个回合（1..3 支镖）。 */
    TURN_SUBMITTED("turn_submitted"),

    /** 撤回自己的上一手。 */
    TURN_UNDONE("turn_undone"),

    /** 房主发起再来一局。 */
    MATCH_REMATCH("match_rematch"),

    /** 房主改房间设置（公开/私密、是否允许观战）。 */
    SETTINGS_CHANGED("settings_changed");

    companion object {
        /**
         * 线上字符串 → 类型；**不认识就返回 null**，由重放整条丢弃。
         *
         * 宁可丢一个事件也不能抛异常：新版本加了一个事件类型、老客户端收到它，
         * 抛异常会让整个房间在老端上变成「打不开」，而丢掉它只是少看见一次设置变更。
         */
        fun fromKey(key: String?): RoomEventType? = values().firstOrNull { it.key == key }
    }
}

/** 事件的载荷。按类型分派，避免一个「万能 Map」让字段拼写在两端各写一遍。 */
sealed interface RoomEventPayload {

    data class RoomCreated(
        val name: String,
        val config: MatchConfig,
        val visibility: RoomVisibility,
        val allowSpectators: Boolean,
        val creatorName: String,
        val creatorAvatar: String,
        /** 房主的 PPR（加入者也要带一份，见 [MemberJoined.ppr]）；`0.0` = 还没打过。 */
        val creatorPpr: Double = 0.0
    ) : RoomEventPayload

    /**
     * 加入房间。
     *
     * [ppr] 随事件下发（默认值让**老端**的事件照样能反序列化）：对面那台手机没有
     * 这位成员的档案可查，而等候页要回答「要不要跟他打」—— 只看昵称答不了。
     */
    data class MemberJoined(val name: String, val avatar: String, val ppr: Double = 0.0) : RoomEventPayload

    data class MemberReady(val ready: Boolean) : RoomEventPayload

    data class MemberKicked(val memberId: String) : RoomEventPayload

    data class TurnSubmitted(val darts: List<Dart>, val clientTurnId: String) : RoomEventPayload

    data class SettingsChanged(
        val visibility: RoomVisibility? = null,
        val allowSpectators: Boolean? = null
    ) : RoomEventPayload

    /** 没有载荷的事件（离开、开局、撤回、再来一局）。 */
    data object Empty : RoomEventPayload
}

/**
 * 一条房间事件。
 *
 * [seq] 在**同一个房间内**严格递增且唯一（数据库 `unique(room_id, seq)` 兜底），
 * 它是排序与去重的唯一依据：**不能**用数据库自增 id 或 `created_at` 排序——
 * 自增 id 只保证插入先后，而两台手机并发插入时「先写的那一条」未必是「应该先发生的那一条」；
 * 时间戳更不可用，两台手机的时钟本来就不要求一致。
 */
data class RoomEvent(
    val roomId: String,
    val seq: Int,
    val actorId: String,
    val type: RoomEventType,
    val payload: RoomEventPayload
)

/**
 * 房间在某一时刻的完整状态：房间本身 + 进行中的权威对局。
 *
 * 它不是「存下来的东西」，而是**事件流重放出来的结果**——因此对局中途换手机、
 * 关掉 App 再回来，只要事件还在，状态就能一字不差地重建。
 */
data class RoomState(
    val room: Room? = null,
    val match: RoomMatch? = null,
    /** 已应用到的最大 seq；提交下一回合时用「它 + 1」。 */
    val seq: Int = 0
) {
    val isDissolved: Boolean get() = room == null
}

/**
 * 事件流重放（在线对战的内核）。
 *
 * ## 为什么是事件流，而不是「把状态同步上去」
 *
 * 在线对战没有服务器可以跑规则引擎（本项目规则引擎是 Kotlin，跑在手机里）。
 * 若让房主算完把结果状态推给别人，就成了「房主权威」——房主断网、退后台、被系统杀掉，
 * 整局就停在那里，而 beta 里最常发生的恰好就是「来消息了切出去一下」。
 *
 * 改成事件流之后：每台手机都拿着**同一份**事件，用**同一个** [RoomMatchRules] 重放。
 * 规则是纯函数，因此重放结果必然一致；谁掉线都不影响别人，回来时拉一次全量事件就能续上。
 * 「权威」从「某一台手机」变成了「事件序列本身」——它没有偏好，也不需要在线。
 *
 * ## 为什么 [RoomEventReplay.apply] 对非法事件是忽略而不是报错
 *
 * 事件是**别人写进数据库的**，本端无法阻止：一台老版本手机、一个手改数据库的请求，
 * 都可能产出一条「不合法」的事件（例如轮到 A 时 B 提交了回合）。
 * 抛异常会让整个房间在这台手机上打不开；而忽略它，这台手机看到的只是「那条没生效」，
 * 与其他人的画面一致（因为别人重放同一条时也会忽略它）。
 *
 * ## 时间
 *
 * 撤销窗口一律按 `now = 0` 参与裁定：[RoomMatchRules.submit] 写入的超时时刻是
 * 「提交时传入的 now + 窗口」，双方各自传 0 得到同一个值，因此窗口**永不因重放而过期**。
 * 实际的「还能不能撤」退化为一条不依赖时钟的规则：
 * **只有最后一个回合是自己投的才能撤**（下一个回合提交时会覆盖回退点）。
 * 这比时钟比较更可靠——两台手机的时钟不要求一致，而「对手已经投了」是事件里写死的事实。
 */
object RoomEventReplay {

    /** 重放一整段事件。输入**不需要**预先排序：这里按 [RoomEvent.seq] 排一次，顺序错了就是错的。 */
    fun replay(events: List<RoomEvent>): RoomState {
        var state = RoomState()
        for (event in events.sortedBy { it.seq }) {
            state = apply(state, event).copy(seq = maxOf(state.seq, event.seq))
        }
        return state
    }

    fun apply(state: RoomState, event: RoomEvent): RoomState = when (event.type) {
        RoomEventType.ROOM_CREATED -> createRoom(state, event)
        RoomEventType.MEMBER_JOINED -> joinMember(state, event)
        RoomEventType.MEMBER_LEFT -> leaveMember(state, event)
        RoomEventType.MEMBER_READY -> setReady(state, event)
        RoomEventType.MEMBER_KICKED -> kickMember(state, event)
        RoomEventType.MATCH_STARTED -> startMatch(state, event)
        RoomEventType.TURN_SUBMITTED -> submitTurn(state, event)
        RoomEventType.TURN_UNDONE -> undoTurn(state, event)
        RoomEventType.MATCH_REMATCH -> rematch(state, event)
        RoomEventType.SETTINGS_CHANGED -> changeSettings(state, event)
    }

    // ===== 各事件的重放规则 =====

    private fun createRoom(state: RoomState, event: RoomEvent): RoomState {
        val payload = event.payload as? RoomEventPayload.RoomCreated ?: return state
        // 已有房间时忽略：房间号是主键，重复创建只可能是重放到了旧数据或恶意写入。
        if (state.room != null) return state

        val creator = RoomMember(
            id = event.actorId,
            name = payload.creatorName,
            avatar = payload.creatorAvatar,
            isCreator = true,
            isReady = true,
            ppr = payload.creatorPpr.takeIf { it > 0.0 }
        )
        return state.copy(
            room = Room(
                id = event.roomId,
                name = payload.name,
                creatorId = event.actorId,
                config = payload.config,
                visibility = payload.visibility,
                allowSpectators = payload.allowSpectators,
                status = RoomStatus.WAITING,
                members = listOf(creator)
            )
        )
    }

    private fun joinMember(state: RoomState, event: RoomEvent): RoomState {
        val payload = event.payload as? RoomEventPayload.MemberJoined ?: return state
        val room = state.room ?: return state
        // 与 [RoomRules.join] 同构的门禁：对局中 / 已满 / 已在房内一律不生效。
        if (room.status != RoomStatus.WAITING) return state
        if (room.contains(event.actorId)) return state
        if (room.isFull) return state

        return state.copy(
            room = room.copy(
                members = room.members + RoomRules.newMember(
                    wireId = event.actorId,
                    rawName = payload.name,
                    avatar = payload.avatar,
                    isCreator = false,
                    ppr = payload.ppr.takeIf { it > 0.0 }
                )
            )
        )
    }

    private fun leaveMember(state: RoomState, event: RoomEvent): RoomState {
        val room = state.room ?: return state
        if (!room.contains(event.actorId)) return state
        // 房主离开 = 房间解散（与 [RoomRules.withoutMember] 返回 null 同义）。
        val next = RoomRules.withoutMember(room, event.actorId)
        return state.copy(room = next, match = if (next == null) null else state.match)
    }

    private fun setReady(state: RoomState, event: RoomEvent): RoomState {
        val payload = event.payload as? RoomEventPayload.MemberReady ?: return state
        val room = state.room ?: return state
        if (!room.contains(event.actorId)) return state
        return state.copy(room = RoomRules.withReady(room, event.actorId, payload.ready))
    }

    private fun kickMember(state: RoomState, event: RoomEvent): RoomState {
        val payload = event.payload as? RoomEventPayload.MemberKicked ?: return state
        val room = state.room ?: return state
        // 只有房主能踢；被踢的是自己时同样走「离开」的语义。
        if (!RoomRules.isHost(room, event.actorId)) return state
        val next = room.copy(members = room.members.filterNot { it.id == payload.memberId })
        return state.copy(room = next)
    }

    private fun startMatch(state: RoomState, event: RoomEvent): RoomState {
        val room = state.room ?: return state
        if (!RoomRules.isHost(room, event.actorId)) return state
        if (room.status != RoomStatus.WAITING) return state

        val started = RoomRules.started(room)
        return state.copy(
            room = started,
            // 非 X01 玩法没有权威对局（[RoomMatchRules.start] 返回 null）：
            // 房间照样进入对局中，但大家看到的只是静态观战帧 —— 与本切片的边界一致。
            match = RoomMatchRules.start(started)
        )
    }

    private fun submitTurn(state: RoomState, event: RoomEvent): RoomState {
        val payload = event.payload as? RoomEventPayload.TurnSubmitted ?: return state
        val match = state.match ?: return state

        // now = 0：见类注释「时间」一节——撤销窗口不参与跨端比较。
        val result = RoomMatchRules.submit(match, event.actorId, payload.darts, now = 0)
        return when (result) {
            is TurnSubmit.Applied -> state.copy(match = result.match)
            else -> state
        }
    }

    private fun undoTurn(state: RoomState, event: RoomEvent): RoomState {
        val match = state.match ?: return state
        val result = RoomMatchRules.undo(match, event.actorId, now = 0)
        return when (result) {
            is UndoResult.Applied -> state.copy(match = result.match)
            else -> state
        }
    }

    private fun rematch(state: RoomState, event: RoomEvent): RoomState {
        val room = state.room ?: return state
        val match = state.match ?: return state
        if (!RoomRules.isHost(room, event.actorId)) return state
        return state.copy(
            room = RoomRules.backToWaiting(room),
            match = RoomMatchRules.rematch(match)
        )
    }

    private fun changeSettings(state: RoomState, event: RoomEvent): RoomState {
        val payload = event.payload as? RoomEventPayload.SettingsChanged ?: return state
        val room = state.room ?: return state
        if (!RoomRules.isHost(room, event.actorId)) return state

        var next = room
        payload.visibility?.let { next = RoomRules.withVisibility(next, it) }
        payload.allowSpectators?.let { next = RoomRules.withAllowSpectators(next, it) }
        return state.copy(room = next)
    }
}
