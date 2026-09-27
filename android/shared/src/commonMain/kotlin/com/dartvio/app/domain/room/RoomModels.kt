package com.dartvio.app.domain.room

import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.platform.PlatformTime

/**
 * 房间状态（M6 F6.1 房间列表展示维度）。
 *
 * 三态必须**互斥且穷尽**：大厅卡片靠它区分「现在能进来」「正在打」「已经是历史」。
 * 早期只有 WAITING / PLAYING 两态，一间打完的房只能继续显示「等候中」——
 * 于是两台手机都能再点进去，各自补一条加入事件，比分与回合记录从此对不上
 * （2026-09-26 真机实测）。[ENDED] 就是为了让这个动作不再可能被做出来。
 */
enum class RoomStatus {
    /** 等候中：可加入。 */
    WAITING,

    /** 对局中：不可加入，但可观战（若房主允许）。 */
    PLAYING,

    /**
     * 已结束：打满局数正常收官，或未打完就有人退出。
     *
     * 不可加入、也不再是 live。卡片上要能被一眼认出是**记录**而不是**可以进的房** ——
     * 否则用户会把它当成「等了很久没人来的房」再点一次。
     */
    ENDED
}

/** 房间可见性（M6 F6.7 私密房间）。 */
enum class RoomVisibility {
    /** 公开房间：出现在大厅列表，可用房间号或列表加入。 */
    PUBLIC,

    /** 私密房间：不出现在大厅列表，仅能凭房间号/邀请加入。 */
    PRIVATE
}

/** 房间成员。 */
data class RoomMember(
    val id: String,
    val name: String,
    /** 头像标识，对应 HumanAvatar / AiAvatar 的 key。 */
    val avatar: String,
    /** 是否为房主。 */
    val isCreator: Boolean = false,
    /** 是否已准备（房主视为常备）。 */
    val isReady: Boolean = false,
    /**
     * 这位成员的 **PPR**（加入那一刻带在 `member_joined` 事件里）。
     *
     * 为什么必须随事件下发而不是让对端去查：房间是**陌生人**之间的场景，
     * 对面那个人在别人的手机上没有档案可查。没有这个数，等候页上就只剩一个昵称 ——
     * 而「要不要跟他打」这个决定，恰恰是靠 PPR 下的（2026-09-27 真机反馈）。
     *
     * `null` / 非正数 = 他还没打过正式赛，界面据此**整块不显示**（写 0.0 会被读成「他很菜」）。
     */
    val ppr: Double? = null,
    /**
     * 是否**在线**（M5 T5）。
     *
     * 断线只把这个位置为 false，**不移除成员**：成员身上还挂着席位、准备状态与已得分，
     * 而「连不上」与「走了」是两件事 —— 关一下 WiFi 不该把人从房间里开除。
     * 真正的移除只有 [com.dartvio.app.domain.room.RoomRules.withoutMember]
     * （明确离开 / 被踢 / 房主解散），以及 T8 的弃权倒计时。
     *
     * 默认 true：本地 Mock 与老端快照里都没有这个字段，缺即视为在线。
     */
    val online: Boolean = true
)

/**
 * 房间（M6 F6.2 创建房间 / F6.3 房间等候）。
 *
 * V0.1 由 [com.dartvio.app.data.room.LocalRoomRepository] 在内存中模拟，
 * 联网后由真实房间服务填充，UI 无需改动。
 */
data class Room(
    /** 6 位数字房间号（内部加入凭证；界面上已不再展示，见 D5）。 */
    val id: String,
    val name: String,
    val creatorId: String,
    val config: MatchConfig,
    val visibility: RoomVisibility = RoomVisibility.PUBLIC,
    /** M6 F6.8：是否允许观战。 */
    val allowSpectators: Boolean = true,
    val status: RoomStatus = RoomStatus.WAITING,
    val members: List<RoomMember> = emptyList(),
    val createdAt: Long = PlatformTime.nowMillis(),
    /** 房间内回合流水号，用于观战排序。 */
    val turnSeq: Int = 0,
    /**
     * 预约开始时刻（epoch 毫秒）；`null` = 立即开始。
     *
     * 与 [createdAt] 是两件事：后者是「这间房什么时候建的」，前者是「比赛什么时候开打」。
     * 预约房在到点之前仍然可被看见与加入（它是一间等人的房），只是卡片上显示的是倒计时
     * 而不是「等候中」。
     */
    val startsAt: Long? = null,
    /**
     * 房主的 PPR（每回合得分，用于让陌生人在点进来之前有个实力预期）。
     * `null` = 这一端拿不到（云端还没这一列 / 房主没打过正式赛），界面据此整块不显示。
     */
    val hostPpr: Double? = null,
    /** 房主的信用分（见 [com.dartvio.app.domain.credit.CreditScorer]）；`null` = 拿不到。 */
    val hostCredit: Int? = null
) {
    val creatorName: String
        get() = members.firstOrNull { it.id == creatorId }?.name ?: "房主"

    val isFull: Boolean get() = members.size >= MAX_MEMBERS

    /** 除房主外是否都已准备。 */
    val allReady: Boolean
        get() = members.size >= 2 && members.filterNot { it.isCreator }.all { it.isReady }

    fun contains(memberId: String): Boolean = members.any { it.id == memberId }

    /** 卡片上的席位文案，如「1/2 人」。上限一律取自 [MAX_MEMBERS]，不写死字面量。 */
    val seatsLabel: String get() = "${members.size}/${MAX_MEMBERS} 人"

    companion object {
        /**
         * 房间人数上限：**2 人**。
         *
         * 飞镖联机是 1v1 —— 早期写成 8 是为了给「手动平衡队伍」留余地，
         * 但那套从来没被实现过，留在界面上的后果是卡片写着「0/8 人」：
         * 用户看到的是一个永远填不满的房间（2026-09-26 真机反馈）。
         */
        const val MAX_MEMBERS = 2
    }
}

