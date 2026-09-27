package com.dartvio.app.net.online

import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.room.RoomEvent
import com.dartvio.app.domain.room.RoomJoinPolicy
import com.dartvio.app.domain.room.RoomLiveState
import com.dartvio.app.domain.room.RoomStatus
import com.dartvio.app.domain.room.RoomVisibility
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.jvm.Volatile
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** 一次写入的结果。 */
sealed interface WriteResult {

    /** 写进去了。 */
    data object Ok : WriteResult

    /**
     * 撞上了 `unique(room_id, seq)`：同一回合位置已经被别人占了。
     *
     * 单独区分它不是为了报错，而是为了**重试**：调用方据此拉一次全量事件、
     * 用新的 seq 重投（见 [OnlineRoomRepository] 的提交逻辑）。
     * 若把它并进 [Failed]，提交就会在并发时静默丢失 —— 表现出来是「点了没反应」。
     */
    data object Conflict : WriteResult

    data class Failed(val message: String) : WriteResult
}

/** 房间索引表里的一行（大厅列表用）。 */
data class RoomSummary(
    val id: String,
    val name: String,
    val creatorId: String,
    val config: MatchConfig,
    val visibility: RoomVisibility,
    val allowSpectators: Boolean,
    val createdAt: Long,
    /** 加入策略。旧库没有这一列时按 [RoomJoinPolicy.OPEN] 处理。 */
    val joinPolicy: RoomJoinPolicy = RoomJoinPolicy.OPEN,
    /** 房主昵称（大厅展示用；身份看 `creator_id`）。 */
    val hostName: String = "",
    /**
     * 房主头像（[com.dartvio.app.domain.model.HumanAvatar] 的 key）。
     * 旧库没有这一列时为空串 —— 大厅按「没有头像」渲染占位，不让缺列变成列表拉不到。
     */
    val hostAvatar: String = "",
    /**
     * 等待房的到期时刻（epoch 毫秒，`bigint`）；null = 不计时（老房间 / 已满员 / 已开局）。
     *
     * 到期由两端各自处置：房主端自毁（[com.dartvio.app.data.room.OnlineRoomRepository]）、
     * 别端的卡片直接不显示（[com.dartvio.app.domain.room.RoomExpiry]）。
     */
    val expiresAt: Long? = null,
    /**
     * 房间状态（`text`：WAITING / PLAYING / ENDED）；老库没有这一列时按 WAITING 处理。
     *
     * 这一列是**大厅唯一能区分「正在等」与「已经打完」的依据**：索引行本身不带事件，
     * 而让每台手机为了画一张卡去拉一遍事件流，等于把列表页的成本乘以房间数。
     */
    val status: RoomStatus = RoomStatus.WAITING,
    /** 预约开始时刻（epoch 毫秒）；null = 立即开始 / 老库没有这一列。 */
    val startsAt: Long? = null,
    /** 房主 PPR（`numeric`）；null = 没有这一列或房主还没打过正式赛。 */
    val hostPpr: Double? = null,
    /** 房主信用分（`int`）；null = 没有这一列。 */
    val hostCredit: Int? = null
)

/**
 * 在线房间的 REST 读写（PostgREST）。
 *
 * 与 `UsageReporter` 同一个取舍：**不引 supabase-kt**。它 3.0 起强依赖 Ktor 3，
 * 而本项目停在 Ktor 2.3.12（Ktor 3 引入 java.time，minSdk 24/25 会在**运行期** NoClassDefFoundError）。
 * 这里直接用现成的 Ktor client 打 REST，零新依赖。
 *
 * 每个方法都自己吞掉异常并转成 [WriteResult.Failed] / null：网络层没有权利让界面崩，
 * 而「房间列表拉不到」与「房间列表是空的」对界面是两种可以区分、也应该区分的状态。
 */
class OnlineRoomApi {

