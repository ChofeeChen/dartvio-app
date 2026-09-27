package com.dartvio.app.domain.credit

/**
 * 玩家信用分（预留算法的**第一个可运行版本**）。
 *
 * ## 它要解决的问题
 *
 * 联机对局里最伤人的不是输，是「对手打着打着不见了」：这一局按规则不算数，
 * 但等你的人已经把这段时间花掉了。房间卡片上没有任何关于对手的信息，
 * 于是「点进去」变成一次纯赌博 —— 信用分就是把它变回一次有依据的选择。
 *
 * ## 为什么是 0–100 的整数
 *
 * 小数与负数都没有解释力：用户不会去比较「信用 73.4 与 71.9 差多少」，
 * 他只会看「这个人靠不靠谱」。整数 + 分档文案（[tier]）才是能被读懂的形式。
 *
 * ## 为什么起点是满分而不是 0
 *
 * 从 0 开始意味着每个新玩家都被当成可疑的人，要靠打很多局才能证明自己 ——
 * 这与「低供给阶段要让人愿意点进来」直接冲突。起点给满分，只**扣**不**奖**到超额，
 * 于是信用分表达的是「你有没有辜负过别人」，而不是「你打了多少局」。
 *
 * ## 现在的输入为什么只有三项
 *
 * 只有**能被本机客观记录**的量才能进算法：完成场次、未完成退出次数、弃权次数。
 * 主观量（对手评价、举报）需要服务端与审核流程，本版不引入 ——
 * 一个靠对手随手打分的分值会立刻被互刷，反而比没有更糟。
 *
 * ## 后续扩展点（不要改调用方）
 *
 * - 加维度：往 [Input] 里加字段 + 在 [evaluate] 里加一项，调用方不用动；
 * - 加时间衰减：`quits` 记成带时间戳的列表，老扣分随时间回补；
 * - 加对局质量：`completed` 换成「完成且无争议的场次」。
 * 这些都不改变「0–100 整数 + 分档文案」这个对外契约。
 */
object CreditScorer {

    const val MIN = 0
    const val MAX = 100

    /**
     * 新玩家起点。见类注释：只扣不奖，因此它同时也是「上限」。
     */
    const val INITIAL = MAX

    /**
     * 一次「未打完就退出」的扣分。
     *
     * 取值理由：8 次退出归零 —— 足够让「习惯性半途消失」的人被看见，
     * 又不至于让一次误操作（来电话、App 被系统回收）就把人钉死在低位。
     */
    const val PENALTY_QUIT = 12

    /** 一次「掉线超时被判负」的扣分：比主动退出轻 —— 它常常不是本人意愿。 */
    const val PENALTY_FORFEIT = 6

    /**
     * 完成若干局之后回补的分（上限 [MAX_RECOVERY]）。
     *
     * 没有这一项，信用分就是一条只降不升的死亡曲线：早期一次误操作会跟着玩家一辈子，
     * 而那时他连「信用是什么」都还没概念。回补让「后来一直很规矩」能被表达出来。
     */
    const val RECOVERY_PER_COMPLETED = 1
    const val MAX_RECOVERY = 10

    /** 算法的全部输入。加维度只改这里与 [evaluate]。 */
    data class Input(
        /** 完整打完的联机场次。 */
        val completedMatches: Int = 0,
        /** 未按约定局数打完就主动退出的次数。 */
        val incompleteQuits: Int = 0,
        /** 掉线超时被判负的次数。 */
        val forfeits: Int = 0
    )

    /**
     * 信用分。纯函数：同样的输入在任何一台设备上得到同一个数 ——
     * 它要被写进房间卡片给别人看，两端算得不一样就没有意义了。
     */
    fun evaluate(input: Input): Int {
        val recovery = (input.completedMatches * RECOVERY_PER_COMPLETED).coerceAtMost(MAX_RECOVERY)
        val penalty = input.incompleteQuits * PENALTY_QUIT + input.forfeits * PENALTY_FORFEIT
        return (INITIAL - penalty + recovery).coerceIn(MIN, MAX)
    }

    /**
     * 分档文案。
     *
     * 只给四档：卡片上的空间只够一个词，而「信用 87」这种数字在扫列表时读不进去 ——
     * 用户要的是「这个人靠不靠谱」的直接答案。
     */
    fun tier(score: Int): String = when {
        score >= 90 -> "极好"
        score >= 75 -> "良好"
        score >= 60 -> "一般"
        else -> "较差"
    }
}
