package com.dartvio.app.ui.practice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dartvio.app.DartVioApp
import com.dartvio.app.data.ImpactRepository
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.data.local.dao.MissBandCount
import com.dartvio.app.data.local.entity.DartHitEntity
import com.dartvio.app.data.profile.ProfileStore
import com.dartvio.app.domain.impact.CevResult
import com.dartvio.app.domain.impact.FinishRate
import com.dartvio.app.domain.impact.ImpactCalculator
import com.dartvio.app.domain.impact.ImpactFingerprint
import com.dartvio.app.domain.impact.ImpactFingerprintCalculator
import com.dartvio.app.domain.impact.ImpactFrame
import com.dartvio.app.domain.impact.ImpactFrames
import com.dartvio.app.domain.impact.ImpactMissBand
import com.dartvio.app.domain.impact.ImpactPrescription
import com.dartvio.app.domain.impact.ImpactRound
import com.dartvio.app.domain.impact.ImpactRounds
import com.dartvio.app.domain.impact.ImpactStats
import com.dartvio.app.domain.impact.ImpactTrend
import com.dartvio.app.domain.impact.ImpactValue
import com.dartvio.app.domain.impact.ImpactWindow
import com.dartvio.app.domain.impact.IntentTarget
import com.dartvio.app.domain.impact.InterventionReport
import com.dartvio.app.domain.impact.OverlayResult
import com.dartvio.app.domain.impact.Prescription
import com.dartvio.app.domain.impact.PrescriptionMetric
import com.dartvio.app.domain.impact.RoundAdvice
import com.dartvio.app.domain.impact.RoundMetrics
import com.dartvio.app.domain.impact.SessionSummary
import com.dartvio.app.domain.impact.TrendDirection
import com.dartvio.app.domain.impact.TrendResult
import com.dartvio.app.domain.impact.VerdictResult
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.vision.BoardGeometry
import com.dartvio.app.domain.vision.Point2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 明细行（一条 = 一镖）；[highlight] 非空表示这镖所属回合被高亮（最紧 / 最散 / 三镖全中）。 */
data class ImpactDetailRow(
    val index: Int,
    val main: String,
    val secondary: String,
    val highlight: String? = null
)

/** 轮后诊断页状态。 */
data class ImpactReportUiState(
    val loaded: Boolean = false,
    /** 会话还在吗（进程被杀后回到本页会没有）。 */
    val hasSession: Boolean = false,
    val target: IntentTarget = IntentTarget.triple(20),
    val spanMm: Double = ImpactWindow.STANDARD_SPAN_MM,
    val goal: ImpactGoal = ImpactGoal.BOTH,
    val dartCount: Int = 0,
    val inWindow: Int = 0,
    val missCount: Int = 0,
    val hitCount: Int = 0,
    /** 本轮窗内点的误差分解（**不含出框**，出框镖不进 σ / R95 / KDE）。 */
    val sessionFrames: List<ImpactFrame> = emptyList(),
    /** 跨目标合并的窗内点（同一档位、各自相对自己的锚点）。 */
    val mergedFrames: List<ImpactFrame> = emptyList(),
    /** 本轮的绝对落点（含出框，出框点是 clamp + 外推出来的，只用于绝对视图）。 */
    val sessionPoints: List<Point2> = emptyList(),
    val sessionStats: ImpactStats? = null,
    val mergedStats: ImpactStats? = null,
    val headline: String? = null,
    val scatterAdvice: String? = null,
    val missRows: List<MissBandCount> = emptyList(),
    /** 四条边的出框计数（`outBand → 镖数`，含两档程度）。 */
    val bandTotals: Map<Int, Int> = emptyMap(),
    val neighborTop: List<Pair<String, Int>> = emptyList(),
    val trend: TrendResult? = null,
    val trendRounds: List<ImpactRound> = emptyList(),
    val detailRows: List<ImpactDetailRow> = emptyList(),
    val mixedSpans: List<Float> = emptyList(),
    /** 历史被 [ImpactRepository.HISTORY_LIMIT] 截断（趋势只用了最近这一段）。 */
    val historyLimited: Boolean = false,
    val suggestWide: Boolean = false,
    val nextAction: String = "",
    val crossMerged: Boolean = false,
    // ---- V1.4 ----
    /** 本轮处方；`null` = 没设（⑤ 卡整卡不渲染）。 */
    val prescription: Prescription? = null,
    /** 达成判定（现算，不落库）。 */
    val verdict: VerdictResult? = null,
    /** ⑥ 新旧叠加图数据口径。 */
    val overlay: OverlayResult? = null,
    /** ⑦ 目标收益对比（**全部落库点**含 miss 折算）。 */
    val cev: CevResult? = null,
    /** ⑦ 双倍区结镖命中率（结镖型目标用）。 */
    val finishRates: List<FinishRate> = emptyList(),
    /** ⑧ 投掷指纹（同档位跨目标）。 */
    val fingerprint: ImpactFingerprint? = null,
    /** ⑨ 干预对照。 */
    val intervention: InterventionReport? = null,
    /** 三镖回合指标（⑩ 高亮用）。 */
    val roundMetrics: RoundMetrics = RoundMetrics.EMPTY,
    val roundAdvice: RoundAdvice? = null
) {
    /** 当前展示的统计口径（受「跨目标合并」开关影响）。 */
    val stats: ImpactStats? get() = if (crossMerged) mergedStats else sessionStats

    /** 当前展示的点集。 */
    val frames: List<ImpactFrame> get() = if (crossMerged) mergedFrames else sessionFrames

    val confidence: ImpactCalculator.Confidence
        get() = ImpactCalculator.confidenceOf(stats?.n ?: 0)

    /** A-IMP-05：数字结论的闸门在 ViewModel 里，不许只靠 UI 隐藏。 */
    val statsReady: Boolean get() = confidence != ImpactCalculator.Confidence.TOO_FEW
}

