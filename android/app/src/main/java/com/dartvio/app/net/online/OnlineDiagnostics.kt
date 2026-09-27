package com.dartvio.app.net.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.dartvio.app.BuildConfig
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.room.RoomEvent
import com.dartvio.app.domain.room.RoomEventPayload
import com.dartvio.app.domain.room.RoomEventReplay
import com.dartvio.app.domain.room.RoomEventType
import com.dartvio.app.domain.room.RoomExpiry
import com.dartvio.app.domain.room.RoomLiveState
import com.dartvio.app.domain.room.RoomMatchRules
import com.dartvio.app.domain.room.RoomVisibility
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.HttpMethod
import io.ktor.http.takeFrom
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.UUID
import kotlin.system.measureTimeMillis

/**
 * 在线链路诊断（真机排查用，临时保留）。
 *
 * ## 为什么要有它
 *
 * 联机故障有两类，界面上长得一模一样（「点进去没反应 / 列表是空的」），修法却完全不同：
 *
 * 1. **配置问题** —— 没配后端、房间号撞了；
 * 2. **链路问题** —— DNS 解不出、TCP 不通、TLS 被中间设备掐掉（开发环境就是这种）、
 *    RLS 把写入挡在门外、Realtime 握手挂在半路。
 *
 * 只有把每一层单独量一遍，才能说清是哪一类。这也是为什么它**一层一层往下走**：
 * 直接跳到「建房」那一步，失败时你只能知道「建不了」，而不知道为什么。
 *
 * ## 为什么同时写日志
 *
 * 手机上没法看变量，只能看 logcat（`adb logcat -s DartVioDiag`）。
 * 屏幕上的那份给用户复制，日志里的那份给排查用。
 */
object OnlineDiagnostics {

    /** 日志 tag：排查时用 `adb logcat -s DartVioDiag:V` 只看这一路。 */
    const val TAG = "DartVioDiag"

    private const val TCP_TIMEOUT_MS = 5_000
    private const val REALTIME_WAIT_MS = 8_000L
    private const val REALTIME_JOIN_DELAY_MS = 1_500L
    /** 握手（TLS + 101 Upgrade）的上限：超过就判定为被中间设备挂住。 */
    private const val REALTIME_HANDSHAKE_MS = 8_000L
    /** join 之后等服务端第一帧（`phx_reply`）的时间。 */
    private const val REALTIME_REPLY_MS = 6_000L

    /** 一行结果。[ok] 为 null 表示中性信息（既不成功也不失败）。 */
    data class Line(
        val label: String,
        val detail: String,
        val ok: Boolean? = null
    ) {
        fun pretty(): String = when (ok) {
            null -> "· $label：$detail"
            true -> "√ $label：$detail"
            false -> "× $label：$detail"
        }
    }