    /**
     * 最近一次网络失败的原因（给人看的完整一句）。
     *
     * 「联不上」必须能说出**为什么**：状态码只是结论，真正的原因（哪条约束、
     * 哪条策略、超时还是没网）在响应体或异常里。不记下来的话，用户能得到的
     * 只有「点了没反应」—— 而这也是最难排查的一类反馈。
     */
    @Volatile
    var lastFailure: String? = null
        private set

    /**
     * 服务端**实际存在**的房间元数据列（逐列探测的结果，探测一次后缓存）。
     *
     * 2026-09-25 真机诊断抓到的坑：这里原本是一个布尔量「meta 支不支持」。
     * 四列里只要**任一列**不存在（当时是 `host_name`），PostgREST 就回 PGRST204，
     * 于是**整组**降级 —— 连已经加好的 `host_avatar` / `expires_at` 也一起不写。
     * 表现是「建房显示成功，但大厅没有房主头像、等待房永不过期」，而且没人知道为什么。
     *
     * 列是**逐条**加上去的，就只能**逐条**判断：缺哪一列只丢哪一列，不牵连已经可用的列。
     */
    private val metaColumns = LinkedHashSet<String>()

    @Volatile
    private var metaColumnsProbed = false

    /**
     * 房间元数据列（大厅卡片的装饰字段 + 状态/预约时间）。加列逐条执行，判断也逐条执行。
     *
     * `status` / `starts_at` / `host_ppr` / `host_credit` 是 2026-09-26 这一轮加的：
     * 前两个让大厅能区分「live 的房」与「已经结束的记录」，后两个让陌生人在点进来
     * 之前对房主有个预期。缺列时各自降级为「不显示 / 按 WAITING 处理」，
     * 不让一次加列把整张列表拉不出来。
     */
    private val metaColumnCandidates = listOf(
        "join_policy", "host_name", "host_avatar", "expires_at",
        "status", "starts_at", "host_ppr", "host_credit"
    )

    /** 服务端是否有 `dartvio_room_state` 表（大厅实时比分）。缺表时大厅只是没有比分。 */
    @Volatile
    var roomStateSupported: Boolean = true
        private set

