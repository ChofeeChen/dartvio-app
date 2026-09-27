package com.dartvio.app.data.local

import com.dartvio.app.data.local.entity.DartHitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V1.4 = DB v8 的**三处一致**守卫（对应 A-IMP-23）。
 *
 * 项目里没有 androidTest / Robolectric，Room 的迁移没法在单测里真跑；能做且必须做的是：
 * 把「实体列定义 ↔ `DartVioDatabase` 里的迁移 SQL ↔ 导出的 `schemas/8.json`」三者钉在一起。
 * 任何一处漏改（少列、少索引、改了已发布的 v7、忘了注册迁移）都会在这里立刻红。
 *
 * 另外锁住一条红线：**已发布的迁移不可回改** —— `MIGRATION_6_7` 的正文里不许出现 v8 的新列，
 * 7.json 也不许被重新导出覆盖。
 */
class DartVioMigrationV8Test {

    @Test
    fun `第八版四列在实体与迁移SQL里逐字对应`() {
        val entityColumns = DartHitEntity::class.java.declaredFields.map { it.name }
        val v8Columns = listOf(
            "prescriptionMetric" to "TEXT NOT NULL DEFAULT ''",
            "prescriptionTarget" to "REAL NOT NULL DEFAULT 0.0",
            "interventionNote" to "TEXT NOT NULL DEFAULT ''",
            "pressureMode" to "INTEGER NOT NULL DEFAULT 0"
        )

        v8Columns.forEach { (column, ddl) ->
            assertTrue("实体没声明 $column", entityColumns.contains(column))
            assertTrue(
                "MIGRATION_7_8 缺少 $column 的 ADD COLUMN",
                databaseSource().contains(
                    flatten("ALTER TABLE dart_hits ADD COLUMN $column $ddl")
                )
            )
        }

        // 老列不能被顺手删/改名。
        listOf("id", "matchId", "profileId", "legNumber", "xMm", "yMm", "number", "multiplier", "source", "hitAt")
            .forEach { assertTrue("v6 老列 $it 不见了", entityColumns.contains(it)) }
        // v7 的 7 列仍在。
        listOf(
            "sessionId", "intentNumber", "intentMultiplier", "dartIndexInRound",
            "windowSpanMm", "outBand", "outLevel"
        ).forEach { assertTrue("v7 列 $it 不见了", entityColumns.contains(it)) }
    }

    @Test
    fun `每列只加一次且七到八迁移已注册`() {
        val source = databaseSource()

        listOf("prescriptionMetric", "prescriptionTarget", "interventionNote", "pressureMode")
            .forEach { column ->
                val hits = Regex("ADDCOLUMN$column(TEXT|REAL|INTEGER)").findAll(source).count()
                assertEquals("$column 被加了 $hits 次（应为 1 次）", 1, hits)
            }

        // ★**不要再钉字面量版本号**。库每加一张表 version 就 +1：钉死 8、9、10 的断言每次都要改，
        // 而且改漏了会以「测试通过」的形式掩盖「迁移其实没注册」——这比测试红更危险。
        // 这里改成以**已导出的最大 schema 号**为准，并要求 7→…→当前 的每一段迁移都已注册。
        val latest = latestSchemaVersion()
        assertTrue(
            "version 应为最新导出的 schema 号 $latest",
            Regex("@Database\\([^)]*version=$latest").containsMatchIn(source)
        )
        (8..latest).forEach { v ->
            assertTrue(
                "addMigrations 未注册 MIGRATION_${v - 1}_$v",
                source.contains("MIGRATION_${v - 1}_$v")
            )
        }
        // 对局历史是用户资产：绝不能退化成静默清库（注释里提到它不算调用）。
        assertFalse(source.contains("fallbackToDestructiveMigration()"))
    }

    @Test
    fun `第十版新增极速结镖表且迁移与schema三方一致`() {
        val json = flatten(schemaFile("10.json").readText())
        assertTrue("schema version 不是 10", json.contains("\"version\":10"))
        assertTrue(
            "10.json 缺 checkout_rush_attempts",
            json.contains("\"tableName\":\"checkout_rush_attempts\"")
        )

        val source = databaseSource()
        assertTrue(
            "MIGRATION_9_10 没建 checkout_rush_attempts",
            source.contains(flatten("CREATE TABLE IF NOT EXISTS `checkout_rush_attempts`"))
        )

        // ★索引必须「实体声明 + 迁移 SQL + 导出 schema」三处都在：
        // 报告页按 sessionId 汇总、个人最佳按 (difficulty, routeHintUsed, result) 查，
        // 少一个索引在几百条历史后就会明显变慢，而且这类问题只在真机上才看得出来。
        listOf(
            "index_checkout_rush_attempts_sessionId",
            "index_checkout_rush_attempts_difficulty_routeHintUsed_result",
        ).forEach { index ->
            assertTrue("实体没声明 $index", json.contains(index))
            assertTrue("MIGRATION_9_10 没建 $index", source.contains(index))
        }

        // 升级是纯增量：v8 的四列与 v9 的两张表在 v10 里必须原样还在。
        listOf("prescriptionMetric", "pressureMode", "versus_match_records", "versus_round_records")
            .forEach { assertTrue("v10 丢了 $it", json.contains(it)) }
    }

