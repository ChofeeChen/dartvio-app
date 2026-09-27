package com.dartvio.app.domain.room

import com.dartvio.app.platform.CivilDateTime
import com.dartvio.app.platform.PlatformTime

/**
 * 预约比赛的**时间文案**（房间卡片用）。
 *
 * ## 为什么「不足 1 小时」才倒着数
 *
 * 倒计时的价值是「该准备上线了」，而这件事只在临门一脚时成立：
 * 约在晚上八点的房，下午显示「还剩 5:12:30」只会让人每看一次都要在脑子里做一次减法。
 * 超过一小时就直说时刻，不足一小时才换成倒计时 —— 两种形态各自回答不同的问题。
 *
 * ## 为什么不用 java.time / Calendar
 *
 * 本机没开核心库脱糖，`java.time` 在 minSdk 24/25 上是**运行期** `NoClassDefFoundError`
 * （编译能通过，装到老机器上才炸）；而本文件随 `domain/` 下沉到 KMP 的 `shared` 模块后，
 * `Calendar` / `SimpleDateFormat` 也不再可用（common 里没有）。
 *
 * 现在的时间口径只有两个入口：[PlatformTime.zoneOffsetMillis]（平台给偏移）
 * 与 [CivilDateTime]（纯 Kotlin 算日历）。两端因此算出的「今天 / 明天」必然一致。
 */
object RoomSchedule {

    /** 距开赛不足这个时长就开始倒计时。 */
    const val COUNTDOWN_FROM_MS = 60 * 60_000L

    /**
     * 卡片上的时间文案。
     *
     * @return `null` = 这间房是「建好就开打」，卡片上**不显示**这一行 ——
     *         给立即开始的房也挂一个时间是噪音，用户会以为自己约错了。
     */
    fun cardLabel(startsAt: Long?, now: Long): String? {
        if (startsAt == null || startsAt <= 0L) return null
        val left = startsAt - now
        if (left <= 0L) return "已到开赛时间"
        if (left <= COUNTDOWN_FROM_MS) return "还剩 ${countdown(left)}"
        return "${dayPrefix(startsAt, now)}${clock(startsAt)} 开始"
    }

    /** 倒计时文案：`12:34`，超过一小时带小时位 `1:02:03`。 */
    fun countdown(remainingMs: Long): String {
        val total = (remainingMs + 999) / 1000
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) {
            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }

    /** `20:30`。 */
    private fun clock(millis: Long): String =
        CivilDateTime.clockLabel(millis, PlatformTime.zoneOffsetMillis(millis))

    /** 今天不写前缀（写了是废话），明天 / 更远的日子才标出来。 */
    private fun dayPrefix(millis: Long, now: Long): String {
        val targetDay = CivilDateTime.dayNumber(millis, PlatformTime.zoneOffsetMillis(millis))
        val today = CivilDateTime.dayNumber(now, PlatformTime.zoneOffsetMillis(now))
        return when (targetDay - today) {
            0 -> ""
            1 -> "明天 "
            else -> CivilDateTime.monthDayLabel(millis, PlatformTime.zoneOffsetMillis(millis))
        }
    }
}
