package com.dartvio.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一**轮**对战记录 = 某一席的 3 镖（加赛轮为 1 镖）。DB v9 新增。
 *
 * 粒度选「一轮」而不是「一场」或「一镖」，是因为需求里的换手结算就发生在这一层：
 * 「每人 3 镖记完自动换手，**换手时落一条记录**」。一场一行的话中途退出就全丢；
 * 一镖一行则会把「一轮的总分 / 目标快照 / 是否加赛」这三件**整轮才有意义**的信息
 * 在每行里冗余三遍。
 *
 * [playerName] 与 [modeKey] 是刻意冗余的：「镖数计入双方训练统计」这条要求里的
 * 「按人 × 按模式」聚合（双方各打了多少镖），直接用一条 `GROUP BY modeKey` 就能出，
 * 不必回表 join 名字（名字在父行里是一串 CSV）。
 */
@Entity(
    tableName = "versus_round_records",
    indices = [
        Index(value = ["matchId"]),
        Index(value = ["playerName"])
    ]
)
data class VersusRoundRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 所属场次（`versus_match_records.matchId`）。 */
    val matchId: String,
    /** 模式 key（冗余，供「按人 × 按模式」聚合）。 */
    val modeKey: String,
    /** 出手席位（0 / 1）。 */
    val playerIndex: Int,
    /** 出手者名字（冗余，见类注释）。 */
    val playerName: String,
    /** 第几轮（1 起）。 */
    val roundNo: Int,
    /** 是否加赛轮（各 1 镖比离 Bull 的距离）。 */
    val isPlayoff: Boolean,
    /** 本轮逐镖，`|` 分隔的 `Dart.label()` 记号（如 `T20|S5|MISS`），由 `VersusDartCodec` 编解码。 */
    val dartsCsv: String,
    /**
     * 本轮镖数。
     *
     * 单列存而不是每行 `COUNT(*)` 之后再解析 CSV：统计查询（「这个模式我投了多少镖」）
     * 不该为了数个数去读全表文本，而且 CSV 一改编码就全废。
     */
    val dartCount: Int,
    /** 本轮得分（含义随模式：Bull 之争 = 实际分数，环游三镖 = 命中镖数）。 */
    val roundScore: Int,
    /** 本轮结束后的**累计分 / 累计进度**，战报曲线的数据源（不必在读取侧重算）。 */
    val runningScore: Int,
    /** 本轮目标的文字快照（如「打 18 分区」）。 */
    val targetSnapshot: String,
    val recordedAt: Long,
)
