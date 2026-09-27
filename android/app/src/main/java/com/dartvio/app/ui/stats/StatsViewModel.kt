package com.dartvio.app.ui.stats

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dartvio.app.DartVioApp
import com.dartvio.app.data.MatchRepository
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.data.stats.PracticeSummaryReader
import com.dartvio.app.domain.stats.PracticeSummary
import com.dartvio.app.domain.stats.StatsCalculator
import com.dartvio.app.domain.stats.StatsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 数据页的**一级分类**。
 *
 * 为什么按「在哪打的」分，而不是按对手强弱分：这两类的**可比性**根本不同 ——
 * 本地（含 AI）那组没有裁判、可以反复重开，联机那组有对手、有弃权判负。
 * 把它们混在一张表里算 PPR，等于宣布「反复重开刷出来的数与真人对局同价」，
 * 那之后榜单上每一个数都不必再信。
 */
enum class StatsScope(val label: String) {
    /**
     * 本地对局：X01 / Cricket **打完整场**的对局（单机 / AI）。
     *
     * 命名从「本地训练」改回「本地对局」：练习数据根本不写这张表（见 [StatsUiState.practice]），
     * 挂着「训练」两个字，打了一晚上练习的人打开这里只会看到一片空白（2026-09-27 反馈）。
     */
    LOCAL("本地对局"),

    /**
     * 练习：训练中心各项目自己落库的数据汇总。
     *
     * 单独一个口径而不是塞进 LOCAL：练习没有 PPR 这类比分指标，
     * 硬并进去等于给「最近练得多」编一个数。
     */
    PRACTICE("练习"),

    /** 比赛大厅：联机对局。 */
    ARENA("比赛大厅"),
}

data class StatsUiState(
    val loading: Boolean = true,
    /** 本地训练口径的统计（排除联机局）。 */
    val snapshot: StatsSnapshot = StatsSnapshot(),
    /**
     * 比赛大厅口径的统计（只要联机局）。
     *
     * 与 [snapshot] **各自独立计算**，不是「总数减去本地」：
     * PPR、胜率这类比值指标不能靠减法得到，减出来的是一个没有意义的数。
     */
    val arenaSnapshot: StatsSnapshot = StatsSnapshot(),
    /**
     * **历史口径**的对局列表（含联机局）—— 打完的联机对局要能在历史里回看。
     *
     * 它与 [snapshot] 的口径**刻意不同**：后者只吃单机局（见下方 combine）。
     * 「看得到」和「算得进」是两件事，把它们合成一个列表，等于逼二者共用一个开关。
     */
    val matches: List<MatchWithPlayers> = emptyList(),
    /** 联机局子集：一级分类切到 [StatsScope.ARENA] 时，历史列表只显示这些。 */
    val arenaMatches: List<MatchWithPlayers> = emptyList(),
    /** 练习口径汇总（切到 [StatsScope.PRACTICE] 时才读，见 [StatsViewModel.refreshPractice]）。 */
    val practice: PracticeSummary = PracticeSummary(),
) {
    val hasAnyMatch: Boolean get() = matches.isNotEmpty()
}

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext: Context = app.applicationContext

    private val repository: MatchRepository = when (val ctx = app.applicationContext) {
        is DartVioApp -> ctx.matchRepository
        else -> MatchRepository(DartVioDatabase.get(ctx).matchRecordDao())
    }

    /**
     * 两个口径同时取（M5 T10）：
     *
     * - `observeStatsMatches()` → [StatsUiState.snapshot]：**战绩口径**，排除联机局。
     *   PPR / 胜率 / 最高收镖的分母里混进一场无裁判的对局，整个指标就失去意义 ——
     *   而联机的弃权判负还会让「拔网线」变成一种提高数据的手段。
     * - `observeMatches()` → [StatsUiState.matches]：**历史口径**，含联机局。
     *   打完的联机对局是用户资产，必须能回看，也因此列表上要标出它的来源。
     * - `observeArenaMatches()` → [StatsUiState.arenaSnapshot]：**大厅口径**，只要联机局。
     *   大厅里给陌生人看的实力预期，必须来自他在大厅里打出来的那些局。
     */
    /**
     * 练习汇总：**进分类时才读**（见 [refreshPractice]）。
     *
     * 它不参与上面的 `combine`，因为它的三个数据源（SP / 结镖表 / versus 表）都不是
     * Flow —— 硬凑成一个流就要么轮询、要么假造一个会推送的包装。
     * 而练习数据的更新时机是「练完一组」，用户回到这一页时重读一次正好够用。
     */
    private val _practice = MutableStateFlow(PracticeSummary())

    val uiState: StateFlow<StatsUiState> = combine(
        repository.observeStatsMatches(),
        repository.observeMatches(),
        repository.observeArenaMatches(),
        _practice,
    ) { statsMatches, history, arena, practice ->
        StatsUiState(
            loading = false,
            snapshot = StatsCalculator.compute(statsMatches),
            arenaSnapshot = StatsCalculator.compute(arena),
            matches = history,
            arenaMatches = arena,
            practice = practice,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(),
        )

    /**
     * 重读练习数据。
     *
     * 用 `runCatching` 兜住而不是让它冒泡：练习汇总读不出来最多是这一栏空着，
     * 不该把整个数据页打崩 —— 而对局统计那条流才是这一页的主线。
     */
    fun refreshPractice() {
        viewModelScope.launch {
            val summary = runCatching {
                PracticeSummaryReader.read(appContext, DartVioDatabase.get(appContext))
            }.getOrDefault(PracticeSummary())
            _practice.value = summary
        }
    }
}
