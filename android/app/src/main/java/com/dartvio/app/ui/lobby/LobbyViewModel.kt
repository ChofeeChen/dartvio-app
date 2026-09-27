package com.dartvio.app.ui.lobby

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dartvio.app.data.room.RoomRepository
import com.dartvio.app.data.room.RoomRepositoryProvider
import com.dartvio.app.domain.room.ArenaState
import com.dartvio.app.domain.room.JoinResult
import com.dartvio.app.domain.room.LocalUser
import com.dartvio.app.domain.room.OfficialArena
import com.dartvio.app.domain.room.Room
import com.dartvio.app.domain.room.RoomFilter
import com.dartvio.app.domain.room.RoomStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 大厅列表页状态（M6 F6.1）。
 *
 * 数据来自 [RoomRepository.publicRooms]，而具体是哪个仓库实现由
 * [RoomRepositoryProvider] 决定（单机 Mock / 联机）。本类不认识网络，
 * 也不认识「主机」这个概念 —— 联机与否对它只是换了一个数据源。
 *
 * @param injectedRepo 仅供测试注入；生产环境传 null，表示「跟随应用当前的仓库」。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LobbyViewModel(
    private val injectedRepo: RoomRepository? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val repo: RoomRepository get() = injectedRepo ?: RoomRepositoryProvider.current

    /** 声明顺序有意放在 [rooms] 之前：Kotlin 按声明顺序初始化属性，后声明的字段在 [rooms] 里读到的是 null。 */
    private val providerSources: Flow<RoomRepository> =
        injectedRepo?.let { flowOf(it) } ?: RoomRepositoryProvider.repositories

    /**
     * 大厅房间列表。
     *
     * 这里必须跟着数据源**换源**，而不是在构造时取一次：用户可能先进入大厅（此时是单机 Mock），
     * 再从大厅开启联机、然后返回大厅。若在构造时就固定了仓库，返回后仍会显示 Mock 房间，
     * 而且没有任何报错 —— 列表「看起来正常」但内容对不上，是最难排查的一类问题。
     */
    val rooms: StateFlow<List<Room>> = providerSources
        .flatMapLatest { it.publicRooms }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = RoomRepositoryProvider.current.publicRooms.value
        )

    /**
     * 官方擂台时段状态（时间锚点）。
     *
     * 与房间列表不同，它由**真实时钟**驱动而非 Mock：低供给阶段大厅最贵的一次流失是
     * 「打开是空的，于是不再打开」，而预约需要已有供给才能工作。
     * 时间锚点只承诺一个时段，不承诺一场对局，因此在 0 供给下也不会违约。
     */
    private var lastArenaState: ArenaState = OfficialArena.stateAt(nowMillis())

    var arenaState: ArenaState by mutableStateOf(lastArenaState)
        private set

    /**
     * 擂台「刚开始」的一次性横幅标记。
     *
     * 只在观察到 `Upcoming → Live` 的**跳变**时置位。若 App 打开时已在时段内，
     * [lastArenaState] 初始就是 `Live`，不会触发——否则每次进大厅都会重放同一条提示，
     * 时间锚点会从「召回」沦为「噪音」。
     *
     * 由 [dismissArenaBanner] 在 UI 计时结束后清除。
     */
    var arenaJustStarted by mutableStateOf(false)
        private set

    var filter by mutableStateOf(RoomFilter.ALL)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    /** 一次性提示文案（加入失败原因等）。 */
    var message by mutableStateOf<String?>(null)
        private set

    init {
        startArenaTicker()
    }

    /**
     * 随系统时钟推进擂台状态。
     *
     * 用 `isActive` 循环而非 `while (true)`：ViewModel 销毁时随 [viewModelScope] 一并取消。
     */
    private fun startArenaTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(ARENA_TICK_MILLIS)
                refreshArenaState()
            }
        }
    }

    /**
     * 刷新擂台状态并识别开赛跳变。
     *
     * 离开时段时立即收横幅，避免「隔夜横幅」：`END_HOUR` 之后到次日 `START_HOUR` 之间的
     * 一整段 `Upcoming` 都会被这条分支清掉。
     */
    private fun refreshArenaState() {
        val next = OfficialArena.stateAt(nowMillis())
        if (OfficialArena.isKickoff(lastArenaState, next)) {
            arenaJustStarted = true
        } else if (next !is ArenaState.Live) {
            arenaJustStarted = false
        }
        lastArenaState = next
        arenaState = next
    }

    /** 开赛横幅展示结束（由 UI 计时到点后回调）。 */
    fun dismissArenaBanner() {
        arenaJustStarted = false
    }

    /** 一键回到「全部」筛选（空结果时的可行动作）。 */
    fun showAllRooms() {
        filter = RoomFilter.ALL
    }

    fun updateFilter(value: RoomFilter) {
        filter = value
    }

    fun dismissMessage() {
        message = null
    }

    fun refresh() {
        if (isRefreshing) return
        viewModelScope.launch {
            isRefreshing = true
            runCatching { repo.refresh() }
            isRefreshing = false
        }
    }

    /** 列表卡片直接加入（房间一经创建就在大厅公开列出，不再有「输房间号」这一路）。 */
    fun joinFromList(room: Room): String? =
        when (val result = repo.joinRoom(room.id, LocalUser.name, LocalUser.avatar)) {
            is JoinResult.Success -> result.room.id
            else -> {
                message = result.message
                null
            }
        }

    /**
     * 能不能加入。
     *
     * [RoomStatus.ENDED] 单独挡在第一个条件：已结束的房是**记录**而不是房间，
     * 让它可加入就等于允许两台手机各补一条加入事件 —— 那份对局从此在两端重放出
     * 不同的比分与回合（2026-09-26 真机实测）。
     */
    fun canJoin(room: Room): Boolean =
        room.status == RoomStatus.WAITING && !room.isFull && !room.contains(LocalUser.ID)

    fun canSpectate(room: Room): Boolean =
        room.status == RoomStatus.PLAYING && room.allowSpectators

    companion object {
        /** 擂台状态刷新间隔：展示粒度为分钟，30 秒足够且开销可忽略。 */
        const val ARENA_TICK_MILLIS = 30_000L
    }
}
