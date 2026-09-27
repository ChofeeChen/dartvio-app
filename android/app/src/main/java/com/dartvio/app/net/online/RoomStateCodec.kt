package com.dartvio.app.net.online

import com.dartvio.app.domain.room.RoomLiveState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * `dartvio_room_state` 的行编解码（追加式快照，见 [RoomLiveState]）。
 *
 * ## 为什么展示字段包在 `state` 里
 *
 * 这张表是 append-only 的：一局会插很多条。把展示字段摊成列，就是每插一条
 * 都要写十几个字段；包成一个 JSON 后，未来加一个展示字段不需要再改表结构
 * （客户端自己兼容旧行）。
 */
object RoomStateCodec {

    private const val ROOM_ID = "room_id"
    private const val SEQ = "seq"
    private const val STATE = "state"

    private const val HOST_ID = "host_id"
    private const val HOST_NAME = "host_name"
    private const val GUEST_ID = "guest_id"
    private const val GUEST_NAME = "guest_name"
    private const val LEGS_H = "legs_h"
    private const val LEGS_G = "legs_g"
    private const val SCORE_H = "score_h"
    private const val SCORE_G = "score_g"
    private const val ROUND = "round"
    private const val LEGS_TO_WIN = "legs_to_win"
    private const val PLAYING = "playing"
    private const val SOURCE = "source"

    fun encodeRow(state: RoomLiveState): JsonObject = buildJsonObject {
        put(ROOM_ID, state.roomId)
        put(SEQ, state.seq)
        put(STATE, encodeState(state))
    }

    fun encodeState(state: RoomLiveState): JsonObject = buildJsonObject {
        put(HOST_ID, state.hostId)
        put(HOST_NAME, state.hostName)
        put(GUEST_ID, state.guestId)
        put(GUEST_NAME, state.guestName)
        put(LEGS_H, state.legsHost)
        put(LEGS_G, state.legsGuest)
        put(SCORE_H, state.scoreHost)
        put(SCORE_G, state.scoreGuest)
        put(ROUND, state.round)
        put(LEGS_TO_WIN, state.legsToWin)
        put(PLAYING, state.playing)
        put(SOURCE, state.source)
    }

    /**
     * 行 → 快照。任何一环不成立返回 null（由调用方整条丢弃）。
     *
     * 缺字段一律取默认值而不是丢弃整条：多一台旧端写进来的行少两个新字段，
     * 整条丢掉的表现是「大厅里这个房间突然没有比分了」。
     */
    fun decodeRow(element: kotlinx.serialization.json.JsonElement): RoomLiveState? {
        val obj = element as? JsonObject ?: return null
        val roomId = obj[ROOM_ID]?.jsonPrimitive?.content ?: return null
        val seq = obj[SEQ]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        return decodeState(roomId, seq, obj[STATE] as? JsonObject ?: JsonObject(emptyMap()))
    }

    private fun decodeState(roomId: String, seq: Int, obj: JsonObject): RoomLiveState = RoomLiveState(
        roomId = roomId,
        seq = seq,
        hostId = obj.str(HOST_ID),
        hostName = obj.str(HOST_NAME),
        guestId = obj.str(GUEST_ID),
        guestName = obj.str(GUEST_NAME),
        legsHost = obj.int(LEGS_H),
        legsGuest = obj.int(LEGS_G),
        scoreHost = obj.int(SCORE_H),
        scoreGuest = obj.int(SCORE_G),
        round = obj.int(ROUND),
        legsToWin = obj.int(LEGS_TO_WIN),
        playing = obj[PLAYING]?.jsonPrimitive?.content.toBoolean(),
        source = obj.str(SOURCE)
    )

    private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.content ?: ""

    private fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
}
