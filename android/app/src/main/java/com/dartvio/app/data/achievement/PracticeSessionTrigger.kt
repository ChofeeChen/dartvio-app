package com.dartvio.app.data.achievement

import android.content.Context
import com.dartvio.app.DartVioApp
import com.dartvio.app.data.local.DartVioDatabase
import com.dartvio.app.domain.achievement.AchievementProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 练习侧触发编排（第③期 ③A）。
 *
 * 把「一次练习结束」这一个业务事件翻译成两步副作用：
 * 1. [PracticePrefsStore.recordSession] 打卡（次数 / 自然日连续天数 / 99 镖完成数）；
 * 2. [AchievementRepository.recalculate] 重算成就，并把**本次新解锁**交给练习结果页展示。
 *
 * 为什么需要 [onNewSession]：各练习 ViewModel 的「结束」判定会被反复触发
 * （CountUp 每轮结算都会走一次 finished 判定、Cricket MPR 的 record 与 finishEarly 双入口），
 * 因此这里用 [sessionLogged] 把「一次练习只打卡一次」收敛到一处，
 * 避免四处各写一遍幂等标记。点「再练一次」必须显式调用 [onNewSession] 才能重新计数。
 *
 * 口径说明（已确认，无需再讨论）：随机结镖的「一次练习」= 一道题的一次尝试，
 * 因为那是它唯一有明确边界的结束点。这会抬高 [PracticeFacts.sessions]，
 * 但 26 项成就里只有「练习初体验（≥1 次）」用到该指标，
 * 连续天数与累计天数都按自然日去重，因此不受影响。
 */
class PracticeSessionTrigger(
    context: Context,
    private val scope: CoroutineScope,
) {

    private val appContext: Context = context.applicationContext

    private val repository: AchievementRepository = when (val ctx = appContext) {
        is DartVioApp -> ctx.achievementRepository
        else -> AchievementRepository(ctx, DartVioDatabase.get(ctx).matchRecordDao())
    }

    private val _newlyUnlocked = MutableStateFlow<List<AchievementProgress>>(emptyList())

    /** 本次练习新解锁的成就，供结果页展示解锁卡片（界面消费后随 [onNewSession] 清空）。 */
    val newlyUnlocked: StateFlow<List<AchievementProgress>> = _newlyUnlocked.asStateFlow()

    /** 一次练习会话是否已打卡。 */
    private var sessionLogged = false

    /**
     * 一次练习结束时调用。
     *
     * @param ninetyNineCompleted 仅 99 Darts 打满 99 镖时传 true（唯一会推进「99 镖全勤」的入口）
     * @param at 结束时刻，同时作为打卡自然日与解锁时刻
     */
    fun onSessionFinished(
        ninetyNineCompleted: Boolean = false,
        at: Long = System.currentTimeMillis(),
    ) {
        if (sessionLogged) return
        sessionLogged = true
        scope.launch {
            PracticePrefsStore.recordSession(appContext, at, ninetyNineCompleted)
            val snapshot = repository.recalculate(at)
            _newlyUnlocked.value = snapshot.items.filter { it.id in snapshot.newlyUnlockedIds }
        }
    }

    /** 开始新一轮练习（各练习的 reset / retry / restart 入口），允许下一次打卡。 */
    fun onNewSession() {
        sessionLogged = false
        _newlyUnlocked.value = emptyList()
    }
}