    /** 跑一遍完整诊断；每一步都通过 [emit] 实时吐出（便于界面边跑边显示）。 */
    suspend fun run(context: Context, emit: suspend (Line) -> Unit) {
        val api = OnlineRoomApi()
        var failures = 0

        fun step(line: Line): Boolean {
            Log.i(TAG, line.pretty())
            return if (line.ok == false) {
                failures++
                true
            } else {
                true
            }
        }

        suspend fun report(line: Line) {
            step(line)
            emit(line)
        }

        // ===== 0. 环境 =====
        report(Line("设备", "${Build.MODEL} · Android ${Build.VERSION.RELEASE} · v${BuildConfig.VERSION_NAME}"))
        report(
            Line(
                "后端配置",
                if (OnlineConfig.isConfigured) OnlineConfig.restUrl else "未配置 SUPABASE_URL/ANON_KEY",
                OnlineConfig.isConfigured
            )
        )
        report(Line("网络", networkSummary(context)))
        if (!OnlineConfig.isConfigured) {
            report(Line("结论", "后端没配，后面的检查没有意义，到此为止", false))
            return
        }

        // ===== 1. DNS =====
        val host = runCatching { URI(OnlineConfig.restUrl).host }.getOrNull()
        var ips: List<String> = emptyList()
        val dnsMs = measureTimeMillis {
            ips = withContext(Dispatchers.IO) {
                runCatching { InetAddress.getAllByName(host).map { it.hostAddress } }.getOrDefault(emptyList())
            }
        }
        report(
            Line(
                "DNS",
                if (ips.isEmpty()) "解析失败（$host）" else "${ips.joinToString(" / ")} · ${dnsMs}ms",
                ips.isNotEmpty()
            )
        )
        if (ips.isEmpty()) {
            report(Line("结论", "域名解析不出来，后面的检查没有意义，到此为止", false))
            return
        }

        // ===== 2. TCP =====
        var tcpOk = false
        val tcpMs = measureTimeMillis {
            tcpOk = withContext(Dispatchers.IO) {
                runCatching {
                    Socket().use { it.connect(InetSocketAddress(ips.first(), 443), TCP_TIMEOUT_MS) }
                }.isSuccess
            }
        }
        report(
            Line(
                "TCP :443",
                if (tcpOk) "连通 · ${tcpMs}ms" else "连不上 / 被重置 · ${tcpMs}ms",
                tcpOk
            )
        )

        // ===== 3. TLS + REST 读 =====
        var pingOk = false
        val pingMs = measureTimeMillis { pingOk = api.ping() }
        report(
            Line(
                "HTTPS 读",
                if (pingOk) "REST 可读 · ${pingMs}ms" else "失败：${api.lastFailure} · ${pingMs}ms",
                pingOk
            )
        )
        if (!pingOk) {
            // 这一层的失败最需要说清：TCP 通但 TLS 挂 = 中间设备在掐加密握手，
            // 换个网络（蜂窝换 WiFi 或反之）往往就好了 —— 它不是 App 的缺陷。
            report(
                Line(
                    "结论",
                    if (tcpOk) "TCP 通、TLS 握手失败 ⇒ 典型的网络中间设备阻断，请换网络重试（不是 App 的问题）"
                    else "网络层不通，请检查手机联网状态",
                    false
                )
            )
            return
        }

        // ===== 4. 建房（索引行，含本次新增的两列）=====
        val stamp = System.currentTimeMillis()
        val hostId = "diag_host_$stamp"
        val guestId = "diag_guest_$stamp"
        var roomId = "d" + UUID.randomUUID().toString().replace("-", "").take(5)
        var createResult: WriteResult = WriteResult.Failed("未尝试")
        repeat(3) {
            createResult = api.insertRoom(
                roomId = roomId,
                name = "诊断测试房",
                creatorId = hostId,
                config = MatchConfig(),
                visibility = RoomVisibility.PRIVATE,
                allowSpectators = true,
                hostName = "诊断房主",
                hostAvatar = "HUMAN_1",
                expiresAtMs = RoomExpiry.deadline()
            )
            if (createResult is WriteResult.Ok) return@repeat
            roomId = "d" + UUID.randomUUID().toString().replace("-", "").take(5)
        }
        report(
            Line("建房（索引行）", "$roomId · ${describe(createResult)}", createResult is WriteResult.Ok)
        )

        // ===== 5. 读回索引行：验证加列是否真的生效 =====
        val summary = api.fetchRoom(roomId)
        report(
            Line(
                "读回索引行",
                if (summary == null) "读不到：${api.lastFailure}" else "host_avatar=${summary.hostAvatar} · expires_at=${summary.expiresAt}",
                summary != null
            )
        )
        // 这两列是 S2 新加的：写进去了但读回来是空，通常意味着加列没执行或被 RLS 挡了 SELECT。
        report(
            Line(
                "新增列校验",
                when {
                    summary == null -> "跳过（索引行读不到）"
                    summary.hostAvatar.isBlank() -> "host_avatar 为空 ⇒ 加列未生效或该列不可读"
                    summary.expiresAt == null -> "expires_at 为空 ⇒ 加列未生效或该列不可读"
                    else -> "host_avatar / expires_at 均已落库"
                },
                if (summary == null) null else summary.hostAvatar.isNotBlank() && summary.expiresAt != null
            )
        )

        // 逐列探测的结果：哪几列库里有、哪几列还没有。
        // 加列是分次执行的，缺列必须**点名**，否则「头像不显示 / 等待房不过期」
        // 这类症状会被误读成界面或逻辑的问题。
        val meta = api.supportedMetaColumns()
        val metaAll = listOf("join_policy", "host_name", "host_avatar", "expires_at")
        report(
            Line(
                "库里的 meta 列",
                "已有：${meta.joinToString().ifBlank { "无" }} · 缺失：${metaAll.filter { it !in meta }.joinToString().ifBlank { "无" }}",
                null
            )
        )

        // ===== 6. 事件写入 + 重放（真正的联机引擎）=====
        var seq = 0
        suspend fun append(type: RoomEventType, actor: String, payload: RoomEventPayload) {
            seq++
            var result: WriteResult = WriteResult.Failed("未尝试")
            // 与生产一致：偶发超时重试一次，别让一次抖动毁掉整轮诊断。
            repeat(2) {
                result = api.insertEvent(RoomEvent(roomId, seq, actor, type, payload))
                if (result is WriteResult.Ok) return
            }
            report(Line("事件 ${type.key}", describe(result), result is WriteResult.Ok))
        }

        append(
            RoomEventType.ROOM_CREATED,
            hostId,
            RoomEventPayload.RoomCreated(
                name = "诊断测试房",
                config = MatchConfig(),
                visibility = RoomVisibility.PRIVATE,
                allowSpectators = true,
                creatorName = "诊断房主",
                creatorAvatar = "HUMAN_1"
            )
        )
        append(RoomEventType.MEMBER_JOINED, guestId, RoomEventPayload.MemberJoined("诊断客人", "HUMAN_2"))
        append(RoomEventType.MATCH_STARTED, hostId, RoomEventPayload.Empty)

        val events = api.fetchEvents(roomId)
        report(
            Line(
                "读回事件",
                if (events == null) "失败：${api.lastFailure}" else "${events.size} 条",
                events?.size == 3
            )
        )
        if (events != null && events.size == 3) {
            val state = RoomEventReplay.replay(events)
            report(Line("重放：成员数", "${state.room?.members?.size}", state.room?.members?.size == 2))
            report(Line("重放：对局已建立", "${state.match != null}", state.match != null))
        }

        // ===== 7. 投一手 + 撤销 =====
        val firstPlayer = events?.let { RoomEventReplay.replay(it).match }?.currentPlayerId
        if (firstPlayer != null) {
            val triple = listOf(
                Dart(number = 20, multiplier = 3),
                Dart(number = 20, multiplier = 3),
                Dart(number = 20, multiplier = 3)
            )
            append(RoomEventType.TURN_SUBMITTED, firstPlayer, RoomEventPayload.TurnSubmitted(triple, "diag-1"))
            val afterTurn = api.fetchEvents(roomId)?.let { RoomEventReplay.replay(it).match }
            val score = afterTurn?.let { RoomMatchRules.snapshot(it).players.firstOrNull { p -> p.id == firstPlayer }?.score }
            report(Line("投镖 180 后比分", "$score", score == 501 - 180))

            append(RoomEventType.TURN_UNDONE, firstPlayer, RoomEventPayload.Empty)
            val afterUndo = api.fetchEvents(roomId)?.let { RoomEventReplay.replay(it).match }
            val undone = afterUndo?.let { RoomMatchRules.snapshot(it).players.firstOrNull { p -> p.id == firstPlayer }?.score }
            report(Line("撤销后比分", "$undone", undone == 501))
        }

        // ===== 8. 大厅快照表 =====
        val live = RoomLiveState(
            roomId = roomId,
            seq = seq,
            hostId = hostId,
            hostName = "诊断房主",
            guestId = guestId,
            guestName = "诊断客人",
            legsHost = 0,
            legsGuest = 0,
            scoreHost = 501,
            scoreGuest = 501,
            round = 1,
            legsToWin = 1,
            playing = true,
            source = "diag"
        )
        val stateResult = api.insertRoomState(live)
        report(Line("大厅快照写入", describe(stateResult), stateResult is WriteResult.Ok))
        val readBack = api.fetchRoomStates(listOf(roomId))
        report(
            Line(
                "大厅快照读回",
                if (readBack == null) "失败：${api.lastFailure}" else "${readBack[roomId]?.scoreHost} / ${readBack[roomId]?.scoreGuest}",
                readBack?.containsKey(roomId) == true
            )
        )

        // ===== 9. 满员停表（A9 的 PATCH）=====
        api.updateExpiresAt(roomId, null)
        val afterPatch = api.fetchRoom(roomId)
        report(
            Line(
                "满员停表（PATCH）",
                when {
                    afterPatch == null -> "读不回索引行：${api.lastFailure}"
                    afterPatch.expiresAt == null -> "expires_at 已置空 ⇒ 停表生效"
                    else -> "expires_at 仍为 ${afterPatch.expiresAt} ⇒ PATCH 没生效（通常是 anon 缺 UPDATE 权限）"
                },
                afterPatch?.expiresAt == null
            )
        )

        // ===== 10. Realtime（最好有，不是必须有）=====
        // 先把握手这一层单列出来：「没收到推送」有三种完全不同的原因
        // （WSS 连不上 / join 被拒 / 连上了但没广播），修法毫不相干，
        // 而界面上都只看到一句「没收到」——所以要看服务端回的原话。
        report(realtimeHandshake(roomId))
        val stream = RoomEventStream(roomId)
        var pushed: RoomEvent? = null
        val rtMs = measureTimeMillis {
            pushed = withTimeoutOrNull(REALTIME_WAIT_MS) {
                val collecting = async { stream.events().firstOrNull() }
                // 订阅是冷流：等它 join 完再写，否则这一条事件可能落在订阅生效之前 ——
                // 那样会得到「Realtime 坏了」的假结论。
                delay(REALTIME_JOIN_DELAY_MS)
                seq++
                api.insertEvent(
                    RoomEvent(roomId, seq, hostId, RoomEventType.TURN_SUBMITTED, RoomEventPayload.TurnSubmitted(emptyList(), "diag-rt"))
                )
                collecting.await()
            }
        }
        report(
            Line(
                "Realtime 推送",
                if (pushed != null) "收到推送（seq=${pushed?.seq}）· ${rtMs}ms" else "${REALTIME_WAIT_MS}ms 内没收到（REST 轮询仍可用）",
                pushed != null
            )
        )

        // ===== 结论 =====
        report(
            Line(
                "结论",
                if (failures == 0) "全部通过" else "$failures 项未通过；最后一次网络错误：${api.lastFailure ?: "无"}",
                failures == 0
            )
        )
    }

