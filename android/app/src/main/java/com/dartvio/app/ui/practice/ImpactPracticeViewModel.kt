package com.dartvio.app.ui.practice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dartvio.app.DartVioApp
import com.dartvio.app.data.ImpactRepository
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.data.local.entity.DartHitEntity
import com.dartvio.app.data.profile.ProfileStore
import com.dartvio.app.domain.impact.ImpactCalculator
import com.dartvio.app.domain.impact.ImpactFrames
import com.dartvio.app.domain.impact.ImpactStats
import com.dartvio.app.domain.impact.ImpactWindow
import com.dartvio.app.domain.impact.IntentTarget
import com.dartvio.app.domain.impact.Prescription
import com.dartvio.app.domain.model.Dart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 逐镖录制页的状态。 */
data class ImpactPracticeUiState(
    val target: IntentTarget = IntentTarget.triple(20),
    val spanMm: Double = ImpactWindow.STANDARD_SPAN_MM,
    val sessionSize: Int = ImpactSession.DEFAULT_SESSION_SIZE,
    /** 本轮量化目标（V1.4，逐镖冗余落库）；`null` = 未设。 */
    val prescription: Prescription? = null,
    /** 本轮干预标签（V1.4，逐镖冗余落库）；`''` = 未标注。 */
    val interventionNote: String = "",
    /** 本轮已落库的镖（写入顺序 = 点录顺序）。 */
    val recorded: List<DartHitEntity> = emptyList(),
    /** 出框率过高时的切档建议（只提示，不自动切 —— 换了档位成绩就不可比）。 */
    val suggestWide: Boolean = false
) {
    val dartCount: Int get() = recorded.size

    val isFull: Boolean get() = dartCount >= sessionSize

    val last: DartHitEntity? get() = recorded.lastOrNull()

    /** 命中数：只看目标环是否真的被打中（出框镖一律不算）。 */
    val hitCount: Int get() = recorded.count { hit ->
        hit.outBand == 0 && target.isHit(Dart(hit.number, hit.multiplier))
    }

    val missCount: Int get() = recorded.count { it.outBand != 0 }
}

/**
 * 落点诊断逐镖录制。
 *
 * 每点一下就**立刻落库**（不是打完才写）：中途退出时已录的镖必须还在，
 * 「撤销」也要能真的从库里删掉最后一镖 —— 这正是 `sessionId` 这个列存在的理由。
 */
class ImpactPracticeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ImpactRepository(
        DartVioDatabase.get(app.applicationContext).dartHitDao()
    )

    private val profileId: String = when (val ctx = app.applicationContext) {
        is DartVioApp -> ctx.localProfileId
        else -> ProfileStore.ensure(ctx).profileId
    }

    private val _uiState = MutableStateFlow(ImpactPracticeUiState())
    val uiState: StateFlow<ImpactPracticeUiState> = _uiState.asStateFlow()

    /** 开一轮：从 [ImpactSession] 取参数，并把库里已有的镖读回来（重进页面不丢数据）。 */
    fun start() {
        val target = ImpactSession.target
        val spanMm = ImpactSession.spanMm
        _uiState.value = ImpactPracticeUiState(
            target = target,
            spanMm = spanMm,
            sessionSize = ImpactSession.sessionSize,
            prescription = ImpactSession.prescription,
            interventionNote = ImpactSession.interventionNote
        )
        if (!ImpactSession.isActive) return
        viewModelScope.launch {
            val recorded = repository.hitsOf(ImpactSession.sessionId)
            _uiState.update { it.copy(recorded = recorded, suggestWide = suggestWide(recorded)) }
        }
    }

    /**
     * 记录一点。
     *
     * @param outBand `0` = 窗内点；`1..4` = 屏幕方向的上 / 右 / 下 / 左条带。
     * @param outLevel `1` = 轻微出框、`2` = 远出框 / 靶外。
     */
    fun record(xMm: Double, yMm: Double, outBand: Int = 0, outLevel: Int = 0) {
        val sessionId = ImpactSession.sessionId
        if (sessionId.isBlank()) return
        val state = _uiState.value
        if (state.isFull) return
        viewModelScope.launch {
            repository.record(
                ImpactRepository.newHit(
                    sessionId = sessionId,
                    profileId = profileId,
                    target = state.target,
                    xMm = xMm,
                    yMm = yMm,
                    dartIndexInRound = state.dartCount % 3 + 1,
                    windowSpanMm = state.spanMm,
                    outBand = outBand,
                    outLevel = outLevel,
                    prescriptionMetric = state.prescription?.metric,
                    prescriptionTarget = state.prescription?.target ?: 0.0,
                    interventionNote = state.interventionNote,
                    pressureMode = ImpactSession.pressureMode
                )
            )
            val recorded = repository.hitsOf(sessionId)
            _uiState.update { it.copy(recorded = recorded, suggestWide = suggestWide(recorded)) }
        }
    }

    /** 撤销上一镖：真的从库里删掉它，而不是只从内存列表里去掉。 */
    fun undo() {
        val sessionId = ImpactSession.sessionId
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            repository.undoLast(sessionId)
            val recorded = repository.hitsOf(sessionId)
            _uiState.update { it.copy(recorded = recorded, suggestWide = suggestWide(recorded)) }
        }
    }

    /**
     * 切档建议：窗内样本够 [ImpactCalculator.MIN_FULL_N] 且 R95 超过 4 个环宽，
     * 或本轮出框率超过 20% —— 两种情况都说明「窗口把结果截断了」。
     *
     * 只提示、不自动切档：换档后历史成绩立刻不可比，这个决定必须由用户做。
     */
    private fun suggestWide(recorded: List<DartHitEntity>): Boolean {
        if (recorded.isEmpty()) return false
        val state = _uiState.value
        if (state.spanMm >= ImpactWindow.WIDE_SPAN_MM) return false
        val outRate = recorded.count { it.outBand != 0 }.toDouble() / recorded.size
        if (outRate > 0.2) return true
        val frames = recorded.filter { it.outBand == 0 }.map {
            ImpactFrames.of(state.target, it.xMm.toDouble(), it.yMm.toDouble())
        }
        val stats: ImpactStats = ImpactCalculator.of(frames) ?: return false
        return stats.n >= ImpactCalculator.MIN_FULL_N && stats.r95 > ImpactCalculator.R95_WIDE_MM
    }
}
