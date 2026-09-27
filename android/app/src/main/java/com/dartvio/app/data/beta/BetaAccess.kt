package com.dartvio.app.data.beta

import android.content.SharedPreferences
import java.util.Calendar
import java.util.UUID

/**
 * Beta Demo 试用包的**邀请码激活闸门**（方案 A：纯本地校验）。
 *
 * 只做三件事：①输入的码是否在白名单里；②这台机器是否已激活；
 * ③试用期是否已过。**不做**的事同样要说清：
 * - 不能阻止同一个码在多台设备激活（无服务端，方案 A 的已知边界）；
 * - 不能防反编译（码表明文在包里）；
 * - 过期改系统时间可绕过（试用包不接受这种对抗强度）。
 *
 * 只在 beta 变体生效：入口由 `BuildConfig.BETA_DEMO` 控制（见 MainActivity），
 * debug 开发包完全无感。
 */
object BetaAccess {

    const val PREFS = "beta_access"
    private const val KEY_CODE = "activated_code"

    /*
     * 「已经做过选择」的标记（填过码，或明确点了「跳过，直接体验」）。
     *
     * 2026-09-20 起邀请码**不再是门禁**，只是渠道标签：没码的人照样能完整使用，
     * 填过的码只用于在后台回答「哪个渠道带来的用户最活跃」。
     * 所以这个标记的唯一作用，是决定首启那张介绍页还要不要出现 —— 选过就不再打扰。
     */
    private const val KEY_DECIDED = "decided"

    /** 详见 [installId]：一串随机的安装标识，用于「一码是否已在别处启用」的判断。 */
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_VERIFIED = "verified"
    private const val KEY_PENDING = "pending_verify"

