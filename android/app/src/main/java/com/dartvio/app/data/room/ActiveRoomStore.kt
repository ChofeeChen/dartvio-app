package com.dartvio.app.data.room

import android.content.Context

/**
 * 「我现在在哪一间房」（PRD D10 / A6 一人一房）。
 *
 * ## 为什么是本机记录，而不是每次问后端
 *
 * 一人一房要挡的是**同一部手机**同时占两间房：房开了不散、又去开一间，
 * 于是大厅里躺着两间「他的房」，其中一间永远等不到人（而且占着第一屏）。
 * 这件事的判定只需要知道「我上次进的是哪间」，不需要全局视图 ——
 * 让服务端去查一人的全部房间，等于为一条界面规则引入一次网络往返和一个新接口。
 *
 * ## 为什么带 TTL
 *
 * 记录是在**进房那一刻**写的，而「我已经不在那间房了」这件事可能没走清除路径：
 * 杀进程、清后台、房间被别人解散、或者干脆是三个月前的事。
 * 一条没有失效期的记录会让用户被永久锁死在「你还有房间没打完」这句提示里，
 * 而这比多占一间房的代价大得多 —— 所以 3 小时后自动当作没有。
 *
 * 服务端加固（按成员 id 做唯一约束）是 Q4，属 P1；这里的定位是**客户端侧的尽力而为**。
 */
object ActiveRoomStore {

    private const val PREFS = "dartvio_active_room"
    private const val KEY_ROOM = "room_id"
    private const val KEY_AT = "saved_at"

    /** 记录的保鲜期：超过它就当作「没有未结束的房间」。 */
    private const val TTL_MS = 3 * 60 * 60 * 1000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 当前未结束的房间 id；没有则 null（过期记录顺手清掉，不留脏数据）。
     */
    fun read(context: Context): String? {
        val prefs = prefs(context)
        val roomId = prefs.getString(KEY_ROOM, null)?.takeIf { it.isNotBlank() } ?: return null
        val at = prefs.getLong(KEY_AT, 0L)
        if (at <= 0L || System.currentTimeMillis() - at > TTL_MS) {
            prefs.edit().clear().apply()
            return null
        }
        return roomId
    }

    /** 进入（创建 / 加入）某间房。 */
    fun save(context: Context, roomId: String) {
        prefs(context).edit()
            .putString(KEY_ROOM, roomId)
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * 离开 / 解散某间房。
     *
     * @param roomId 只在这个 id 与记录一致时才清：退房是异步的，
     *   若期间又进了另一间房，贸然清空会把新那间的占用也一起抹掉。
     */
    fun clear(context: Context, roomId: String? = null) {
        val prefs = prefs(context)
        if (roomId != null && prefs.getString(KEY_ROOM, null) != roomId) return
        prefs.edit().clear().apply()
    }
}
