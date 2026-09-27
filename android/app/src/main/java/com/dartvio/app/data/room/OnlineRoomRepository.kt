package com.dartvio.app.data.room

import android.content.Context
import com.dartvio.app.data.MatchRepository
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.room.JoinResult
import com.dartvio.app.domain.room.RoomLiveState
import com.dartvio.app.domain.stats.StatsCalculator
import com.dartvio.app.domain.room.LocalUser
import com.dartvio.app.domain.room.Room
import com.dartvio.app.domain.room.RoomDraft
import com.dartvio.app.domain.room.RoomEvent
import com.dartvio.app.domain.room.RoomEventPayload
import com.dartvio.app.domain.room.RoomEventReplay
import com.dartvio.app.domain.room.RoomEventType
import com.dartvio.app.domain.room.RoomExpiry
import com.dartvio.app.domain.room.RoomJoinPolicy
import com.dartvio.app.domain.room.RoomJoinSource
import com.dartvio.app.domain.room.RoomLobbyState
import com.dartvio.app.domain.room.RoomMatchRules
import com.dartvio.app.domain.room.RoomMatchView
import com.dartvio.app.domain.room.RoomMember
import com.dartvio.app.domain.room.RoomRules
import com.dartvio.app.domain.room.RoomState
import com.dartvio.app.domain.room.RoomStatus
import com.dartvio.app.domain.room.RoomVisibility
import com.dartvio.app.domain.room.SpectatorSnapshot
import com.dartvio.app.domain.room.forSelf
import com.dartvio.app.domain.room.toWireMemberId
import com.dartvio.app.domain.room.withLocalIdentity
import com.dartvio.app.domain.room.withPpr
import com.dartvio.app.net.online.OnlineConfig
import com.dartvio.app.net.online.OnlineRoomApi
import com.dartvio.app.net.online.RoomEventStream
import com.dartvio.app.net.online.RoomSummary
import com.dartvio.app.net.online.StreamStatus
import com.dartvio.app.net.online.WriteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * 在线房间仓库：**云端**对战的数据源（联机只走这一条；切换点在 [RoomRepositoryProvider]）。
 *
 * ## 事实来源只有一份：事件
 *
 * 本类不保存「房间现在是什么样」，只保存**收到的事件**（[logs]），
 * 需要时由 [RoomEventReplay] 重放出来。
 *
 * 这么绕一圈换来的是：任何一台手机掉线、杀进程、换设备，只要重新拉一次事件就能得到
 * **与其它人完全一致**的状态。没有「房主机」这个角色，因此也就没有
 * 「房主切出去回个消息，整局就废了」这回事。
 *
 * ## 写入是「追加事件」，不是「改状态」
 *
 * 准备、开局、投镖、撤回，全都是往事件表里插一行。
 * 撞上 `unique(room_id, seq)`（两个人同时往同一个位置写）时不静默丢弃：
 * 拉一次全量、换下一个 seq 重投。并发在回合制里很罕见，但一旦发生，
 * 「点了没反应」比「多等半秒」难解释得多。
 *
 * ## 乐观更新的边界
 *
 * 写成功之后才把事件并入本地日志（[ingest]），**不提前假装成功**。
 * 代价是本机也要等一个网络往返（局域网那套是即时的），换来的是
 * 「本地显示的比分」与「别人看到的比分」永远来自同一份事件 —— 不会因为一次失败的请求
 * 让本机的画面独自往前走一步。
 */