    /**
     * 试用期截止：**本地时间** 2026-10-20 00:00:00（即 10-19 全天可用）。
     * 用 Calendar 构造而非写死 epoch 毫秒，时间语义一眼可读。
     */
    val EXPIRE_AT_MILLIS: Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 20, 0, 0, 0)
    }.timeInMillis

    /** 界面上直接展示的到期日文案：避免每个页面各写一遍日子（各写一遍迟早不一致）。 */
    const val EXPIRE_LABEL = "2026-10-19"

    /**
     * 输入归一化：去首尾空白 + 转大写。微信里复制粘贴常带空格或小写，
     * 归一化做在**校验之前**，避免朋友因为一个空格以为码是假的。
     */
    fun normalize(input: String): String = input.trim().uppercase()

    /** 试用期是否已过（含 [EXPIRE_AT_MILLIS] 时刻本身；now 由调用方注入，便于单测）。 */
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= EXPIRE_AT_MILLIS

    /**
     * 截止前多少天开始**每次启动提醒**。
     *
     * 7 天而不是当天：包装在别人手机里，到期当天才第一次告诉他，他此前没有任何机会
     * 安排更新；提前一周开始温和提醒，既不打扰也不突然。
     */
    const val WARN_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L

    /** 试用期状态。[EXPIRING] = 还能用，但快到期了。 */
    enum class Phase { OK, EXPIRING, EXPIRED }

    /**
     * 当前处于哪一段。
     *
     * 抽出来而不是让 UI 各自比较时间戳：到期涉及**三种界面表现**（正常 / 顶部提醒 /
     * 到期页），判定散在三处迟早不一致 —— 边界只留一份。
     */
    fun phase(nowMillis: Long): Phase = when {
        nowMillis >= EXPIRE_AT_MILLIS -> Phase.EXPIRED
        nowMillis >= EXPIRE_AT_MILLIS - WARN_WINDOW_MILLIS -> Phase.EXPIRING
        else -> Phase.OK
    }

    /** 还剩几天（向上取整：剩 3 小时说成「还剩 1 天」，说「0 天」会让人以为已经到期）。 */
    fun daysLeft(nowMillis: Long): Int {
        val day = 24L * 60L * 60L * 1000L
        return ((EXPIRE_AT_MILLIS - nowMillis + day - 1) / day).toInt().coerceAtLeast(0)
    }

    /**
     * 到期后**能不能继续进 App**。
     *
     * 为 false 时 UI 给出到期页，但**绝不删任何本地数据**：试用者打出来的、
     * 哪怕只有几局的数据也是他的资产；到期只是「这个包不能再用了」。
     * （所以这里**不要**去做清库、清 SharedPreferences 这类「自证清白」的动作。）
     */
    fun usable(nowMillis: Long): Boolean = !isExpired(nowMillis)

    /** 本机已激活的邀请码；未激活返回 null（用于「我的」弹窗里展示，便于分发对账）。 */
    fun activatedCode(prefs: SharedPreferences): String? = prefs.getString(KEY_CODE, null)

    /**
     * 尝试激活：码在白名单里则落盘并返回 true；否则返回 false 且**不写任何状态**
     * （失败的尝试绝不能留下半激活状态）。commit 而非 apply：激活是低频一次性
     * 写入，同步落盘换确定。
     */
    fun grant(prefs: SharedPreferences, rawInput: String): Boolean {
        val code = normalize(rawInput)
        if (code !in BetaInviteCodes.lookup) return false
        prefs.edit().putString(KEY_CODE, code).commit()
        return true
    }

    /** 是否已做过选择：填过码，或明确跳过了。用来决定首启介绍页是否还要出现。 */
    fun hasDecided(prefs: SharedPreferences): Boolean =
        activatedCode(prefs) != null || prefs.getBoolean(KEY_DECIDED, false)

    /**
     * 记下「选择跳过」。填码路径由 [grant] 落盘，这里只管跳过。
     * commit 而非 apply：与 [grant] 一致，同步落盘换「之后真不再弹」的确定性。
     */
    fun markSkipped(prefs: SharedPreferences) {
        prefs.edit().putBoolean(KEY_DECIDED, true).commit()
    }

    // ------------------------------------------------------- 后台校验所需的状态

    /**
     * 本机安装 ID（**不是**设备指纹）。
     *
     * 用途只有一个：让后端判断「这个码是不是已经被另一台设备用掉了」。
     * 它是一串安装时随机生成并落盘的 UUID —— 卸载重装就是新 ID，
     * 不读任何硬件标识，因此**不是**个人信息，也不该被拿去做别的事。
     */
    fun installId(prefs: SharedPreferences): String {
        val existing = prefs.getString(KEY_INSTALL_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, fresh).commit()
        return fresh
    }

    /** 这个码是**后台确认过的**（后台校验通过才会写）。 */
    fun isVerified(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_VERIFIED, false) && activatedCode(prefs) != null

    /**
     * 待补验：当时后台不通，靠本地白名单先放行了，联网后要再问一次后台。
     *
     * 为什么要记这个状态而不是「放过就算了」：本地白名单**无法**判断「码是否已在别处启用」，
     * 一码多机会把渠道统计弄脏；等有网时补验一次，既不断人家的网，也不把脏数据留着。
     */
    fun isPending(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_PENDING, false) && activatedCode(prefs) != null

    /**
     * 后台**确认通过**：记录码 + 清掉待补验标记。
     * 服务端已读过 `rank`，但**刻意不落盘** —— 见 `BetaInviteApi` 里关于「第几位」的说明。
     */
    fun grantVerified(prefs: SharedPreferences, code: String) {
        prefs.edit()
            .putString(KEY_CODE, normalize(code))
            .putBoolean(KEY_VERIFIED, true)
            .putBoolean(KEY_PENDING, false)
            .commit()
    }

    /** 标记待补验（离线放行）。 */ 
    fun markPending(prefs: SharedPreferences) {
        prefs.edit().putBoolean(KEY_PENDING, true).commit()
    }

    /** 补验被后台拒绝：撤掉本地放行状态，回到「没填过码」。豁达一点也不再补。 */
    fun revoke(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_CODE)
            .putBoolean(KEY_VERIFIED, false)
            .putBoolean(KEY_PENDING, false)
            .commit()
    }
}
