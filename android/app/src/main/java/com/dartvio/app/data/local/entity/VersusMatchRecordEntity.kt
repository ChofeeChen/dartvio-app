package com.dartvio.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一场双人对战（DB v9 新增）。
 *
 * ## 为什么另开一张表，而不是复用 `match_records`
 *
 * 需求红线：对抗练习「**不进入线上对局记录、成就与排行榜**」。这条红线如果靠
 * `match_records` 加一个 `matchSource` 判别列来实现，那么每个既有统计查询都得改一遍谓词，
 * 漏掉任何一处就会把练习数据算进胜率 / 成就 —— 而这类漏改恰恰是最难发现的。
 * 拆表之后，「不进成就」由**表结构**保证：既有代码读不到这张表，将来也不可能漏判。
 * [matchSource] 仍然按需求原样落库（`VERSUS_PRACTICE`），用于导出数据时自证来源。
 *
 * ## 写入时机：开赛插入 → 逐轮追加 → 结算回填
 *
 * 开赛就插一行（[endedAt] = 0、[endReason] = `''`），每次换手往 `versus_round_records`
 * 追加一条，终局 / 认输 / 中途退出时回填本行（REPLACE）。
 * 这样「中途退出不丢数据」不依赖任何 onDestroy 回调 —— 已经写进去的东西本来就在库里。
 */
@Entity(
    tableName = "versus_match_records",
    indices = [Index(value = ["modeKey", "endedAt"])]
)
data class VersusMatchRecordEntity(
    @PrimaryKey val matchId: String,
    /** 模式 key（`VersusModes` 里的 `modeKey`）。 */
    val modeKey: String,
    /** 模式中文名。落库以免枚举改名后历史战报失真（同 `match_records.matchTypeLabel` 的做法）。 */
    val modeLabel: String,
    /** 数据来源标识，恒为 [MATCH_SOURCE]。 */
    val matchSource: String = MATCH_SOURCE,
    /** 开赛配置快照（`BattleConfig.toStorageString()`）：战报要能复现当时的规则与让分。 */
    val configJson: String,
    /** 双方名字，[PLAYER_SEPARATOR] 分隔（名字录入口已禁用该分隔符，见 `VersusSetupScreen`）。 */
    val playerNamesCsv: String,
    val playerCount: Int = 2,
    /** 胜者席位；[NO_WINNER] = 中止或未结束。 */
    val winnerIndex: Int = NO_WINNER,
    /** 胜者名字；无胜者时为空串（战报直接展示，不必再查名字表）。 */
    val winnerName: String = "",
    /** `BattleEndReason` 枚举名；`''` = 还在进行中 / 未回填。 */
    val endReason: String = UNFINISHED,
    /** 让分摘要（如「玩家2：目标 10 分」），无人让分时为空串。 */
    val handicapSummary: String = "",
    /** 已结算的回合数（含加赛轮）。 */
    val roundCount: Int = 0,
    /** 本场总镖数 = 双方所有轮次的镖数之和。 */
    val totalDarts: Int = 0,
    /** 是否经过平分加赛（战报要说明「这一胜来自加赛」）。 */
    val wentToPlayoff: Boolean = false,
    val startedAt: Long,
    /** 结束时刻；`0` = 未结束（这一行是「进行中」或「被中止但没来得及回填」）。 */
    val endedAt: Long = 0L,
    val durationMs: Long = 0L,
) {

    /** 是否已回填终局信息（`endedAt > 0`）。 */
    val isFinished: Boolean get() = endedAt > 0L

    val playerNames: List<String>
        get() = if (playerNamesCsv.isEmpty()) emptyList() else playerNamesCsv.split(PLAYER_SEPARATOR)

    /** 取某一席的名字（越界回落空串，战报不因缺名字崩）。 */
    fun nameOf(index: Int): String = playerNames.getOrNull(index) ?: ""

    /** 终局回填：**只改终局相关的字段**，开赛快照（配置 / 名字 / 起始时刻）逐字不动。 */
    fun finished(
        winnerIndex: Int,
        winnerName: String,
        endReason: String,
        roundCount: Int,
        totalDarts: Int,
        wentToPlayoff: Boolean,
        endedAt: Long,
    ): VersusMatchRecordEntity = copy(
        winnerIndex = winnerIndex,
        winnerName = winnerName,
        endReason = endReason,
        roundCount = roundCount,
        totalDarts = totalDarts,
        wentToPlayoff = wentToPlayoff,
        endedAt = endedAt,
        durationMs = (endedAt - startedAt).coerceAtLeast(0L),
    )

    companion object {
        /** 数据来源标识（对抗练习）。 */
        const val MATCH_SOURCE = "VERSUS_PRACTICE"

        /** 名字分隔符。 */
        const val PLAYER_SEPARATOR = "|"

        /** `winnerIndex` 的「无胜者」。 */
        const val NO_WINNER = -1

        /** `endReason` 的「未回填」。 */
        const val UNFINISHED = ""

        fun namesCsv(names: List<String>): String = names.joinToString(PLAYER_SEPARATOR)
    }
}
