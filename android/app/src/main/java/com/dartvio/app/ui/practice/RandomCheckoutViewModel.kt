package com.dartvio.app.ui.practice

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dartvio.app.data.achievement.PracticeSessionTrigger
import com.dartvio.app.domain.achievement.AchievementProgress
import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.practice.CheckoutResult
import com.dartvio.app.domain.practice.CheckoutSolver
import com.dartvio.app.domain.practice.RandomCheckoutRules
import com.dartvio.app.domain.practice.RandomCheckoutState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 随机结镖练习 ViewModel。
 *
 * 统计本地持久化（SharedPreferences），后续接入云端后替换为远程同步。
 */
class RandomCheckoutViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(RandomCheckoutState())
    val state = _state.asStateFlow()

    private val _attempts = MutableStateFlow(prefs.getInt(KEY_ATTEMPTS, 0))
    val attempts = _attempts.asStateFlow()

    private val _successes = MutableStateFlow(prefs.getInt(KEY_SUCCESSES, 0))
    val successes = _successes.asStateFlow()

    /** 练习打卡与成就重算编排（第③期 ③A），协程随 ViewModel 生命周期自动取消。 */
    private val practiceTrigger = PracticeSessionTrigger(app.applicationContext, viewModelScope)

    /** 本次尝试新解锁的成就，供结果页展示解锁卡片（决策④）。 */
    val newlyUnlocked: StateFlow<List<AchievementProgress>> = practiceTrigger.newlyUnlocked

    init {
        nextTarget()
    }

    fun throwDart(dart: Dart) {
        val before = _state.value
        if (before.isFinished) return
        val after = RandomCheckoutRules.throwDart(before, dart)
        _state.value = after
        if (after.isFinished && !before.isFinished) {
            recordResult(after.result == CheckoutResult.SUCCESS)
        }
    }

    fun retry() {
        _state.value = RandomCheckoutRules.retry(_state.value)
        practiceTrigger.onNewSession()
    }

    fun nextTarget() {
        val target = CheckoutSolver.generateTarget()
        val routes = CheckoutSolver.routesFor(target)
        _state.value = RandomCheckoutRules.newTarget(target, routes)
        practiceTrigger.onNewSession()
    }

    fun toggleAnswer() {
        _state.value = RandomCheckoutRules.toggleAnswer(_state.value)
    }

    fun skip() {
        nextTarget()
    }

    fun resetStats() {
        _attempts.value = 0
        _successes.value = 0
        prefs.edit().remove(KEY_ATTEMPTS).remove(KEY_SUCCESSES).apply()
    }

    private fun recordResult(success: Boolean) {
        _attempts.value += 1
        if (success) _successes.value += 1
        prefs.edit()
            .putInt(KEY_ATTEMPTS, _attempts.value)
            .putInt(KEY_SUCCESSES, _successes.value)
            .apply()
        // 一次尝试结束 → 打卡 + 成就重算（本练习的「一次练习」= 一道题的一次尝试）
        practiceTrigger.onSessionFinished()
    }

    private companion object {
        const val PREFS_NAME = "dartvio_practice"
        const val KEY_ATTEMPTS = "random_checkout_attempts"
        const val KEY_SUCCESSES = "random_checkout_successes"
    }
}
