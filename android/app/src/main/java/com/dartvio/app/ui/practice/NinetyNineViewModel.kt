package com.dartvio.app.ui.practice

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dartvio.app.data.achievement.PracticeSessionTrigger
import com.dartvio.app.domain.achievement.AchievementProgress
import com.dartvio.app.domain.practice.NinetyNineRules
import com.dartvio.app.domain.practice.NinetyNineState
import com.dartvio.app.domain.practice.SectorHit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 99 Darts 本地统计存储（后续接入云端后替换为远程同步）。 */
object NinetyNineStatsStore {
    const val PREFS_NAME = "dartvio_practice"

    fun bestKey(sector: Int) = "ninety_nine_best_$sector"
    fun sessionsKey(sector: Int) = "ninety_nine_sessions_$sector"

    fun best(context: Context, sector: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(bestKey(sector.coerceIn(1, 20)), 0)

    fun sessions(context: Context, sector: Int): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(sessionsKey(sector.coerceIn(1, 20)), 0)
}

/**
 * 99 Darts 练习 ViewModel。
 *
 * - 进入练习前先选择 1-20 中的一个分区（sector）。
 * - 33 轮 × 3 镖 = 99 镖，命中得分累加。
 * - 结束时保存该分区最佳成绩与练习次数。
 */
class NinetyNineViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(NinetyNineStatsStore.PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(NinetyNineState(sector = 20))
    val state = _state.asStateFlow()

    private val _bestPoints = MutableStateFlow(0)
    val bestPoints = _bestPoints.asStateFlow()

    private val _lastIsNewBest = MutableStateFlow(false)
    val lastIsNewBest = _lastIsNewBest.asStateFlow()

    /** 练习打卡与成就重算编排（第③期 ③A），协程随 ViewModel 生命周期自动取消。 */
    private val practiceTrigger = PracticeSessionTrigger(app.applicationContext, viewModelScope)

    /** 本次练习新解锁的成就，供结果弹窗展示解锁卡片（决策④）。 */
    val newlyUnlocked: StateFlow<List<AchievementProgress>> = practiceTrigger.newlyUnlocked

    val sector: Int get() = _state.value.sector

    fun start(sector: Int) {
        val s = sector.coerceIn(1, 20)
        if (_state.value.sector == s && _state.value.throws.isNotEmpty()) return
        _state.value = NinetyNineRules.newSession(s)
        _bestPoints.value = prefs.getInt(NinetyNineStatsStore.bestKey(s), 0)
        _lastIsNewBest.value = false
        practiceTrigger.onNewSession()
    }

    fun record(hit: SectorHit) {
        val before = _state.value
        if (before.finished) return
        val after = NinetyNineRules.record(before, hit)
        _state.value = after
        if (after.finished) saveResult(after)
    }

    fun undo() {
        _state.value = NinetyNineRules.undo(_state.value)
    }

    fun restart() {
        _state.value = NinetyNineRules.newSession(_state.value.sector)
        _lastIsNewBest.value = false
        practiceTrigger.onNewSession()
    }

    private fun saveResult(state: NinetyNineState) {
        val sector = state.sector
        val previousBest = prefs.getInt(NinetyNineStatsStore.bestKey(sector), 0)
        val isNewBest = state.totalPoints > previousBest
        _lastIsNewBest.value = isNewBest

        val editor = prefs.edit()
        editor.putInt(
            NinetyNineStatsStore.sessionsKey(sector),
            prefs.getInt(NinetyNineStatsStore.sessionsKey(sector), 0) + 1
        )
        if (isNewBest) {
            editor.putInt(NinetyNineStatsStore.bestKey(sector), state.totalPoints)
            _bestPoints.value = state.totalPoints
        }
        editor.apply()
        // 练习结束 → 打卡 + 成就重算；saveResult 只在打满 99 镖时被调用，
        // 因此这里必然是「完整打完一次 99 Darts」，直接推进「99 镖全勤」。
        practiceTrigger.onSessionFinished(ninetyNineCompleted = true)
    }
}
