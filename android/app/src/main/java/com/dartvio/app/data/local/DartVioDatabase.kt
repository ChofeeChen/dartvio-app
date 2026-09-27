package com.dartvio.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dartvio.app.data.local.dao.CheckoutRushDao
import com.dartvio.app.data.local.dao.DartHitDao
import com.dartvio.app.data.local.dao.MatchRecordDao
import com.dartvio.app.data.local.dao.VersusDao
import com.dartvio.app.data.local.entity.CheckoutRushAttemptEntity
import com.dartvio.app.data.local.entity.DartHitEntity
import com.dartvio.app.data.local.entity.MatchPlayerEntity
import com.dartvio.app.data.local.entity.MatchRecordEntity
import com.dartvio.app.data.local.entity.VersusMatchRecordEntity
import com.dartvio.app.data.local.entity.VersusRoundRecordEntity

/**
 * DartVio 本地数据库。
 *
 * 承载两类数据：逐场/逐人的**聚合事实**（`match_records` / `match_players`，M9 第①期）与
 * **逐镖落点**（`dart_hits`，M3 靶盘点选输入，v6 起），后者是热力图的唯一数据源。
 *
 * v7 起 `dart_hits` 还承载**落点诊断训练的意图与窗口档位**（M11 新增子练习），
 * v8 起再加**量化目标 / 干预标签 / 压力预留**（V1.4），语义见 [DartHitEntity] 的类注释；
 * 对局镖的意图列为 `0`，因此两批数据互不污染。
 *
 * v9 起新增**双人对战**两张表（`versus_match_records` / `versus_round_records`）。
 * 它们刻意**不复用** `match_records`：对抗练习的数据红线是「不进入线上对局记录、成就与排行榜」，
 * 靠判别列去实现这条红线意味着每个既有统计查询都要补谓词、漏一处就污染；拆表之后由表结构保证。
 *
 * schema 会导出到 `app/schemas/`，作为 T1《数据库 Schema》交付物的真实来源。
 */
