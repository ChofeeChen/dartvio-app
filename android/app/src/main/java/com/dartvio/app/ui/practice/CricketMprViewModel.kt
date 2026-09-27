package com.dartvio.app.ui.practice

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dartvio.app.data.achievement.PracticeSessionTrigger
import com.dartvio.app.domain.achievement.AchievementProgress
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.practice.CricketMprRules
import com.dartvio.app.domain.practice.CricketMprState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cricket MPR 挑战本地统计存储（后续接入云端后替换为远程同步）。 */
object CricketMprStatsStore {
    const val PREFS_NAME = "dartvio_practice"

    /** MPR × 100 存为 Int，避免 SharedPreferences 无 Float 精度问题。 */
    const val BEST_KEY = "cricket_mpr_best_x100"
    const val SESSIONS_KEY = "cricket_mpr_sessions"

    fun best(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(BEST_KEY, 0) / 100f

    fun sessions(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(SESSIONS_KEY, 0)
}

/**
 * Cricket MPR 挑战 ViewModel。
 *
 * - 10 轮 × 3 镖，统计 15-20 与 Bull 的标记数，分区不封顶。
 * - 结束时保存历史最佳 MPR（Float 以 ×100 存 Int）与练习次数。
 */
class CricketMprViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(CricketMprStatsStore.PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(CricketMprState())
    val state = _state.asStateFlow()

    private val _bestMpr = MutableStateFlow(0f)
    val bestMpr = _bestMpr.asStateFlow()

    private val _isNewBest = MutableStateFlow(false)
    val isNewBest = _isNewBest.asStateFlow()

    /** 练习打卡与成就重算编排（第③期 ③A），协程随 ViewModel 生命周期自动取消。 */
    private val practiceTrigger = PracticeSessionTrigger(app.applicationContext, viewModelScope)

    /** 本次练习新解锁的成就，供结果页展示解锁卡片（决策④）。 */
    val newlyUnlocked: StateFlow<List<AchievementProgress>> = practiceTrigger.newlyUnlocked

    init {
        _bestMpr.value = CricketMprStatsStore.best(app)
    }

    fun record(dart: Dart) {
        val before = _state.value
        if (before.finished) return
        val after = CricketMprRules.record(before, dart)
        _state.value = after
        if (after.finished) saveResult(after)
    }

    fun undo() {
        _state.value = CricketMprRules.undo(_state.value)
    }

    fun reset() {
        _state.value = CricketMprRules.newSession()
        _isNewBest.value = false
        practiceTrigger.onNewSession()
    }

    fun finishEarly() {
        val after = CricketMprRules.finishEarly(_state.value)
        _state.value = after
        if (after.finished) saveResult(after)
    }

    private fun saveResult(state: CricketMprState) {
        val mpr = state.mpr
        val previousBest = prefs.getInt(CricketMprStatsStore.BEST_KEY, 0) / 100f
        val isNewBest = mpr > previousBest
        _isNewBest.value = isNewBest

        val editor = prefs.edit()
        editor.putInt(
            CricketMprStatsStore.SESSIONS_KEY,
            prefs.getInt(CricketMprStatsStore.SESSIONS_KEY, 0) + 1
        )
        if (isNewBest) {
            editor.putInt(CricketMprStatsStore.BEST_KEY, (mpr * 100).toInt())
            _bestMpr.value = mpr
        }
        editor.apply()
        // 练习结束 → 打卡 + 成就重算（幂等由 practiceTrigger 内部保证）
        practiceTrigger.onSessionFinished()
    }
}
