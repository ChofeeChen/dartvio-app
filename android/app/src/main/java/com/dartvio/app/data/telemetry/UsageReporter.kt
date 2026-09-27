package com.dartvio.app.data.telemetry

import android.content.Context
import com.dartvio.app.BuildConfig
import com.dartvio.app.data.beta.BetaAccess
import com.dartvio.app.data.beta.BetaConsent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * 匿名使用统计的**上报器**（每天最多一条）。
 *
 * 为什么不用 supabase-kt：它 3.0 起强依赖 Ktor 3，而本项目刻意停在 Ktor 2.3.12
 * （Ktor 3 引 java.time，minSdk 24/25 会在**运行期** NoClassDefFoundError，
 * 编译期看不出来）。这里用现成的 Ktor client 2.3.12 直接打 REST，零新依赖。
 *
 * 三条不可动摇的约束，违反任何一条都会伤害用户：
 * 1. **绝不影响使用**：整个上报包一层超时 + 全捕获，失败静默。地下室、飞行模式、
 *    Supabase 挂掉，App 都要照常能玩 —— 统计是旁路，不是门禁；
 * 2. **不上报 = 不重试风暴**：失败就等下次冷启动，不做循环重试（那会在弱网下
 *    白耗电，还会放大失败请求数）；
 * 3. **只写不读**：本模块只发 POST，从不查询。客户端拿不到任何一行数据。
 *
 * 配置缺失（没填 local.properties）时 [isEnabled] 为 false，整个模块**完全不发请求**，
 * 因此这段逻辑可以在没有后端的情况下编译、测试、打包。
 */
object UsageReporter {

    /** 单次上报的总预算：超时即放弃，不阻塞、不影响启动。 */
    const val TIMEOUT_MILLIS = 4_000L

    private const val PATH_SIGNUP = "/auth/v1/signup"
    private const val PATH_REFRESH = "/auth/v1/token?grant_type=refresh_token"
    private const val PATH_PING = "/rest/v1/usage_ping"

    /** 建表见交付说明；列与这里逐字对应。 */
    private const val TABLE_PING = "usage_ping"

    /**
     * 是否真的会发请求。
     *
     * 三重开关缺一不可：编译期变体门（`TELEMETRY_ENABLED`）+ 运行期配置完整性。
     * 少任何一个都静默关闭 —— 宁可少收数据，也不能在我没配好后端时对外发包。
     */
    fun isEnabled(): Boolean {
        if (!BuildConfig.TELEMETRY_ENABLED) return false
        return BuildConfig.TELEMETRY_URL.isNotBlank() && BuildConfig.TELEMETRY_ANON_KEY.isNotBlank()
    }