    private val json = Json { ignoreUnknownKeys = true }

    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = TIMEOUT_MS
            connectTimeoutMillis = TIMEOUT_MS
            socketTimeoutMillis = TIMEOUT_MS
        }
        expectSuccess = false
    }

    // ===== 探活 =====

    /**
     * 云端到底通不通：**一次最轻的请求回答最要紧的问题**。
     *
     * 2026-09-25：蜂窝网络下最难的一类故障是「既不成功也不失败」——连 WebSocket 挂住、
     * 界面显示「连接中」永远不变，而用户无法判断是 App 的问题还是网络的问题。
     * 这条探针的作用就是让「能不能用」变成一个**有明确答案**的问题：
     * 只取一行的一个字段（`select=id&limit=1`），几百字节，可以随时反复打，
     * 而且它走的是 REST —— 恰好是真正扛起同步的那条路（见 [com.dartvio.app.data.room.OnlineRoomRepository] 的轮询）。
     */
    suspend fun ping(): Boolean =
        get("/rest/v1/dartvio_rooms?select=id&limit=1") != null

    // ===== 事件流 =====

    /** 追加一条事件；[WriteResult.Conflict] 表示这个 seq 已被占用。 */
    suspend fun insertEvent(event: RoomEvent): WriteResult {
        val body = RoomEventCodec.encodeRow(event).toString()
        return post("/rest/v1/dartvio_room_events", body, prefer = "return=minimal")
    }

    /**
     * 拉取某个房间的事件（按 seq 升序）。null = 拉不到（网络/鉴权问题）。
     *
     * @param afterSeq 只取 `seq` 大于它的事件。**事件是 append-only 的**，因此「已有最大 seq
     *                 之后的增量」与「全量」在结算上完全等价，却让轮询只需下载新增的那一两条。
     *
     * 自带重试：实测这条查询**偶发会卡到几分钟**（同一条 URL 平时 1 秒，偶发 320 秒）。
     * 它是「进入房间 / 掉线重连」的唯一入口 —— 卡住一次的表现是整个房间打不开，
     * 而重试一次几乎必中。与其让用户等一个 15 秒的失败，不如早超时、早重试。
     */
    suspend fun fetchEvents(roomId: String, afterSeq: Int = 0): List<RoomEvent>? {
        repeat(FETCH_ATTEMPTS) { attempt ->
            fetchEventsOnce(roomId, afterSeq)?.let { return it }
            if (attempt < FETCH_ATTEMPTS - 1) delay(FETCH_RETRY_DELAY_MS * (attempt + 1))
        }
        return null
    }

    private suspend fun fetchEventsOnce(roomId: String, afterSeq: Int): List<RoomEvent>? {
        val since = if (afterSeq > 0) "&seq=gt.$afterSeq" else ""
        val text = get(
            "/rest/v1/dartvio_room_events?select=*&room_id=eq.$roomId$since&order=seq.asc"
        ) ?: return null
        return runCatching {
            val array = json.parseToJsonElement(text) as? JsonArray ?: return null
            array.mapNotNull { RoomEventCodec.decodeRow(it) }
        }.getOrNull()
    }

    // ===== 房间索引 =====

    /**
     * 库里到底有哪些元数据列。**只探测一次**，之后一直用缓存。
     *
     * 云端不通时返回空集但**不缓存**：一次断网如果被记成「这些列都不存在」，
     * 之后即便恢复联网也永远不再写它们 —— 故障会以「头像不显示 / 等待房不过期」
     * 的形式一直留在用户眼睛里，而没人会想到去查一次网络。
     */
    suspend fun supportedMetaColumns(): Set<String> {
        if (metaColumnsProbed) return metaColumns.toSet()
        if (!ping()) return emptySet()
        val found = metaColumnCandidates.filter { probeColumn(it) }
        metaColumns.clear()
        metaColumns.addAll(found)
        metaColumnsProbed = true
        return metaColumns.toSet()
    }

    /**
     * 只读探测：只取一行的一个字段。缺列时 PostgREST 回 400，[get] 拿不到正文。
     *
     * 探测出的 400 是**预期答案**（「这列不存在」），不是故障，因此不留在 [lastFailure] 里 ——
     * 否则界面上「最后一次网络错误」会永远指向一条探测请求，把真正的故障挤出去。
     */
    private suspend fun probeColumn(column: String): Boolean {
        val before = lastFailure
        val exists = get("/rest/v1/dartvio_rooms?select=$column&limit=1") != null
        if (!exists) lastFailure = before
        return exists
    }

    /**
     * 建房间：房间号是主键，撞号即冲突。
     *
     * 新列（[joinPolicy] / [hostName] / [hostAvatar] / [expiresAtMs]）按
     * [supportedMetaColumns] 逐条决定写不写 —— 缺列只丢那一列，不牵连其它列。
     *
     * @param expiresAtMs 到期时刻（epoch 毫秒）。null 表示这一列不参与写入（老库降级时也走这条）。
     */
    suspend fun insertRoom(
        roomId: String,
        name: String,
        creatorId: String,
        config: MatchConfig,
        visibility: RoomVisibility,
        allowSpectators: Boolean,
        joinPolicy: RoomJoinPolicy = RoomJoinPolicy.OPEN,
        hostName: String = "",
        hostAvatar: String = "",
        expiresAtMs: Long? = null,
        status: RoomStatus = RoomStatus.WAITING,
        startsAtMs: Long? = null
    ): WriteResult {
        val base = buildJsonObject {
            put("id", roomId)
            put("name", name)
            put("creator_id", creatorId)
            put("config", RoomEventCodec.encodeConfig(config))
            put("visibility", visibility.name)
            put("allow_spectators", allowSpectators)
        }
        val meta = supportedMetaColumns()
        val withMeta = buildJsonObject {
            base.forEach { (key, value) -> put(key, value) }
            if ("join_policy" in meta) put("join_policy", joinPolicy.key)
            if ("host_name" in meta) put("host_name", hostName)
            if ("host_avatar" in meta) put("host_avatar", hostAvatar)
            // 到期时刻要么写具体值（等待房），要么显式写 null（不计时）——
            // 省略这一列在老库上是必须的，在新库上则是「永不失效」，两者后果完全不同，
            // 因此只在调用方没有给出到期时刻时才省略。
            if ("expires_at" in meta && expiresAtMs != null) put("expires_at", expiresAtMs)
            if ("status" in meta) put("status", status.name)
            if ("starts_at" in meta && startsAtMs != null) put("starts_at", startsAtMs)
        }
        val result = post("/rest/v1/dartvio_rooms", withMeta.toString(), prefer = "return=minimal")
        if (result is WriteResult.Failed && looksLikeMissingColumn(result.message)) {
            // 探测与实际写入不一致（列刚被删、RLS 变了）：从报错里点名剔除那几列，
            // 只重发一次剩下的 —— 不把整个 meta 组一次性判死。
            val missing = metaColumnCandidates.filter { result.message.contains(it) }
            missing.forEach { metaColumns.remove(it) }
            val retried = buildJsonObject {
                base.forEach { (key, value) -> put(key, value) }
                if ("join_policy" !in missing && "join_policy" in meta) put("join_policy", joinPolicy.key)
                if ("host_name" !in missing && "host_name" in meta) put("host_name", hostName)
                if ("host_avatar" !in missing && "host_avatar" in meta) put("host_avatar", hostAvatar)
                if ("expires_at" !in missing && "expires_at" in meta && expiresAtMs != null) {
                    put("expires_at", expiresAtMs)
                }
                if ("status" !in missing && "status" in meta) put("status", status.name)
                if ("starts_at" !in missing && "starts_at" in meta && startsAtMs != null) {
                    put("starts_at", startsAtMs)
                }
            }
            return post("/rest/v1/dartvio_rooms", retried.toString(), prefer = "return=minimal")
        }
        return result
    }

    /**
     * 改房间索引行的**状态**（WAITING / PLAYING / ENDED）。
     *
     * 为什么必须落这一列而不是让每台手机自己去拉事件推：大厅列表一次要画几十张卡，
     * 而「这间房还活着吗」是**点进去之前**就要回答的问题 ——
     * 靠事件推的话，列表页的成本等于房间数 × 一次事件拉取。
     *
     * 失败**静默**：状态写不进去的后果是卡片少一个标记，
     * 而建房 / 加入 / 投镖这些主路径一次都不能因为装饰性写入而失败。
     */
    suspend fun updateRoomStatus(roomId: String, status: RoomStatus) {
        patchMeta(roomId) { if ("status" in it) put("status", status.name) }
    }

    /**
     * 把房主的 PPR 与信用分写进房间索引行（建房后补写一次）。
     *
     * 走 PATCH 而不是放在 [insertRoom] 里：PPR 要从本机对局库里现算（一次 Room 查询），
     * 而建房这一下必须**立刻**返回一个房间对象让界面导航进去 ——
     * 为了两个装饰数字去卡住主路径，换来的只是「建房慢半拍」。
     */
    suspend fun updateHostStats(roomId: String, ppr: Double?, credit: Int?) {
        if (ppr == null && credit == null) return
        patchMeta(roomId) { supported ->
            if (ppr != null && "host_ppr" in supported) put("host_ppr", ppr)
            if (credit != null && "host_credit" in supported) put("host_credit", credit)
        }
    }

    /**
     * 统一的「按列可用性 PATCH 索引行」。
     *
     * [block] 拿到的是**当前可用的列集合**，因此调用方只描述「想写什么」，
     * 不用各自重复一遍「这一列在不在」。缺列时直接不写（不报错、不重试）。
     */
    private suspend fun patchMeta(
        roomId: String,
        block: JsonObjectBuilder.(Set<String>) -> Unit
    ) {
        // 列探测是一次 suspend 调用，不能放进 buildJsonObject 的非挂起 lambda 里。
        val supported = supportedMetaColumns()
        val body = buildJsonObject {
            block(supported)
        }
        // 一列都没写进去就不发请求：空 body 的 PATCH 在 PostgREST 上是无意义的往返。
        if (body.isEmpty()) return
        val result = patch(
            "/rest/v1/dartvio_rooms?id=eq.$roomId",
            body.toString(),
            prefer = "return=minimal"
        )
        if (result is WriteResult.Failed && looksLikeMissingColumn(result.message)) {
            metaColumnCandidates.filter { result.message.contains(it) }
                .forEach { metaColumns.remove(it) }
        }
    }

    /**
     * 改房间索引行的**到期时刻**（满员时置 null，A9）。
     *
     * 为什么走 PATCH 而不是再插一行：索引行是房间的**当前状态**（主键是房间号），
     * 而事件表才是历史。把「已经满员」写成一条新行，等于让大厅对着同一个房间出现两行。
     *
     * 失败**静默**：这只是让别端的倒计时停下来，写不进去的后果是「对面的卡片多走几秒」，
     * 而建房 / 加入 / 投镖这些主路径一次都不能因为这种装饰性写入而失败。
     */
    suspend fun updateExpiresAt(roomId: String, expiresAtMs: Long?) {
        if ("expires_at" !in supportedMetaColumns()) return
        val body = buildJsonObject {
            if (expiresAtMs == null) put("expires_at", JsonNull) else put("expires_at", expiresAtMs)
        }.toString()
        val result = patch(
            "/rest/v1/dartvio_rooms?id=eq.$roomId",
            body,
            prefer = "return=minimal"
        )
        if (result is WriteResult.Failed && looksLikeMissingColumn(result.message)) {
            metaColumns.remove("expires_at")
        }
    }

    /**
     * 大厅列表。
     *
     * 不再按 `visibility` 过滤：本期**所有房间都进大厅**（含「仅限好友」，它只是不能从
     * 列表直接加入）。低供给阶段最贵的一次流失是「打开大厅是空的」。
     */
    suspend fun fetchRooms(limit: Int = 30): List<RoomSummary>? {
        val text = get(
            "/rest/v1/dartvio_rooms?select=*&order=created_at.desc&limit=$limit"
        ) ?: return null
        return runCatching {
            val array = json.parseToJsonElement(text) as? JsonArray ?: return null
            array.mapNotNull { decodeSummary(it.jsonObject) }
        }.getOrNull()
    }

    suspend fun fetchRoom(roomId: String): RoomSummary? {
        val text = get("/rest/v1/dartvio_rooms?select=*&id=eq.$roomId&limit=1") ?: return null
        return runCatching {
            val array = json.parseToJsonElement(text) as? JsonArray ?: return null
            array.firstOrNull()?.jsonObject?.let(::decodeSummary)
        }.getOrNull()
    }

    // ===== 大厅实时快照（追加式） =====

    /**
     * 插入一条大厅快照。
     *
     * 失败**不阻断对局**：大厅比分是「锦上添花」，而建房 / 打镖是主路径。写不进去时
     * 只记 [lastFailure]，大厅退化为「有房间但没比分」——这比「点了提交没反应」好得多。
     */
    suspend fun insertRoomState(state: RoomLiveState): WriteResult {
        if (!roomStateSupported) return WriteResult.Failed("大厅状态表不可用（已降级）")
        val result = post(
            "/rest/v1/dartvio_room_state",
            RoomStateCodec.encodeRow(state).toString(),
            prefer = "return=minimal"
        )
        if (result is WriteResult.Failed && looksLikeMissingTable(result.message)) {
            roomStateSupported = false
        }
        return result
    }

    /**
     * 取一批房间的最新快照。null = 拉不到（调用方按「没有比分」处理）。
     *
     * 按房间号过滤而不是「取最近 N 条」：后者在房间一多时会把某些房间的快照挤出结果集，
     * 表现是「列表里一部分房间永远不显示比分」——看起来像随机 bug。
     */
    suspend fun fetchRoomStates(roomIds: List<String>): Map<String, RoomLiveState>? {
        if (!roomStateSupported || roomIds.isEmpty()) return null
        val filter = roomIds.joinToString(",")
        val text = get(
            "/rest/v1/dartvio_room_state?select=*&room_id=in.($filter)&order=seq.desc&limit=${roomIds.size * STATE_ROWS_PER_ROOM}"
        ) ?: return null
        return runCatching {
            val array = json.parseToJsonElement(text) as? JsonArray ?: return null
            val latest = LinkedHashMap<String, RoomLiveState>()
            array.mapNotNull { RoomStateCodec.decodeRow(it) }.forEach { state ->
                // 表里是这一局的全部历史快照，大厅只关心 seq 最大的那一条。
                val current = latest[state.roomId]
                if (current == null || state.seq > current.seq) latest[state.roomId] = state
            }
            latest
        }.getOrNull()
    }

    // ===== 内部 =====

    /**
     * 判断一次写入失败是不是「服务端还没有这些新列」。
     *
     * PostgREST 的 400 里带着列名（或 `PGRST204`），只看状态码永远分不出
     * 「缺列」和「数据非法」——而两者的处理完全相反：前者要降级重试，后者要报错。
     */
    private fun looksLikeMissingColumn(message: String): Boolean {
        if (!message.contains("400")) return false
        return message.contains("join_policy") ||
            message.contains("host_name") ||
            message.contains("host_avatar") ||
            message.contains("expires_at") ||
            message.contains("status") ||
            message.contains("starts_at") ||
            message.contains("host_ppr") ||
            message.contains("host_credit") ||
            message.contains("PGRST204") ||
            message.contains("does not exist", ignoreCase = true)
    }

    /** 同上，针对「整张表不存在」（`PGRST205`）。 */
    private fun looksLikeMissingTable(message: String): Boolean =
        message.contains("dartvio_room_state") ||
            message.contains("PGRST205") ||
            message.contains("does not exist", ignoreCase = true)

    private suspend fun get(path: String): String? = runCatching {
        val response = http.get("${OnlineConfig.restUrl}$path") {
            OnlineConfig.authHeaders().forEach { (key, value) -> header(key, value) }
        }
        if (response.status != HttpStatusCode.OK) {
            lastFailure = "GET $path -> HTTP ${response.status.value}"
            null
        } else {
            response.bodyAsText()
        }
    }.onFailure { lastFailure = "GET $path -> ${it::class.simpleName}: ${it.message}" }.getOrNull()

    private suspend fun post(path: String, body: String, prefer: String): WriteResult = runCatching {
        val response = http.post("${OnlineConfig.restUrl}$path") {
            OnlineConfig.authHeaders().forEach { (key, value) -> header(key, value) }
            header("Prefer", prefer)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        when {
            response.status == HttpStatusCode.Conflict -> WriteResult.Conflict
            response.status == HttpStatusCode.Created -> WriteResult.Ok
            response.status == HttpStatusCode.OK -> WriteResult.Ok
            else -> {
                // 非 2xx 时把响应体一起带上：PostgREST 把原因写在 body 里
                // （哪条约束冲突、哪条策略拒绝），只看状态码永远查不出来。
                val detail = runCatching { response.bodyAsText() }.getOrDefault("")
                WriteResult.Failed("HTTP ${response.status.value} ${detail.take(200)}")
                    .also { lastFailure = "POST $path -> ${it.message}" }
            }
        }
    }.getOrElse {
        lastFailure = "POST $path -> ${it::class.simpleName}: ${it.message}"
        WriteResult.Failed(it.message ?: "网络不可用")
    }

    /** 与 [post] 同一套取舍，只是动词不同（改索引行）。 */
    private suspend fun patch(path: String, body: String, prefer: String): WriteResult = runCatching {
        val response = http.patch("${OnlineConfig.restUrl}$path") {
            OnlineConfig.authHeaders().forEach { (key, value) -> header(key, value) }
            header("Prefer", prefer)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        when (response.status) {
            HttpStatusCode.Conflict -> WriteResult.Conflict
            HttpStatusCode.Created, HttpStatusCode.OK, HttpStatusCode.NoContent -> WriteResult.Ok
            else -> {
                val detail = runCatching { response.bodyAsText() }.getOrDefault("")
                WriteResult.Failed("HTTP ${response.status.value} ${detail.take(200)}")
                    .also { lastFailure = "PATCH $path -> ${it.message}" }
            }
        }
    }.getOrElse {
        lastFailure = "PATCH $path -> ${it::class.simpleName}: ${it.message}"
        WriteResult.Failed(it.message ?: "网络不可用")
    }

    /**
     * 索引行 → [RoomSummary]。
     *
     * 取值一律走 [stringOf] / [longOf] 这两个**能接受 null 列**的访问器：
     * `JsonElement.jsonPrimitive` 遇到 JSON `null` 会抛异常，而大厅列表是一次
     * 批量解析 —— 一行里有一个 null（新加的 `host_avatar` / `expires_at` 在老行上必然是 null）
     * 就会让**整次解析**失败，表现是「大厅拉不到列表」，而不是「少个头像」。
     * 把一行的装饰字段升级成整页的故障，是这次加列最该避免的事。
     */
    private fun decodeSummary(obj: JsonObject): RoomSummary? {
        val id = stringOf(obj, "id") ?: return null
        val config = (obj["config"] as? JsonObject)?.let(RoomEventCodec::decodeConfig)
            ?: MatchConfig()
        return RoomSummary(
            id = id,
            name = stringOf(obj, "name") ?: "房间 $id",
            creatorId = stringOf(obj, "creator_id") ?: "",
            config = config,
            visibility = runCatching {
                RoomVisibility.valueOf(stringOf(obj, "visibility") ?: "PUBLIC")
            }.getOrDefault(RoomVisibility.PUBLIC),
            allowSpectators = stringOf(obj, "allow_spectators").toBoolean(),
            createdAt = longOf(obj, "created_at") ?: 0L,
            joinPolicy = RoomJoinPolicy.fromKey(stringOf(obj, "join_policy")),
            hostName = stringOf(obj, "host_name").orEmpty(),
            hostAvatar = stringOf(obj, "host_avatar").orEmpty(),
            expiresAt = longOf(obj, "expires_at"),
            // 未知取值（老端写的 / 手改库）一律退回 WAITING：把「我不认识这个状态」
            // 变成「这间房打不开」是最坏的一种降级。
            status = runCatching {
                RoomStatus.valueOf(stringOf(obj, "status") ?: RoomStatus.WAITING.name)
            }.getOrDefault(RoomStatus.WAITING),
            startsAt = longOf(obj, "starts_at"),
            hostPpr = doubleOf(obj, "host_ppr"),
            hostCredit = longOf(obj, "host_credit")?.toInt()
        )
    }

    /** 取字符串列；JSON null / 缺列 → null。 */
    private fun stringOf(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonPrimitive)?.content

    /** 取整数列；JSON null / 非数字 / 缺列 → null。 */
    private fun longOf(obj: JsonObject, key: String): Long? =
        (obj[key] as? JsonPrimitive)?.content?.toLongOrNull()

    /** 取小数列；JSON null / 非数字 / 缺列 → null。 */
    private fun doubleOf(obj: JsonObject, key: String): Double? =
        (obj[key] as? JsonPrimitive)?.content?.toDoubleOrNull()

    private companion object {
        // 15 秒：一次 HTTP 往返在移动网络上正常是几百毫秒，但在电梯/地铁里
        // 几秒是很常见的。取值再大没有意义 —— 用户早就以为没点上了；
        // 真正兜底的是上层的重试（见 OnlineRoomRepository.write）。
        const val TIMEOUT_MS = 15_000L

        // 拉事件偶发会卡几分钟（实测），重试几乎必中；退避短一点，
        // 因为这是「进房间」的等待路径，用户正盯着屏幕。
        const val FETCH_ATTEMPTS = 3
        const val FETCH_RETRY_DELAY_MS = 400L

        /** 每个房间最多取多少条快照（取最新一条即可，留出历史条数余量）。 */
        const val STATE_ROWS_PER_ROOM = 20
    }
}
