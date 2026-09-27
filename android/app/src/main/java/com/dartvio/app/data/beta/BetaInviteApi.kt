package com.dartvio.app.data.beta

import com.dartvio.app.net.online.OnlineConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 邀请码的**后台校验**（Beta 分发用）。
 *
 * ## 为什么必须有后台这一步
 *
 * 包里的白名单（[BetaInviteCodes]）只能回答「这串字符有没有发过」，回答不了三件要紧的事：
 * ① 这个码是不是已经在**别的手机**上启用过了；② 这个码是不是已经被回收（停用）了；
 * ③ 谁激活了哪个码 —— 这条发放台账要能在试用期里跟实际使用的人数对得上。
 * 前两条是发放管理的需要，第三条是事后可追溯的依据。
 *
 * ## 为什么走在 [OnlineConfig] 上
 *
 * 这正是自建 REST 后端的现成通道（同一域名、同一 anon key、同一层 Caddy），
 * 不需要为「一次性的激活请求」再引入一个新域名与新凭证 —— 多一条凭证就多一处要管。
 *
 * ## 失败语义
 *
 * 后台**拒答**（网络不通 / 后端没配）与后台**拒绝**（码无效）是两件事，必须分开：
 * - 拒答 → 调用方可以选择离线放行（本地白名单），但必须记成「待补验」；
 * - 拒绝 → 不允许激活，也没得商量。
 * 把它们合成一个「失败」，会变成「有网时用不了、没网时随便用」，正好反了。
 */
object BetaInviteApi {

    /** 单次激活的总预算：用户在首启页等着看结果，超时要给得出结论。 */
    const val TIMEOUT_MILLIS = 6_000L

    sealed interface Result {
        /** 后台确认可用。[rank] = 第几位激活者（后端算的，**刻意不在 UI 展示**，理由见类注释）。 */
        data class Accepted(val rank: Int?) : Result

        /** 后台明确拒绝：[reason] 是服务端给的机器可读原因，交给 UI 翻译成人话。 */
        data class Rejected(val reason: String) : Result

        /** 后台没回应（未配置 / 网络不通 / 返回不可解析）。不是用户的问题。 */
        data object Unavailable : Result
    }

    private val http by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MILLIS
                connectTimeoutMillis = TIMEOUT_MILLIS
            }
            // 与 OnlineRoomApi 一致：非 2xx 不抛异常，自己判状态码。
            expectSuccess = false
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** 是否已具备后台校验的条件（没配后端就只能走本地白名单）。 */
    fun available(): Boolean = OnlineConfig.isConfigured

    /**
     * 校验一个码。
     *
     * 服务端接口：`POST /rest/v1/rpc/redeem_invite`，参数 `p_code` / `p_install`。
     * 用 RPC 而不是让客户端直接改写表：**谁能激活、什么时候激活、能不能放行**这类发放规则
     * 必须留在服务端 —— 写在客户端里的规则等于没有规则。
     */
    suspend fun verify(code: String, installId: String): Result {
        if (!available()) return Result.Unavailable
        val body = buildJsonObjectOf(code, installId)
        val text = runCatching {
            http.post("${OnlineConfig.restUrl}/rest/v1/rpc/redeem_invite") {
                OnlineConfig.authHeaders().forEach { (k, v) -> header(k, v) }
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()
        }.getOrNull()
        return parse(text)
    }

    private fun buildJsonObjectOf(code: String, installId: String): String =
        buildJsonObject {
            put("p_code", code)
            put("p_install", installId)
        }.toString()

    /**
     * 解析服务端返回。
     *
     * 只认 `{ ok: Boolean, reason?: String, rank?: Int }` 这一个形状 ——
     * 认不出就 [Result.Unavailable]（而不是当成拒绝）：解析错误是**我们**的问题，
     * 不该让用户在首启页背上「邀请码无效」的结论。
     */
    fun parse(raw: String?): Result {
        if (raw.isNullOrBlank()) return Result.Unavailable
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: return Result.Unavailable
        val ok = runCatching { root["ok"]?.jsonPrimitive?.content?.toBoolean() }.getOrNull()
            ?: return Result.Unavailable
        if (!ok) {
            return Result.Rejected(
                runCatching { root["reason"]?.jsonPrimitive?.content }.getOrNull() ?: "REJECTED"
            )
        }
        val rank = runCatching { root["rank"]?.jsonPrimitive?.content?.toIntOrNull() }.getOrNull()
        return Result.Accepted(rank)
    }

    /** 后台拒绝原因 → 界面文案。不在界面上露机器码，也不教用户怎么绕过校验。 */
    fun messageFor(reason: String): String = when (reason.uppercase()) {
        "NOT_FOUND" -> "这个邀请码不在名单里，检查一下有没有输错"
        "REVOKED" -> "这个邀请码已被停用，请联系发放人换一个"
        "USED_ELSEWHERE" -> "这个邀请码已经在另一台设备上用过了"
        else -> "邀请码暂不可用（$reason）；不填也能直接用"
    }
}
