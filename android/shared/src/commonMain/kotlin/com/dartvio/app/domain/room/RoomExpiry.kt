package com.dartvio.app.domain.room

/**
 * 等待房的 5 分钟有效期（PRD D4 / A7–A9）。
 *
 * ## 为什么是「等不到人就散」
 *
 * 大厅是**公开**的：任何人都能看见并点进来。因此一间没人来的房间不只是一个人的事 ——
 * 它一直占着大厅最上面那一格，而大厅第一屏只有三四张卡。房主自己忘了它在那儿，
 * 别人的第一印象就是「这个 App 没人」。
 *
 * ## 为什么是 5 分钟
 *
 * 参照 Dartsmind（创建弹层里就写着「5 分钟内没人加入自动解散」）：够一个人
 * 念出房间名、让朋友打开 App 找到它；又不至于让一间死房占满一屏。
 *
 * ## 时间为什么用 epoch 毫秒而不是 ISO 字符串
 *
 * 这一列要被三处**不同技术栈**读写：Android 客户端（写 + 读）、Postgres（清理）、
 * 将来的服务端。用 `bigint`（epoch 毫秒）三者都能原生理解，不需要任何一方解析时区字符串；
 * Android 侧因此不碰 `java.time`（本机没开核心库脱糖，minSdk 24/25 会 NoClassDefFoundError）。
 */
object RoomExpiry {

    /** 等待房的有效期。 */
    const val WAITING_TTL_MS = 5 * 60_000L

    /** 剩余多少毫秒开始转橙（A8）。 */
    const val WARN_FROM_MS = 60_000L

    /** 建房时写入的到期时刻。 */
    fun deadline(now: Long = System.currentTimeMillis()): Long = now + WAITING_TTL_MS

    /**
     * 卡片上应当显示的剩余毫秒；`null` = 这张卡不显示倒计时。
     *
     * 三种不显示的情况各自有明确含义，不能混成一个「0」：
     * - `expiresAt == null`：老端建的房 / 已经满员的房（满员即停止计时，A9）
     * - [full]：有人入座 —— 倒计时完成使命，再数下去就是在给一场马上要开始的比赛倒计时
     * - 已经过期：卡片本身应当消失（A7），不是「显示 0:00」
     */
    fun remainingMs(expiresAt: Long?, now: Long, full: Boolean): Long? {
        if (expiresAt == null || full) return null
        val left = expiresAt - now
        return left.takeIf { it > 0 }
    }

    /** 到期判定：房间索引行的 `expires_at` 已经过去 —— 该销毁了（A7）。 */
    fun isExpired(expiresAt: Long?, now: Long): Boolean = expiresAt != null && expiresAt <= now

    /** 卡片上的文案：`4:32`。向上取整，免得刚建房就显示「4:59」像已经过了一分钟。 */
    fun label(remainingMs: Long): String {
        val seconds = (remainingMs + 999) / 1000
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    /** 是否进入告警档（转橙）。 */
    fun isWarning(remainingMs: Long): Boolean = remainingMs <= WARN_FROM_MS
}
