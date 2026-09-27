package com.dartvio.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 逐镖落点（M3 靶盘点选输入采集，DB v6 新增；v7 增「落点诊断」7 列；v8 再增 4 列）。
 *
 * 与 `match_records` / `match_players` 的**聚合事实**不同，本表存的是**每一镖的原始点位**：
 * 只有落到这个粒度，才谈得上「热力图随时间收敛」。MISS 也存（落在 170mm 之外的坐标为凭证），
 * 但没有点位的录入方式（键盘 / AI 托管）不产生本表的行。
 *
 * 坐标为靶面 mm（原点靶心、x 右 / y 上），与 [com.dartvio.app.domain.vision.BoardGeometry] 同一坐标系，
 * 因此热区可以直接叠画在标准的 `BoardTapPad` 上。
 *
 * ## v7 新增 7 列的默认值语义（**必须按这张表理解，否则查询会写错**）
 *
 * | 列 | 默认 | 语义 |
 * |---|---|---|
 * | [sessionId] | `''` | 练习会话 id；**空 = 对局产生的镖**（沿用既有语义，不是「未知」） |
 * | [intentNumber] / [intentMultiplier] | `0` | **0 = 无意图**（对局镖、或未知）；有值时为 `(number, multiplier)` 组合 |
 * | [dartIndexInRound] | `0` | 本回合第几镖（1..3）；`0` = 未记录 |
 * | [windowSpanMm] | `0.0` | 当次局部窗口的**径向跨度**（60 / 120…）；`0` = 非局部窗口录入（对局镖或全盘录入）。**用于禁止跨档位合并统计** |
 * | [outBand] | `0` | 出框方向：`0`=窗内；`1`=上、`2`=右、`3`=下、`4`=左（**屏幕方向**，不随目标旋转）；`!= 0` 即「出框」 |
 * | [outLevel] | `0` | 出框程度：`0`=无；`1`=轻微出框；`2`=远出框 / 靶外。**σ / R95 / KDE 只看 `outBand == 0` 的点，计数必须包含全部** |
 *
 * ## v8 新增 4 列的默认值语义（处方 / 干预 / 压力预留）
 *
 * | 列 | 默认 | 语义 |
 * |---|---|---|
 * | [prescriptionMetric] | `''` | 本轮量化目标口径：`''`=未设 / `BIAS_ABS` / `R95` / `RMSE` / `HIT_RATE` / `OUT_RATE`（与 `PrescriptionMetric` 同名） |
 * | [prescriptionTarget] | `0.0` | 目标阈值；**是否已设看 [prescriptionMetric] 是否为空串，不要用 `target > 0`**（`0.0` 是合法目标）。单位随口径：mm 或 0–1 比例 |
 * | [interventionNote] | `''` | 本轮**打算刻意改动**什么（自由文本，≤24 字）；`''` = 未填。只作对照标签，**不作因果结论** |
 * | [pressureMode] | `0` | **P1 预留**：`1` = 本轮在压力约束（必须一镖结镖 / 倒计时）下完成。本版只落列与开关，不实现压力玩法本身 |
 *
 * 前 3 列是 **`session` 级属性**（与 [windowSpanMm] 同约定，冗余存于本轮每一行，同一 `sessionId` 内取值必须一致）。
 * 两批变更都是**纯增量**：老行读出来的每个字段值与升级前逐位相同，且因意图为 `0`
 * 而不会进入落点诊断的任何统计（对局点选与训练数据互不污染）。
 *
 * ## 两个索引**必须在这里声明**（不能只写在迁移 SQL 里）
 *
 * `index_dart_hits_profileId_hitAt`（v7 建）/ `index_dart_hits_sessionId`（v8 建）此前只由
 * `MIGRATION_6_7` / `MIGRATION_7_8` 的 `CREATE INDEX` 建出，实体上**没有** `@Index` —— 这会让
 * Room 的**迁移后 schema 校验**失败：`TableInfo.read()` 会读到库里的这两个索引，而期望值
 * （由实体生成）索引集为空，`onValidateSchema` 直接抛 `IllegalStateException`
 * （"Migration didn't properly handle: dart_hits ... Expected ... indices=[] Found ..."）。
 * 另外全新安装走的是生成的 `CREATE TABLE`，没有 `@Index` 就**根本不会建索引**。
 *
 * 索引名必须与迁移 SQL 逐字一致（Room 默认命名恰好就是 `index_<表名>_<列名...>`），
 * 且**两侧都不能改名**：改名会让存量库的旧索引被判定为「多余索引」而再次校验失败。
 */
@Entity(
    tableName = "dart_hits",
    indices = [
        Index(value = ["profileId", "hitAt"]),
        Index(value = ["sessionId"])
    ]
)
data class DartHitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 所属对局（与 `match_records.matchId` 同源）。练习录入为空串。 */
    val matchId: String = "",
    /** 本机玩家身份（与 `match_records` 的 profileId 同源）。 */
    val profileId: String = "",
    /** 第几局：用于按局切分趋势。 */
    val legNumber: Int = 0,
    val xMm: Float = 0f,
    val yMm: Float = 0f,
    /** 判定结果：分区号（1..20 / 25 / 0=MISS）。 */
    val number: Int = 0,
    /** 判定结果：倍数（1 / 2 / 3）。 */
    val multiplier: Int = 0,
    /** 录入来源（如 `BOARD_TAP`），与 [com.dartvio.app.domain.model.DartSource] 的名称一致。 */
    val source: String = "",
    /** 录入时刻（epoch millis），收敛趋势的时间轴。 */
    val hitAt: Long = 0L,

    /** 练习会话 id；`''` = 对局产生的镖（见类注释的语义表）。 */
    val sessionId: String = "",
    /** 瞄准意图分区号；`0` = 无意图。 */
    val intentNumber: Int = 0,
    /** 瞄准意图倍数；`0` = 无意图。 */
    val intentMultiplier: Int = 0,
    /** 本回合第几镖（1..3）；`0` = 未记录。 */
    val dartIndexInRound: Int = 0,
    /** 当次局部窗口的径向跨度（mm）；`0.0` = 非局部窗口录入。**不同档位不得合并算散布**。 */
    val windowSpanMm: Float = 0f,
    /** 出框方向（屏幕）：`0`=窗内、`1`=上、`2`=右、`3`=下、`4`=左。 */
    val outBand: Int = 0,
    /** 出框程度：`0`=无、`1`=轻微出框、`2`=远出框 / 靶外。 */
    val outLevel: Int = 0,

    /** 本轮量化目标口径；`''` = 未设（报告不出处方卡）。 */
    val prescriptionMetric: String = "",
    /** 目标阈值；**是否已设看 [prescriptionMetric] 是否为空串**（`0.0` 是合法目标）。 */
    val prescriptionTarget: Double = 0.0,
    /** 本轮打算刻意改动的自由文本；`''` = 未填。 */
    val interventionNote: String = "",
    /** P1 预留：`1` = 压力约束下完成；`0` = 常规。 */
    val pressureMode: Int = 0
)
