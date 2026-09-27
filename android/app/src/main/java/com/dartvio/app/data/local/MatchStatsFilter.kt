package com.dartvio.app.data.local

import com.dartvio.app.data.local.entity.MatchRecordEntity

/**
 * 「这场算不算战绩」的**唯一**判据（M5 T10）。
 *
 * ## 为什么必须有这一处集中判据
 *
 * 联机对局落进的是**同一张** `match_records` 表（它要出现在历史列表里），
 * 于是每一个既有统计查询都天然会读到它。若把排除条件写成散落各处的
 * `WHERE ... AND source <> 'LAN'`，漏掉的那处不会报错，只会**静默污染**一个指标 ——
 * 这正是本库当初为对抗练习**拆表**（`versus_*`）而不是加判别列的理由。
 *
 * T10 不能拆表：验收要求「历史可见且标注来源」，联机局必须出现在历史列表里。
 * 于是把红线收在这里：**判定只有这一个函数**，DAO 的 SQL 谓词只是**性能前置**
 * （少读一批行），即使它被绕过，本函数的兜底过滤仍然成立 —— 反之则不成立。
 *
 * ## 口径
 *
 * 联机（[com.dartvio.app.domain.model.MatchSource.LAN]）不计入：无裁判 + 掉线可判负，
 * 计入会让「拔网线」变成提高数据的手段。其余来源（含未知老值）照旧计入，
 * 以保证老用户的统计分母不会因为升级而变化。
 */
object MatchStatsFilter {

    /**
     * 这一行是否计入 M9 统计 / 成就 / 排行榜。
     *
     * @param record 一行对局记录；null（缺行）视为不计入 ——
     *   宁可少算一场，也不要为一行读不出来的记录去猜它的来源。
     */
    fun countsForStats(record: MatchRecordEntity?): Boolean =
        record != null && !record.isLan
}
