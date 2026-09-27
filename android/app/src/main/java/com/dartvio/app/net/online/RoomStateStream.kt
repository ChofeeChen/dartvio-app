package com.dartvio.app.net.online

import com.dartvio.app.domain.room.RoomJoinSource
import com.dartvio.app.domain.room.RoomLiveState
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.HttpMethod
import io.ktor.http.takeFrom
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 订阅**大厅快照表**的插入（`realtime:public:dartvio_room_state`）。
 *
 * ## 为什么还要订阅，M1 的轮询不够吗
 *
 * 轮询 30s 的代价是「有人刚投完一手，大厅要等半分钟才变」——
 * 大厅的价值恰恰是「现在有人正在打，我随时能进去」，
 * 半分钟的延迟会把「正在打」变成「也许在打」，用户就不敢点了。
 * 订阅让这件事回到秒级，**轮询保留为兜底**（掉线时大厅仍然可用，只是慢）。
 *
 * ## 为什么不按房间号过滤
 *
 * [RoomEventStream] 是 `filter=room_id=eq.<id>`：那里只关心一个房间。
 * 大厅关心的是**一屏未知且会变的房间集合**（有人建房、有人结束），
 * 设了 filter 就必须随列表变化反复重新订阅，而重订阅期间的事件会漏。
 * 代价是每台机器都会收到所有房间的推送 —— 在小规模下这个量可以忽略；
 * 真到了需要按房间过滤的时候，说明大厅已经大到该换方案了。
 *
 * ## 与 [RoomEventStream] 的关系
 *
 * 两者是同一套 Phoenix 协议的两份实现，代码形状相近。**暂时不合并**：
 * 事件流那一份已经真机验证过连断线行为，抽公共基类的收益不抵回归风险。
 */
class RoomStateStream {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(WebSockets)
        // ★同 [RoomEventStream]：蜂窝下 TLS/Upgrade 可能既不成功也不失败，
        // 没有这条超时，一次挂死的握手会让状态永远停在「连接中」。
        install(HttpTimeout) { connectTimeoutMillis = HANDSHAKE_TIMEOUT_MS }
    }

    private val _status = MutableStateFlow(
        if (OnlineConfig.isConfigured) StreamStatus.CONNECTING else StreamStatus.NOT_CONFIGURED
    )
    val status: StateFlow<StreamStatus> = _status.asStateFlow()

    /** 快照流（冷流）：有人 collect 才连，没人 collect 就断。 */
    fun states(): Flow<RoomLiveState> = callbackFlow {
        if (!OnlineConfig.isConfigured) {
            _status.value = StreamStatus.NOT_CONFIGURED
            awaitClose { }
            return@callbackFlow
        }

        var backoffMs = 1_000L
        val pump = scope.launch {
            while (isActive) {
                try {
                    runSession { state -> trySend(state) }
                    backoffMs = 1_000L
                } catch (t: Throwable) {
                    _status.value = StreamStatus.RECONNECTING
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                }
            }
        }

        awaitClose { pump.cancel() }
    }

    private suspend fun runSession(emit: (RoomLiveState) -> Unit) {
        _status.value = StreamStatus.CONNECTING
        // 握手限时：超时就交给重连循环，避免永久挂在「连接中」（见 [RoomEventStream] 注释）。
        val session = withTimeout(HANDSHAKE_TIMEOUT_MS) {
            client.webSocketSession {
                method = HttpMethod.Get
                url { takeFrom(OnlineConfig.realtimeUrl) }
            }
        }

        val heartbeat = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                session.outgoing.send(Frame.Text(HEARTBEAT))
            }
        }

        try {
            session.send(Frame.Text(JOIN_MESSAGE))
            _status.value = StreamStatus.LIVE
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                parse(frame.readText())?.let(emit)
            }
        } finally {
            heartbeat.cancel()
            runCatching { session.close() }
        }
    }

    /**
     * 只认快照的 INSERT 帧；`phx_reply` / `system` / 心跳回执一律忽略。
     *
     * ★与 [RoomEventStream.parse] 同一个坑：Realtime 现行推送
     * `event="postgres_changes"` + `payload.data.record`（真机联调实测），
     * 老的 `event="insert"` + `payload.record` 已不再出现；
     * 按老格式解析会让每条推送都在这里被静默丢弃（大厅永远在等轮询）。
     */
    private fun parse(text: String): RoomLiveState? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val record = when (root["event"]?.jsonPrimitive?.content) {
            "postgres_changes" -> {
                val data = root["payload"]?.jsonObject?.get("data")?.jsonObject ?: return null
                if (data["type"]?.jsonPrimitive?.content != "INSERT") return null
                data["record"]
            }
            "insert" -> root["payload"]?.jsonObject?.get("record")
            else -> null
        } ?: return null
        return RoomStateCodec.decodeRow(record)?.takeIf { it.roomId.isNotBlank() }
    }

    private companion object {
        const val TABLE = "dartvio_room_state"
        const val HEARTBEAT_MS = 25_000L
        /** 握手上限，理由同 [RoomEventStream.HANDSHAKE_TIMEOUT_MS]。 */
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
        const val HEARTBEAT =
            """{"topic":"phoenix","event":"heartbeat","payload":{},"ref":null}"""

        /** 只订阅 INSERT：这张表是 append-only 的，不会有 UPDATE / DELETE。 */
        val JOIN_MESSAGE: String = buildJsonObject {
            put("topic", "realtime:public:$TABLE")
            put("event", "phx_join")
            put("payload", buildJsonObject {
                put("config", buildJsonObject {
                    put("postgres_changes", buildJsonArray {
                        add(buildJsonObject {
                            put("event", "INSERT")
                            put("schema", "public")
                            put("table", TABLE)
                        })
                    })
                })
            })
            put("ref", "1")
            put("join_ref", "1")
        }.toString()
    }
}

/** 进房来源在**统计口径**里的读法（缺省空串 = 老端，按房间号处理）。 */
fun RoomLiveState.joinSource(): RoomJoinSource = RoomJoinSource.fromKey(source)
