package com.dartvio.app.domain.room

import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.rulesCode

/**
 * 房间纯规则（M5 实时同步 / M6 比赛大厅与房间）。
 *
 * ## 为什么单独抽一层
 *
 * 联网后房间状态由**服务端权威**产生（[com.dartvio.app.net.host.AuthoritativeRoomStore]），
 * 客户端只能发起「语义命令」而不能直接改状态。判定逻辑一旦散落在 server 路由与客户端乐观看法里，
 * 两端就会长出两套稍有不同的规则 —— 那正是「A 显示已满、B 显示可加入」这类问题的来源。
 *
 * 因此所有判定都收敛到本 object 的**纯函数**：
 * - 入参是数据、出参是数据，不持有状态、不碰协程、不需要 Android 运行时；
 * - 因此边界可以被单测完全枚举（见 `RoomRulesTest`）；
 * - 服务端路由、Host 仓库、客户端乐观看法共用同一份实现。
 *
 * 注意：本层刻意不引入「错误码」概念 —— 错误码属于协议层（
 * [com.dartvio.app.net.protocol.RpcErrors]），由路由做一次映射，避免领域层被传输细节污染。
 */
object RoomRules {

    /** 开局所需最少人数。 */
    const val MIN_PLAYERS = 2

    fun isHost(room: Room, memberId: String): Boolean = room.creatorId == memberId

    /**
     * 大厅可见房间：公开 + 等候中优先 + 新建优先。
     *
     * 排序键最后一级用 `id` 兜底，保证**同一份状态永远产出同一份列表**：
     * 否则 `createdAt` 相同的两个房间顺序会随 map 迭代顺序漂移，
     * 表现为客户端列表无端抖动，而 `RoomBroadcastPlan` 也会误判「列表变了」而反复广播。
     */
    fun publicRoomsOf(source: Map<String, Room>): List<Room> =
        source.values
            .filter { it.visibility == RoomVisibility.PUBLIC }
            .sortedWith(
                compareBy(
                    // 已结束的房排最后：它是**记录**而不是「可以进去的房间」，
                    // 让它混在等候中的房里，用户会把它当成「等了很久没人来」再点一次。
                    { it.status == RoomStatus.ENDED },
                    { it.status != RoomStatus.WAITING },
                    { -it.createdAt },
                    { it.id }
                )
            )

    // ===== 加入 =====

    /**
     * 加入判定，顺序与既有 Mock（`LocalRoomRepository.joinRoom`）保持一致：
     * 不存在 → 已在房内 → 已结束 → 已开局 → 已满。
     *
     * 顺序有意义：「房间已满」与「对局已开始」同时成立时，先报「已开始」——
     * 对用户而言「这局开始了」比「人满了」更接近真实原因。
     *
     * [RoomStatus.ENDED] 单独判在「已开局」之前：它是**历史记录**，不是一场进行中的对局，
     * 报「对局已开始」会让人以为「再等一下就能进去」，于是反复点。
     * 沿用 [JoinResult.RoomNotFound]（文案是「房间不存在或已关闭」）：
     * 为它新造一个结果类型要动 `JoinResult` 的所有分支，而「已关闭」这句话本来就说对了。
     */
    fun join(room: Room?, member: RoomMember): JoinResult {
        if (room == null) return JoinResult.RoomNotFound
        if (room.contains(member.id)) return JoinResult.AlreadyJoined
        if (room.status == RoomStatus.ENDED) return JoinResult.RoomNotFound
        if (room.status != RoomStatus.WAITING) return JoinResult.MatchStarted
        if (room.isFull) return JoinResult.RoomFull
        return JoinResult.Success(room.copy(members = room.members + member))
    }

    // ===== 成员 =====

    /**
     * 构造新成员。
     *
     * 房主视为常备（`isReady = true`），与既有 Mock 一致：
     * 否则房主必须自己点一次「准备」，而 UI 并不给房主这个按钮。
     */
    fun newMember(
        wireId: String,
        rawName: String,
        avatar: String,
        isCreator: Boolean,
        /** 加入者自带的 PPR；`null` = 他还没打过正式赛（界面不显示，不写 0）。 */
        ppr: Double? = null
    ): RoomMember = RoomMember(
        id = wireId,
        name = normalizeDisplayName(rawName),
        avatar = avatar,
        isCreator = isCreator,
        isReady = isCreator,
        ppr = ppr
    )

    /** 昵称兜底：空白名不允许上线，否则列表里会出现「两个空名字」。 */
    fun normalizeDisplayName(raw: String): String =
        raw.trim().ifBlank { "${LocalUser.DEFAULT_NAME}1" }

    /** 房间名兜底：未命名房间用「<房主名> 的房间」。 */
    fun normalizeRoomName(raw: String, hostName: String): String =
        raw.trim().ifBlank { "$hostName 的房间" }

    /** 整体替换某个成员（准备状态、改名等）。成员不在房内时原样返回。 */
    fun withMember(room: Room, member: RoomMember): Room =
        room.copy(
            members = room.members.map { if (it.id == member.id) member else it }
        )

