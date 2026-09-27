package com.dartvio.app.data.local

import com.dartvio.app.data.local.entity.MatchRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * DB v11 = M5 T10 的**三处一致**守卫（实体列 ↔ 迁移 SQL ↔ 导出的 `schemas/11.json`）。
 *
 * 项目里没有 androidTest / Robolectric，Room 的迁移没法在单测里真跑；能做且必须做的是
 * 把这三处钉在一起 —— 少一列、改了已发布的 v10、忘了注册迁移，都在这里立刻红。
 *
 * 与 `DartVioMigrationV8Test` 分工：那边守 v8（dart_hits）与 v9/v10（对抗练习 / 极速结镖），
 * 这里守 v11（对局来源与联机上下文）。**不改动既有文件里的任何断言**，v11 的守卫独立成类。
 */
class DartVioMigrationV11Test {

    private val newColumns = listOf(
        "source" to "TEXT",
        "roomId" to "TEXT",
        "winnerName" to "TEXT",
        "forfeited" to "INTEGER",
    )

    @Test
    fun `第十一版四列在实体与迁移SQL里逐字对应`() {
        val entityColumns = MatchRecordEntity::class.java.declaredFields.map { it.name }

        newColumns.forEach { (column, affinity) ->
            assertTrue("实体没声明 $column", entityColumns.contains(column))
            assertTrue(
                "MIGRATION_10_11 缺少 $column 的 ADD COLUMN",
                databaseSource().contains(
                    flatten("ALTER TABLE match_records ADD COLUMN $column $affinity NOT NULL DEFAULT ${
                        defaultValueOf(column)
                    }")
                )
            )
        }

        // 老列不能被顺手删/改名：对局历史是用户资产，掉一列等于抹掉一整列史实。
        listOf(
            "matchId", "gameType", "matchType", "matchTypeLabel", "isFormal", "containsAi",
            "playerCount", "startedAt", "endedAt", "durationMs", "winnerPlayerId",
            "legCount", "legsToWin", "startScore", "x01Mode", "doubleOut", "doubleIn",
            "overtimeRule", "outMode", "inMode", "bullMode", "maxRounds", "cricketVariant",
            "targetSetCsv", "endedByRoundLimit", "totalDarts"
        ).forEach { assertTrue("v10 老列 $it 不见了", entityColumns.contains(it)) }
    }

    @Test
    fun `每列默认值在实体与迁移里一致`() {
        // 实体侧的 `@ColumnInfo(defaultValue = ...)` 与迁移 SQL 的 DEFAULT 必须逐字一致，
        // 否则 Room 的迁移后 schema 校验会失败（"Migration didn't properly handle"）。
        // 注解写在**实体**上，所以这里读实体源码；迁移 SQL 在 Database.kt 里。
        val entity = entitySource()

        newColumns.forEach { (column, _) ->
            assertTrue(
                "实体 $column 缺 @ColumnInfo(defaultValue = \"${defaultValueOf(column)}\")",
                entity.contains("""defaultValue="${defaultValueOf(column)}"""")
            )
            assertTrue(
                "MIGRATION_10_11 的 $column 缺 DEFAULT ${defaultValueOf(column)}",
                databaseSource().contains(
                    flatten("ADD COLUMN $column ${affinityOf(column)} NOT NULL DEFAULT ${defaultValueOf(column)}")
                )
            )
        }
    }

    @Test
    fun `每列只加一次且十到十一迁移已注册`() {
        val source = databaseSource()

        newColumns.forEach { (column, _) ->
            val hits = Regex("ADDCOLUMN$column(TEXT|INTEGER)").findAll(source).count()
            assertEquals("$column 被加了 $hits 次（应为 1 次）", 1, hits)
        }

        // ★不钉字面量版本号：以**已导出的最大 schema 号**为准。
        val latest = latestSchemaVersion()
        assertTrue(
            "version 应为最新导出的 schema 号 $latest",
            Regex("@Database\\([^)]*version=$latest").containsMatchIn(source)
        )
        assertTrue("addMigrations 未注册 MIGRATION_10_11", source.contains("MIGRATION_10_11"))

        // 对局历史是用户资产：绝不能退化成静默清库。
        assertFalse(source.contains("fallbackToDestructiveMigration()"))
    }

    @Test
    fun `导出的第十一版schema与迁移SQL三方一致`() {
        val json = flatten(schemaFile("11.json").readText())
        assertTrue("schema version 不是 11", json.contains("\"version\":11"))

        newColumns.forEach { (column, affinity) ->
            assertTrue(
                "$column 在 11.json 里不是 $affinity NOT NULL",
                json.contains("\"columnName\":\"$column\",\"affinity\":\"$affinity\",\"notNull\":true")
            )
        }

        // 升级是纯增量：v8/v9/v10 的东西在 v11 里必须原样还在。
        listOf("prescriptionMetric", "pressureMode", "versus_match_records", "checkout_rush_attempts")
            .forEach { assertTrue("v11 丢了 $it", json.contains(it)) }
        assertTrue(json.contains("index_dart_hits_profileId_hitAt"))
    }

    @Test
    fun `已发布的九到十迁移不许夹带第十一版新列`() {
        // 从 9→10 的正文（不含它自己的 KDoc）截到下一个 KDoc 注释为止。
        val block = databaseSource()
            .substringAfter("MIGRATION_9_10")
            .substringBefore("/**")

        newColumns.forEach { (column, _) ->
            assertFalse("已发布的 v10 迁移里被塞进了 $column", block.contains(column))
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 迁移 SQL 里的 DEFAULT 字面量（与实体 `@ColumnInfo(defaultValue)` 同源）。 */
    private fun defaultValueOf(column: String): String = when (column) {
        "source" -> "'LOCAL'"
        "roomId" -> "''"
        "winnerName" -> "''"
        "forfeited" -> "0"
        else -> throw AssertionError("未知列 $column")
    }

    private fun affinityOf(column: String): String =
        newColumns.firstOrNull { it.first == column }?.second ?: throw AssertionError("未知列 $column")

    /** 去掉空白与源码里的字符串拼接符号，让「跨行写的 SQL」也能逐字比较。 */
    private fun flatten(text: String): String =
        text.replace(Regex("\\s+"), "").replace("\"+\"", "")

    private fun databaseSource(): String = flatten(
        findFile("app/src/main/java/com/dartvio/app/data/local/DartVioDatabase.kt").readText()
    )

    /** 实体源码：`@ColumnInfo(defaultValue = ...)` 写在实体上，不在 Database 里。 */
    private fun entitySource(): String = flatten(
        findFile("app/src/main/java/com/dartvio/app/data/local/entity/MatchRecordEntity.kt").readText()
    )

    /** 已导出的最大 schema 版本号（`11.json` ⇒ 11）。 */
    private fun latestSchemaVersion(): Int = schemaDir()
        .listFiles()
        ?.mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
        ?.maxOrNull()
        ?: throw AssertionError("schemas 目录下没有 N.json")

    private fun schemaDir(): File = schemaFile("11.json").parentFile

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