/** 创建房间的表单草稿。 */
data class RoomDraft(
    val name: String,
    val config: MatchConfig,
    val visibility: RoomVisibility = RoomVisibility.PUBLIC,
    val allowSpectators: Boolean = true,
    /** 加入策略（在线大厅用；局域网创建走默认 [RoomJoinPolicy.OPEN]）。 */
    val joinPolicy: RoomJoinPolicy = RoomJoinPolicy.OPEN,
    /** 预约开始时刻（epoch 毫秒）；`null` = 建好就开打（[StartMode.NOW]）。 */
    val startsAt: Long? = null
)

/**
 * 开赛方式（创建房间设置页的一组单选）。
 *
 * 放在领域层而不是 UI 层：它会被写进 [RoomDraft.startsAt] 并一路带到房间索引表，
 * 「立即开始」与「预约」在**数据上**的唯一区别就是 `null` 与非 null ——
 * 若只在界面里留一个布尔，重建页面时「到底约了几点」就会丢。
 */
enum class StartMode(val label: String) {
    /** 建好就开打：房间立刻变成「等候中」。 */
    NOW("立即开始"),

    /** 约一个时间点：到点之前卡片显示倒计时。 */
    SCHEDULED("预约时间")
}

/** 大厅列表筛选维度（M6 F6.9，P1 先做轻量版）。 */
enum class RoomFilter(val label: String) {
    ALL("全部"),
    X01("X01"),
    CRICKET("Cricket"),
    WAITING("等候中");

    fun matches(room: Room): Boolean = when (this) {
        ALL -> true
        X01 -> room.config.matchType == MatchType.X01
        CRICKET -> room.config.matchType == MatchType.CRICKET
        WAITING -> room.status == RoomStatus.WAITING
    }
}

/** 加入房间的结果。 */
sealed interface JoinResult {
    data class Success(val room: Room) : JoinResult
    data object RoomNotFound : JoinResult
    data object RoomFull : JoinResult
    data object MatchStarted : JoinResult
    data object AlreadyJoined : JoinResult

    /**
     * 未能得到房间的明确答复（未连接、超时、主机返回了无法归类的原因）。
     *
     * 存在的理由：单机 Mock 永远能给出一句确定的话，但联机时「连不上主机」是常态之一。
     * 若把它硬塞进 [RoomNotFound]，界面会显示「房间不存在或已关闭」——
     * 把网络问题说成房间问题，用户会去重新输房号，而真正该做的是检查 WiFi。
     * [message] 直接沿用主机给出的文案（或本机合成的文案），不做二次翻译。
     */
    data class Failed(override val message: String) : JoinResult

    val message: String
        get() = when (this) {
            is Success -> "已加入房间"
            RoomNotFound -> "房间不存在或已关闭"
            RoomFull -> "房间人数已满"
            MatchStarted -> "对局已开始，无法加入"
            AlreadyJoined -> "你已在该房间中"
            is Failed -> message
        }
}

// ===== 观战（M6 F6.6 仅比分观战，P0）=====

/** 观战视角下的选手比分。 */
data class SpectatorPlayer(
    val id: String,
    val name: String,
    val avatar: String,
    val legsWon: Int,
    /** 本局剩余分（Cricket 时表示已封闭数量）。 */
    val score: Int,
    val isActive: Boolean = false,
    /**
     * 历史 PPR（由房间成员信息带上；`null` = 拿不到）。
     *
     * 与卡片上那个「剩余分」分工：剩余分说的是**这一局**，PPR 说的是**这个人** ——
     * 转播里两张卡并排时，这两件事缺一个就看不出「谁占优、还是只是这局手气好」。
     *
     * 刻意放在**参数表最后**：既有调用点（含单测）都用位置参数构造选手，
     * 插在中间会让它们静默地把 `score` 传进 `ppr`。
     */
    val ppr: Double? = null
)

