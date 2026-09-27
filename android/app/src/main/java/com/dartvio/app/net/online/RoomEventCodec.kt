package com.dartvio.app.net.online

import com.dartvio.app.domain.model.BullMode
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.InMode
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.domain.room.RoomEvent
import com.dartvio.app.domain.room.RoomEventPayload
import com.dartvio.app.domain.room.RoomEventType
import com.dartvio.app.domain.room.RoomVisibility
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 在线房间事件的线上格式（手写编解码）。
 *
 * 与 `net/protocol/RoomCodec` 一样刻意**不用** `@Serializable`：本项目没有启用
 * Kotlin 序列化编译器插件（见 `gradle/libs.versions.toml` 的注释），只依赖 `JsonElement` API。
 *
 * 每一个取值都用「缺省即默认」的方式读取：线上多一个字段、少一个字段都不该让整条事件报废。
 * 原因是这些事件会被**比当前版本更老的 App** 读到 —— 老端看不懂新字段最多是少显示一样东西，
 * 而解码失败会让它连「对手投了什么」都收不到。
 */
object RoomEventCodec {

    private val json = Json { ignoreUnknownKeys = true }

    /** 数据库行 → 事件；**任何一环不成立都返回 null**，由调用方整条丢弃。 */
    fun decodeRow(element: JsonElement): RoomEvent? {
        val obj = element as? JsonObject ?: return null
        val roomId = obj.str("room_id") ?: return null
        val seq = obj.int("seq", -1)
        if (seq < 0) return null
        val actorId = obj.str("actor_id") ?: return null
        val type = RoomEventType.fromKey(obj.str("type")) ?: return null
        val payload = decodePayload(type, obj.obj("payload")) ?: return null
        return RoomEvent(roomId = roomId, seq = seq, actorId = actorId, type = type, payload = payload)
    }

    /** 事件 → 数据库行（POST 的 body）。 */
    fun encodeRow(event: RoomEvent): JsonObject = buildJsonObject {
        put("room_id", event.roomId)
        put("seq", event.seq)
        put("actor_id", event.actorId)
        put("type", event.type.key)
        put("payload", encodePayload(event.payload))
    }

    // ===== 载荷 =====

    fun decodePayload(type: RoomEventType, source: JsonObject?): RoomEventPayload? {
        val obj = source ?: JsonObject(emptyMap())
        return when (type) {
            RoomEventType.ROOM_CREATED -> {
                val config = obj.obj("config")?.let(::decodeConfig) ?: return null
                RoomEventPayload.RoomCreated(
                    name = obj.str("name") ?: return null,
                    config = config,
                    visibility = obj.enumOrNull<RoomVisibility>("visibility") ?: RoomVisibility.PUBLIC,
                    allowSpectators = obj.bool("allowSpectators", true),
                    creatorName = obj.str("creatorName") ?: return null,
                    creatorAvatar = obj.str("creatorAvatar") ?: "HUMAN_1"
                )
            }

            RoomEventType.MEMBER_JOINED -> RoomEventPayload.MemberJoined(
                name = obj.str("name") ?: return null,
                avatar = obj.str("avatar") ?: "HUMAN_1"
            )

            RoomEventType.MEMBER_READY -> RoomEventPayload.MemberReady(
                ready = obj.bool("ready", true)
            )

            RoomEventType.MEMBER_KICKED -> RoomEventPayload.MemberKicked(
                memberId = obj.str("memberId") ?: return null
            )

            RoomEventType.TURN_SUBMITTED -> RoomEventPayload.TurnSubmitted(
                darts = obj.arr("darts")?.let(::decodeDarts) ?: return null,
                clientTurnId = obj.str("turnId") ?: ""
            )

            RoomEventType.SETTINGS_CHANGED -> RoomEventPayload.SettingsChanged(
                visibility = obj.enumOrNull<RoomVisibility>("visibility"),
                allowSpectators = obj.str("allowSpectators")?.toBooleanStrictOrNull()
            )

            RoomEventType.MEMBER_LEFT,
            RoomEventType.MATCH_STARTED,
            RoomEventType.TURN_UNDONE,
            RoomEventType.MATCH_REMATCH -> RoomEventPayload.Empty
        }
    }

