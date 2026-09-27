package com.dartvio.app.domain.achievement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成就清单结构单测（决策① / ② / ⑥ 的可执行规格）。
 *
 * 清单数量与数据源分层是**产品决策的硬约束**，不是实现细节：
 * 谁想改这里的数字，必须先改 PRD。
 */
class AchievementCatalogTest {

    @Test
    fun `清单共 26 项，其中 P0 18 项、P1 8 项`() {
        assertEquals(26, AchievementCatalog.ALL.size)
        assertEquals(18, AchievementCatalog.countOf(AchievementPriority.P0))
        assertEquals(8, AchievementCatalog.countOf(AchievementPriority.P1))
    }

    @Test
    fun `分组计数为 里程碑 6 神枪手 8 Cricket 6 练习室 6`() {
        assertEquals(6, AchievementCatalog.countOf(AchievementGroup.MILESTONE))
        assertEquals(8, AchievementCatalog.countOf(AchievementGroup.X01))
        assertEquals(6, AchievementCatalog.countOf(AchievementGroup.CRICKET))
        assertEquals(6, AchievementCatalog.countOf(AchievementGroup.PRACTICE))
    }

    @Test
    fun `ID 唯一，且标题描述单位与门槛均合法`() {
        val ids = AchievementCatalog.ALL.map { it.id }
        assertEquals("成就 ID 必须唯一（ID 是落盘键）", ids.size, ids.toSet().size)
        AchievementCatalog.ALL.forEach { def ->
            assertTrue("${def.id} 门槛必须为正", def.target > 0)
            assertTrue("${def.id} 标题不能为空", def.title.isNotBlank())
            assertTrue("${def.id} 描述不能为空", def.description.isNotBlank())
            assertTrue("${def.id} 单位不能为空", def.unit.isNotBlank())
        }
    }

    @Test
    fun `连胜类成就只认正式赛数据源`() {
        listOf("x01_streak_3", "x01_streak_5", "x01_streak_10").forEach { id ->
            val def = AchievementCatalog.of(id)
            assertTrue("$id 应在清单中", def != null)
            assertEquals(
                "$id 属于排名语义，必须限定 S 级正式赛（决策②）",
                AchievementDataSource.FORMAL,
                def!!.dataSource,
            )
        }
    }

    @Test
    fun `入门层里程碑成就的数据源为任意对局`() {
        listOf(
            "milestone_first_match",
            "milestone_first_win",
            "milestone_first_180",
            "milestone_checkout_100",
            "milestone_match_10",
        ).forEach { id ->
            assertEquals(
                "$id 是入门层成就，须放宽到任意对局（决策②）",
                AchievementDataSource.CASUAL,
                AchievementCatalog.of(id)!!.dataSource,
            )
        }
    }
}
