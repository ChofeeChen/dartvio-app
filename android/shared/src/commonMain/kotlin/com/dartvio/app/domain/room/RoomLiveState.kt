package com.dartvio.app.domain.room

/**
 * 房间在大厅里的实时展示状态（一局一个快照）。
 *
 * ## 为什么是「追加」而不是「更新」
 *
 * 线上表的 anon key 只有读 + 插入权限，没有 update。放开 update 的代价是任何人都
 * 能改任意房间的大厅展示（甚至伪造「已结束」）。所以房主每打完一手就**插入**一条快照，
 * 大厅取每个房间最新的那一条：append-only 与现有权限模型一致，也让「一局的过程」
 * 天然留痕。
 *
 * ## 为什么带上 hostId / guestId
 *
 * 昵称可以重名，身份不能。展示用昵称 + 短码，归属用 [hostId] / [guestId]（`playerId`）。
 * 只存昵称的话，将来做个人战绩页时无从区分两个同名的人。
 */
data class RoomLiveState(
    val roomId: String,
    /** 快照序号：同一房间内递增，大厅取最大的一条。 */
    val seq: Int,
    val hostId: String = "",
    val hostName: String = "",
    val guestId: String = "",
    val guestName: String = "",
    val legsHost: Int = 0,
    val legsGuest: Int = 0,
    /** 本局剩余分（未开局时等于起始分）。 */
    val scoreHost: Int = 0,
    val scoreGuest: Int = 0,
    /** 已投轮数。 */
    val round: Int = 0,
    val legsToWin: Int = 0,
    val playing: Boolean = false,
    /**
     * 进房来源（[RoomJoinSource.key]）：大厅 / 房间号。
     *
     * 只有**统计**用它，大厅列表不显示 —— 一个玩家不该在大厅里看见「这家伙是从
     * 大厅进来的」。缺省空串 = 老端写的行，不做任何推断。
     */
    val source: String = ""
) {

    /** 大厅标题里的局制文案。 */
    val legsLabel: String
        get() = if (legsToWin <= 1) "1 局定胜负" else "先到 $legsToWin 局"
}
