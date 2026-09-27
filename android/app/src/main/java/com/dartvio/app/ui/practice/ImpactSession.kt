package com.dartvio.app.ui.practice

import com.dartvio.app.domain.impact.ImpactWindow
import com.dartvio.app.domain.impact.IntentTarget
import com.dartvio.app.domain.impact.Prescription

/**
 * 轮前选「这一轮想改善什么」——它决定轮末**只给哪一条动作**（设计 §4.4 的闭环）。
 *
 * 三个选项不是礼貌性的分类：选「散布」时轮末不该再提系统偏移，否则一轮结束给出三条互不相干的
 * 建议，用户只会全部忽略。
 */
enum class ImpactGoal(val label: String) {
    OFFSET("系统偏移"),
    SCATTER("散布"),
    BOTH("两个都看")
}

/**
 * 落点诊断的跨页会话状态（选目标 → 逐镖点录 → 诊断报告）。
 *
 * ## 术语（V1.4 统一，**代码里不存在「组」这个单位**）
 *
 * - **`session`（本轮训练）**：一次练习，默认 [DEFAULT_SESSION_SIZE] 镖 —— 本对象描述的粒度；
 * - **`round`（回合）**：3 镖 —— 只出现在 [com.dartvio.app.domain.impact.ImpactRounds] 的指标里。
 *
 * 与 `SetupSession` / `MatchSession` 同一模式：页面之间只传「这一次练习是什么」，
 * 不传大数据流；真正的镖都在库里，靠 `sessionId` 取回。
 *
 * 进程被杀之后再回到报告页会拿到空会话 —— 报告页据此提示并退回，不假装有数据。
 */
object ImpactSession {

    /** 默认一轮多少镖（可选 12 / 20 / 30）。 */
    const val DEFAULT_SESSION_SIZE = 20

    /** 一轮镖数可选项：12 = 能出均值结论的最小量；30 = 能出散布结论。 */
    val SESSION_SIZE_CHOICES: List<Int> = listOf(12, 20, 30)

    /** 默认窗口档位（可随历史表现建议切到宽松档）。 */
    val DEFAULT_SPAN_MM: Double = ImpactWindow.STANDARD_SPAN_MM

    var sessionId: String = ""
        private set

    var target: IntentTarget = IntentTarget.triple(20)
        private set

    var spanMm: Double = DEFAULT_SPAN_MM
        private set

    var sessionSize: Int = DEFAULT_SESSION_SIZE
        private set

    var goal: ImpactGoal = ImpactGoal.BOTH
        private set

    /** 本轮量化目标（V1.4）；`null` = 没设，轮末只给描述性结论。 */
    var prescription: Prescription? = null
        private set

    /** 本轮打算刻意改动的自由文本；`''` = 未标注（只作对照标签，不作因果结论）。 */
    var interventionNote: String = ""
        private set

    /** P1 预留：`1` = 压力约束下完成。本版不实现压力玩法，恒为 `0`。 */
    var pressureMode: Int = 0
        private set

    val isActive: Boolean get() = sessionId.isNotBlank()

    /** 开一轮：会话 id 由仓库生成（UI 不碰 UUID）。 */
    fun begin(
        newSessionId: String,
        target: IntentTarget,
        spanMm: Double,
        sessionSize: Int,
        goal: ImpactGoal,
        prescription: Prescription? = null,
        interventionNote: String = "",
        pressureMode: Int = 0
    ) {
        this.sessionId = newSessionId
        this.target = target
        this.spanMm = spanMm
        this.sessionSize = sessionSize
        this.goal = goal
        this.prescription = prescription
        this.interventionNote = interventionNote
        this.pressureMode = pressureMode
    }

    fun clear() {
        sessionId = ""
    }
}