class OnlineRoomRepository(context: Context) : RoomRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = OnlineRoomApi()

    /** 存档用的上下文（一人一房记录）。刻意只用于本地持久化，不参与任何传输。 */
    private val appContext = context.applicationContext

    /** 本机在线身份。房间里的成员 id 用它，不是 [LocalUser.ID]（两台手机会撞成同一个人）。 */
    private val selfId = OnlineIdentity.read(context)

    private val logs = MutableStateFlow<Map<String, List<RoomEvent>>>(emptyMap())

    /** 重放结果：唯一的状态来源。 */
    private val states: StateFlow<Map<String, RoomState>> = logs
        .map { map -> map.mapValues { (_, events) -> RoomEventReplay.replay(events) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** 大厅列表（房间索引表）。 */
    private val index = MutableStateFlow<List<RoomSummary>>(emptyList())

    /**
     * 各房间的**最新大厅快照**（`dartvio_room_state`：谁在房里、打没打、几分）。
     *
     * 索引行只有「这间房叫什么、谁建的」，没有「现在有几个人、是不是在打」。
     * 早先卡片因此永远写 `WAITING` 与「0/8 人」——两态都不来自事实，
     * 于是打完的房看着还能进、满员的房看着还能加入（2026-09-26 真机实测）。
     * 这里把快照并进列表，卡片才第一次拿到**人数与状态**。
     */
    private val liveStates = MutableStateFlow<Map<String, RoomLiveState>>(emptyMap())

    private val streams = mutableMapOf<String, RoomEventStream>()
    private val pumps = mutableMapOf<String, Job>()

    init {
        startCloudProbe()
    }

    private val _submitAcks = MutableSharedFlow<TurnAck>(extraBufferCapacity = 4)
    override val submitAcks: SharedFlow<TurnAck> = _submitAcks.asSharedFlow()

    /**
     * WebSocket 那条线的状态。**不直接展示给用户**：见 [status]。
     */
    private val _wsStatus = MutableStateFlow(
        if (OnlineConfig.isConfigured) StreamStatus.CONNECTING else StreamStatus.NOT_CONFIGURED
    )

    /** 最后一次 REST 轮询成功的时间戳（0 = 还没成功过）。 */
    private val _pollOkAt = MutableStateFlow(0L)

    /** 连续的云端探活失败次数（成功即清零）。 */
    private val _probeFailures = MutableStateFlow(0)

    /**
     * 实时连接状态（诊断页与联机页用）。
     *
     * ★2026-09-25：这里刻意**不把 WS 的状态当成联机状态**，只回答「数据有没有在流」。
     * 蜂窝网络下 Realtime 经常连不上（也不报错），而协作本身是由 REST 轮询保证的；
     * 若把 WS 的状态直接当作「联机状态」显示，用户看到的就是永远的「连接中 / 重连中」，
     * 于是以为联机不能用 —— 实际上一局照打不误，只是同步慢到 3 秒。
     * 所以：WS 不通而轮询通 → [StreamStatus.POLLING_ONLY]（可用，慢一点）；
     * 两者都不通 → 如实显示 CONNECTING / RECONNECTING。
     */
    val status: StateFlow<StreamStatus> = combine(
        _wsStatus,
        _pollOkAt,
        _probeFailures
    ) { ws, pollOkAt, failures ->
        val pollingHealthy =
            pollOkAt > 0 && System.currentTimeMillis() - pollOkAt < POLL_HEALTHY_WINDOW_MS
        when {
            ws == StreamStatus.LIVE -> ws
            ws == StreamStatus.NOT_CONFIGURED -> ws
            pollingHealthy -> StreamStatus.POLLING_ONLY
            // 云端确实叫不应：已经重试过好几次了，再说「连接中」就是在撒谎 ——
            // 说「重连中」，至少把「正在重试，也许节奏对不上」这件事传达出去。
            failures >= PROBE_FAILURES_BEFORE_RECONNECTING -> StreamStatus.RECONNECTING
            else -> ws
        }
    }.stateIn(scope, SharingStarted.Eagerly, _wsStatus.value)

    override val publicRooms: StateFlow<List<Room>> = combine(index, liveStates) { list, states ->
        val now = System.currentTimeMillis()
        list
            // 到期的房（等不到人自动解散 / 某人退出后 30 秒宽限已过）**不进列表**。
            // 中途退出这一路刻意复用「到期」而不是新造一个「已删除」状态：
            // 别端本来就在按 expires_at 过滤，新增语义要同时改协议与清理任务。
            .filter { !RoomExpiry.isExpired(it.expiresAt, now) }
            /*
             * 已结束的房同样不进列表（2026-09-27 真机反馈）。
             *
             * 此前只按 expires_at 过滤，于是「房主从对局页返回」「客人中途退出」之后，
             * 大厅里会留下一张写着「已结束 / 等候中」的同名卡：点进去只有回合记录、
             * 没有任何可继续的动作，而用户从这张卡上读不出「这局完了、该回大厅」。
             * 已结束是一份**记录**，不是一间还能进的房 —— 它该出现在战报里，不该占着大厅。
             */
            .filter { it.status != RoomStatus.ENDED }
            .map { it.toRoom(states[it.id]) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // ===== 读 =====

    override fun room(roomId: String): Room? =
        states.value[roomId]?.room?.withLocalIdentity(selfId)

    /**
     * 房间详情流。
     *
     * 与大厅索引 [index] 一起 combine，只为把**房主的 PPR / 信用**带进来：
     * 那两个数只落在大厅索引行上，事件流里没有 —— 而后加入的人在这一页
     * 要决定「要不要留下来打」，只看得到一个昵称是下不了决心的（2026-09-26 真机反馈）。
     */
    override fun observeRoom(roomId: String): Flow<Room?> = combine(
        tracked(roomId) { state -> state?.room?.withLocalIdentity(selfId) },
        index
    ) { room, rows ->
        room?.withHostStats(rows.firstOrNull { it.id == roomId })
    }

    /** 索引行里那两个数补进房间；已有的不覆盖（事件流若带了就以它为准）。 */
    private fun Room.withHostStats(summary: RoomSummary?): Room {
        if (summary == null) return this
        if (hostPpr != null && hostCredit != null) return this
        return copy(
            hostPpr = hostPpr ?: summary.hostPpr,
            hostCredit = hostCredit ?: summary.hostCredit
        )
    }

    override fun observeSpectator(roomId: String): Flow<SpectatorSnapshot?> = tracked(roomId) { state ->
        snapshotOf(state)?.withLocal(selfId)
    }

    override fun observeMatch(roomId: String): Flow<RoomMatchView?> = tracked(roomId) { state ->
        val match = state?.match ?: return@tracked null
        /*
         * 对局页玩家卡上的 PPR 来自**房间成员**：对局帧（事件重放）里只有比分，
         * 「这个人什么水平」不在其中，而对局页要回答的正是「谁占优、还是只是这局手气好」
         * （X01 转播里两张卡并排时，缺了这个数就只能靠剩余分猜 —— 2026-09-27 反馈）。
         * 房主那份在索引行上（hostPpr），客人那份由他自己加入时带过来。
         *
         * 顺序：**先补 PPR 再做身份投影** —— 成员 id 是线上身份，投影之后就对不上了。
         */
        val pprById = state.room?.members.orEmpty().associate { it.id to (it.ppr ?: if (it.isCreator) state.room?.hostPpr else null) }
        RoomMatchRules.snapshot(match)
            .withPpr(pprById)
            .withLocal(selfId)
            .forSelf(LocalUser.ID)
    }

    /**
     * 在线模式没有缺席判负。
     *
     * 局域网那套需要它，是因为「房主手机不见了」等于「对局没了」；
     * 而这里的事件在云端，谁掉线都不影响别人，回来拉一次全量就续上。
     * 强行造一个倒计时出来，只会把「对手在想」误判成「对手跑了」。
     */
    override fun observeAbsence(roomId: String): Flow<Map<String, Long>> = flowOf(emptyMap())

    override fun hasAuthoritativeMatch(roomId: String): Boolean =
        states.value[roomId]?.match != null

    // ===== 写 =====

    override fun submitTurn(roomId: String, darts: List<Dart>, clientTurnId: String) {
        scope.launch {
            if (write(roomId, RoomEventPayload.TurnSubmitted(darts, clientTurnId))) {
                _submitAcks.tryEmit(TurnAck.Accepted(states.value[roomId]?.match?.version ?: 0))
            }
        }
    }

    override fun undoTurn(roomId: String) {
        scope.launch {
            if (!write(roomId, RoomEventPayload.Empty, RoomEventType.TURN_UNDONE)) {
                // 写不进去与「不允许撤」对界面的处置相同：留一句提示，什么都不改。
                _submitAcks.tryEmit(TurnAck.Rejected("这一手撤不回来"))
            }
        }
    }

    /** 在线模式不需要它：撤销窗口由「对手是否已经投下一手」决定，而那件事写在事件里。 */
    override fun notifyTurnActivated(roomId: String) = Unit

    override fun createRoom(draft: RoomDraft, creatorName: String, creatorAvatar: String): Room {
        val roomId = newRoomId()
        val name = draft.name.ifBlank { "$creatorName 的房间" }
        val payload = RoomEventPayload.RoomCreated(
            name = name,
            config = draft.config,
            visibility = draft.visibility,
            allowSpectators = draft.allowSpectators,
            creatorName = creatorName,
            creatorAvatar = creatorAvatar,
            // 房主的 PPR 走**索引行的 host_ppr**（下面的 updateHostStats），不塞进事件：
            // 建房这一刻算 PPR 要读一次对局库（suspend），而创建必须同步返回房间对象；
            // 客人那份 PPR 则由 member_joined 事件带 —— 那是他加入时异步写的，等得起。
            creatorPpr = 0.0
        )
        // 先本地成立（界面需要一个房间对象导航进去），再落库。
        ingest(roomId, listOf(RoomEvent(roomId, 1, selfId, RoomEventType.ROOM_CREATED, payload)))
        ensureStream(roomId)

        // 预约房的到期从**开赛时刻**起算：仍按 5 分钟「没人来就散」，
        // 但若约在两小时后，5 分钟后就自毁，等于预约根本没生效。
        val selfDestructAt = draft.startsAt?.plus(RoomExpiry.WAITING_TTL_MS) ?: RoomExpiry.deadline()

        scope.launch {
            when (val result = api.insertRoom(
                roomId = roomId,
                name = name,
                creatorId = selfId,
                config = draft.config,
                visibility = draft.visibility,
                allowSpectators = draft.allowSpectators,
                joinPolicy = draft.joinPolicy,
                hostName = creatorName,
                hostAvatar = creatorAvatar,
                // 等待房一律带到期时刻：没人来就散（PRD A7）。
                // 有人加入时由 publishLobbyState 置 null（A9）。
                expiresAtMs = selfDestructAt,
                startsAtMs = draft.startsAt
            )) {
                is WriteResult.Ok,
                is WriteResult.Conflict -> {
                    // 房间号撞了也只是索引行没写进去（事件仍以本机的为准），
                    // 不因此让创建者的界面回退 —— 那会表现为「点了创建但没反应」。
                    // 快照由 write() 成功后统一写：房主自己建的不算"进来"，来源是 HOST。
                    selfJoinSource[roomId] = RoomJoinSource.HOST
                    ActiveRoomStore.save(appContext, roomId)
                    ownWaitingDeadline[roomId] = selfDestructAt
                    write(roomId, payload, RoomEventType.ROOM_CREATED)
                    // PPR / 信用是**装饰**：建房已经成功了，它们只让陌生人在点进来之前
                    // 对房主有个预期，因此放在这里补写，失败静默（不影响建房结果）。
                    api.updateHostStats(roomId, arenaPpr(), PlayerCreditStore.credit(appContext))
                }

                is WriteResult.Failed ->
                    _submitAcks.tryEmit(TurnAck.Rejected("房间创建失败，请检查网络"))
            }
        }

        return states.value[roomId]?.room?.withLocalIdentity(selfId)
            ?: Room(id = roomId, name = name, creatorId = selfId, config = draft.config)
    }

    /**
     * 加入房间。
     *
     * 房间是否存在要联网才知道，而接口是同步的 —— 因此这里**乐观返回成功**，
     * 真正的答案由 [observeRoom] 随后给出（房间不存在时房间页会收到 null）。
     * 让调用方在这里阻塞等一个网络往返，会把「输错一位房间号」变成一次界面卡顿。
     */
    override fun joinRoom(
        roomId: String,
        name: String,
        avatar: String,
        source: RoomJoinSource
    ): JoinResult {
        ensureStream(roomId)
        // 来源只在这一刻有意义（他是怎么找到这个房间的），之后的每一次写入沿用它 ——
        // 否则客人一投镖，来源就被覆盖成 HOST，统计里「大厅带来的对局」会凭空消失。
        selfJoinSource[roomId] = source
        ActiveRoomStore.save(appContext, roomId)
        scope.launch {
            // 先把事件拉全量：判定「我是不是已经在里面」只能以**云端事件**为准，
            // 用本机内存判断的话，刚启动 / 刚重连时内存是空的，判断必然失真。
            val reachable = refreshEvents(roomId)
            // ★进入一间**已经在里面**的房时不再补一条加入事件。
            // 事件是 append-only 的，重复加入会被重放成「又多了一个人」——
            // 两端各自补一条之后，同一份事件流在两台手机上重放出不同的人数与出手顺序，
            // 表现正是「分数和回合记录不再同步」（2026-09-26 真机实测）。
            // 房间已结束同理：那是一份记录，再进去只会把历史改坏。
            if (isMatchOver(roomId) || logs.value[roomId]?.let { events ->
                    RoomEventReplay.replay(events).room?.contains(selfId) == true
                } == true) {
                return@launch
            }
            // 云端叫不应时**不写**：写了也只在本地生效，本机画面会独自往前走一步，
            // 而别人看到的还是原样 —— 这正是「两端不一致」最典型的一条路。
            if (!reachable) {
                _submitAcks.tryEmit(TurnAck.Rejected("连不上服务器，没能进入房间"))
                return@launch
            }
            // 写成功后 write() 会顺带刷新大厅快照：大厅要显示双方昵称，
            // 而房主端要到下一次写快照才会知道客人是谁。
            //
            // PPR 一并带走：房主那台手机之前只看到「有人进来了」，看到的是一个空名字 +
            // 没有任何实力信息的成员行（2026-09-27 反馈）。对面没有本机档案可查，
            // 这一份只能由加入者自己报。
            write(roomId, RoomEventPayload.MemberJoined(name, avatar, arenaPpr() ?: 0.0))
        }

        val room = states.value[roomId]?.room?.withLocalIdentity(selfId)
            ?: Room(id = roomId, name = "房间 $roomId", creatorId = "", config = MatchConfig())
        return JoinResult.Success(room)
    }

    override fun leaveRoom(roomId: String, memberId: String) {
        // memberId 是界面里的（可能是 LocalUser.ID），出站前翻译回线上身份。
        val wireId = toWireMemberId(memberId, selfId)
        // 房主离开即解散：无论如何这间房对这台手机都不再是「未结束的房间」了。
        ActiveRoomStore.clear(appContext, roomId)

        // 退出前先看清「这一局到底打完了没有」：这个判断决定房间在大厅里
        // 是留成一份**记录**，还是彻底消失。
        val started = matchOf(roomId) != null
        val over = isMatchOver(roomId)
        if (started && !over) {
            // 未按约定局数打完就退出：这一局不算数，也不留痕迹。
            PlayerCreditStore.recordIncompleteQuit(appContext)
        }

        // 本机先把这张卡拿掉：等下一次轮询才消失的话，用户会看见自己刚退出的房还在列表里，
        // 点进去又是一次「重复加入」——那是把不一致的入口又递到手边。
        index.update { list -> list.filterNot { it.id == roomId } }

        scope.launch {
            if (!write(roomId, RoomEventPayload.Empty, RoomEventType.MEMBER_LEFT, wireId)) return@launch
            if (over) return@launch
            /*
             * 退出之后房间**再活 30 秒**，而不是当场消失（2026-09-27 真机反馈）。
             *
             * 为什么不能立刻销毁：手机上的「返回」有太多种被误触的方式 —— 顶栏箭头、
             * 系统返回键、误以为这一局已经结束。立刻销毁的后果是**两个人都回不去**：
             * 退出的人点回来只剩空大厅，对手那边正在投的那一回合也跟着没了。
             * 30 秒够「发现点错了 → 点回来接着打」，又不至于让死房间占着大厅。
             *
             * 为什么不能立刻置 ENDED：ENDED 在大厅里的意思是「这局打完了」，
             * 而中途退出**没打完**。当场置 ENDED 会让对手看到「已结束」却不知道自己为什么输了。
             */
            api.updateExpiresAt(roomId, System.currentTimeMillis() + ABANDON_GRACE_MS)
            // 宽限走完 → 置为已结束并退出大厅（到期过滤 + ENDED 过滤两条都会命中）。
            // 这一下由**退出的人**发起，是尽力而为：他此刻可能在电梯里，房间就多留一会儿，
            // 直到下一次有人轮询到它时按 expires_at 自行消失。
            delay(ABANDON_GRACE_MS + 1_000L)
            if (isMatchOver(roomId)) return@launch
            api.updateRoomStatus(roomId, RoomStatus.ENDED)
        }
    }

    /**
     * 一人一房（PRD D10 / A6）：本机此刻还占着哪间房。
     *
     * 只回答「本机记录里那一间」，不联网核实 —— 大厅要的是在用户点下去**之前**就拦住，
     * 而一次网络往返会让「点创建」变成一次卡顿。记录本身带 TTL（[ActiveRoomStore]），
     * 因此「房间早就没了但记录还在」不会把人永久锁死。
     */
    override fun activeRoomId(): String? = ActiveRoomStore.read(appContext)

    override fun setReady(roomId: String, memberId: String, ready: Boolean) {
        val wireId = toWireMemberId(memberId, selfId)
        scope.launch { write(roomId, RoomEventPayload.MemberReady(ready), actorId = wireId) }
    }

    override fun setAllowSpectators(roomId: String, allowed: Boolean) {
        scope.launch { write(roomId, RoomEventPayload.SettingsChanged(allowSpectators = allowed)) }
    }

    override fun setVisibility(roomId: String, visibility: RoomVisibility) {
        scope.launch { write(roomId, RoomEventPayload.SettingsChanged(visibility = visibility)) }
    }

    override fun kickMember(roomId: String, memberId: String) {
        val wireId = toWireMemberId(memberId, selfId)
        scope.launch { write(roomId, RoomEventPayload.MemberKicked(wireId)) }
    }

    override fun startMatch(roomId: String) {
        scope.launch {
            if (write(roomId, RoomEventPayload.Empty, RoomEventType.MATCH_STARTED)) {
                api.updateRoomStatus(roomId, RoomStatus.PLAYING)
                setLocalStatus(roomId, RoomStatus.PLAYING)
                // 开打即停止「等不到人就散」的计时：那计时是给「还没人来」设的。
                api.updateExpiresAt(roomId, null)
                ownWaitingDeadline.remove(roomId)
            }
        }
    }

    override fun rematch(roomId: String) {
        scope.launch { write(roomId, RoomEventPayload.Empty, RoomEventType.MATCH_REMATCH) }
    }

    /** 本机对局库（只在补写房主 PPR 时用到，惰性建库，不参与任何传输）。 */
    private val matchRepository by lazy {
        MatchRepository(DartVioDatabase.get(appContext).matchRecordDao())
    }

    /**
     * 本人在**比赛大厅里**打出来的正式赛 PPR（房间卡片上给陌生人的实力预期）。
     *
     * 口径必须是联机局（`observeArenaMatches`）而不是战绩口径（排联机）：
     * 大厅里要回答的是「这个人在这个场子里什么水平」，拿本地 / AI 对局凑出来的数
     * 回答不了这个问题 —— 而用战绩口径算，这个数永远是空的，卡片上就永远没有 PPR。
     *
     * **正式赛优先、否则退回全部已打完的对局**：只看 `pprFormal` 会让只打过休闲局的人
     * 永远显示不出 PPR（休闲局不是正式赛），而他已经完成过比赛 —— 这正是
     * 「卡片上显示的信息有误」那一类：不是没数据，是取数的口子开得太窄。
     *
     * `null` = 还没在大厅打过 / 读库失败 —— 卡片据此**整块不显示**，而不是显示一个 0：
     * 「PPR 0.0」会被读成「这个人很菜」，而真实含义只是「他还没打过」。
     */
    private suspend fun arenaPpr(): Double? = runCatching {
        val x01 = StatsCalculator.compute(matchRepository.observeArenaMatches().first()).x01
        x01.pprFormal.takeIf { it > 0.0 } ?: x01.pprAll.takeIf { it > 0.0 }
    }.getOrNull()

    /**
     * 写一条大厅快照（append-only）。
     *
     * 房主写 `seq=0`、客人写 `seq=1`：同一个房间里会出现多条快照，大厅取最大 seq 的那条。
     * 这样房主端不必等下一次提交才知道客人是谁 —— 大厅里「谁在等」是能不能点进去的关键信息。
     *
     * 写入失败由 [OnlineRoomApi.insertRoomState] 静默降级：大厅只是没有比分，不影响对局。
     */
    private suspend fun publishLobbyState(roomId: String) {
        val events = logs.value[roomId]
        if (events.isNullOrEmpty()) return

        // 用事件重放的结果，而不是 states.value：stateIn 的更新是异步的，
        // 刚写完就读会读到上一手的状态（表现是「大厅比分永远慢一手」）。
        val state = RoomEventReplay.replay(events)
        // 序号取事件流最大 seq：两端各自写快照时，必须共用同一个单调递增的量，
        // 否则会出现「客人写的 seq 比房主小 ⇒ 大厅一直显示房主那条旧的」。
        val seq = events.maxOf { it.seq }
        val live = RoomLobbyState.of(
            state = state,
            seq = seq,
            source = selfJoinSource[roomId] ?: RoomJoinSource.HOST
        ) ?: return
        api.insertRoomState(live)

        // ★对局一打完就把房间标成 ENDED。
        // 不标的话索引行永远停在 WAITING：卡片显示「等候中 + 加入比赛」，
        // 两台手机于是都能再点进去各补一条加入事件 —— 分数与回合记录从此对不上
        // （2026-09-26 真机实测）。标了之后卡片是「已结束」，那个按钮根本不会出现。
        if (isOver(state)) {
            api.updateRoomStatus(roomId, RoomStatus.ENDED)
            setLocalStatus(roomId, RoomStatus.ENDED)
            // 只给**参与者**记一次完成：旁观这次写入的人不该因此涨信用。
            if (endedRecorded.add(roomId) && state.room?.members?.any { it.id == selfId } == true) {
                PlayerCreditStore.recordCompleted(appContext)
            }
        }

        // 有人入座 ⇒ 这间房不再会「等不到人」，到期计时即时停止（PRD A9）。
        // 计时是给「还没人来」这件事设的，满员之后继续倒数，
        // 会让一场马上要开始的比赛带着「还剩 1:32 就解散」的倒计时去吓双方。
        if (live.guestId.isNotBlank() && expiryCleared.add(roomId)) {
            api.updateExpiresAt(roomId, null)
            ownWaitingDeadline.remove(roomId)
        }
    }

    /**
     * 本机索引行跟着改状态。
     *
     * 只改云端是不够的：大厅列表直接读 [index]，而它要等下一次 `refresh()` 才更新 ——
     * 那之前用户看到的还是「等候中 + 加入比赛」，正好是这一轮要消灭的那张卡。
     */
    private fun setLocalStatus(roomId: String, status: RoomStatus) {
        index.update { list -> list.map { if (it.id == roomId) it.copy(status = status) else it } }
    }

    /** 已经结算过「打完一局」的房间（信用分只记一次，重连补帧会重复走到这里）。 */
    private val endedRecorded = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /** 这个房间的对局（还没开局则 null）。 */
    private fun matchOf(roomId: String) = logs.value[roomId]?.let { RoomEventReplay.replay(it).match }

    /** 这一局打完了没有（含休闲模式的「单局定胜负」）。 */
    private fun isMatchOver(roomId: String): Boolean =
        logs.value[roomId]?.let { isOver(RoomEventReplay.replay(it)) } ?: false

    private fun isOver(state: RoomState): Boolean {
        val match = state.match ?: return false
        val snapshot = RoomMatchRules.snapshot(match)
        return snapshot.isFinished ||
            (snapshot.legsToWin <= 0 && snapshot.turns.lastOrNull()?.isCheckout == true)
    }

    /**
     * 已经停止计时的房间（写过一次 `expires_at = null`）。
     *
     * 只记集合不记值：置 null 是**幂等**的，重复写没有意义，
     * 而每一手投镖都发一次 PATCH 会把装饰性写入变成真正的流量。
     */
    private val expiryCleared = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /**
     * 自己建的等待房 → 到期时刻。
     *
     * 为什么不用房间索引表里的 `expires_at` 反查：那一列是给**别人**看倒计时用的，
     * 而自毁只需要本机知道「我建的那间什么时候到期」。索引表还要靠一次 `refresh()`
     * 才更新，房主端自己建房并不会顺带刷新它 —— 拿它当依据，等于让自毁依赖大厅的刷新节奏。
     */
    private val ownWaitingDeadline = ConcurrentHashMap<String, Long>()

    /**
     * 房主端自检自毁（PRD A7 的第二道保险）。
     *
     * 服务端清理（pg_cron）负责「房主已经不在 App 里」的情况，而这一条负责
     * 「房主正开着这个房间却忘了它」—— 后者恰恰是最常见的一种：
     * 建完房去回个消息，回来发现房间早该过期了却还挂在大厅上。
     *
     * 自毁的方式是**房主离开**（`MEMBER_LEFT`）：房主离开即解散是既有规则，
     * 因此这里不需要新增事件类型 —— 加一个 `room_expired` 要动协议，
     * 而协议一旦分叉，老端会把这类房间读成「打不开」。
     */
    private suspend fun expireOwnWaitingRooms() {
        val now = System.currentTimeMillis()
        ownWaitingDeadline.entries.toList().forEach { (roomId, deadline) ->
            if (now < deadline) return@forEach
            // 已经开局的房间不再自毁：那时它是一场比赛，不是一间等人的房。
            val events = logs.value[roomId] ?: return@forEach
            if (RoomEventReplay.replay(events).match != null) {
                ownWaitingDeadline.remove(roomId)
                return@forEach
            }
            if (write(roomId, RoomEventPayload.Empty, RoomEventType.MEMBER_LEFT)) {
                ownWaitingDeadline.remove(roomId)
                ActiveRoomStore.clear(appContext, roomId)
                // 自毁之后这张卡也必须从大厅消失：索引行还在的话，
                // 别人会看见一间「房主已经不在了」的房，点进去只会得到一句空话。
                api.updateExpiresAt(roomId, System.currentTimeMillis() - 1)
                index.update { list -> list.filterNot { it.id == roomId } }
            }
        }
    }

    /** 本机是从哪儿进到这个房间的（仅统计用，见 [publishLobbyState]）。 */
    private val selfJoinSource = ConcurrentHashMap<String, RoomJoinSource>()

    override fun newRoomId(): String {
        val used = logs.value.keys
        var candidate: String
        do {
            candidate = (100_000..999_999).random().toString()
        } while (candidate in used)
        return candidate
    }

    override suspend fun refresh() {
        val rooms = api.fetchRooms()
        if (rooms != null) index.value = rooms
        // 快照与索引一起拉：卡片上的「几人 / 在打 / 已结束」全部来自快照，
        // 只刷索引的话状态永远停在旧的那一版（下拉刷新也刷不动状态）。
        val ids = (rooms ?: index.value).map { it.id }
        api.fetchRoomStates(ids)?.let { liveStates.value = it }
        logs.value.keys.forEach { roomId -> refreshEvents(roomId) }
    }

    /**
     * 这个房间号真的存在吗。
     *
     * 以**事件**为准而不是房间索引表：索引行有可能因为撞号没写进去（见 [createRoom]），
     * 而事件一旦写了，房间就客观存在。用索引表判断会把这种房间判成「不存在」。
     */
    suspend fun roomExists(roomId: String): Boolean =
        api.fetchEvents(roomId)?.isNotEmpty() == true || api.fetchRoom(roomId) != null

    // ===== 内部 =====

    /** 订阅 + 拉全量；同一个房间只跑一条泵。 */
    private fun ensureStream(roomId: String) {
        if (pumps[roomId]?.isActive == true) return

        val stream = streams.getOrPut(roomId) { RoomEventStream(roomId) }
        scope.launch { stream.status.collect { _wsStatus.value = it } }

        pumps[roomId] = scope.launch {
            // 先补历史：订阅只推送**之后**发生的事件，重连期间落下的那些不会补发。
            refreshEvents(roomId)

            // ★订阅旁边必须并行一条慢速轮询。
            // Realtime 是「最好有」而不是「必须有」：当事件表没加入 `supabase_realtime`
            // publication，或 RLS 拦住了 anon 的 SELECT 时，WebSocket 照样 join 成功、
            // 状态照样是 LIVE，却**一条推送都不来** —— 没有任何报错。
            // 真机上的表现正是它：客人加入时拉了全量历史，所以看得到房主；
            // 房主只能等推送，于是永远停在「等待玩家加入」。
            // 轮询把「能不能联上」从依赖一项静默失败的服务，降级成只影响延迟。
            val poller = scope.launch {
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    refreshEvents(roomId)
                    // 顺路自检：自己建的等待房过期了就自毁（PRD A7）。
                    // 挂在轮询而不是另起一个循环：它需要的那份索引已经在内存里，
                    // 单独开循环只会多一条没人看着的定时器。
                    expireOwnWaitingRooms()
                }
            }
            try {
                stream.events().collect { ingest(roomId, listOf(it)) }
            } finally {
                poller.cancel()
            }
        }
    }

    /**
     * 云端探活循环（随仓库一起启动，不依赖「有没有进房间」）。
     *
     * 为什么必须独立于房间：联机入口页要显示「在线状态」，而那一刻既没有订阅（WebSocket 是冷流，
     * 不进房间就不会启动），也没有任何 REST 请求在跑 —— 没有这条探针，那一行只能永远停在
     * 初始值「连接中」，变成一个不表达任何事实的装饰。
     *
     * 代价是每 10 秒一次几百字节的请求：换来的是「能不能联机」在任何页面上都有真话可说。
     */
    private fun startCloudProbe() {
        scope.launch {
            while (isActive) {
                if (api.ping()) {
                    _pollOkAt.value = System.currentTimeMillis()
                    _probeFailures.value = 0
                } else {
                    _probeFailures.value++
                }
                delay(CLOUD_PROBE_INTERVAL_MS)
            }
        }
    }

    /**
     * 只拉本机还没有的那部分（事件 append-only，增量与全量结算等价）。
     *
     * @return REST 是否回答了（拿到什么不重要，空列表也是「后端是活的」）——
     * 它是 [status] 判定「联机到底能不能用」的唯一依据。
     */
    private suspend fun refreshEvents(roomId: String): Boolean {
        val after = logs.value[roomId]?.maxOfOrNull { it.seq } ?: 0
        val events = api.fetchEvents(roomId, after) ?: return false
        _pollOkAt.value = System.currentTimeMillis()
        ingest(roomId, events)
        releaseIfGone(roomId)
        return true
    }

    /**
     * 房间已经没有我的位置了 ⇒ 释放「一人一房」的占用（[ActiveRoomStore]）。
     *
     * 必须靠事件流来释放：房间被房主解散、我被踢出，这两件事发生时**我这一端
     * 并没有走 `leaveRoom`** —— 界面是收到「房间没了」才返回的。
     * 只把释放挂在 `leaveRoom` 上，用户就会被自己没做过的事锁住三小时
     * （「你还有一间没打完的房间」，而那间房早就散了）。
     */
    private fun releaseIfGone(roomId: String) {
        val events = logs.value[roomId] ?: return

        // 只在我**确实进去过**之后才谈「我不在里面了」：加入事件是异步写进云端的，
        // 在它落库之前，重放结果里本来就没有我。那时若据此释放占用，
        // 一人一房会在「刚点完加入」这一刻自己失效。
        val joined = events.any { it.type == RoomEventType.MEMBER_JOINED && it.actorId == selfId } ||
            events.any { it.type == RoomEventType.ROOM_CREATED && it.actorId == selfId }
        if (!joined) return

        val room = RoomEventReplay.replay(events).room
        if (room == null || room.members.none { it.id == selfId }) {
            ActiveRoomStore.clear(appContext, roomId)
        }
    }

    /** 并入事件日志。按 seq 去重，因此「自己写成功后立即并入」与「稍后收到推送」不会重复结算。 */
    private fun ingest(roomId: String, events: List<RoomEvent>) {
        if (events.isEmpty()) return
        logs.update { map ->
            val merged = ((map[roomId] ?: emptyList()) + events)
                .distinctBy { it.seq }
                .sortedBy { it.seq }
            map + (roomId to merged)
        }
    }

    /**
     * 追加一条事件；撞号时拉全量后用新 seq 重投。
     *
     * @return 是否写成功。**只有成功后才并入本地日志**：不提前假装成功，
     * 免得一次失败的请求让本机画面独自往前走一步。
     */
    private suspend fun write(
        roomId: String,
        payload: RoomEventPayload,
        type: RoomEventType = typeOf(payload),
        actorId: String = selfId
    ): Boolean {
        var lastError: String? = null

        repeat(MAX_WRITE_ATTEMPTS) { attempt ->
            val seq = (logs.value[roomId]?.maxOfOrNull { it.seq } ?: 0) + 1
            val event = RoomEvent(roomId, seq, actorId, type, payload)

            when (val result = api.insertEvent(event)) {
                is WriteResult.Ok -> {
                    ingest(roomId, listOf(event))
                    // 每写成功一手就刷一次大厅快照：大厅上「正在打 + 现在几分」是唯一能让人
                    // 下决心点进来的信息，只在建房时写一次的话，大厅永远显示「剩余 501」。
                    publishLobbyState(roomId)
                    return true
                }

                is WriteResult.Conflict -> {
                    // 这个位置被别人占了：拉一次全量，下一个 seq 重投。
                    refreshEvents(roomId)
                    lastError = "这一手被抢先了，正在重试"
                }

                is WriteResult.Failed -> {
                    // 网络抖动（超时、断网）也重试：对局里「点了没反应」是最难解释的现象，
                    // 而重投是安全的 —— seq 唯一约束会让重复的那一次变成 Conflict，
                    // 而 Conflict 的处理是拉全量后换新号，不会把一手算两遍。
                    lastError = result.message
                }
            }
            delay(RETRY_DELAY_MS * (attempt + 1))
        }

        _submitAcks.tryEmit(TurnAck.Rejected(lastError ?: "网络不太顺，再试一次"))
        return false
    }

    /** 带订阅的观察流：collect 之前先把这个房间的实时通道拉起来。 */
    private fun <T> tracked(
        roomId: String,
        project: (RoomState?) -> T
    ): Flow<T> = flow {
        ensureStream(roomId)
        emitAll(states.map { project(it[roomId]) })
    }

    private fun snapshotOf(state: RoomState?): SpectatorSnapshot? {
        val room = state?.room ?: return null
        val match = state.match ?: return RoomRules.initialSpectatorSnapshot(room)
        return RoomMatchRules.snapshot(match)
    }

    private fun SpectatorSnapshot.withLocal(selfWireId: String): SpectatorSnapshot = copy(
        players = players.map { if (it.id == selfWireId) it.copy(id = LocalUser.ID) else it },
        undo = undo?.copy(
            playerId = if (undo?.playerId == selfWireId) LocalUser.ID else undo?.playerId ?: ""
        )
    )

    /**
     * 索引行 → [Room]。
     *
     * 成员由**大厅快照**（[live]）补出来：索引表里不维护成员，而卡片上「1/2 人」是
     * 用户判断「我能不能进去」的唯一依据 —— 没有它，卡片只能写一个假的 0。
     * 快照缺失时退化为「只有房主一人」：那是建房后还没人来的真实形态，不是猜的。
     *
     * 成员 id 一律经 [projectId] 投影：快照里是**线上身份**，而界面只认识 `LocalUser.ID`；
     * 不投影的话「我自己在房间里」这件事永远判不出来，卡片会给自己一个「加入比赛」。
     */
    private fun RoomSummary.toRoom(live: RoomLiveState?): Room {
        val hostId = live?.hostId?.takeIf { it.isNotBlank() } ?: creatorId
        val guestId = live?.guestId?.takeIf { it.isNotBlank() }
        val members = buildList {
            add(
                RoomMember(
                    id = projectId(hostId),
                    name = live?.hostName?.takeIf { it.isNotBlank() } ?: hostName,
                    avatar = hostAvatar.ifBlank { LocalUser.AVATAR_FALLBACK },
                    isCreator = true,
                    isReady = true
                )
            )
            if (guestId != null) {
                add(
                    RoomMember(
                        id = projectId(guestId),
                        name = live?.guestName?.takeIf { it.isNotBlank() } ?: "对手",
                        avatar = LocalUser.AVATAR_FALLBACK,
                        isReady = true
                    )
                )
            }
        }
        // 状态以**索引行的 status 列**为准，快照的 playing 只作兜底：
        // 那一列由「谁真的写了一条事件」驱动，而快照可能因为表不存在或写入失败而缺失；
        // 反过来说，快照说在打而 status 没跟上，也以「在打」为准 —— 宁可多一个观战入口，
        // 也不要把正在进行的局显示成可以加入。
        val resolvedStatus = when {
            this.status == RoomStatus.ENDED -> RoomStatus.ENDED
            live?.playing == true -> RoomStatus.PLAYING
            this.status == RoomStatus.PLAYING -> RoomStatus.PLAYING
            else -> RoomStatus.WAITING
        }
        return Room(
            id = id,
            name = name,
            creatorId = projectId(creatorId),
            config = config,
            visibility = visibility,
            allowSpectators = allowSpectators,
            status = resolvedStatus,
            members = members,
            createdAt = createdAt,
            startsAt = startsAt,
            hostPpr = hostPpr,
            hostCredit = hostCredit
        )
    }

    /** 线上身份 → 界面身份（本机翻译成 `LocalUser.ID`）。 */
    private fun projectId(wireId: String): String =
        if (wireId == selfId) LocalUser.ID else wireId

    private companion object {
        const val MAX_WRITE_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 300L

        /**
         * 轮询间隔。取 3 秒是两端体验的折中：
         * 有人加入/准备这类事，晚 3 秒看得到不影响「能不能开局」；
         * 再快会让两台手机在等待页里持续发请求，而这一页通常要停留几十秒。
         * 订阅正常时它只是多拉几次空结果（增量，几乎不耗流量）。
         */
        const val POLL_INTERVAL_MS = 3_000L

        /**
         * 「轮询还算健康」的窗口。取 3 倍轮询间隔：连着三次都成功才敢说它稳，
         * 否则一次网络抖动就会让界面在「可用」和「重连中」之间来回跳。
         */
        const val POLL_HEALTHY_WINDOW_MS = POLL_INTERVAL_MS * 3

        /** 云端探活间隔，理由见 [startCloudProbe]。 */
        const val CLOUD_PROBE_INTERVAL_MS = 10_000L

        /**
         * 有人退出之后房间**继续存活**的宽限（30 秒），理由见 [leaveRoom] 里那段注释。
         *
         * 30 秒的来历：够一个人「发现自己点错了 → 从大厅点回来」（大厅一次刷新 3 秒），
         * 又短到不会让一间没人管的房占着列表 —— 大厅里最贵的不是流量，是注意力。
         */
        const val ABANDON_GRACE_MS = 30_000L

        /**
         * 连续失败几次后才改口说「重连中」：约 30 秒。
         * 一次失败<｜hy_place▁holder▁no▁813｜>是蜂窝网络下的常态（进电梯、切基站），为它改状态会让界面抽风。
         */
        const val PROBE_FAILURES_BEFORE_RECONNECTING = 3

        fun typeOf(payload: RoomEventPayload): RoomEventType = when (payload) {
            is RoomEventPayload.RoomCreated -> RoomEventType.ROOM_CREATED
            is RoomEventPayload.MemberJoined -> RoomEventType.MEMBER_JOINED
            is RoomEventPayload.MemberReady -> RoomEventType.MEMBER_READY
            is RoomEventPayload.MemberKicked -> RoomEventType.MEMBER_KICKED
            is RoomEventPayload.TurnSubmitted -> RoomEventType.TURN_SUBMITTED
            is RoomEventPayload.SettingsChanged -> RoomEventType.SETTINGS_CHANGED
            RoomEventPayload.Empty -> RoomEventType.MEMBER_LEFT
        }
    }
}