/** 观战视角下的单回合摘要。 */
data class SpectatorTurn(
    val seq: Int,
    val playerName: String,
    /** 三镖文本，如 "T20 T20 D20"。 */
    val darts: String,
    /** 本回合得分。 */
    val scored: Int,
    /** 回合结束后的剩余分。 */
    val remaining: Int,
    /** 是否爆分。 */
    val isBust: Boolean = false,
    /** 是否收镖结束本局。 */
    val isCheckout: Boolean = false
)

/** 观战快照。P0 只传比分与回合历史，不传视频流。 */
/**
 * 对局是**怎么**结束的（M5 T8）。
 *
 * 只有「非正常收镖」的结局才需要它：正常收镖时，帧里最后一条 `isCheckout` 已经把胜者说清楚了。
 * 弃权则不同 —— 那一手镖根本没投出来，帧里不会留下任何痕迹，胜者只能由服务端额外下发。
 *
 * [reason] 用字符串而不是枚举：它目前只有一个取值，而枚举会把「老端发来一个我不认识的原因」
 * 变成解码失败 —— 把一个只在文案上出现的差异升级成丢帧，不值得。
 */
data class MatchFinish(
    /** 胜者的**线上身份**（进入 UI 前需经身份投影）。 */
    val winnerId: String,
    val reason: String
) {
    companion object {
        /** 弃权：有人掉线超过时限。 */
        const val REASON_FORFEIT = "forfeit"
    }
}

/**
 * 观战帧里的**撤销窗口**（M5 T7）。
 *
 * 它随帧下发而不是靠 `turn_locked` 事件单独送一份：帧是「现在是什么样」，
 * 撤销窗口正是「现在」的一部分。事件只负责**通知变化**（撤回了 / 锁定了），
 * 不负责承载状态 —— 否则「收到事件但没收到帧」时，两端对「还能不能撤」会有两种答案。
 */
data class SpectatorUndo(
    /** 可撤回那一回合的提交者（**线上身份**，进入 UI 前须经 `RoomMatchView` 翻译）。 */
    val playerId: String,
    /** 被撤回回合的 `seq`。 */
    val seq: Int,
    /**
     * 窗口**长度**（毫秒），不是截止时刻。
     *
     * 不送绝对时刻是有意的：两端时钟无关（换设备、改系统时间都能让它们偏离），
     * 送时刻就要求客户端再维护一个时钟偏移。而局域网里帧的迟到量是毫秒级，
     * 客户端拿「窗口长度」自己走完这段倒计时，误差远小于一次点击的时间。
     * 真正的裁定仍在服务端：过了窗口点撤回，服务端照样拒（[UndoResult.Locked]）。
     */
    val windowMs: Long
)

data class SpectatorSnapshot(
    val roomId: String,
    val roomName: String,
    val configName: String,
    /** 当前第几局（从 1 开始）。 */
    val leg: Int,
    val legsToWin: Int,
    /**
     * 规则短码，如 `SI-DO`（直入双倍出）；空串 = 该玩法没有进/出镖口径（非 X01）。
     *
     * 由**权威帧**带来而不是界面自己从配置反推：对局页与观战页共用同一份快照，
     * 若各自去读自己那份配置，「两台手机上规则标签不一样」就又回来了。
     */
    val rulesShort: String = "",
    val players: List<SpectatorPlayer>,
    val turns: List<SpectatorTurn>,
    /**
     * 对局版本（M5 T6，来自 [com.dartvio.app.domain.room.RoomMatch.version]）。
     *
     * 默认 0 是为了让**老端的帧**照样读得懂：老主机不认识这个字段，缺即视为「还没打过」，
     * 客户端于是以「不带 expect_version」的方式提交（退化成 T6 之前的行为）。
     */
    val version: Int = 0,
    /** 撤销窗口（M5 T7）。null = 这一手已不可撤回；老端缺省即无窗口。 */
    val undo: SpectatorUndo? = null
) {
    val isFinished: Boolean
        get() = legsToWin > 0 && players.any { it.legsWon >= legsToWin }
}

/** 本地「我」的身份占位。M6 依赖 M1 账户体系，联网后替换为真实档案。 */
object LocalUser {
    const val ID = "local_me"
    var name: String = "我"
    var avatar: String = "HUMAN_1"

    /** 昵称未设置时的兜底前缀。 */
    const val DEFAULT_NAME = "玩家"

    /**
     * 头像未设置时的兜底标识。
     *
     * 与 [avatar] 的初始值同源：协议层（`DartVioHostServer`）在客户端没有上报头像时用它，
     * 而不是硬编码字面量 —— 否则「默认头像换成别的」就要改两处，且改漏处不会报错。
     */
    const val AVATAR_FALLBACK = "HUMAN_1"
}
