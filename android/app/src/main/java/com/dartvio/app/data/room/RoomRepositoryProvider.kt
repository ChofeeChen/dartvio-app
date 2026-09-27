package com.dartvio.app.data.room

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 房间数据源的唯一切换点：单机 Mock ↔ 联机（主机 / 客人）。
 *
 * ## 为什么是一个可观察的对象，而不是一个 `var`
 *
 * 如果只暴露 `var current`，那么「切换数据源」对**已经活着的** ViewModel 是完全不可见的：
 * `LobbyViewModel` 在创建时读到旧值并一直用它，用户开启联机后回到大厅，
 * 看到的仍是本地 Mock 房间 —— 而且不报错，只是列表对不上。
 *
 * 因此这里提供 [repositories] 这条流，让大厅用 `flatMapLatest` 跟着换源；
 * [current] 只是给「调用那一刻取一次」的场景（各命令的发送方）用的便利访问。
 *
 * ## 谁负责切换
 *
 * [OnlineRoomLink]（在线数据源的装配点；局域网那套已按决策 D1 删除）。
 * 本对象不认识网络、不持有连接，只回答「现在该用哪个仓库」——
 * 这样 ViewModel 的测试可以完全绕开网络。
 */
object RoomRepositoryProvider {

    private val _repositories = MutableStateFlow<RoomRepository>(LocalRoomRepository)

    /** 当前启用中的仓库；切换时会发射新值。 */
    val repositories: StateFlow<RoomRepository> = _repositories.asStateFlow()

    /** 取当前仓库的快捷方式。 */
    val current: RoomRepository get() = _repositories.value

    fun use(repository: RoomRepository) {
        _repositories.value = repository
    }

    /** 退出联机，回到单机 Mock。 */
    fun resetToLocal() {
        _repositories.value = LocalRoomRepository
    }
}
