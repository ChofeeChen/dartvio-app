package com.dartvio.app.domain.room

/**
 * 房间事实 → 大厅快照（纯函数）。
 *
 * ## 为什么单独抽出来
 *
 * 「大厅里这一行该显示什么」是**展示口径**，改动频率远高于事件重放本身；
 * 夹在写快照的协程里就只能靠真机对拍验证。抽成纯函数之后，
 * 「没开局时显示起始分」「打完一手之后比分与轮数取哪一局」这类问题可以在单测里枚举。
 *
 * ## 取值口径
 * - 剩余分：对局中的**本局**剩余分（`X01LegState.players`），未开局时是起始分 ——
 *   大厅上「剩余 501」表示「还没开始」，不是「他打得很差」。
 * - 局数：取 `RoomMatch.legsWon`（跨局累计），**不取** `X01PlayerState.legsWon`
 *   （后者是本地对局口径，联机下每局都重置）。
 * - 轮数：本局已完成的轮数 + 1；未开局时为 0（界面显示「等待开局」而不是「第 1 轮」）。
 */
object RoomLobbyState {

    /**
     * @param seq 快照序号。传**事件流的最大 seq**：两端各自写快照时，
     *       用同一个单调递增的量才不会出现「客人写的 seq 比房主小，大厅一直显示旧的那条」。
     * @param source 进房来源（仅统计用）。
     * @return null = 这个房间还不成立（没有任何事件），大厅里不该出现它。
     */
    fun of(
        state: RoomState,
        seq: Int,
        source: RoomJoinSource = RoomJoinSource.HOST
    ): RoomLiveState? {
        val room = state.room ?: return null
        val match = state.match

        val host = room.members.firstOrNull { it.id == room.creatorId }
            ?: room.members.firstOrNull()
        val guest = room.members.firstOrNull { it.id != host?.id }

        val hostId = host?.id ?: room.creatorId
        val guestId = guest?.id.orEmpty()

        val leg = match?.leg
        val finished = match?.isFinished == true

        return RoomLiveState(
            roomId = room.id,
            seq = seq,
            hostId = hostId,
            hostName = host?.name.orEmpty(),
            guestId = guestId,
            guestName = guest?.name.orEmpty(),
            legsHost = match?.legsWon?.get(hostId) ?: 0,
            legsGuest = match?.legsWon?.get(guestId) ?: 0,
            scoreHost = remainingOf(match, room, hostId),
            scoreGuest = remainingOf(match, room, guestId),
            round = if (leg == null || finished) 0 else leg.roundsCompleted + 1,
            legsToWin = room.config.legsToWin,
            playing = room.status == RoomStatus.PLAYING && !finished,
            source = source.key
        )
    }

    /**
     * 某个成员此刻的剩余分。
     *
     * 席位下标是唯一可靠的对应方式：`X01LegState.players` 与 `RoomMatch.members`
     * **同序**（见 `RoomMatch.members` 的注释）。按 id 找会更直观，但 `X01PlayerState`
     * 里存的是 `playerId`，与成员 id 是否同一套编号由重放决定 —— 下标是重放自己保证的。
     */
    private fun remainingOf(match: RoomMatch?, room: Room, memberId: String): Int {
        val start = room.config.targetScore
        if (match == null || memberId.isBlank()) return start
        val index = match.members.indexOfFirst { it.id == memberId }
        return match.leg.players.getOrNull(index)?.remaining ?: start
    }
}