    /**
     * Realtime 握手：连 WebSocket → join 频道 → 把服务端回的第一帧**原话**说出来。
     *
     * 这一步的作用是把「没收到推送」拆开：
     * 连不上（网络/中间设备拦 WSS）、join 被拒（`unauthorized` / `unmatched topic` / RLS
     * 不放行）、连上了但事件没广播 —— 三者界面表现一致，修法完全不同。
     */
    private suspend fun realtimeHandshake(roomId: String): Line = withContext(Dispatchers.IO) {
        val client = HttpClient(CIO) {
            install(WebSockets)
            install(HttpTimeout) { connectTimeoutMillis = REALTIME_HANDSHAKE_MS }
        }
        try {
            val session = withTimeoutOrNull(REALTIME_HANDSHAKE_MS) {
                client.webSocketSession {
                    method = HttpMethod.Get
                    url { takeFrom(OnlineConfig.realtimeUrl) }
                }
            }
                ?: return@withContext Line(
                    "Realtime 握手",
                    "WebSocket 连不上（${REALTIME_HANDSHAKE_MS}ms 超时）",
                    false
                )

            session.send(Frame.Text(realtimeJoinMessage(roomId)))
            val first = withTimeoutOrNull(REALTIME_REPLY_MS) {
                (session.incoming.receive() as? Frame.Text)?.readText()
            }
            if (first == null) {
                return@withContext Line("Realtime 握手", "join 后无回应（${REALTIME_REPLY_MS}ms）", false)
            }
            val root = runCatching {
                Json { ignoreUnknownKeys = true }.parseToJsonElement(first).jsonObject
            }.getOrNull()
            val payload = root?.get("payload")?.jsonObject
            val status = payload?.get("status")?.jsonPrimitive?.content
                ?: root?.get("event")?.jsonPrimitive?.content
            val reason = payload?.get("response")?.jsonObject
                ?.get("reason")?.jsonPrimitive?.content
            Line(
                "Realtime 握手",
                "status=$status${if (reason != null) " · reason=$reason" else ""}",
                status == "ok"
            )
        } catch (t: Throwable) {
            Line("Realtime 握手", "连接失败：${t.message}", false)
        } finally {
            runCatching { client.close() }
        }
    }

