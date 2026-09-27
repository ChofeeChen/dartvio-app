package com.dartvio.app.data.room

import android.content.Context

/**
 * 在线（跨网络）数据源的持有者。
 *
 * ## 为什么必须是单例
 *
 * [OnlineRoomRepository] 在自己的内存里保存着「已收到的事件」。
 * 每次想用它就 new 一个，等于每次都从零开始拉全量事件：
 * 房间号、成员、比分都会**在导航过程中丢一次**，表现为「进对局页时比分闪回 501」。
 *
 * ## 为什么不由 `RoomRepositoryProvider` 直接持有
 *
 * Provider 只回答「现在用哪个源」（并且可以被切回本地 Mock），不该替在线源承担生命周期。
 * 在线源需要 [Context]（读本机身份），而这个对象负责把它藏在这里 —— 界面因此不需要到处传 Context。
 */
object OnlineRoomLink {

    @Volatile
    private var instance: OnlineRoomRepository? = null

    private fun repository(context: Context): OnlineRoomRepository =
        instance ?: synchronized(this) {
            instance ?: OnlineRoomRepository(context.applicationContext).also { instance = it }
        }

    /** 把「当前房间数据源」切到在线，并返回它（供调用方立刻发命令）。 */
    fun enable(context: Context): OnlineRoomRepository {
        // 昵称 / 头像持久化在档案里，而房间页与大屏都在读 LocalUser。
        // 在「切换数据源」这个唯一入口同步一次，避免「房间页显示旧昵称」这种不报错的错。
        PlayerProfileStore.hydrateLocalUser(context)
        val repository = repository(context)
        RoomRepositoryProvider.use(repository)
        return repository
    }

    /** 实时连接状态，供界面显示「连上了 / 重连中 / 未配置」。 */
    fun status(context: Context): kotlinx.coroutines.flow.StateFlow<com.dartvio.app.net.online.StreamStatus> =
        repository(context).status
}