    private val http by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MILLIS
                connectTimeoutMillis = TIMEOUT_MILLIS
            }
            expectSuccess = false
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 冷启动上报一次「今天有一个人打开了 App」。
     *
     * 全程 [withTimeout] + 全捕获：任何异常（含 `CancellationException` 之外的
     * 一切）都不许冒出这个方法。调用方可以放心在 `Application.onCreate` 里直接调。
     */
    suspend fun reportOnce(context: Context) {
        if (!isEnabled()) return
        // 知情同意门：**未经同意一个包都不发**。
        // 放在这里（而不是只在 UI 首启弹窗里）是因为 Application.onCreate 比界面更早，
        // 只有在这一层卡住，才敢说「没征得同意就没有数据离开设备」。
        if (!BetaConsent.granted(BetaConsent.prefs(context))) return
        withContext(Dispatchers.IO) {
            try {
                withTimeout(TIMEOUT_MILLIS) { report(context) }
            } catch (_: Throwable) {
                // 统计失败对用户没有任何可见后果 —— 这里刻意不记日志、不重试。
            }
        }
    }

    private suspend fun report(context: Context) {
        val prefs = UsageIdentity.prefs(context)
        val now = System.currentTimeMillis()

        val session = UsageIdentity.read(prefs)?.let { existing ->
            if (!UsageIdentity.isExpired(existing, now)) {
                existing
            } else {
                refresh(existing.refreshToken)?.also { UsageIdentity.save(prefs, it) }
            }
        } ?: signUpAnonymously()?.also { UsageIdentity.save(prefs, it) }

        // 拿不到身份就这次不报 —— 刻意**不**新建身份顶上，否则「用户数」会被失败重试灌水。
        if (session == null) return

        postPing(context, session)
    }

    /*
     * 匿名登录：不带任何身份信息，后端返回一个全新的随机用户。
     * 需要在 Supabase Dashboard 开启 "Allow anonymous sign-ins"，否则这里会拿到 4xx，
     * 然后走上面的静默降级 —— App 照常可用，只是不上报。
     */
    private suspend fun signUpAnonymously(): UsageIdentity.Session? {
        val text = http.post(BuildConfig.TELEMETRY_URL + PATH_SIGNUP) {
            header("apikey", BuildConfig.TELEMETRY_ANON_KEY)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(buildJsonObject { put("data", buildJsonObject {}) }.toString())
        }.bodyAsText()
        return parseSession(text)
    }

    /** 用 refresh_token 续期。失败返回 null，保留旧身份下次再试。 */
    private suspend fun refresh(refreshToken: String): UsageIdentity.Session? {
        val text = http.post(BuildConfig.TELEMETRY_URL + PATH_REFRESH) {
            header("apikey", BuildConfig.TELEMETRY_ANON_KEY)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(buildJsonObject { put("refresh_token", refreshToken) }.toString())
        }.bodyAsText()
        return parseSession(text)
    }

    /**
     * 写一行「今天来过」。
     *
     * `Prefer: resolution=ignore-duplicates` 配合表上的 `unique(user_id, day)`：
     * 同一天重复上报会被服务端安静丢弃，既天然去重（一天最多一条，DB 不会膨胀），
     * 也让「一天多次打开」不会被算成多个活跃。
     */
    private suspend fun postPing(context: Context, session: UsageIdentity.Session) {
        // 渠道码是**可选**的：没填过邀请码的人照样上报，只是这一列为空。
        // 这正是「邀请码从门禁降级为渠道标签」在数据侧的样子。
        val betaPrefs = context.getSharedPreferences(BetaAccess.PREFS, Context.MODE_PRIVATE)
        val code = BetaAccess.activatedCode(betaPrefs)
        http.post(BuildConfig.TELEMETRY_URL + PATH_PING) {
            header("apikey", BuildConfig.TELEMETRY_ANON_KEY)
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header("Prefer", "return=minimal,resolution=ignore-duplicates")
            setBody(
                buildJsonObject {
                    put("user_id", session.userId)
                    put("day", today())
                    put("app_version", BuildConfig.VERSION_NAME)
                    put("variant", variantName())
                    if (!code.isNullOrBlank()) put("invite_code", code)
                }.toString()
            )
        }
    }

    /** 本机日期（不是 UTC）：日活本来就该按用户感受到的「今天」算。 */
    private fun today(): String =
        // SimpleDateFormat.format(Long) 会走 format(Object) 那个重载，必须包 Date(...)。
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis()))

    private fun variantName(): String = if (BuildConfig.BETA_DEMO) "beta" else "debug"

    /**
     * 解析 GoTrue 的会话响应。
     *
     * 票据是「全有或全无」：少一个字段就返回 null，宁可这次不报，
     * 也不要存下一份下次必然失败的半截身份。
     */
    private fun parseSession(body: String): UsageIdentity.Session? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val user = root["user"] as? JsonObject
        val userId = str(user?.get("id")) ?: str(root["id"])
        val access = str(root["access_token"])
        val refresh = str(root["refresh_token"])
        val expiresIn = str(root["expires_in"])?.toLongOrNull()
        if (userId.isNullOrBlank() || access.isNullOrBlank() || refresh.isNullOrBlank()) return null
        val expiresAt = if (expiresIn != null) {
            System.currentTimeMillis() + expiresIn * 1_000L
        } else {
            // 没有 expires_in 就按一小时算，反正 [UsageIdentity.isExpired] 会提前续。
            System.currentTimeMillis() + 3_600_000L
        }
        return UsageIdentity.Session(userId, access, refresh, expiresAt)
    }

    /*
     * 只认字符串字段：数字、对象、null、缺失一律当读不到。
     * 票据必须是字符串，宽容解析只会把坏身份存下去、让之后每次启动都白跑一趟。
     */
    private fun str(element: JsonElement?): String? {
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.content.takeIf { it.isNotBlank() }
    }
}
