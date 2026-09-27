package com.dartvio.app.data.room

import android.content.Context
import com.dartvio.app.data.profile.ProfileStore
import com.dartvio.app.domain.model.HumanAvatar
import com.dartvio.app.domain.room.LocalUser
import com.dartvio.app.domain.room.NicknameRules

/**
 * 本机玩家的档案：昵称 + 头像（**持久化**）。
 *
 * ## 为什么必须落盘
 *
 * `LocalUser` 是内存 object（进程一杀就没了），而昵称要在大厅里被陌生人反复看到 ——
 * 每次进大厅重新填一次，等于给「找对手」这条主路径上多加一道门。
 *
 * ## 为什么身份不放在这里
 *
 * 身份是 [OnlineIdentity]：它是房间成员 id，上面挂着席位、得分与准备状态，
 * 每次进房间换一个就会变成「陌生人要求坐回原位」。昵称改了不影响身份 —— 这正是
 * 「显示名与身份分离」的意义：A 可以随时改叫 ABC，历史数据仍然归 A。
 */
object PlayerProfileStore {

    private const val PREFS = "dartvio_player_profile"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 身份 ID（设备级持久，与昵称解耦）。 */
    fun playerId(context: Context): String = OnlineIdentity.read(context)

    /**
     * 昵称 —— **只有一份真源**：「我的」页里编辑的那份（[ProfileStore]）。
     *
     * 这里曾经自己存一份（`KEY_NICKNAME`），于是同一个人在两个地方有两个名字：
     * 在「我的」里改完之后，房间卡 / 等候页上显示的是房间那份旧名字 ——
     * 表现为「A 手机上自己叫『玩家xxxx』，别人看到的他却是『茄子』」（2026-09-27 真机反馈）。
     * 两份存储**各自都能自圆其说**，因此这类 bug 不会报错、只会安静地错。
     *
     * 修法是删掉这份副本而不是加同步：同步要写两遍、且任何一处漏写都会回到旧状态。
     * 昵称的**写入口**只有「我的」页一处，房间这一侧只读。
     */
    fun nickname(context: Context): String =
        ProfileStore.read(ProfileStore.prefs(context))?.nickname
            ?.takeIf { it.isNotBlank() }
            ?: NicknameRules.FALLBACK

    /**
     * 头像：**同样只有一份真源**（与 [nickname] 同一套取舍）。
     *
     * 头像**不作重名消歧手段**（预置集合只有 4 个，区分度不够）——消歧交给短码。
     */
    fun avatar(context: Context): HumanAvatar =
        ProfileStore.read(ProfileStore.prefs(context))?.avatar
            ?: HumanAvatar.HUMAN_1

    /** 大厅展示名：`昵称·短码`。 */
    fun displayName(context: Context): String =
        NicknameRules.display(nickname(context), playerId(context))

    /**
     * 把档案灌进 [LocalUser]。
     *
     * 房间页与大厅都在读 `LocalUser.name`，而它是可变的全局状态；与其在每个界面各读一次
     * SP，不如在**数据源切换**这个唯一入口同步一次（`OnlineRoomLink.enable`）——
     * 漏同步的表现是「房间页显示旧昵称」，且不报错。
     */
    fun hydrateLocalUser(context: Context) {
        LocalUser.name = nickname(context)
        LocalUser.avatar = avatar(context).key
    }
}
