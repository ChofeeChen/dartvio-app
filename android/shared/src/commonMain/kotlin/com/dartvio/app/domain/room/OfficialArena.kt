package com.dartvio.app.domain.room

import com.dartvio.app.platform.CivilDateTime
import com.dartvio.app.platform.PlatformTime

/**
 * 「官方擂台」时间锚点（大厅留存手段，**不是**匹配功能）。
 *
 * 设计意图：低供给阶段，大厅最贵的一次流失是「打开 → 空 → 关掉 App」。
 * 预约需要「已经有两个人想在同一时间玩」，属于**反应**已有的同时性；
 * 时间锚点由官方承诺「一个时段」，属于**创造**同时性。
 *
 * 两者的本质区别：
 * - 预约承诺「一场对局」——低供给下**必然违约**（保证不了对方来）；
 * - 时间锚点只承诺「一个时间」——**永远不会违约**。
 *
 * 因此本类刻意不引入「对手」「匹配」等语义，[ArenaState.headline] 对待开始态
 * 必须写出固定的钟点（由 `OfficialArenaTest` 的不变式断言钉死）。
 *
 * ## 调整时段
 *
 * **只需改 [START_HOUR] / [END_HOUR]**，其余全部派生：分钟换算、[WINDOW_LABEL]、
 * 卡片文案、空状态文案。`OfficialArenaTest` 的期望值也都由常量派生，
 * 因此改时段**只会让「决策锁」那一组用例失败**（它锁的是决策而非逻辑），
 * 失败信息会明确提示这是决策变更。
 *
 * ## 已知限制：不支持跨零点时段
 *
 * 例如 `23:00–01:00`。这不是「稍后再说」的细节，而是一个**静默算错**的陷阱：
 * `START_HOUR=23, END_HOUR=1` 会让 00:30 走进「尚未开赛」分支，显示「还有 22 小时开始」，
 * 而真实语义下此刻正在进行中——不崩溃，只是安静地说谎。
 *
 * 因此此类配置由 [requireValidWindow] **显式拒绝**，在测试阶段即失败，不可能上线。
 * 若真实活跃时段确实落在跨零点，需要先改造 [stateAt] 与「今晚 / 明晚」语义。
 *
 * ## 实现约束
 *
 * 刻意**不使用 `java.time`**。本项目 `minSdk = 24`，`java.time` 需要 core library desugaring
 * （同 [com.dartvio.app.domain.achievement.LocalDateKey] 的说明）。
 *
 * ⚠️ 当前取值（20:00–22:00）是**待确认的占位决策**，需用真实活跃时段分布校准。
 */
object OfficialArena {

    const val MINUTES_PER_DAY = 24 * 60

    /** 开赛时刻（本地时间，小时）。 */
    const val START_HOUR = 20

    /** 结束时刻（本地时间，小时）。 */
    const val END_HOUR = 22

    const val START_MINUTE_OF_DAY = START_HOUR * 60
    const val END_MINUTE_OF_DAY = END_HOUR * 60

    /** 锚点名称，用于大厅卡片与文案。 */
    const val TITLE = "官方擂台"

    /** 固定时段文案。 */
    const val WINDOW_LABEL = "每晚 ${START_HOUR}:00 - ${END_HOUR}:00"

    init {
        // 配置错误必须在测试阶段炸掉，而不是在用户手机上静默算错。
        requireValidWindow(START_HOUR, END_HOUR)
    }

    /**
     * 按「本地时间在当天的第几分钟」判定擂台状态。纯函数，便于单测覆盖全部边界。
     *
     * @param minuteOfDay 取值 `0 until` [MINUTES_PER_DAY]；越界直接失败，不做静默兜底。
     */
    fun stateAt(minuteOfDay: Int): ArenaState {
        require(minuteOfDay in 0 until MINUTES_PER_DAY) {
            "minuteOfDay 必须在 0 until $MINUTES_PER_DAY，实际为 $minuteOfDay"
        }
        return when {
            minuteOfDay < START_MINUTE_OF_DAY ->
                ArenaState.Upcoming(
                    minutesUntilStart = START_MINUTE_OF_DAY - minuteOfDay,
                    startsToday = true
                )

            minuteOfDay < END_MINUTE_OF_DAY ->
                ArenaState.Live(minutesUntilEnd = END_MINUTE_OF_DAY - minuteOfDay)

            else ->
                ArenaState.Upcoming(
                    minutesUntilStart = MINUTES_PER_DAY - minuteOfDay + START_MINUTE_OF_DAY,
                    startsToday = false
                )
        }
    }

