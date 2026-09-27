package com.dartvio.app.net.online

import com.dartvio.app.domain.room.RoomEvent
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

/** 实时连接的状态。**它只描述本机的连接**，不代表对手在不在 —— 那是事件流的事。 */
enum class StreamStatus {
    CONNECTING,
    LIVE,
    RECONNECTING,
    /**
     * WebSocket 没连上，但 REST 轮询是通的 —— **联机可用，只是延迟更高**（约 3 秒）。
     *
     * 2026-09-25：蜂窝网络下 Realtime 常被拦在半路（连接不报错也不成功），
     * 此时数据是轮询扛着的；若笼统地显示「连接中」或「重连中」，用户会以为联机不能用，
     * 而实际上一局照打不误。把这个状态单列出来，是为了**说清楚到底还能不能玩**。
     */
    POLLING_ONLY,
    /** 后端没配置（缺 `local.properties`）：在线对战整体不可用。 */
    NOT_CONFIGURED
}

/**
 * 订阅**一个房间**的事件流（Supabase Realtime 的 Postgres Changes）。
 *
 * ## 为什么是 Postgres Changes 而不是 Broadcast
 *
 * 实测：Supabase 现在不接受任意自定义 broadcast topic —— join `room:123456` 一律回
 * `unmatched topic`（自定义 channel 需要在后台逐个预注册，而房间号是动态生成的）。
 * 只有 `realtime:public:<表>` 这种 Postgres Changes 形态能直接 join。
 *
 * 于是把「往频道里广播」换成「往表里插一行」：插入走已验证可用的 PostgREST，
 * 到达走 WebSocket 推送。代价是每次变更都要落一行数据库；换来的是
 * **事件天然持久化**——掉线重连不必依赖任何一台手机还活着。
 *
 * ## 心跳
 *
 * Phoenix 协议要求客户端定期心跳，超时服务端会静默断开。
 * 这里每 25 秒发一次（服务端超时通常 30 秒），断线由重连循环兜住。
 */
class RoomEventStream(private val roomId: String) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(WebSockets)
        // ★2026-09-25：蜂窝网络下 Realtime 的 TLS/Upgrade 常常既不成功也不失败 ——
        // 连接半途挂在中间设备上，`webSocketSession` 就那么挂到天荒地老，
        // 于是状态永远停在 CONNECTING（界面表现：一直「连接中」，既不报错也不重连）。
        // 建连必须有上限，挂住也要落到重连循环里去。
        install(HttpTimeout) { connectTimeoutMillis = HANDSHAKE_TIMEOUT_MS }
    }

    private val _status = MutableStateFlow(
        if (OnlineConfig.isConfigured) StreamStatus.CONNECTING else StreamStatus.NOT_CONFIGURED
    )
    val status: StateFlow<StreamStatus> = _status.asStateFlow()

    /** 事件流（冷流）：有人 collect 才连，没人 collect 就断。 */
    fun events(): Flow<RoomEvent> = callbackFlow {
        if (!OnlineConfig.isConfigured) {
            _status.value = StreamStatus.NOT_CONFIGURED
            // 不 close()：保持一个空流，界面显示「未配置」而不是闪一下空白。
            awaitClose { }
            return@callbackFlow
        }

        // 重连退避：1s → 2s → 4s … 上限 30s。抖动为零：朋友间对战不会有「惊群」问题。
        var backoffMs = 1_000L
        val pump = scope.launch {
            while (isActive) {
                try {
                    runSession { event -> trySend(event) }
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

    private suspend fun runSession(emit: (RoomEvent) -> Unit) {
        _status.value = StreamStatus.CONNECTING
        // 连同握手（101 Switching）一起限时：超时即抛 TimeoutCancellation，
        // 由重连循环接手 —— 否则一次挂死的握手会把整个实时通道钉死在「连接中」。
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
            session.send(Frame.Text(joinMessage(roomId)))
            // join 之后立刻拉一次全量：重连期间落下的事件，订阅本身不会补发。
            _status.value = StreamStatus.LIVE
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val event = parse(frame.readText()) ?: continue
                emit(event)
            }
        } finally {
            heartbeat.cancel()
            runCatching { session.close() }
        }
    }

    // ===== 协议 =====

    private fun joinMessage(roomId: String): String = buildJsonObject {
        put("topic", "realtime:public:$TABLE")
        put("event", "phx_join")
        put("payload", buildJsonObject {
            put("config", buildJsonObject {
                put("postgres_changes", buildJsonArray {
                    add(buildJsonObject {
                        put("event", "INSERT")
                        put("schema", "public")
                        put("table", TABLE)
                        put("filter", "room_id=eq.$roomId")
                    })
                })
            })
        })
        put("ref", "1")
        put("join_ref", "1")
    }.toString()

    /**
     * 只认房间事件的 INSERT 帧；`phx_reply` / `system` / 心跳回执一律忽略。
     *
     * ★Supabase Realtime 的现行格式是 `event="postgres_changes"`，行在
     * `payload.data.record`、变更类型在 `payload.data.type`（真机联调时实测）。
     * 早期文档里的 `event="insert"` + `payload.record` 已不再推送 ——
     * 按**老格式**解析的后果是：订阅 join 成功、每条推送都到达、
     * 却在解析时**被全部静默丢弃**（表现：房主永远停在「等待玩家加入」）。
     * 这里同时兼容两种格式：新格式为主，老格式留着以防服务端回退。
     */
    private fun parse(text: String): RoomEvent? {
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
        // 服务端已按 filter 过滤，这里再比一次 room_id：换房间时旧连接的迟到消息不该混进来。
        return RoomEventCodec.decodeRow(record)?.takeIf { it.roomId == roomId }
    }

    private companion object {
        const val TABLE = "dartvio_room_events"
        const val HEARTBEAT_MS = 25_000L
        const val HEARTBEAT =
            """{"topic":"phoenix","event":"heartbeat","payload":{},"ref":null}"""

        /**
         * 握手（含 TLS + 101 Upgrade）的上限。
         *
         * 取 10 秒是两头都想照顾：蜂窝/弱网下建连慢一点是正常的，给足；

         * 但超过这个数基本可以判定为「被中间设备挂住」，继续等没有意义 ——
         * 交还给重连循环，由那里的 1s→30s 退避去重试。
         */
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
    }
}
