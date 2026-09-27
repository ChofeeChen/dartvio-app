package com.dartvio.app.data.room

import android.content.Context
import com.dartvio.app.domain.credit.CreditScorer

/**
 * 本机玩家的信用**原始事实**（落盘）。
 *
 * ## 只存事实，不存分数
 *
 * 这里存的是「完成过几局、退出过几次」这些**事实**，分数由 [CreditScorer.evaluate]
 * 每次现算。存分数的话，算法一改就要给所有人做一次迁移，而老分数与新分数混在一起
 * 会比没有分数更糟 —— 用户看到的是「我明明没退过，怎么掉了 5 分」。
 *
 * ## 为什么是本机
 *
 * 信用目前只服务于「让陌生人敢点进来」，而房间卡上要显示的是**房主**的信用。
 * 房主建房时把自己的分写进房间索引行（[com.dartvio.app.net.online.OnlineRoomApi]），
 * 因此这个值必须先在本机有个唯一出处。
 */
object PlayerCreditStore {

    private const val PREFS = "dartvio_player_credit"
    private const val KEY_COMPLETED = "completed_matches"
    private const val KEY_QUITS = "incomplete_quits"
    private const val KEY_FORFEITS = "forfeits"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 当前信用分（由事实现算）。 */
    fun credit(context: Context): Int = CreditScorer.evaluate(facts(context))

    /** 当前信用分档文案，见 [CreditScorer.tier]。 */
    fun tier(context: Context): String = CreditScorer.tier(credit(context))

    fun facts(context: Context): CreditScorer.Input = prefs(context).let {
        CreditScorer.Input(
            completedMatches = it.getInt(KEY_COMPLETED, 0),
            incompleteQuits = it.getInt(KEY_QUITS, 0),
            forfeits = it.getInt(KEY_FORFEITS, 0)
        )
    }

    /**
     * 记一次「打完整局」。
     *
     * 只在**打满约定局数**时调用：中途散掉的局不算完成 ——
     * 否则「投两镖就退」也会被算成一份正面记录，信用分就失去了区分力。
     */
    fun recordCompleted(context: Context) {
        prefs(context).edit().putInt(KEY_COMPLETED, facts(context).completedMatches + 1).apply()
    }

    /** 记一次「未按局数打完就退出」。 */
    fun recordIncompleteQuit(context: Context) {
        prefs(context).edit().putInt(KEY_QUITS, facts(context).incompleteQuits + 1).apply()
    }

    /** 记一次「掉线超时被判负」。 */
    fun recordForfeit(context: Context) {
        prefs(context).edit().putInt(KEY_FORFEITS, facts(context).forfeits + 1).apply()
    }
}
