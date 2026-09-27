package com.dartvio.app.net.online

import com.dartvio.app.BuildConfig

/**
 * 在线对战的后端配置（编译期注入，不进版本库）。
 *
 * 与统计上报共用同一个 Supabase 项目，但**刻意用独立的两个 BuildConfig 字段**：
 * 万一将来把对战后端换走（或给统计换项目），两处不该一起改，
 * 而共用一个字段会让「只想换一个」变成「必须同时换」。
 */
object OnlineConfig {

    /** 配好了才认为「在线对战可用」；没配的包装上去只会得到一串连不上的错误。 */
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val restUrl: String get() = BuildConfig.SUPABASE_URL.trimEnd('/')

    /**
     * Realtime 的 WebSocket 地址。
     *
     * 由 REST 地址推导（`https://x.supabase.co` → `wss://x.supabase.co/realtime/v1/websocket`），
     * 而不是再要一个配置项：两者必然同源，多一个配置就多一处「填错了但不报错」的机会。
     */
    val realtimeUrl: String
        get() {
            val base = restUrl
                .replaceFirst("https://", "wss://")
                .replaceFirst("http://", "ws://")
            return "$base/realtime/v1/websocket?apikey=${BuildConfig.SUPABASE_ANON_KEY}&vsn=1.0.0"
        }

    /** PostgREST 的鉴权头。anon key 对两张在线表只有「读 + 插入」权限（无 update / delete）。 */
    fun authHeaders(): Map<String, String> = mapOf(
        "apikey" to BuildConfig.SUPABASE_ANON_KEY,
        "Authorization" to "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
    )
}
