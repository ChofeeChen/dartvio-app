package com.dartvio.app.data.room

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.HumanAvatar
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.domain.room.JoinResult
import com.dartvio.app.domain.room.LocalUser
import com.dartvio.app.domain.room.Room
import com.dartvio.app.domain.room.RoomDraft
import com.dartvio.app.domain.room.RoomJoinSource
import com.dartvio.app.domain.room.RoomMatchView
import com.dartvio.app.domain.room.RoomMember
import com.dartvio.app.domain.room.RoomStatus
import com.dartvio.app.domain.room.RoomVisibility
import com.dartvio.app.domain.room.SpectatorPlayer
import com.dartvio.app.domain.room.SpectatorSnapshot
import com.dartvio.app.domain.room.SpectatorTurn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 本地房间实现（V0.1 Mock）。
 *
 * 目的：在没有网络层（M5 实时同步）的情况下，让 M6 的 5 个页面呈现**完整可交互**的状态，
 * 包括房间列表刷新、创建房间、成员进出、准备、开始对局、观战比分推进。
 *
 * 联网替换点：把本类换成 OnlineRoomRepository（云端在线）即可，接口签名已按「语义操作」设计。
 * 所有副作用都通过 [scope] 驱动，进程结束即消失，不污染本地对局数据库。
 */
