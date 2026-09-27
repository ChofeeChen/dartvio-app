package com.dartvio.app.ui.lobby

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.dartvio.app.data.room.RoomRepository
import com.dartvio.app.data.room.RoomRepositoryProvider
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.domain.room.LocalUser
import com.dartvio.app.domain.room.Room
import com.dartvio.app.domain.room.RoomDraft
import com.dartvio.app.domain.room.RoomVisibility
import com.dartvio.app.domain.room.StartMode
import java.util.Calendar

/**
 * 创建房间表单状态（M6 F6.2）。
 *
 * [repo] 的默认值写成 [RoomRepositoryProvider.current] 而不是具体实现：
 * 默认参数在**每次构造时**求值，因此本表单总能拿到当时启用中的仓库
 * （单机 Mock 或联机）。这样「创建房间」这一处调用点不需要知道联机是否已开启。
 */
class CreateRoomViewModel(
    private val repo: RoomRepository = RoomRepositoryProvider.current
) : ViewModel() {

    // 默认房间名「XX 的房间」：绝大多数人只是想开一局，名字不重要 ——
    // 让他先打一个名字，等于在「开房间」这条主路径上多装一道门（2026-09-26 真机反馈）。
    var name by mutableStateOf(defaultRoomName())
    var matchType by mutableStateOf(MatchType.X01)
        private set
    var targetScore by mutableStateOf(501)
        private set
    var mode by mutableStateOf(MatchMode.MULTI_LEG)
        private set
    var legsToWin by mutableStateOf(3)
        private set
    var doubleOut by mutableStateOf(true)
        private set
    var visibility by mutableStateOf(RoomVisibility.PUBLIC)
        private set
    var allowSpectators by mutableStateOf(true)
        private set

    /**
     * 开赛方式：建好就打 / 约一个时间。
     *
     * 默认「立即开始」：想打的人此刻就想打，让他先选一次时间等于在主动作上多加一道门。
     */
    var startMode by mutableStateOf(StartMode.NOW)
        private set

    /** 预约的月（1~12）。默认当月。 */
    var scheduledMonth by mutableStateOf(currentMonth())
        private set

    /** 预约的日（1~月末）。默认今天；换月时按新月的天数收敛（见 [updateScheduledMonth]）。 */
    var scheduledDay by mutableStateOf(currentDay())
        private set

    /** 预约的小时（24 小时制）。默认给「下一整点」，比给当前时刻更好点。 */
    var scheduledHour by mutableStateOf(nextHour())
        private set

    /**
     * 预约的分钟：**恒为 0**（界面不提供分钟档）。
     *
     * 「约几点」在社会语境里就是整点 —— 真的需要 19:15 才开打的场景，
     * 在点的人自己也说不清为什么要精确到刻钟。保留字段是为了 `startsAt` 的算式不变。
     */
    private val scheduledMinute: Int get() = 0

    /**
     * 预约开始时刻（epoch 毫秒）；「立即开始」时为 null。
     *
     * 约的时间点若已经过去（例如今天是 10 月 2 日而选了 10 月 1 日），顺延到**明年**同一时刻：
     * 让用户选一个已经过去的时间然后建房，得到的是一间「已到开赛时间」的房，
     * 那既不是他要的预约，也不是他要的立即开始。
     */
    val startsAt: Long?
        get() = if (startMode == StartMode.NOW) {
            null
        } else {
            occurrenceOf(scheduledMonth, scheduledDay, scheduledHour, scheduledMinute)
        }

    val config: MatchConfig
        get() = MatchConfig(
            matchType = matchType,
            targetScore = if (matchType == MatchType.X01) targetScore else MatchConfig.X01_501.targetScore,
            mode = mode,
            legsToWin = if (mode == MatchMode.CASUAL) 0 else legsToWin,
            // 房间页目前只暴露「双倍出 / 直出」这个开关，所以映射到三档里的两档：
            // X01 且开关打开 = 双倍出，其余 = 直出（Cricket 本来就不按倍区收尾）。
            // 开局 / 牛眼 / 最多轮数在房间页还没给控件，取默认值（直入 / 25-50 / 无上限）。
            outMode = if (matchType == MatchType.X01 && doubleOut) OutMode.DOUBLE_OUT else OutMode.STRAIGHT_OUT
        )

    fun onNameChange(value: String) {
        name = value.take(16)
    }

    fun updateMatchType(value: MatchType) {
        matchType = value
    }

    fun updateTargetScore(value: Int) {
        targetScore = value
    }

    fun updateMode(value: MatchMode) {
        mode = value
    }

    fun updateLegsToWin(value: Int) {
        legsToWin = value
    }

    fun updateDoubleOut(value: Boolean) {
        doubleOut = value
    }

    fun updateVisibility(value: RoomVisibility) {
        visibility = value
    }

    fun updateAllowSpectators(value: Boolean) {
        allowSpectators = value
    }

    fun updateStartMode(value: StartMode) {
        startMode = value
    }

    /**
     * 换月。日必须跟着收敛：1 月 31 日切到 2 月会得到「2 月 31 日」，
     * 而 `Calendar` 对越界日期是**静默进位**成 3 月 3 日 —— 用户选的是 2 月，
     * 卡片上却写着 3 月，这类错没有任何提示，只能靠这里钳住。
     */
    fun updateScheduledMonth(value: Int) {
        scheduledMonth = value
        scheduledDay = scheduledDay.coerceAtMost(lastDayOf(value))
    }

    fun updateScheduledDay(value: Int) {
        scheduledDay = value
    }

    fun updateScheduledHour(value: Int) {
        scheduledHour = (value + 24) % 24
    }

    /**
     * 三角箭头**循环**调月 / 日 / 时。
     *
     * 循环而不是夹在两端：12 月 +1 回到 1 月是一次「绕回去」，正好是用户要做的事；
     * 而在端点把箭头置灰，会让用户怀疑这一控件是不是坏了。
     * 日按**当前所选月**的天数绕，2 月不会绕出 2 月 30 日。
     */
    fun stepMonth(delta: Int) {
        updateScheduledMonth(((scheduledMonth - 1 + delta + 12) % 12) + 1)
    }

    fun stepDay(delta: Int) {
        val max = (dayOptions.maxOrNull() ?: 31)
        scheduledDay = ((scheduledDay - 1 + delta + max) % max) + 1
    }

    fun stepHour(delta: Int) = updateScheduledHour(scheduledHour + delta)

    /** 创建后可选的局数：休闲模式无意义，多局模式 1~5 局。 */
    val legsOptions: List<Int> get() = (1..5).toList()

    /** 可选的日：按所选月的真实天数（自动处理 2 月与闰年）；步进时的模就用它。 */
    val dayOptions: List<Int> get() = (1..lastDayOf(scheduledMonth)).toList()

    companion object {
        fun currentMonth(): Int = Calendar.getInstance().get(Calendar.MONTH) + 1

        fun currentDay(): Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

        /** 下一个整点（预约默认给这一格：比「当前时刻」更好点，也不会立刻变成过去）。 */
        fun nextHour(): Int = (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1) % 24

        /** 该月有多少天（闰年交给 Calendar 算，不自己写规则）。 */
        fun lastDayOf(month: Int): Int = Calendar.getInstance().apply {
            set(Calendar.MONTH, (month - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)

        fun occurrenceOf(month: Int, day: Int, hour: Int, minute: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.MONTH, (month - 1).coerceIn(0, 11))
                set(Calendar.DAY_OF_MONTH, day.coerceIn(1, lastDayOf(month)))
                set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.YEAR, 1)
            return target.timeInMillis
        }
    }

    /**
     * 默认房间名：`「昵称」的房间`。
     *
     * [LocalUser] 里的昵称由 `OnlineRoomLink.enable` 在数据源切换时同步过来
     * （见 `PlayerProfileStore.hydrateLocalUser`），因此这里读到的就是本机档案里的昵称。
     * 昵称还没建档时留空：交给仓库的兜底（`creatorName 的房间`），不在 UI 里再猜一次。
     */
    private fun defaultRoomName(): String {
        val nickname = LocalUser.name.trim()
        return if (nickname.isEmpty()) "" else "$nickname 的房间"
    }

    fun create(): Room = repo.createRoom(
        draft = RoomDraft(
            name = name.trim(),
            config = config,
            visibility = visibility,
            allowSpectators = allowSpectators,
            startsAt = startsAt
        ),
        creatorName = LocalUser.name,
        creatorAvatar = LocalUser.avatar
    )
}
