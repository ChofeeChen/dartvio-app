package com.dartvio.app.data.room

import android.content.Context
import java.util.UUID

/**
 * 本机在**线上**的身份。
 *
 * ## 为什么不能用 `LocalUser.ID`
 *
 * `LocalUser.ID` 是常量 `"local_me"`，两台手机编译进的是同一个值。
 * 直接拿它当线上成员 id，A 与 B 会撞成同一个人：房主判定、「（你）」标记同时错乱，
 * 而且不崩溃，只是安静地显示错（见 `RoomIdentity` 的说明）。
 *
 * ## 为什么是「生成一次然后一直用」
 *
 * 它是房间的成员 id，上面挂着席位、得分与准备状态。每次进房间换一个，
 * 重连就变成「一个陌生人要求坐回原来的位置」。
 */
object OnlineIdentity {

    private const val PREFS = "dartvio_online_identity"
    private const val KEY_ID = "member_id"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 读取（没有就现生成一个并落盘）。 */
    fun read(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val fresh = "dv_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        prefs.edit().putString(KEY_ID, fresh).apply()
        return fresh
    }
}