    fun encodePayload(payload: RoomEventPayload): JsonObject = when (payload) {
        is RoomEventPayload.RoomCreated -> buildJsonObject {
            put("name", payload.name)
            put("config", encodeConfig(payload.config))
            put("visibility", payload.visibility.name)
            put("allowSpectators", payload.allowSpectators)
            put("creatorName", payload.creatorName)
            put("creatorAvatar", payload.creatorAvatar)
        }

        is RoomEventPayload.MemberJoined -> buildJsonObject {
            put("name", payload.name)
            put("avatar", payload.avatar)
        }

        is RoomEventPayload.MemberReady -> buildJsonObject {
            put("ready", payload.ready)
        }

        is RoomEventPayload.MemberKicked -> buildJsonObject {
            put("memberId", payload.memberId)
        }

        is RoomEventPayload.TurnSubmitted -> buildJsonObject {
            put("darts", encodeDarts(payload.darts))
            put("turnId", payload.clientTurnId)
        }

        is RoomEventPayload.SettingsChanged -> buildJsonObject {
            payload.visibility?.let { put("visibility", it.name) }
            payload.allowSpectators?.let { put("allowSpectators", it) }
        }

        RoomEventPayload.Empty -> JsonObject(emptyMap())
    }

    // ===== 配置（只走在线对战真正用到的字段）=====

    fun encodeConfig(config: MatchConfig): JsonObject = buildJsonObject {
        put("matchType", config.matchType.name)
        put("targetScore", config.targetScore)
        put("mode", config.mode.name)
        put("legsToWin", config.legsToWin)
        put("outMode", config.outMode.name)
        put("inMode", config.inMode.name)
        put("bullMode", config.bullMode.name)
        put("maxRounds", config.maxRounds)
    }

    fun decodeConfig(obj: JsonObject): MatchConfig = MatchConfig(
        matchType = obj.enumOrNull<MatchType>("matchType") ?: MatchType.X01,
        targetScore = obj.int("targetScore", 501),
        mode = obj.enumOrNull<MatchMode>("mode") ?: MatchMode.MULTI_LEG,
        legsToWin = obj.int("legsToWin", 3),
        outMode = obj.enumOrNull<OutMode>("outMode") ?: OutMode.DOUBLE_OUT,
        inMode = obj.enumOrNull<InMode>("inMode") ?: InMode.STRAIGHT_IN,
        bullMode = obj.enumOrNull<BullMode>("bullMode") ?: BullMode.STANDARD_25_50,
        maxRounds = obj.int("maxRounds", 0)
    )

    // ===== 飞镖 =====

    private fun encodeDarts(darts: List<Dart>): JsonArray = buildJsonArray {
        darts.forEach { dart ->
            add(
                buildJsonObject {
                    put("n", dart.number)
                    put("m", dart.multiplier)
                }
            )
        }
    }

    private fun decodeDarts(array: JsonArray): List<Dart> =
        array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val number = obj.int("n", -1)
            val multiplier = obj.int("m", 0)
            if (number < 0 || multiplier <= 0) null else Dart(number = number, multiplier = multiplier)
        }

    // ===== 取值辅助 =====

    private fun JsonObject.str(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun JsonObject.int(key: String, default: Int): Int =
        (get(key) as? JsonPrimitive)?.intOrNull ?: default

    private fun JsonObject.bool(key: String, default: Boolean): Boolean =
        (get(key) as? JsonPrimitive)?.booleanOrNull ?: default

    private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

    private fun JsonObject.arr(key: String): JsonArray? = get(key) as? JsonArray

    private inline fun <reified T : Enum<T>> JsonObject.enumOrNull(key: String): T? =
        str(key)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}
