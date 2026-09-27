package com.dartvio.app.ui.practice

import com.dartvio.app.domain.practice.RushDifficulty

/**
 * 会话规模：10 题挑战（做完出报告）/ 自由练习（用户主动结束才出报告）。
 */
enum class RushSessionKind(val label: String, val desc: String, val problemCount: Int?) {
    TEN("10 题挑战", "固定 10 题，做完自动生成会话报告", 10),
    FREE("自由练习", "连续出题，随时结束并生成报告", null),
}

/**
 * 极速结镖的跨页会话（入口页 → 训练页 → 报告页）。
 *
 * 与 [com.dartvio.app.ui.practice.versus.VersusSession] 同一条约定：
 * 一次训练只在本机进行、不需要跨进程恢复，因此用内存单例传参，
 * 而不是把「会话规模 + 难度」塞进路由参数。
 *
 * ⚠️ **刻意不持久化**：进程被杀后回到训练页时 [isReady] 为 false，页面提示并退回入口页。
 * 已经落库的题目不受影响（逐题落库），只是这一次训练无法接着做。
 *
 * 单例只在主线程读写（组合期与点击回调），因此不额外加同步。
 */
object CheckoutRushSessionStore {

    private var sessionIdOrNull: String? = null
    private var kindValue: RushSessionKind = RushSessionKind.TEN
    private var difficultyValue: RushDifficulty = RushDifficulty.MIXED

    val sessionId: String? get() = sessionIdOrNull
    val kind: RushSessionKind get() = kindValue
    val difficulty: RushDifficulty get() = difficultyValue

    /** 入口页 → 训练页：开一个新会话。 */
    fun start(kind: RushSessionKind, difficulty: RushDifficulty, sessionId: String) {
        kindValue = kind
        difficultyValue = difficulty
        sessionIdOrNull = sessionId
    }

    val isReady: Boolean get() = sessionIdOrNull != null

    /** 报告页退出 / 训练页退出：清掉，避免下次进来误用上一次的会话。 */
    fun clear() {
        sessionIdOrNull = null
    }
}