/**
 * 落点诊断 · 轮后报告。
 *
 * 分工很清楚：**所有统计、门槛、趋势判定都在这里（或更下游的领域层）算完**，
 * UI 只负责把结论画出来。理由是可验收 —— 「n < 30 不出散布结论」这种规则必须在
 * 有单测的地方执行，而不是散在一堆 `if` 里。
 */
class ImpactReportViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ImpactRepository(
        DartVioDatabase.get(app.applicationContext).dartHitDao()
    )

    private val profileId: String = when (val ctx = app.applicationContext) {
        is DartVioApp -> ctx.localProfileId
        else -> ProfileStore.ensure(ctx).profileId
    }

    private val _uiState = MutableStateFlow(ImpactReportUiState())
    val uiState: StateFlow<ImpactReportUiState> = _uiState.asStateFlow()

    fun load() {
        if (_uiState.value.loaded) return
        val sessionId = ImpactSession.sessionId
        if (sessionId.isBlank()) {
            _uiState.value = ImpactReportUiState(loaded = true, hasSession = false)
            return
        }
        viewModelScope.launch { compute(sessionId) }
    }

    /** 「本目标 / 跨目标合并」开关：默认关，因为合并会把各目标的系统偏移平均掉。 */
    fun toggleCrossMerged() {
        _uiState.value = _uiState.value.copy(crossMerged = !_uiState.value.crossMerged)
    }

    /** 「下一轮（同目标）」：参数沿用（含处方与干预标签），换一个新的会话 id 重新开始录。 */
    fun beginNextRound(onReady: () -> Unit) {
        val state = _uiState.value
        ImpactSession.begin(
            newSessionId = repository.newSessionId(),
            target = state.target,
            spanMm = state.spanMm,
            sessionSize = ImpactSession.sessionSize,
            goal = state.goal,
            prescription = state.prescription,
            interventionNote = state.intervention?.rows?.lastOrNull()?.note.orEmpty()
        )
        _uiState.value = ImpactReportUiState()
        onReady()
    }

    private suspend fun compute(sessionId: String) {
        val target = ImpactSession.target
        val spanMm = ImpactSession.spanMm
        val sessionHits = repository.hitsOf(sessionId)

        val frames = sessionHits.filter { it.outBand == 0 }
            .map { ImpactFrames.of(target, it.xMm.toDouble(), it.yMm.toDouble()) }
        val stats = ImpactCalculator.of(frames)
        val points = sessionHits.map { Point2(it.xMm.toDouble(), it.yMm.toDouble()) }
        val inWindow = frames.size
        val missCount = sessionHits.size - inWindow

        // 趋势：同一意图 + 同一档位的历史，按 session 聚合成轮（当前这一轮也在库里，是最后一轮）。
        val historyHits =
            repository.historyFor(profileId, target, spanMm, ImpactRepository.HISTORY_LIMIT)
        val samples = historyHits.map { ImpactRepository.sampleOf(it, target) }
        val rounds = ImpactTrend.rounds(samples)
        val trend = ImpactTrend.compare(rounds)
        val overlay = ImpactTrend.overlay(rounds)

        // 跨目标合并：同档位、各自相对自己的锚点。
        val mergedFrames = repository
            .mergedHistory(profileId, spanMm, ImpactRepository.HISTORY_LIMIT)
            .mapNotNull { ImpactRepository.mergedSampleOf(it) }
            .map { it.frame!! }

        // 三镖回合（§4.3）：本轮自己的回合指标 + 解读。
        val roundMetrics = ImpactRounds.metricsOf(ImpactRepository.roundDartsOf(sessionHits, target))
        val roundAdvice = ImpactRounds.advice(stats, roundMetrics)

        // 处方判定（§4.8）：本轮目标 + 上一轮 R95 + 已连续达标轮数（全部现算）。
        val metrics = ImpactRepository.metricsOf(sessionHits, target)
        val prescription = prescriptionOf(sessionHits)
        val previousR95 = rounds.dropLast(1).lastOrNull()?.stats?.r95
        val sessions = ImpactRepository.sessionsOf(historyHits)
        val summaries = ImpactRepository.summariesOf(sessions, target)
        val streak = achievedStreakBefore(summaries, sessionId)
        val verdict = ImpactPrescription.judge(
            m = metrics,
            p = prescription,
            prevR95 = previousR95,
            achievedStreak = streak
        )

        // ⑦ 收益对比：**必须吃全部落库点**（含 miss 折算），与 σ/R95 的输入刻意分离（§4.6 红线）。
        val cevSamples = ImpactRepository.cevSamplesOf(sessionHits)
        val cev = ImpactValue.compare(cevSamples, target)
        val finishRates = ImpactValue.finishRates(cevSamples, target)

        // ⑧ 指纹：同档位、跨目标（占比是尺度无关的）。
        val fingerprint = ImpactFingerprintCalculator.of(
            repository.fingerprintSamples(profileId, spanMm)
        )

        // ⑨ 干预对照：按 note 聚合，措辞层保证中性。
        val intervention = ImpactPrescription.interventionCompare(summaries)

        val detail = sessionHits.mapIndexed { i, hit ->
            detailRow(i + 1, hit, target, roundMetrics, i / 3 + 1)
        }
        val bandTotals = mutableMapOf<Int, Int>()
        sessionHits.filter { it.outBand != 0 }.forEach { hit ->
            bandTotals[hit.outBand] = (bandTotals[hit.outBand] ?: 0) + 1
        }
        val missRows = repository.missBands(sessionId)

        _uiState.value = ImpactReportUiState(
            loaded = true,
            hasSession = true,
            target = target,
            spanMm = spanMm,
            goal = ImpactSession.goal,
            dartCount = sessionHits.size,
            inWindow = inWindow,
            missCount = missCount,
            hitCount = sessionHits.count {
                it.outBand == 0 && target.isHit(Dart(it.number, it.multiplier))
            },
            sessionFrames = frames,
            mergedFrames = mergedFrames,
            sessionPoints = points,
            sessionStats = stats,
            mergedStats = ImpactCalculator.of(mergedFrames),
            headline = stats?.let { ImpactCalculator.headline(it) },
            scatterAdvice = stats?.let { ImpactCalculator.scatterAdvice(it) },
            missRows = missRows,
            bandTotals = bandTotals,
            neighborTop = neighborTop(sessionHits, target),
            trend = trend,
            trendRounds = rounds,
            detailRows = detail,
            mixedSpans = repository.windowSpansOf(profileId, target),
            historyLimited = historyHits.size >= ImpactRepository.HISTORY_LIMIT,
            suggestWide = sessionHits.isNotEmpty() &&
                missCount.toDouble() / sessionHits.size > 0.2 &&
                spanMm < ImpactWindow.WIDE_SPAN_MM,
            nextAction = nextAction(ImpactSession.goal, stats, trend),
            crossMerged = false,
            prescription = prescription,
            verdict = verdict,
            overlay = overlay,
            cev = cev,
            finishRates = finishRates,
            fingerprint = fingerprint,
            intervention = intervention,
            roundMetrics = roundMetrics,
            roundAdvice = roundAdvice
        )
    }

    /** 本轮开局时确定的目标（冗余存在每一镖上）；口径为空串 ⇒ 未设。 */
    private fun prescriptionOf(hits: List<DartHitEntity>): Prescription? {
        val head = hits.firstOrNull() ?: return null
        val metric = PrescriptionMetric.fromKey(head.prescriptionMetric) ?: return null
        return Prescription(metric = metric, target = head.prescriptionTarget)
    }

    /** 本轮之前已连续达标的轮数（不含本轮）。 */
    private fun achievedStreakBefore(
        summaries: List<SessionSummary>,
        sessionId: String
    ): Int {
        val index = summaries.indexOfFirst { it.sessionId == sessionId }
        if (index <= 0) return 0
        var streak = 0
        var cursor = index - 1
        while (cursor >= 0 && summaries[cursor].achieved == true) {
            streak++
            cursor--
        }
        return streak
    }

    /** 未命中镖落在哪些区（Top3）：这就是「差在哪」。 */
    private fun neighborTop(hits: List<DartHitEntity>, target: IntentTarget): List<Pair<String, Int>> {
        val counts = LinkedHashMap<String, Int>()
        hits.filter { it.outBand == 0 }
            .map { BoardGeometry.dartAt(it.xMm.toDouble(), it.yMm.toDouble()) }
            .filter { !target.isHit(it) }
            .forEach { dart -> counts[dart.label()] = (counts[dart.label()] ?: 0) + 1 }
        return counts.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key to it.value }
    }

    /** 轮后只给**一个**动作，且必须与轮前选的「想改善什么」对齐。 */
    private fun nextAction(goal: ImpactGoal, stats: ImpactStats?, trend: TrendResult): String = when {
        trend.direction == TrendDirection.IMPROVED -> "把窗口收窄一档再录一轮，验证进步不是窗口给的。"
        trend.direction == TrendDirection.WORSENED -> "条件回到上一轮（站位 / 节奏 / 器材）再录，这一轮别改瞄点。"
        stats == null -> "多用窗内区域记录，出框镖不进散布统计。"
        goal == ImpactGoal.OFFSET ->
            "固定站位与出手点，只盯「偏差方向」这个常数，别去调瞄准动作的幅度。"
        goal == ImpactGoal.SCATTER ->
            "只练节奏与随挥的一致性，瞄点这一轮不要改。"
        ImpactCalculator.biasLevelOf(stats.bias) == ImpactCalculator.BiasLevel.SEVERE ||
            ImpactCalculator.biasLevelOf(stats.bias) == ImpactCalculator.BiasLevel.OBVIOUS ->
            "先吃掉系统偏移（固定站位 / 出手点），偏移降到 1 个环宽内再看散布。"
        stats.r95 > ImpactCalculator.R95_WIDE_MM -> "散布偏大：练节奏与随挥，不要改瞄点。"
        else -> "条件别动，再录一轮；单轮结论随时会被下一轮推翻。"
    }

    private fun detailRow(
        index: Int,
        hit: DartHitEntity,
        target: IntentTarget,
        roundMetrics: RoundMetrics,
        roundOrdinal: Int
    ): ImpactDetailRow {
        val key = "${hit.sessionId}#$roundOrdinal"
        val highlight = when {
            roundMetrics.tightRoundKey == key -> "最紧回合"
            roundMetrics.looseRoundKey == key -> "最散回合"
            roundMetrics.rounds.any { it.sessionId == hit.sessionId && it.roundOrdinal == roundOrdinal && it.allHit } ->
                "三镖全中"
            else -> null
        }
        if (hit.outBand != 0) {
            val semantic = ImpactMissBand.tangentLabel(target, hit.outBand)
                .ifBlank { ImpactFrames.semanticOf(target, hit.outBand).label }
            return ImpactDetailRow(
                index = index,
                main = "出框（$semantic）",
                secondary = if (hit.outLevel == 2) "远出框 / 靶外" else "轻微出框",
                highlight = highlight
            )
        }
        val frame = ImpactFrames.of(target, hit.xMm.toDouble(), hit.yMm.toDouble())
        val (clockwise, counterClockwise) = target.neighbors()
        val side = if (frame.eTan >= 0.0) clockwise else counterClockwise
        val dart = Dart(hit.number, hit.multiplier)
        return ImpactDetailRow(
            index = index,
            main = if (target.isHit(dart)) "${dart.label()} ✓" else dart.label(),
            secondary = "${if (frame.eRad >= 0) "偏外" else "偏内"} ${mm(abs(frame.eRad))} / " +
                "偏 $side 侧 ${mm(abs(frame.eTan))}",
            highlight = highlight
        )
    }

    private fun mm(value: Double): String = ((value * 10).toInt() / 10.0).toString()
}