    /** 与 [RoomEventStream] 的订阅消息保持一致，避免「诊断连上了、App 没连上」这种假象。 */
    private fun realtimeJoinMessage(roomId: String): String = buildJsonObject {
        put("topic", "realtime:public:dartvio_room_events")
        put("event", "phx_join")
        put("payload", buildJsonObject {
            put("config", buildJsonObject {
                put("postgres_changes", buildJsonArray {
                    add(buildJsonObject {
                        put("event", "INSERT")
                        put("schema", "public")
                        put("table", "dartvio_room_events")
                        put("filter", "room_id=eq.$roomId")
                    })
                })
            })
        })
        put("ref", "1")
        put("join_ref", "1")
    }.toString()

    private fun describe(result: WriteResult): String = when (result) {
        is WriteResult.Ok -> "成功"
        is WriteResult.Conflict -> "冲突（主键已存在）"
        is WriteResult.Failed -> "失败：${result.message}"
    }

    /**
     * 当前网络的一句话描述。
     *
     * 「WiFi 还是蜂窝」在联机排查里是第一个要问的问题：蜂窝网络下 Realtime 的握手
     * 经常被中间设备拦在半路，而 REST 完全正常 —— 不知道这条，就会把网络问题
     * 当成 App 问题去查。
     */
    private fun networkSummary(context: Context): String {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return "未知"
        val network = manager.activeNetwork ?: return "无活动网络"
        val caps = manager.getNetworkCapabilities(network) ?: return "未知"
        // 并列列出所有 transport，而不是挑一个：手机开热点时会同时具备 WiFi（软 AP）
        // 与蜂窝两种，而真正出网的是蜂窝 —— 只报「WiFi」会把排查引向错误的方向。
        val transports = mutableListOf<String>()
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports += "WiFi"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports += "蜂窝"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports += "以太网"
        if (transports.isEmpty()) transports += "未知"
        val metered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return transports.joinToString(" + ") + if (metered) "" else "（按流量计费）"
    }
}
