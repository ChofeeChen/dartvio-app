package com.dartvio.app.ui.practice

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dartvio.app.data.achievement.PracticeSessionTrigger
import com.dartvio.app.domain.achievement.AchievementProgress
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.practice.CountUpRules
import com.dartvio.app.domain.practice.CountUpState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Count Up 练习状态机（M11 §6.2）。
 *
 * 最佳成绩使用 SharedPreferences 本地持久化，后续接入云端（M5）后替换为远程同步。
 */
class CountUpViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(CountUpState())
    val state = _state.asStateFlow()

    private val _bestScore = MutableStateFlow(prefs.getInt(KEY_BEST_SCORE, 0))
    val bestScore = _bestScore.asStateFlow()

    private val _isNewRecord = MutableStateFlow(false)
    val isNewRecord = _isNewRecord.asStateFlow()

    /** 练习打卡与成就重算编排（第③期 ③A），协程随 ViewModel 生命周期自动取消。 */
    private val practiceTrigger = PracticeSessionTrigger(app.applicationContext, viewModelScope)

    /** 本次练习新解锁的成就，供结果页展示解锁卡片（决策④）。 */
    val newlyUnlocked: StateFlow<List<AchievementProgress>> = practiceTrigger.newlyUnlocked

    fun throwDart(dart: Dart) {
        val before = _state.value
        if (before.finished || before.isRoundFull) return
        _state.value = CountUpRules.throwDart(before, dart)
        if (_state.value.isRoundFull) {
            viewModelScope.launch {
                delay(AUTO_ADVANCE_MS)
                finalizeRound()
            }
        }
    }

    fun finalizeRound() {
        val before = _state.value
        if (before.finished || before.currentDarts.isEmpty()) return
        _state.value = CountUpRules.finalizeRound(before)
        persistBestIfFinished()
    }

    fun bust() {
        val before = _state.value
        if (before.finished) return
        _state.value = CountUpRules.bust(before)
        viewModelScope.launch {
            delay(BUST_FLASH_MS)
            _state.value = _state.value.copy(bustFlash = false)
        }
        persistBestIfFinished()
    }

    fun finishEarly() {
        val before = _state.value
        if (before.finished) return
        _state.value = CountUpRules.finishEarly(before)
        persistBestIfFinished()
    }

    fun undoLastDart() {
        _state.value = CountUpRules.undoLastDart(_state.value)
    }

    fun reset() {
        _state.value = CountUpState()
        _isNewRecord.value = false
        // 新一轮练习：重新允许打卡（决策⑤ 的「次数」按练习轮次计）
        practiceTrigger.onNewSession()
    }

    private fun persistBestIfFinished() {
        if (!_state.value.finished) return
        val total = _state.value.totalScore
        if (total > _bestScore.value) {
            _bestScore.value = total
            _isNewRecord.value = true
            prefs.edit().putInt(KEY_BEST_SCORE, total).apply()
        }
        // 练习结束 → 打卡 + 成就重算（幂等由 practiceTrigger 内部保证）
        practiceTrigger.onSessionFinished()
    }

    private companion object {
        const val PREFS_NAME = "dartvio_practice"
        const val KEY_BEST_SCORE = "countup_best_score"
        const val AUTO_ADVANCE_MS = 480L
        const val BUST_FLASH_MS = 900L
    }
}
