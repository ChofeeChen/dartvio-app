package com.dartvio.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dartvio.app.domain.model.BullMode
import com.dartvio.app.domain.model.CricketTarget
import com.dartvio.app.domain.model.CricketVariant
import com.dartvio.app.domain.model.InMode
import com.dartvio.app.domain.model.MatchSource
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.domain.model.parseCricketTargets

/**
 * 一场比赛（对局）的记录。一场 = 若干局（leg），直到分出胜负。
 *
 * 可信度分层（M9 §2）：
 * - [isFormal] = true   → S 级（本地正式赛，计入胜率与连胜）
 * - [isFormal] = false  → A 级（本地休闲 / AI 对战，计入投镖数据但不计入胜率排名）
 * - B 级（练习模式，M11）本期不落库。
 */
@Entity(tableName = "match_records")
data class MatchRecordEntity(
    @PrimaryKey val matchId: String,
    /** 玩法类型：X01 / CRICKET */
    val gameType: String,
    /** MatchType 枚举名 */
    val matchType: String,
    /** MatchType 的中文展示名，落库以免枚举改名后历史记录失真 */
    val matchTypeLabel: String,
    /** true = S 级可信度 */
    val isFormal: Boolean,
    /** 本场是否有 AI 参与 */
    val containsAi: Boolean,
    val playerCount: Int,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    /** 胜者玩家 ID；null 表示未完成 */
    val winnerPlayerId: String?,
    /** 本场实际打完的局数 */
    val legCount: Int,
    /** 取胜所需的局数（MatchConfig.legsToWin） */
    val legsToWin: Int,
    // ---- X01 配置（Cricket 时为 0 / null）----
    val startScore: Int,
    val x01Mode: String?,
    val doubleOut: Boolean,
    val doubleIn: Boolean,
    val overtimeRule: String?,
    /**
     * X01 结束规则档（Out Mode，2026-09-12）：存 [OutMode] 枚举名。
     *
     * 为什么不复用上面的 `doubleOut`（**新字段不得塞进老字段**）：布尔只有两档，塞不下
     * 「大师出」——硬塞会把大师出写成 `doubleOut = true`，读回来摇身变成双倍出，
     * 历史记录从此显示错误且不可逆。老布尔列保留不删：老版本读新行时仍有值可用，
     * 两者由 [com.dartvio.app.data.local.MatchMapper] 从同一份 [com.dartvio.app.domain.model.MatchConfig] 写出。
     *
     * 列默认值 'DOUBLE_OUT' 只服务「列刚补上、还没回填」的瞬间；迁移 v4→v5 随即按老布尔逐行订正，
     * 所以历史行读出来的口径与升级前逐位相同。
     */
    @ColumnInfo(defaultValue = "'DOUBLE_OUT'")
    val outMode: String = OutMode.DOUBLE_OUT.name,
    /** X01 开局规则档（In Mode）：存 [InMode] 枚举名。默认 'STRAIGHT_IN'（= 老 `doubleIn = false`）。 */
    @ColumnInfo(defaultValue = "'STRAIGHT_IN'")
    val inMode: String = InMode.STRAIGHT_IN.name,
    /** X01 牛眼规则档（Bull Mode）：存 [BullMode] 枚举名。老数据只能是默认 25/50。 */
    @ColumnInfo(defaultValue = "'STANDARD_25_50'")
    val bullMode: String = BullMode.STANDARD_25_50.name,
    /**
     * 本局轮数上限：`0` = 无上限（2026-09-12 起 X01 与 Cricket **共用同一字段同一列**）。
     *
     * - X01 局：即原「最多轮数」（老布尔 `overtime_rule` 那句空开关的落点），打满 ⇒ 剩余分最低者胜；
     * - Cricket 局：即 M2 §4.9.8 的轮数上限（仅 Tactics 会非 0），打满 ⇒ 总分最高者胜。
     *
     * 两者单位相同（每人各投一轮记 1）；**超时胜负口径不等同**，由各自规则引擎按玩法分派。
     */
    @ColumnInfo(defaultValue = "0")
    val maxRounds: Int = 0,
    // ---- Cricket 配置 ----
    /**
     * Cricket 玩法变体（M9 §8.2，存 [CricketVariant] 枚举名）。
     *
     * 列默认值 'STANDARD'：变体上线前打的对局没有这一列，补列后按 standard 回填
     * （A9.27「旧记录缺省按 standard」）；读取侧统一走 [CricketVariant.fromKey] 的宽容解析，
     * 异常值也不会抛。X01 局该值无意义，恒为 standard。
     */
    @ColumnInfo(defaultValue = "STANDARD")
    val cricketVariant: String = CricketVariant.DEFAULT.name,
    /**
     * 本局 Cricket 目标集，token 逗号分隔（M2 §4.8⑦ 二期 2B 留位）。
     *
     * `''` = **默认 7 分区**（20,19,18,17,16,15,25）：变体上线前的历史行、以及新的
     * standard 行写的都是空串，两者语义完全相同，所以 C2 的分母不会因为这次升级而变。
     * 只有非默认目标集（2C 的 tactics / random）才会写出 token 列表。
     *
     * 读取统一走 [cricketTargets]：空串 / 无法识别的 token 一律回落默认 7 分区，
     * **不抛异常**（老版本读到新版本写入的类别档 token 时少算一条即可）。
     */
    @ColumnInfo(defaultValue = "''")
    val targetSetCsv: String = "",
    /**
     * 本局是否由**轮数上限**终局（M2 §4.9.8 二期 2C）。
     *
     * 列默认值 `0`：历史行与所有非 Tactics 局都是「不是超时局」，与既有语义相同。
     * 超时局的赢家通常并没有关满，语义上不该计入关满类统计 —— 落这一列就是为了
     * 让 M9 能**直接说明**这一点，而不是靠分数去反推「这局到底是不是超时」。
     */
    @ColumnInfo(defaultValue = "0")
    val endedByRoundLimit: Boolean = false,
    // ---- 汇总 ----
    val totalDarts: Int,

    // ---- M5 T10：来源与联机上下文（v10 → v11 新增，纯增量）----
    /**
     * 这场是**怎么来的**（[MatchSource]：LOCAL = 单机 / LAN = 联机）。
     *
     * 列默认值 `'LOCAL'`：本列补上时历史行一律回填单机，与它们**当年实际的来源**一致
     * （那时还没有联机落库），所以既有统计的分母不会因为这次升级变化。
     *
     * 两种来源**不同待遇**：联机局进历史列表（用户打完要能回看），但由
     * [com.dartvio.app.data.local.MatchStatsFilter] 统一排除在统计 / 成就 / 排行榜之外。
     */
    @ColumnInfo(defaultValue = "'LOCAL'")
    val source: String = MatchSource.LOCAL.name,

    /**
     * 联机局所属的 6 位房间号；单机局为空串。
     *
     * 落房间号而不是「联机」两个字，是为了让两场同名房间的记录仍可区分 ——
     * 房间号会复用（6 位），名字更是随手改的，只有「哪一场」没有第二个可追溯的标识。
     */
    @ColumnInfo(defaultValue = "''")
    val roomId: String = "",

    /**
     * 胜者的**名字**。
     *
     * 单机局的胜者在本机档案里，靠 `winnerPlayerId` 就能显示；联机局的对手不在本机，
     * 他那一行只有一个**线上身份**（重连可能变），所以名字必须随行落库 ——
     * 否则历史列表上会只剩一个谁也认不出来的 id。
     */
    @ColumnInfo(defaultValue = "''")
    val winnerName: String = "",

    /** 是否以**判负**收场（对手掉线超过时限，M5 T8）。正常收镖为 false。 */
    @ColumnInfo(defaultValue = "0")
    val forfeited: Boolean = false,
) {
    /**
     * 是否可计入 Cricket 成就与标准指标（M9 §8.3.1 指标准入表）。
     *
     * **三段判据缺一不可**（M2 §4.9.6②③「C1–C10 / 成就 6 项全部仅 standard」）：
     * 1. `gameType == CRICKET` —— X01 行的 `targetSetCsv` 也是空串，少了它会把每场 X01
     *    当成标准 Cricket 计入成就；
     * 2. `cricketVariant == STANDARD` —— `no_score` / `cut_throat` 用的是**同一个默认目标集**
     *    （`targetSetCsv` 同样为空串），只按目标集判会让它们漏进 C1–C10 与关满类成就，
     *    正是 §4.9.6 要求「比一期更严」要排除的那部分；
     * 3. `targetSetCsv` 为空串（= 默认 7 分区）—— Tactics / Random 的 `cricketVariant`
     *    仍是 `STANDARD`（Q7 不为玩法新增变体取值），只按变体名判会把它们全部误判成标准局。
     *
     * 易被误读的一点（**勿简化成「只看目标集」**）：本条曾按 §8.10① 的字面式
     * `gameType == CRICKET && targetSetCsv.isEmpty()` 落地，但那会**丢掉 `variant` 那半**。
     * **r7 裁决（2026-09-12）已定为「在既有判据上增加目标集那一半，而非替换」** ——
     * 即上面的三段 AND；三处文档措辞（《2C解禁提示词》§8.10①、同文 §5.4 红线第 4 条、
     * 《4.9候选文本》§4.9.6④）已同步修订。
     * `cricketVariant` 这半若省掉，`no_score` / `cut_throat` 会被当成标准局，
     * 与 §4.9.6② 直接冲突，也会让一期的两条既有断言（`StatsCalculatorTest`、
     * `AchievementCalculatorTest` 各一条）当场变红。
     */
    val isCricketStandard: Boolean
        get() = gameType == MatchType.CRICKET.name &&
            CricketVariant.fromKey(cricketVariant) == CricketVariant.DEFAULT &&
            targetSetCsv.isEmpty()

    /** 本局目标集；空串 / 未知 token 回落默认 7 分区（见 [targetSetCsv]）。 */
    val cricketTargets: List<CricketTarget>
        get() = parseCricketTargets(targetSetCsv)

    /**
     * X01 三组规则档位的**宽容读取**（未知值回落宽松档，不抛异常）。
     *
     * 读取点只许用这三个属性，不要再直接比对 `outMode` 字符串 —— 枚举名一旦扩充，
     * 散落的字符串比较会在读旧行时节节失守。
     */
    val x01OutMode: OutMode get() = OutMode.fromKey(outMode)
    val x01InMode: InMode get() = InMode.fromKey(inMode)
    val x01BullMode: BullMode get() = BullMode.fromKey(bullMode)

    /**
     * 是否联机（LAN）对局 —— 「算不算战绩」的**唯一**判据，见 [MatchSource] 与
     * [com.dartvio.app.data.local.MatchStatsFilter]。
     *
     * 宽容读取：未知来源按**单机**处理。历史行补列时回填的就是 LOCAL，与它们当年
     * 实际的来源一致；反过来（未知即剔除）会让一次枚举扩充静默抹掉一批老战绩。
     */
    val matchSource: MatchSource get() = MatchSource.fromKey(source)

    val isLan: Boolean get() = matchSource == MatchSource.LAN
}