    /** 按时间戳判定擂台状态（使用设备默认时区）。 */
    fun stateAt(millis: Long): ArenaState = stateAt(minuteOfDayOf(millis))

    /** 时间戳对应的本地「当天第几分钟」（0..1439）。 */
    fun minuteOfDayOf(millis: Long): Int =
        CivilDateTime.minuteOfDay(millis, PlatformTime.zoneOffsetMillis(millis))

    /**
     * 判定一次状态推进是否构成「开赛跳变」（尚未开赛 → 进行中）。
     *
     * 抽成纯函数而非内联进轮询逻辑：拉横幅是一次性副作用，但「哪一次推进才算开赛」
     * 属于口径，需要能被边界单测钉死——与 [requireValidWindow] 抽出来的理由相同
     * （`object` 的状态机本身无法在测试里被反复驱动）。
     *
     * 判据用 `previous !is Live` 而非 `previous is Upcoming`：两者在当前状态机下等价，
     * 但前者表达的是「此前从未在进行中」，未来即便新增状态（如「已结束」）也不会误判。
     */
    fun isKickoff(previous: ArenaState, next: ArenaState): Boolean =
        next is ArenaState.Live && previous !is ArenaState.Live
}

/**
 * 校验擂台时段配置。
 *
 * 独立性说明：本函数刻意做成**纯函数**而非只在 [OfficialArena] 的 `init` 里内联，
 * 这样「非法配置会被拒绝」这件事本身可以被单测覆盖——`object` 的 `init` 一个类加载器
 * 只跑一次，无法在测试中反复触发。
 *
 * 约束 `endHour in 1..23` 而非 `1..24`：它保证时段**完整落在同一天内**，
 * 与「不支持跨零点」保持一致，同时避免 `END_HOUR=24` 让 `startsToday = false`
 * 这条路径变得不可达（「明晚」文案将永远不会出现）。
 */
// 原为 internal：随 domain/ 下沉到 shared 后，internal 对 app 模块（与它的单测）不再可见，
// 而这里要被 `OfficialArenaTest` 直接覆盖，故改为 public。**它仍属于内部校验，业务代码勿调用。**
fun requireValidWindow(startHour: Int, endHour: Int) {
    require(startHour in 0..23) {
        "START_HOUR 必须在 0..23，实际为 $startHour"
    }
    require(endHour in 1..23) {
        "END_HOUR 必须在 1..23，实际为 $endHour。" +
            "若想表达「进行到午夜」，请改用 END_HOUR=23 并复核时段语义。"
    }
    require(startHour < endHour) {
        "官方擂台暂不支持跨零点时段：START_HOUR($startHour) 必须早于 END_HOUR($endHour)。" +
            "当前若按此配置运行，跨零点的时段会被判定为「尚未开赛」并显示错误的倒计时。" +
            "若真实活跃时段落在跨零点（如 23:00–01:00），需先改造 OfficialArena.stateAt 与「今晚/明晚」语义。"
    }
}

/**
 * 官方擂台时段状态。
 *
 * 文案口径遵循「一个确定的回来时间 + 一个立刻能做的动作」：
 * 空大厅不渲染「0 人在线」这类令人沮丧的数字，而是给出擂台时刻与单人可做的事。
 */
sealed interface ArenaState {

    /** 尚未开赛。[startsToday] 为 false 表示指向次日。 */
    data class Upcoming(val minutesUntilStart: Int, val startsToday: Boolean) : ArenaState

    /** 正在进行。 */
    data class Live(val minutesUntilEnd: Int) : ArenaState

    /** 卡片标题：待开始态必须写出固定钟点，不能只说「即将开始」。 */
    val headline: String
        get() = when (this) {
            is Upcoming ->
                "${OfficialArena.TITLE} · ${if (startsToday) "今晚" else "明晚"} " +
                    "${OfficialArena.START_HOUR}:00 开始"

            is Live -> "${OfficialArena.TITLE} · 进行中"
        }

    /** 卡片副标题：倒计时 + 现在就能做的事。 */
    val subline: String
        get() = when (this) {
            is Upcoming ->
                if (startsToday) {
                    "还有 ${durationText(minutesUntilStart)}开始 · 先去练几镖"
                } else {
                    "现在先去练几镖，或开个房间等镖友"
                }

            is Live -> "还剩 ${durationText(minutesUntilEnd)} · 现在加入最容易配上"
        }
}

/** 分钟数转中文时长；不足 1 小时不出现「0 小时」。 */
private fun durationText(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest 分钟"
        rest == 0 -> "$hours 小时"
        else -> "$hours 小时 $rest 分"
    }
}
