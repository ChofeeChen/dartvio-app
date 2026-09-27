package com.dartvio.app.ui.practice.versus

import com.dartvio.app.domain.versus.BattleConfig
import com.dartvio.app.domain.versus.VersusModeInfo
import com.dartvio.app.domain.versus.VersusModes
import com.dartvio.app.domain.versus.VersusRule

/**
 * 对抗练习的跨页会话（配置页 → 对局页 → 战报页）。
 *
 * 与 `SetupSession` / `ImpactSession` 同一条约定：**参战双方在本机输入、不需要跨会话持久化**，
 * 因此用内存单例传递，而不是把配置塞进路由参数（`BattleConfig` 有十来个字段，
 * 拼进 URL 既易错又难读，且中文玩家名需要转义）。
 *
 * ⚠️ **刻意不持久化**：进程被杀后回到对局页时 [isReady] 为 false，页面显示「配置已失效」并退出。
 * 已经落库的轮次不受影响（逐轮落库），只是这一局无法接着打 ——
 * 「续局」需要把整个 `BattleState` 快照也存下来，属于另一个需求，这里不做。
 *
 * 单例只在主线程读写（组合期与点击回调），因此不额外加同步。
 */
object VersusSession {

    private var modeKeyOrNull: String? = null
    private var configOrNull: BattleConfig? = null
    private var namesOrEmpty: List<String> = emptyList()
    private var matchIdOrNull: String? = null
    private var startedAtValue: Long = 0L

    val modeKey: String? get() = modeKeyOrNull
    val config: BattleConfig? get() = configOrNull
    val playerNames: List<String> get() = namesOrEmpty
    val matchId: String? get() = matchIdOrNull
    val startedAt: Long get() = startedAtValue

    val info: VersusModeInfo? get() = modeKeyOrNull?.let { VersusModes.infoOf(it) }

    val rule: VersusRule? get() = modeKeyOrNull?.let { VersusModes.ruleOf(it) }

    /** 配置齐备（能开一局新的）。 */
    val isReady: Boolean get() = configOrNull != null && rule != null

    /** 配置页 → 对局页：写入模式与配置，并清掉上一局的场次信息。 */
    fun prepare(modeKey: String, config: BattleConfig, playerNames: List<String>) {
        modeKeyOrNull = modeKey
        configOrNull = config
        namesOrEmpty = playerNames
        matchIdOrNull = null
        startedAtValue = 0L
    }

    /** 对局页开赛：登记本场 id 与起始时刻（战报页靠 id 回库读）。 */
    fun markStarted(matchId: String, startedAt: Long) {
        matchIdOrNull = matchId
        startedAtValue = startedAt
    }

    /**
     * 「再来一局」：**保留模式 / 配置 / 名字**，只把场次信息清掉。
     * 这正是需求里「再来一局（同配置）」想要的语义 —— 换的只是下一场，不是一套新设置。
     */
    fun restart() {
        matchIdOrNull = null
        startedAtValue = 0L
    }

    fun clear() {
        modeKeyOrNull = null
        configOrNull = null
        namesOrEmpty = emptyList()
        matchIdOrNull = null
        startedAtValue = 0L
    }
}