@Database(
    entities = [
        MatchRecordEntity::class,
        MatchPlayerEntity::class,
        DartHitEntity::class,
        VersusMatchRecordEntity::class,
        VersusRoundRecordEntity::class,
        CheckoutRushAttemptEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class DartVioDatabase : RoomDatabase() {

    abstract fun matchRecordDao(): MatchRecordDao

    abstract fun dartHitDao(): DartHitDao

    abstract fun versusDao(): VersusDao

    abstract fun checkoutRushDao(): CheckoutRushDao

    companion object {
        private const val DB_NAME = "dartvio.db"

        /**
         * v1 → v2：match_records 增加 Cricket 玩法变体列（M9 §8.2）。
         *
         * 变体上线前打的对局没有这一列，按 standard 回填（A9.27：旧记录缺省按 standard），
         * 所以老用户的 Cricket 统计口径不会因为这次升级发生变化。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE match_records ADD COLUMN cricketVariant TEXT NOT NULL DEFAULT 'STANDARD'"
                )
            }
        }

        /**
         * v2 → v3：match_records 增加 Cricket 目标集列（M2 §4.8⑦ 二期 2B 留位）。
         *
         * 默认 `''` 的语义就是「默认 7 分区」（提示词 §4.1），所以历史行补列后
         * 读出来仍是 20/19/18/17/16/15/25 —— C2 的分母不变，老用户的关闭率与新口径逐位相同。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE match_records ADD COLUMN targetSetCsv TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * v3 → v4：match_records 增加「轮数上限终局」标记（M2 §4.9.8 二期 2C）。
         *
         * 默认 `0` = 不是超时局，与历史行和非 Tactics 局的既有语义完全一致 ——
         * 这是一次**纯增量**变更：老数据读出来的每个字段值与升级前逐位相同。
         *
         * 这是**二期第二处** schema 变更（M2 §4.8⑦ r6 修订：第一处 = v2→v3 的 `targetSetCsv`）。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE match_records ADD COLUMN endedByRoundLimit INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v4 → v5：match_records 增加 X01 三组规则档位与最多轮数（2026-09-12）。
         *
         * 这是**纯增量**：老行只有 `doubleOut` / `doubleIn` 两个布尔，没有三档信息，
         * 所以翻译出的档位就是它当年**实际**表达的「直 / 双倍」两档 —— 历史事实不变，
         * 升级后历史记录显示的口径与升级前逐位相同（`大师出` 只会出现在升级后的新行里）。
         *
         * 两个细节：
         *  1. 加列时的 DEFAULT 取「与老布尔 true 等价」的值，否则老行会被列默认值误标成双倍档
         *     （ALTER TABLE 的 DEFAULT 不能引用同表其它列），所以后面必须补一次 UPDATE 订正；
         *  2. `maxRounds` 默认 0 = 无上限，与老 `overtimeRule` 那句从无实现的空开关语义一致。
         *
         * 这是**二期第三处** schema 变更（前两处：v2→v3 的 `targetSetCsv`、v3→v4 的 `endedByRoundLimit`）。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE match_records ADD COLUMN outMode TEXT NOT NULL DEFAULT 'DOUBLE_OUT'"
                )
                db.execSQL(
                    "ALTER TABLE match_records ADD COLUMN inMode TEXT NOT NULL DEFAULT 'STRAIGHT_IN'"
                )
                db.execSQL(
                    "ALTER TABLE match_records ADD COLUMN bullMode TEXT NOT NULL DEFAULT 'STANDARD_25_50'"
                )
                db.execSQL(
                    "ALTER TABLE match_records ADD COLUMN maxRounds INTEGER NOT NULL DEFAULT 0"
                )
                // 逐行订正：老布尔 false 的那一半不能被列默认值吞掉。
                db.execSQL("UPDATE match_records SET outMode = 'STRAIGHT_OUT' WHERE doubleOut = 0")
                db.execSQL("UPDATE match_records SET inMode = 'DOUBLE_IN' WHERE doubleIn = 1")
            }
        }

        /**
         * v5 → v6：新增 `dart_hits` 逐镖落点表（M3 靶盘点选输入）。
         *
         * **纯增量**：只新建一张表，不动前两张表的任何列，老数据读出来的每个值逐位不变。
         * 走「加表」而不是「给 match_players 加一个 JSON 点位列」的理由是：
         * 聚合表一行是「一场每人」，点位是「每镖」，塞进去只能靠分隔符编码，
         * 既不能按时间切片，也不能按局/单镖查询 —— 而这正是热力图要回答的问题。
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `dart_hits` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`matchId` TEXT NOT NULL, " +
                        "`profileId` TEXT NOT NULL, " +
                        "`legNumber` INTEGER NOT NULL, " +
                        "`xMm` REAL NOT NULL, " +
                        "`yMm` REAL NOT NULL, " +
                        "`number` INTEGER NOT NULL, " +
                        "`multiplier` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`hitAt` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v6 → v7：`dart_hits` 增「落点诊断」7 列 + `(profileId, hitAt)` 索引（M11 新增子练习）。
         *
         * **纯增量**：7 条 `ADD COLUMN ... NOT NULL DEFAULT` + 1 条 `CREATE INDEX`，
         * 不动任何既有列，老行读出来的每个值逐位不变。默认值的语义（哪一列用 `''` / `0` / `0.0`
         * 表达「无」）写在本文件上游的 `DartHitEntity` 类注释里，改查询前先看那张表。
         *
         * ALTER TABLE 的 DEFAULT 只能取字面量、不能引用同表其它列 —— 这里不需要订正 UPDATE，
         * 因为新列的默认值恰好就是「老行应该被读成的值」（对局镖 = 无意图、窗内）。
         *
         * 索引 `(profileId, hitAt)` 服务于「某人的点位按时间倒序」这一类热力图 / 趋势查询，
         * 这也是 v7 唯一新增的索引。
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dart_hits ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE dart_hits ADD COLUMN intentNumber INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE dart_hits ADD COLUMN intentMultiplier INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE dart_hits ADD COLUMN dartIndexInRound INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE dart_hits ADD COLUMN windowSpanMm REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE dart_hits ADD COLUMN outBand INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE dart_hits ADD COLUMN outLevel INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_dart_hits_profileId_hitAt " +
                        "ON dart_hits (profileId, hitAt)"
                )
            }
        }

        /**
         * v7 → v8：`dart_hits` 增「处方 / 干预 / 压力预留」4 列 + `sessionId` 索引（M11 V1.4）。
         *
         * **纯增量**：4 条 `ADD COLUMN ... NOT NULL DEFAULT` + 1 条 `CREATE INDEX`。
         * 默认值恰好就是「老行应该被读成的值」（未设目标、无干预、常规），因此不需要订正 UPDATE。
         *
         * ⚠️ 这 4 列**不得**追加进 [MIGRATION_6_7]：迁移一旦发布不可回改，**存量库不会重跑** v6→v7，
         * 追加进去会导致老用户静默缺列。新列一律走本迁移。
         *
         * 索引 `(sessionId)` 服务于「按本轮训练聚合」的查询（干预对照表、三镖回合指标）——
         * 这也是 v8 唯一新增的**业务**索引；`(profileId, hitAt)` 由 v7 建。
         *
         * ⚠️ 这里**多补了一条 `index_dart_hits_profileId_hitAt`**（幂等，已存在的库不受影响）：
         * `DartHitEntity` 此前没有声明 `@Index`，因此**在 v7 全新安装出来的库上这两个索引都不存在**
         * （迁移 SQL 只在「升级路径」上执行过）。V1.4 给实体补上 `@Index` 后，Room 的迁移后 schema 校验
         * 会把这两个索引都当成**期望值**，如果 v8 只补 `sessionId`，那些「v7 全新安装」的库升级时仍会
         * 因缺 `(profileId, hitAt)` 而校验失败（"Migration didn't properly handle: dart_hits"）。
         * ⇒ 三条路径（v6→v8 / v7 升级→v8 / v8 全新安装）在 v8 之后都必须收敛到**同一组索引**。
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE dart_hits ADD COLUMN prescriptionMetric TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE dart_hits ADD COLUMN prescriptionTarget REAL NOT NULL DEFAULT 0.0"
                )
                db.execSQL(
                    "ALTER TABLE dart_hits ADD COLUMN interventionNote TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE dart_hits ADD COLUMN pressureMode INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_dart_hits_sessionId ON dart_hits (sessionId)"
                )
                // 幂等补齐：v7 全新安装的库没有这个索引（实体当时未声明 @Index）。
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_dart_hits_profileId_hitAt " +
                        "ON dart_hits (profileId, hitAt)"
                )
            }
        }

        /**
         * v8 → v9：新增双人对战两张表（对抗练习）。
         *
         * **纯增量**：只新建两张表与它们的索引，`match_records` / `match_players` / `dart_hits`
         * 一列未动 —— 老数据读出来的每个值逐位不变，既有胜率 / 成就 / 排行榜不受任何影响。
         *
         * 为什么是拆表而不是给 `match_records` 加判别列：见类注释（红线靠表结构保证，不靠谓词）。
         *
         * 两处 DDL 细节：
         *  1. **不写 `DEFAULT`**：实体上没有 `@ColumnInfo(defaultValue = ...)` 时，Room 生成的
         *     `CREATE TABLE` 也没有 DEFAULT 子句，Room 的迁移后校验会逐列比对默认值 ——
         *     这里多写一个 DEFAULT 就会校验失败（"Migration didn't properly handle"）。
         *     新表没有存量行，本来也无需默认值。
         *  2. 索引名必须与实体 `@Index` 生成的默认名逐字一致
         *     （`index_<表名>_<列名...>`），且**两侧都不能改名**。
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `versus_match_records` (" +
                        "`matchId` TEXT NOT NULL, " +
                        "`modeKey` TEXT NOT NULL, " +
                        "`modeLabel` TEXT NOT NULL, " +
                        "`matchSource` TEXT NOT NULL, " +
                        "`configJson` TEXT NOT NULL, " +
                        "`playerNamesCsv` TEXT NOT NULL, " +
                        "`playerCount` INTEGER NOT NULL, " +
                        "`winnerIndex` INTEGER NOT NULL, " +
                        "`winnerName` TEXT NOT NULL, " +
                        "`endReason` TEXT NOT NULL, " +
                        "`handicapSummary` TEXT NOT NULL, " +
                        "`roundCount` INTEGER NOT NULL, " +
                        "`totalDarts` INTEGER NOT NULL, " +
                        "`wentToPlayoff` INTEGER NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, " +
                        "`endedAt` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`matchId`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_versus_match_records_modeKey_endedAt " +
                        "ON versus_match_records (modeKey, endedAt)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `versus_round_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`matchId` TEXT NOT NULL, " +
                        "`modeKey` TEXT NOT NULL, " +
                        "`playerIndex` INTEGER NOT NULL, " +
                        "`playerName` TEXT NOT NULL, " +
                        "`roundNo` INTEGER NOT NULL, " +
                        "`isPlayoff` INTEGER NOT NULL, " +
                        "`dartsCsv` TEXT NOT NULL, " +
                        "`dartCount` INTEGER NOT NULL, " +
                        "`roundScore` INTEGER NOT NULL, " +
                        "`runningScore` INTEGER NOT NULL, " +
                        "`targetSnapshot` TEXT NOT NULL, " +
                        "`recordedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_versus_round_records_matchId " +
                        "ON versus_round_records (matchId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_versus_round_records_playerName " +
                        "ON versus_round_records (playerName)"
                )
            }
        }

        /**
         * v9 → v10：极速结镖（[CheckoutRushAttemptEntity]）。
         *
         * 只**新增**一张表，不动任何既有表/列 —— 三条路径（9→10 迁移 / 10 全新安装 /
         * 导出的 `10.json`）产出的结构必须逐字一致，尤其是：
         *  1. 不写 `DEFAULT`（实体上没有 `@ColumnInfo(defaultValue=...)`，写了会让 Room 迁移校验失败）；
         *  2. 索引名必须与实体 `@Index` 生成的默认名一致（`index_<表名>_<列名...>`），两侧都不改名；
         *  3. **索引必须在实体里声明**（`@Entity(indices=[...])`）—— 只在迁移 SQL 里
         *     `CREATE INDEX` 不算，Room 的 schema 校验会因为「实体没声明但库里有」而失败。
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checkout_rush_attempts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` TEXT NOT NULL, " +
                        "`targetScore` INTEGER NOT NULL, " +
                        "`outMode` TEXT NOT NULL, " +
                        "`bullMode` TEXT NOT NULL, " +
                        "`difficulty` TEXT NOT NULL, " +
                        "`dartsCsv` TEXT NOT NULL, " +
                        "`dartCount` INTEGER NOT NULL, " +
                        "`inputMode` TEXT NOT NULL, " +
                        "`throwElapsedMs` INTEGER NOT NULL, " +
                        "`inputElapsedMs` INTEGER NOT NULL, " +
                        "`routeHintUsed` INTEGER NOT NULL, " +
                        "`result` TEXT NOT NULL, " +
                        "`remainingAfter` INTEGER NOT NULL, " +
                        "`bustReason` TEXT NOT NULL, " +
                        "`retryOfAttemptId` INTEGER NOT NULL, " +
                        "`timingInvalidated` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_checkout_rush_attempts_sessionId " +
                        "ON checkout_rush_attempts (sessionId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_checkout_rush_attempts_difficulty_routeHintUsed_result " +
                        "ON checkout_rush_attempts (difficulty, routeHintUsed, result)"
                )
            }
        }

        /**
         * v10 → v11：`match_records` 增「来源与联机上下文」4 列（M5 T10）。
         *
         * **纯增量**：4 条 `ADD COLUMN ... NOT NULL DEFAULT`，不动任何既有列，
         * 老行读出来的每个值逐位不变 —— 默认值恰好就是「老行应该被读成的值」
         * （那时候还没有联机落库，每一行都是单机局），因此不需要订正 UPDATE。
         *
         * 为什么是**加列**而不是像对抗练习那样拆表（v9 的 `versus_*`）：
         * 拆表的理由是「红线靠表结构保证」，它成立的前提是那批数据**不进历史列表**；
         * 而 T10 的验收明确要求联机局「历史可见且标注来源」，它必须落在 `match_records` 里。
         * 于是红线改由**单一判据** [com.dartvio.app.data.local.MatchStatsFilter] 保证，
         * 并在 DAO 的统计查询里用 SQL 谓词前置（见 [com.dartvio.app.data.local.dao.MatchRecordDao]
         * 的 `observeStatsSource`）。
         *
         * 4 列的默认值与实体上的 `@ColumnInfo(defaultValue = ...)` 必须**逐字一致**，
         * 否则 Room 的迁移后 schema 校验会失败（"Migration didn't properly handle"）。
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE match_records ADD COLUMN source TEXT NOT NULL DEFAULT 'LOCAL'"
                )
                db.execSQL(
                    "ALTER TABLE match_records ADD COLUMN roomId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE match_records ADD COLUMN winnerName TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE match_records ADD COLUMN forfeited INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var instance: DartVioDatabase? = null

        fun get(context: Context): DartVioDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): DartVioDatabase =
            Room.databaseBuilder(context, DartVioDatabase::class.java, DB_NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                )
                // 对局历史是用户资产，宁可迁移失败也不静默清库。
                // 后续加字段时继续补 Migration，不要退化为 fallbackToDestructiveMigration。
                .build()
    }
}