    /** 设置准备状态。成员不在房内时原样返回。 */
    fun withReady(room: Room, memberId: String, ready: Boolean): Room =
        room.copy(
            members = room.members.map {
                if (it.id == memberId) it.copy(isReady = ready) else it
            }
        )

    /**
     * 标记某个成员的在线与否（M5 T5）。**只动这一个字段**：
     * 席位、准备状态、房主标记、创建顺序一律不动 —— 重连者要拿回的正是它们。
     */
    fun withOnline(room: Room, memberId: String, online: Boolean): Room =
        room.copy(
            members = room.members.map {
                if (it.id == memberId) it.copy(online = online) else it
            }
        )

    /**
     * 移除成员。
     *
     * @return 移除后的房间；**房主离开返回 null，语义是「房间解散」**
     *   （对应既有 Mock 与 `room_destroyed` 事件）。用 null 而不是布尔返回值，
     *   是为了让调用方无法忘记处理解散分支。
     */
    fun withoutMember(room: Room, memberId: String): Room? =
        if (isHost(room, memberId)) {
            null
        } else {
            room.copy(members = room.members.filterNot { it.id == memberId })
        }

    // ===== 房间设置（房主权限，调用前须自行判定 isHost） =====

    fun withVisibility(room: Room, visibility: RoomVisibility): Room =
        room.copy(visibility = visibility)

    fun withAllowSpectators(room: Room, allowed: Boolean): Room =
        room.copy(allowSpectators = allowed)

    // ===== 开局 =====

    /**
     * 开局前置条件；返回 null 表示可以开始。
     *
     * 与 `RoomWaitingScreen.BottomAction` 的按钮可用条件
     * （`isHost && members.size >= 2 && allReady`）严格同构 —— 按钮看起来能点就必须真的能开，
     * 否则用户会遇到「按钮亮着但点了没反应」。
     */
    fun startBlocker(room: Room, memberId: String): StartBlocker? = when {
        !isHost(room, memberId) -> StartBlocker.NOT_HOST
        room.status != RoomStatus.WAITING -> StartBlocker.ALREADY_PLAYING
        room.members.size < MIN_PLAYERS -> StartBlocker.NOT_ENOUGH_PLAYERS
        !room.allReady -> StartBlocker.MEMBERS_NOT_READY
        else -> null
    }

    /** 进入对局中：回合流水号归零，观战排序从头开始。 */
    fun started(room: Room): Room =
        room.copy(status = RoomStatus.PLAYING, turnSeq = 0)

    /**
     * 回到等候中（M5 T9「再来一局」）：与 [started] 对称，回合流水号同样归零。
     *
     * 只改状态，**不动成员与配置** —— 那是「再来一局」唯一要保留的东西
     * （见 `RoomMatchRules.rematch` 里「为什么不再读一次房间」）。
     */
    fun backToWaiting(room: Room): Room =
        room.copy(status = RoomStatus.WAITING, turnSeq = 0)

    /**
     * 开局时的观战首帧，即权威对局的**第 0 帧**。
     *
     * X01 交给 [RoomMatchRules] 派生，而不是在这里再拼一份：首帧与「第一次提交之后的帧」
     * 必须出自同一段代码，否则「开局显示 501:501、一次提交后突然跳到某一边 480」这类不一致
     * 会以最难查的形式出现 —— 两处结果在人眼看来都「挺合理」。
     *
     * 其它玩法本切片不维护权威对局（见 [RoomMatchRules.start]），因此退回静态首帧：
     * 双方按目标分（Cricket 记 0）、首局、无回合历史。此处刻意不预留假回合，
     * 免得看起来像「已经在同步」。
     *
     * ⚠️ `LocalRoomRepository.initialSnapshotFor` 是同一语义的 Mock 私有实现。
     * 按 BL-003 决策那个文件**不被改动**，因此这里有意保留第二份实现。
     */
    fun initialSpectatorSnapshot(room: Room): SpectatorSnapshot =
        RoomMatchRules.start(room)?.let(RoomMatchRules::snapshot) ?: staticSpectatorSnapshot(room)

    private fun staticSpectatorSnapshot(room: Room): SpectatorSnapshot = SpectatorSnapshot(
        roomId = room.id,
        roomName = room.name,
        configName = room.config.displayName,
        leg = 1,
        legsToWin = room.config.legsToWin,
        rulesShort = room.config.rulesCode(),
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
}

/** 开局被拒绝的原因；文案直接面向用户，因此放在领域层而不是散在路由里。 */
enum class StartBlocker(val message: String) {
    /** 非房主。协议码 40301。 */
    NOT_HOST("只有房主可以开始比赛"),

    /** 已经开局（或已结束）。协议码 40001。 */
    ALREADY_PLAYING("对局已经开始"),

    /** 人数不足。协议码 40001。 */
    NOT_ENOUGH_PLAYERS("至少需要 ${RoomRules.MIN_PLAYERS} 名玩家"),

    /** 有人未准备。协议码 40001（契约原文：「有玩家未准备」）。 */
    MEMBERS_NOT_READY("有玩家未准备");
}
