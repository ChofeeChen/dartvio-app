package com.dartvio.app.data.telemetry

import android.content.Context
import android.content.SharedPreferences

/*
 * 匿名使用统计的**本机身份**（`SharedPreferences("dartvio_usage")`）。
 *
 * 这是「不收集隐私」这条承诺的落点，所以先把边界写死在这里：
 * - 落盘的只有后端签发的随机 UUID 与续期票据，**没有**设备名、型号、通讯录、
 *   位置、广告 ID —— 多一个字段都不许往里加；
 * - 这个 ID **不可反向识别到人**：它只在这一台设备上有意义，卸载重装就是新 ID；
 * - 它**不是账号**：没有注册、没有登录、没有找回。用户全程无感。
 *
 * 沿用 `ProfileStore` / `BetaAccess` 的依赖倒置写法：核心只依赖 [SharedPreferences]，
 * 生产入口才接 [Context]。测试依赖只有 JUnit，因此持久化语义必须能在纯 JVM 覆盖。
 *
 * 三条规定：
 * 1. [save] 是**唯一的写入口**，且整份覆盖 —— 不留下「有 user_id 但没 token」这种半截状态；
 * 2. [isExpired] 把 now 留给调用方注入（时间可注入才测得准），并留 [REFRESH_SKEW_MILLIS]
 *    提前量：等到真的过期那一秒才去续期，请求必然打在失效窗口上；
 * 3. 票据不可用时**不重建身份**（那会凭空多出一个用户，把「用户数」这个指标污染掉），
 *    只返回 null，交给调用方下次再试。
 */
object UsageIdentity {

    const val PREFS_NAME = "dartvio_usage"

    // 这些键一旦发布不得改名：改名 = 就地丢弃用户的匿名 ID = 统计曲线断一截。
    private const val KEY_USER_ID = "user_id"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at_millis"

    /*
     * 提前 60 秒续期。GoTrue 的 access_token 默认一小时有效，
     * 卡在最后一秒去续期，网络一抖就打在失效窗口上 —— 宁可早一点。
     */
    const val REFRESH_SKEW_MILLIS = 60_000L

    /** 一次匿名会话：随机 ID + 两张票据。三者要么全有、要么全无。 */
    data class Session(
        val userId: String,
        val accessToken: String,
        val refreshToken: String,
        val expiresAtMillis: Long,
    )

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 已建立的身份；票据不全或还没拿到身份时返回 null。 */
    fun read(prefs: SharedPreferences): Session? {
        val userId = prefs.getString(KEY_USER_ID, null).orEmpty().trim()
        val access = prefs.getString(KEY_ACCESS_TOKEN, null).orEmpty().trim()
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null).orEmpty().trim()
        if (userId.isEmpty() || access.isEmpty() || refresh.isEmpty()) return null
        return Session(
            userId = userId,
            accessToken = access,
            refreshToken = refresh,
            expiresAtMillis = prefs.getLong(KEY_EXPIRES_AT, 0L),
        )
    }

    /**
     * 票据是否该续期了。
     *
     * `expiresAtMillis == 0` 视为「该续」：那是读到脏数据/旧版本的情形，
     * 当成有效反而会一路带着坏票据失败下去。
     */
    fun isExpired(session: Session, nowMillis: Long): Boolean =
        nowMillis >= session.expiresAtMillis - REFRESH_SKEW_MILLIS

    /** 落盘（整份覆盖）。身份是低频写入，`commit()` 换确定性，与 `BetaAccess.grant` 一致。 */
    fun save(prefs: SharedPreferences, session: Session) {
        prefs.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAtMillis)
            .commit()
    }

    /** 清空身份（仅供「重置统计 ID」这类显式动作）。 */
    fun clear(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    /** 展示用的短 ID：只取前 8 位，够对账、又不至于被拿去当标识用。 */
    fun shortId(userId: String?): String =
        userId?.take(8)?.takeIf { it.length == 8 }.orEmpty()
}
