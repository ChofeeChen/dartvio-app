package com.dartvio.app.domain.room

/**
 * 房间的加入策略（创建设置 2）。
 *
 * ## 为什么是「意图」而不是「状态」
 *
 * 用户原案「已有镖友 / 待招镖友」描述的是**房间里有没有人**，而房主实际在选的是
 * **接不接受陌生人加入**。用状态描述意图，用户无法预判点下去会发生什么，
 * 而且「已有镖友」极易被读成「已满员」，新用户不敢点。
 *
 * ## 为什么两种房间都进大厅
 *
 * 本期不提供私密房间（用户决策）：低供给阶段大厅最贵的一次流失是「打开是空的」。
 * 因此 [FRIENDS_ONLY] 只是**不能从列表直接加入**，仍然出现在列表里供观战。
 */
enum class RoomJoinPolicy(
    val key: String,
    val label: String,
    /** 创建页副标题：把「点下去会怎样」写在选项上。 */
    val hint: String
) {
    OPEN(
        key = "open",
        label = "开放招募",
        hint = "出现在线大厅，任何人可以直接加入"
    ),
    FRIENDS_ONLY(
        key = "friends",
        label = "仅限好友",
        hint = "大厅可见，但只有拿到房间号的人能加入，其他人可观战"
    );

    /** 是否必须凭房间号才能加入（列表上的「加入」按钮据此禁用）。 */
    val requiresRoomCode: Boolean get() = this == FRIENDS_ONLY

    companion object {

        /**
         * 数据库 / 旧端兼容解析：未知 key 一律按 [OPEN]。
         *
         * 不能因为解析失败就隐藏房间 —— 那表现为「明明有人在建房，大厅却是空的」，
         * 而这类问题在真机上无法自查。
         */
        fun fromKey(key: String?): RoomJoinPolicy =
            entries.firstOrNull { it.key == key } ?: OPEN
    }
}

/**
 * 这一局的人是从哪儿进来的（PRD 拍板项②：统计加「进房来源」）。
 *
 * 它回答的是产品问题，不是技术问题：**房间号这个入口还有没有人在用**。
 * 大厅上线之后，房间号唯一剩下的价值就是熟人约战（微信发号）；
 * 如果来源数据表明几乎没人再用房间号，那条路径就该被简化掉，
 * 反之则不该为了大厅去动它 —— 两个入口的成本差着一个数量级。
 *
 * 只落在大厅快照里（不进事件流）：事件流是对局事实，加一个与对局无关的来源字段
 * 会让所有重放代码为它付代价。
 */
enum class RoomJoinSource(val key: String) {
    /** 房主自己建的房间（不是"进来"的）。 */
    HOST(key = "host"),

    /** 从在线大厅点进去的。 */
    LOBBY(key = "lobby"),

    /** 输入房间号进来的（熟人约战）。 */
    CODE(key = "code");

    companion object {
        fun fromKey(key: String?): RoomJoinSource =
            entries.firstOrNull { it.key == key } ?: CODE
    }
}
