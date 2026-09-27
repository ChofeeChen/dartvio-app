package com.dartvio.app.data.profile

import android.content.Context
import android.content.SharedPreferences
import com.dartvio.app.domain.model.HumanAvatar
import com.dartvio.app.domain.profile.LocalProfile

/**
 * 本机玩家档案的落盘（`SharedPreferences("dartvio_profile")`）。
 *
 * 沿用 `PracticePrefsStore` / `AchievementStore` 的**依赖倒置**写法：
 * 核心实现只依赖 [SharedPreferences] 这个接口，生产入口 [ensure] 才接 [Context]。
 * 这样 `data/` 层的持久化语义（建档幂等、空白名兜底、未知头像回落）能在纯 JVM 单测里覆盖，
 * 无需引入 Robolectric（本项目测试依赖只有 JUnit）。
 *
 * 三条对外契约：
 * 1. [ensure] **幂等**：已有 profileId 时原样返回，绝不覆盖 —— 它已是历史数据的外键。
 * 2. 档案缺失时返回 `null`（[read]），由调用方决定是否建档；UI 不靠异常表达「还没建档」。
 * 3. [clear] 只清档案本身，**不碰对局历史**（历史里已落库的 profileId 会成为孤儿，
 *    这是「清除本地数据」的既有语义：删数据不删身份）。
 */
object ProfileStore {

    const val PREFS_NAME = "dartvio_profile"

    // ---- 存储键 ----
    // 注意：这些键一旦发布不得改名，否则等同于重置用户的档案 ID（历史战绩全部失联）。
    const val KEY_PROFILE_ID = "profile_id"
    const val KEY_NICKNAME = "profile_nickname"
    const val KEY_AVATAR = "profile_avatar"

    /** 取本模块的 prefs 句柄（框架按 name 缓存，重复调用拿到同一实例）。 */
    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 读取档案；**未建档或 ID 损坏时返回 null**。
     *
     * 昵称与头像做的是「宽容读取」：读不到就回落默认值，
     * 因为这两个字段只影响展示，不值得让整份档案失效。
     */
    fun read(prefs: SharedPreferences): LocalProfile? {
        val id = prefs.getString(KEY_PROFILE_ID, null).orEmpty().trim()
        if (id.isEmpty()) return null
        return LocalProfile(
            profileId = id,
            nickname = LocalProfile.normalizeNickname(prefs.getString(KEY_NICKNAME, null)),
            avatar = HumanAvatar.fromKey(prefs.getString(KEY_AVATAR, null).orEmpty()),
        )
    }

    /** 生产入口：确保已建档并返回当前档案。 */
    fun ensure(context: Context): LocalProfile = ensure(prefs(context))

    /**
     * 幂等建档：没有档案就生成一个并落盘，已有则原样返回。
     *
     * [newId] 可注入，便于单测断言「ID 只生成一次」；生产路径用 [LocalProfile.newProfileId]。
     */
    fun ensure(
        prefs: SharedPreferences,
        newId: () -> String = { LocalProfile.newProfileId() },
    ): LocalProfile {
        read(prefs)?.let { return it }
        val created = LocalProfile(
            profileId = newId(),
            nickname = LocalProfile.DEFAULT_NICKNAME,
        )
        prefs.edit()
            .putString(KEY_PROFILE_ID, created.profileId)
            .putString(KEY_NICKNAME, created.nickname)
            .putString(KEY_AVATAR, created.avatar.key)
            .apply()
        return created
    }

    /** 更新昵称（空白名兜底）。**未建档时不建档、返回 null**，避免隐式创建一份半成品档案。 */
    fun updateNickname(prefs: SharedPreferences, raw: String?): LocalProfile? {
        val current = read(prefs) ?: return null
        val updated = current.copy(nickname = LocalProfile.normalizeNickname(raw))
        prefs.edit().putString(KEY_NICKNAME, updated.nickname).apply()
        return updated
    }

    /** 更新头像。未建档时返回 null，理由同 [updateNickname]。 */
    fun updateAvatar(prefs: SharedPreferences, avatar: HumanAvatar): LocalProfile? {
        val current = read(prefs) ?: return null
        val updated = current.copy(avatar = avatar)
        prefs.edit().putString(KEY_AVATAR, updated.avatar.key).apply()
        return updated
    }

    /** 清空档案（仅供「重置身份」这类显式动作使用；正常路径不调用）。 */
    fun clear(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_PROFILE_ID)
            .remove(KEY_NICKNAME)
            .remove(KEY_AVATAR)
            .apply()
    }
}