    @Test
    fun `六到七迁移逐字未改也不许夹带第八版新列`() {
        // 从 6→7 的正文（不含它自己的 KDoc）截到下一个 KDoc 注释为止。
        val block = databaseSource()
            .substringAfter("MIGRATION_6_7")
            .substringBefore("/**")

        listOf(
            "sessionId", "intentNumber", "intentMultiplier", "dartIndexInRound",
            "windowSpanMm", "outBand", "outLevel"
        ).forEach { column ->
            assertTrue("v7 迁移少了 $column", block.contains("ADDCOLUMN$column"))
        }
        listOf("prescriptionMetric", "prescriptionTarget", "interventionNote", "pressureMode")
            .forEach { column ->
                assertFalse("已发布的 v7 迁移里被塞进了 $column", block.contains(column))
            }
        assertTrue(block.contains("index_dart_hits_profileId_hitAt"))
    }

    @Test
    fun `导出的第八版schema与迁移SQL和实体三方一致`() {
        val json = flatten(schemaFile("8.json").readText())

        assertTrue("schema version 不是 8", json.contains("\"version\":8"))
        // 四列的类型 / 非空与迁移 DDL 一致（Room 不导出 DEFAULT，默认值只在迁移里）。
        listOf(
            "prescriptionMetric" to "TEXT",
            "prescriptionTarget" to "REAL",
            "interventionNote" to "TEXT",
            "pressureMode" to "INTEGER"
        ).forEach { (column, affinity) ->
            assertTrue(
                "$column 在 8.json 里不是 $affinity NOT NULL",
                json.contains("\"columnName\":\"$column\",\"affinity\":\"$affinity\",\"notNull\":true")
            )
        }

        // 索引：名与列序都要与迁移 SQL 逐字一致（改任一侧，存量库都会被判定为多余索引）。
        assertTrue(
            json.contains("\"name\":\"index_dart_hits_profileId_hitAt\",\"unique\":false,\"columnNames\":[\"profileId\",\"hitAt\"]")
        )
        assertTrue(
            json.contains("\"name\":\"index_dart_hits_sessionId\",\"unique\":false,\"columnNames\":[\"sessionId\"]")
        )
        val source = databaseSource()
        assertTrue(source.contains(flatten("CREATE INDEX IF NOT EXISTS index_dart_hits_sessionId ON dart_hits (sessionId)")))
        assertTrue(source.contains(flatten("CREATE INDEX IF NOT EXISTS index_dart_hits_profileId_hitAt ON dart_hits (profileId, hitAt)")))
    }

    @Test
    fun `导出的第九版schema含双人对战两张表且老表未退化`() {
        val json = flatten(schemaFile("9.json").readText())
        assertTrue("schema version 不是 9", json.contains("\"version\":9"))

        listOf("versus_match_records", "versus_round_records").forEach { table ->
            assertTrue("9.json 缺 $table", json.contains("\"tableName\":\"$table\""))
        }
        val source = databaseSource()
        listOf("versus_match_records", "versus_round_records").forEach { table ->
            assertTrue(
                "MIGRATION_8_9 没建 $table",
                source.contains(flatten("CREATE TABLE IF NOT EXISTS `$table`"))
            )
        }

        // 升级是纯增量：v8 的四列与两个 dart_hits 索引在 v9 里必须原样还在。
        listOf("prescriptionMetric", "prescriptionTarget", "interventionNote", "pressureMode")
            .forEach { column -> assertTrue("v9 丢了 v8 的 $column", json.contains(column)) }
        assertTrue(json.contains("index_dart_hits_sessionId"))
        assertTrue(json.contains("index_dart_hits_profileId_hitAt"))
        assertFalse(source.contains("fallbackToDestructiveMigration()"))
    }

    @Test
    fun `第七版schema保持冻结`() {
        val json = flatten(schemaFile("7.json").readText())

        assertTrue(json.contains("\"version\":7"))
        listOf("prescriptionMetric", "prescriptionTarget", "interventionNote", "pressureMode")
            .forEach { column ->
                assertFalse("v7 的导出 schema 被回改了：$column", json.contains(column))
            }
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 去掉空白与源码里的字符串拼接符号，让「跨行写的 SQL」也能逐字比较。
     *
     * 例：`"CREATE INDEX ... " + "ON dart_hits (...)"` → `CREATE INDEX ... ON dart_hits (...)`。
     */
    private fun flatten(text: String): String =
        text.replace(Regex("\\s+"), "").replace("\"+\"", "")

    private fun databaseSource(): String = flatten(
        findFile("app/src/main/java/com/dartvio/app/data/local/DartVioDatabase.kt").readText()
    )

    /** 已导出的最大 schema 版本号（`10.json` ⇒ 10）。 */
    private fun latestSchemaVersion(): Int = schemaDir()
        .listFiles()
        ?.mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
        ?.maxOrNull()
        ?: throw AssertionError("schemas 目录下没有 N.json")

    private fun schemaDir(): File = schemaFile("8.json").parentFile

    private fun schemaFile(name: String): File =
        findFile("app/schemas/com.dartvio.app.data.local.DartVioDatabase/$name")

    /** 单元测试的工作目录随 Gradle/IDE 变化，按「模块内 → 工作区」两级往上找。 */
    private fun findFile(relativePath: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            listOf(
                File(dir, relativePath),
                File(dir, "App_DartVio_Android_CB_V0.1/$relativePath")
            ).firstOrNull { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        throw AssertionError("找不到 $relativePath（cwd = ${File("").absolutePath}）")
    }
}