object LocalRoomRepository : RoomRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val rooms = MutableStateFlow(seedRooms())
    private val spectators = MutableStateFlow(seedSpectators())

    override val publicRooms: StateFlow<List<Room>> = rooms
        .map(::publicRoomsOf)
        .stateIn(scope, SharingStarted.Eagerly, publicRoomsOf(rooms.value))

    init {
        startSpectatorTicker()
    }

    private fun publicRoomsOf(source: Map<String, Room>): List<Room> =
        source.values
            .filter { it.visibility == RoomVisibility.PUBLIC }
            .sortedWith(compareBy({ it.status != RoomStatus.WAITING }, { -it.createdAt }))

    // ===== 查询 =====

    override fun room(roomId: String): Room? = rooms.value[roomId]

    override fun observeRoom(roomId: String): Flow<Room?> =
        rooms.map { it[roomId] }

    override fun observeSpectator(roomId: String): Flow<SpectatorSnapshot?> =
        spectators.map { it[roomId] }

    /**
     * 单机 Mock **没有**权威对局，因此恒发 null。
     *
     * Mock 房间的比分来自 [startSpectatorTicker] 的演示推进（按预置流水往下播），
     * 没有任何东西在**裁定**它。若在这里造一个「本机权威」出来，同一个 App 就会有两种
     * 「谁在决定比分」的模式（BL-001），而 Mock 那一种永远没有对手可以与之对拍。
     *
     * 调用方据此分流：Mock 房间开局后仍进观战页，只有联机房间才进对局页（见 `DartVioNavHost`），
     * 所以这里的 null 不会被用户看见。
     */
    override fun observeMatch(roomId: String): Flow<RoomMatchView?> = flowOf(null)

    override fun newRoomId(): String {
        var candidate: String
        do {
            candidate = Random.nextInt(100_000, 1_000_000).toString()
        } while (rooms.value.containsKey(candidate))
        return candidate
    }

    // ===== 写入 =====

    override fun createRoom(draft: RoomDraft, creatorName: String, creatorAvatar: String): Room {
        val host = RoomMember(
            id = LocalUser.ID,
            name = creatorName.ifBlank { "${LocalUser.DEFAULT_NAME}1" },
            avatar = creatorAvatar,
            isCreator = true,
            isReady = true
        )
        val room = Room(
            id = newRoomId(),
            name = draft.name.ifBlank { "${host.name} 的房间" },
            creatorId = LocalUser.ID,
            config = draft.config,
            visibility = draft.visibility,
            allowSpectators = draft.allowSpectators,
            status = RoomStatus.WAITING,
            members = listOf(host),
            startsAt = draft.startsAt
        )
        put(room)
        simulateCompanionJoin(room.id)
        return room
    }

    // 单机没有"发现渠道"，来源只对在线有意义 —— 这里收下但不使用。
    override fun joinRoom(roomId: String, name: String, avatar: String, source: RoomJoinSource): JoinResult {
        val room = rooms.value[roomId] ?: return JoinResult.RoomNotFound
        if (room.contains(LocalUser.ID)) return JoinResult.AlreadyJoined
        if (room.status == RoomStatus.PLAYING) return JoinResult.MatchStarted
        if (room.isFull) return JoinResult.RoomFull

        val member = RoomMember(
            id = LocalUser.ID,
            name = name.ifBlank { "${LocalUser.DEFAULT_NAME}1" },
            avatar = avatar,
            isCreator = false,
            isReady = false
        )
        val updated = room.copy(members = room.members + member)
        put(updated)
        simulateCompanionJoin(roomId)
        return JoinResult.Success(updated)
    }

    override fun leaveRoom(roomId: String, memberId: String) {
        val room = rooms.value[roomId] ?: return
        // 房主离开即解散房间；联网版本对应 room_closed 事件
        if (room.creatorId == memberId) {
            rooms.update { it - roomId }
            spectators.update { it - roomId }
            return
        }
        put(room.copy(members = room.members.filterNot { it.id == memberId }))
    }

    override fun setReady(roomId: String, memberId: String, ready: Boolean) {
        val room = rooms.value[roomId] ?: return
        put(
            room.copy(
                members = room.members.map {
                    if (it.id == memberId) it.copy(isReady = ready) else it
                }
            )
        )
    }

    override fun setAllowSpectators(roomId: String, allowed: Boolean) {
        val room = rooms.value[roomId] ?: return
        put(room.copy(allowSpectators = allowed))
    }

    override fun setVisibility(roomId: String, visibility: RoomVisibility) {
        val room = rooms.value[roomId] ?: return
        put(room.copy(visibility = visibility))
    }

    override fun kickMember(roomId: String, memberId: String) {
        val room = rooms.value[roomId] ?: return
        put(room.copy(members = room.members.filterNot { it.id == memberId }))
    }

    override fun startMatch(roomId: String) {
        val room = rooms.value[roomId] ?: return
        val started = room.copy(status = RoomStatus.PLAYING, turnSeq = 0)
        put(started)
        // 对局开始即生成观战数据，供观战页演示
        spectators.update { it + (roomId to initialSnapshotFor(started)) }
    }

    /**
     * 单机 Mock 不接受回合提交 —— 理由同 [observeMatch]：没有权威对局可提交。
     *
     * 刻意留成空实现而不是抛异常：`RoomRepository` 是联机与单机共用的接口，
     * 抛异常会把「单机演示时误触提交」升级成一次崩溃；这里最多表现为一次无效点击，
     * 而且单机下根本到不了对局页。
     */
    override fun submitTurn(roomId: String, darts: List<Dart>, clientTurnId: String) = Unit

    /**
     * 恒空：单机 Mock 里没有「主机」来裁定撤回，也没有撤销窗口可言。
     *
     * 与 [submitTurn] 的 no-op 同源 —— Mock 的对局页根本到不了（`observeMatch` 恒 null），
     * 因此这里永远不会被调用。单机玩法有自己的撤销（在各自的 ViewModel 里），不走这里。
     */
    override fun undoTurn(roomId: String) = Unit

    /** 恒空：同 [undoTurn]。Mock 里不存在「对手动手了」这回事。 */
    override fun notifyTurnActivated(roomId: String) = Unit

    /** 恒空：单机 Mock 里没有「对手掉线」这回事 —— 没有主机，也就没有缺席可计。 */
    override fun observeAbsence(roomId: String): Flow<Map<String, Long>> = flowOf(emptyMap())

    /** 恒空：单机 Mock 没有权威对局（[observeMatch] 恒 null），也就没有「上一场」可再来。 */
    override fun rematch(roomId: String) = Unit

    /**
     * 恒空：单机 Mock 里没有「主机」来答复，也没有权威版本可言。
     *
     * 与 [submitTurn] 的 no-op 同源 —— Mock 的对局页根本到不了（`observeMatch` 恒 null），
     * 因此这里永远不会被 collect。
     */
    override val submitAcks: SharedFlow<TurnAck> = MutableSharedFlow()

    /**
     * 恒为 false：单机 Mock 没有权威对局，因此没有「能录镖的页面」可进。
     *
     * 这样 Mock 房间开局后仍按原样进观战页 —— 那里的比分由 Mock 的演示推进产生，
     * 至少是**自洽**的；若把它导航到对局页，用户会面对一个永远显示「正在同步」的页面。
     */
    override fun hasAuthoritativeMatch(roomId: String): Boolean = false

    override suspend fun refresh() {
        delay(700)
        val waiting = rooms.value.values.filter {
            it.status == RoomStatus.WAITING &&
                it.visibility == RoomVisibility.PUBLIC &&
                !it.contains(LocalUser.ID) &&
                it.members.size <= 3
        }
        if (waiting.isEmpty() || Random.nextFloat() > 0.75f) return

        val target = waiting.random()
        val guest = GUEST_POOL.random()
        if (target.members.any { it.id == guest.id }) return
        put(target.copy(members = target.members + guest.toMember()))
    }

    private fun put(room: Room) {
        rooms.update { it + (room.id to room) }
    }

    // ===== Mock 行为：其它玩家加入 / 准备 =====

    private fun simulateCompanionJoin(roomId: String) {
        scope.launch {
            delay(2600)
            val room = rooms.value[roomId] ?: return@launch
            if (room.status != RoomStatus.WAITING || room.isFull) return@launch
            val guest = GUEST_POOL.random()
            if (room.members.any { it.id == guest.id }) return@launch
            put(room.copy(members = room.members + guest.toMember()))

            delay(2600)
            // 模拟对手进来后主动准备
            setReady(roomId, guest.id, true)
        }
    }

    // ===== Mock 行为：观战比分推进 =====

    private fun startSpectatorTicker() {
        scope.launch {
            while (isActive) {
                delay(2200)
                spectators.update { map -> map.mapValues { (_, snap) -> advance(snap) } }
            }
        }
    }

    private fun advance(snap: SpectatorSnapshot): SpectatorSnapshot {
        if (snap.isFinished) return snap

        val index = snap.players.indexOfFirst { it.isActive }.coerceAtLeast(0)
        val current = snap.players[index]
        val mock = MOCK_TURNS.random()
        val seq = (snap.turns.maxOfOrNull { it.seq } ?: 0) + 1

        var remaining = current.score - mock.scored
        val isBust = remaining < 0 || remaining == 1
        if (isBust) remaining = current.score
        val isCheckout = !isBust && remaining == 0

        val turn = SpectatorTurn(
            seq = seq,
            playerName = current.name,
            darts = mock.darts,
            scored = if (isBust) 0 else mock.scored,
            remaining = remaining,
            isBust = isBust,
            isCheckout = isCheckout
        )

        val leg = if (isCheckout) snap.leg + 1 else snap.leg
        val rebuilt = snap.players.mapIndexed { i, p ->
            when {
                i != index -> p.copy(isActive = false)
                isCheckout -> p.copy(legsWon = p.legsWon + 1, score = TARGET_SCORE)
                else -> p.copy(score = remaining)
            }
        }.toMutableList()

        val next = (index + 1) % rebuilt.size
        rebuilt[next] = rebuilt[next].copy(isActive = true)

        return snap.copy(
            leg = leg,
            players = rebuilt,
            turns = (snap.turns + turn).takeLast(14)
        )
    }

    // ===== 种子数据 =====

    private data class Guest(val id: String, val name: String, val avatar: String) {
        fun toMember() = RoomMember(id = id, name = name, avatar = avatar, isReady = false)
    }

    private val GUEST_POOL = listOf(
        Guest("u_hao", "郝乐", HumanAvatar.HUMAN_2.key),
        Guest("u_mu", "穆青", HumanAvatar.HUMAN_3.key),
        Guest("u_yan", "严川", HumanAvatar.HUMAN_4.key),
        Guest("u_bei", "贝可", HumanAvatar.HUMAN_1.key),
        Guest("u_du", "杜衡", HumanAvatar.HUMAN_2.key),
        Guest("u_su", "苏禾", HumanAvatar.HUMAN_4.key),
    )

    private fun seedRooms(): Map<String, Room> {
        val now = System.currentTimeMillis()
        val rooms = listOf(
            Room(
                id = "384712",
                name = "新手友好 501",
                creatorId = "u_lin",
                config = MatchConfig(
                    matchType = MatchType.X01,
                    targetScore = 501,
                    mode = MatchMode.MULTI_LEG,
                    legsToWin = 3
                ),
                members = listOf(
                    RoomMember("u_lin", "林小风", HumanAvatar.HUMAN_1.key, isCreator = true, isReady = true),
                    RoomMember("u_qi", "齐南", HumanAvatar.HUMAN_3.key, isReady = true)
                ),
                createdAt = now - 240_000
            ),
            Room(
                id = "927461",
                name = "Cricket 快速局",
                creatorId = "u_ming",
                config = MatchConfig(matchType = MatchType.CRICKET, outMode = OutMode.STRAIGHT_OUT),
                members = listOf(
                    RoomMember("u_ming", "周明", HumanAvatar.HUMAN_2.key, isCreator = true, isReady = true)
                ),
                createdAt = now - 90_000
            ),
            Room(
                id = "156823",
                name = "高手房 701 · 双出",
                creatorId = "u_zhao",
                config = MatchConfig(
                    matchType = MatchType.X01,
                    targetScore = 701,
                    mode = MatchMode.MULTI_LEG,
                    legsToWin = 5,
                    outMode = OutMode.DOUBLE_OUT
                ),
                members = listOf(
                    RoomMember("u_zhao", "赵野", HumanAvatar.HUMAN_1.key, isCreator = true, isReady = true),
                    RoomMember("u_shen", "沈奕", HumanAvatar.HUMAN_4.key, isReady = true),
                    RoomMember("u_wu", "吴桐", HumanAvatar.HUMAN_3.key)
                ),
                createdAt = now - 500_000
            ),
            Room(
                id = "640158",
                name = "休闲 301 随便打",
                creatorId = "u_luo",
                config = MatchConfig(
                    matchType = MatchType.X01,
                    targetScore = 301,
                    mode = MatchMode.CASUAL,
                    legsToWin = 0,
                    outMode = OutMode.STRAIGHT_OUT
                ),
                members = listOf(
                    RoomMember("u_luo", "罗晴", HumanAvatar.HUMAN_4.key, isCreator = true, isReady = true)
                ),
                createdAt = now - 40_000
            ),
            Room(
                id = "208594",
                name = "Cricket 三局两胜",
                creatorId = "u_he",
                config = MatchConfig(
                    matchType = MatchType.CRICKET,
                    mode = MatchMode.MULTI_LEG,
                    legsToWin = 2,
                    outMode = OutMode.STRAIGHT_OUT
                ),
                members = listOf(
                    RoomMember("u_he", "何岸", HumanAvatar.HUMAN_2.key, isCreator = true, isReady = true),
                    RoomMember("u_qi2", "齐悦", HumanAvatar.HUMAN_1.key, isReady = true),
                    RoomMember("u_tan", "谭啸", HumanAvatar.HUMAN_3.key, isReady = true),
                    RoomMember("u_yu", "余笙", HumanAvatar.HUMAN_4.key)
                ),
                createdAt = now - 150_000
            ),
            // 对局中的房间：用于观战演示
            Room(
                id = "751936",
                name = "X01 501 · 实战局",
                creatorId = "u_zhao2",
                config = MatchConfig(
                    matchType = MatchType.X01,
                    targetScore = 501,
                    mode = MatchMode.MULTI_LEG,
                    legsToWin = 3
                ),
                status = RoomStatus.PLAYING,
                members = listOf(
                    RoomMember("u_zhao2", "赵野", HumanAvatar.HUMAN_1.key, isCreator = true, isReady = true),
                    RoomMember("u_ming2", "周明", HumanAvatar.HUMAN_2.key, isReady = true)
                ),
                createdAt = now - 640_000
            ),
            // 私密房间：不出现在大厅列表，仅房间号可入
            Room(
                id = "471029",
                name = "老友局",
                creatorId = "u_qiu",
                config = MatchConfig(matchType = MatchType.X01, targetScore = 501, legsToWin = 3),
                visibility = RoomVisibility.PRIVATE,
                members = listOf(
                    RoomMember("u_qiu", "邱实", HumanAvatar.HUMAN_3.key, isCreator = true, isReady = true)
                ),
                createdAt = now - 300_000
            )
        )
        return rooms.associateBy { it.id }
    }

    private fun seedSpectators(): Map<String, SpectatorSnapshot> {
        val snapshot = SpectatorSnapshot(
            roomId = "751936",
            roomName = "X01 501 · 实战局",
            configName = "X01 - 501",
            leg = 4,
            legsToWin = 3,
            players = listOf(
                SpectatorPlayer(
                    id = "u_zhao2",
                    name = "赵野",
                    avatar = HumanAvatar.HUMAN_1.key,
                    legsWon = 2,
                    score = 141
                ),
                SpectatorPlayer(
                    id = "u_ming2",
                    name = "周明",
                    avatar = HumanAvatar.HUMAN_2.key,
                    legsWon = 1,
                    score = 76,
                    isActive = true
                )
            ),
            turns = listOf(
                SpectatorTurn(1, "赵野", "T20 T20 T20", 180, 321),
                SpectatorTurn(2, "周明", "S20 S20 S20", 60, 441),
                SpectatorTurn(3, "赵野", "T19 S19 D16", 108, 213),
                SpectatorTurn(4, "周明", "T20 T20 S5", 125, 316),
                SpectatorTurn(5, "赵野", "S1 S5 S20", 26, 187),
                SpectatorTurn(6, "周明", "D20 D20 D20", 120, 196),
                SpectatorTurn(7, "赵野", "S20 T1 S20", 43, 144),
                SpectatorTurn(8, "周明", "T20 S20 T18", 114, 82),
                SpectatorTurn(9, "赵野", "T20 T20 S1", 121, 23)
            )
        )
        return mapOf("751936" to snapshot)
    }

    private fun initialSnapshotFor(room: Room): SpectatorSnapshot = SpectatorSnapshot(
        roomId = room.id,
        roomName = room.name,
        configName = room.config.displayName,
        leg = 1,
        legsToWin = room.config.legsToWin,
        players = room.members.mapIndexed { index, member ->
            SpectatorPlayer(
                id = member.id,
                name = member.name,
                avatar = member.avatar,
                legsWon = 0,
                score = if (room.config.matchType == MatchType.X01) room.config.targetScore else 0,
                isActive = index == 0
            )
        },
        turns = emptyList()
    )

    private data class MockTurn(val darts: String, val scored: Int)

    private val MOCK_TURNS = listOf(
        MockTurn("T20 T20 T20", 180),
        MockTurn("T20 T20 S5", 125),
        MockTurn("T20 S20 T18", 114),
        MockTurn("S20 S20 S20", 60),
        MockTurn("T19 S19 D16", 108),
        MockTurn("T20 S5 D20", 105),
        MockTurn("S20 T1 S20", 43),
        MockTurn("D20 D20 D20", 120),
        MockTurn("T20 T20 S1", 121),
        MockTurn("S1 S5 S20", 26),
        MockTurn("T18 T18 T18", 162),
        MockTurn("S20 S20 D20", 80)
    )

    private const val TARGET_SCORE = 501
}
