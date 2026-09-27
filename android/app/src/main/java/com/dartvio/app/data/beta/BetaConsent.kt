package com.dartvio.app.data.beta

import android.content.Context
import android.content.SharedPreferences

/**
 * 隐私政策的**知情同意**记录（`SharedPreferences("beta_consent")`）。
 *
 * ## 为什么单独存
 *
 * 「同意」这件事有独立于业务逻辑的生命周期：**政策改版就必须重新征得同意**。
 * 所以这里存的不是「一个布尔值」，而是「**用户回答的是哪一版政策** + 当时的答案」——
 * 只看布尔值的话，政策改了之后老用户永远不会被重新告知，等于替他签了字。
 *
 * ## 为什么「不同意」也要落盘
 *
 * 不落盘的话，每次冷启动都会再弹一次。反复在被拒绝的地方弹同一个框，
 * 最后会被当成 Bug 反馈回来 —— 而用户明明已经明确拒绝过了。
 *
 * ## 为什么不同意**不影响使用**
 *
 * 本 App 唯一会离开设备的数据是匿名使用统计（可选功能），它不是「提供服务所必需」的
 * 个人信息处理。因此按 PIPL 第十六条的口径：**不得以用户拒绝处理非必要个人信息为由
 * 拒绝提供服务** —— 不同意 = 关闭统计，App 照常用。这一点同时写在丑闻第一屏上。
 */
object BetaConsent {

    const val PREFS = "beta_consent"

    /**
     * **政策版本号**：政策内容发生**实质性**变化（新增收集项、变更用途、换第三方）时必须 +1，
     * 让存量用户下一次启动重新被告知。错别字修订、措辞澄清不必 +1。
     */
    const val POLICY_VERSION = 1

    private const val KEY_ANSWERED_VERSION = "answered_policy_version"
    private const val KEY_GRANTED = "granted"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 用户已回答过的政策版本；0 = 从没回答过。 */
    fun answeredVersion(prefs: SharedPreferences): Int = prefs.getInt(KEY_ANSWERED_VERSION, 0)

    /** 是否还需要弹首启隐私说明：政策没回答过，或回答的是旧版。 */
    fun needsConsent(prefs: SharedPreferences): Boolean = answeredVersion(prefs) != POLICY_VERSION

    /** 是否同意（含「必须对当前版本」这一层，避免老版本的同意被带过来）。 */
    fun granted(prefs: SharedPreferences): Boolean =
        answeredVersion(prefs) == POLICY_VERSION && prefs.getBoolean(KEY_GRANTED, false)

    /** 记下本次选择。commit 而非 apply：选择后马上就要按它决定是否上报。 */
    fun save(prefs: SharedPreferences, granted: Boolean) {
        prefs.edit()
            .putInt(KEY_ANSWERED_VERSION, POLICY_VERSION)
            .putBoolean(KEY_GRANTED, granted)
            .commit()
    }

    /**
     * 匿名统计**此刻**是否允许发送。
     *
     * 三重开关：①用户已对当前版本政策选择了同意；②包变体允许；③后端地址已配置。
     * 缺任何一个都不发 —— 宁可少收数据，也不能在没征得同意的情况下对外发包。
     */
    fun allowsTelemetry(context: Context): Boolean = granted(prefs(context))
}
