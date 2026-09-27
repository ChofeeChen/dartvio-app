package com.dartvio.app.domain.rules

import com.dartvio.app.domain.model.Dart
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.domain.practice.CheckoutTargetFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结镖路线统一事实出口（[X01Rules.checkoutRoutes]）的测试 —— 极速结镖的选题正确性完全建立在这上面。
 *
 * 覆盖 PRD §十一验收：
 * ① 2-170 每个目标都能给出一到三镖的具体路线；
 * ② Double Out 下不排除 2-19；`remaining=1` 不生成目标。
 */
class CheckoutRoutesTest {

    /** 路线必须以 Double 或牛眼收尾：判定直接复用 [OutMode.finishes]，不在测试里抄一份规则。 */
    private fun isLegalFinish(dart: Dart): Boolean = OutMode.DOUBLE_OUT.finishes(dart)

    @Test
    fun `一分为无路可走`() {
        assertFalse(X01Rules.hasCheckoutRoute(1))
        assertTrue(X01Rules.checkoutRoutes(1).isEmpty())
    }

    @Test
    fun `二分到十九分都有合法路线`() {
        (2..19).forEach { score ->
            val routes = X01Rules.checkoutRoutes(score)
            assertTrue("应当存在结镖路线：$score", routes.isNotEmpty())
            routes.forEach { route ->
                assertTrue("$score 的路线应以双区/牛眼收尾", isLegalFinish(route.last()))
                assertEquals(score, route.sumOf { it.score })
            }
        }
    }

    @Test
    fun `一百七十分的最佳路线是三镖 T20 T20 BULL`() {
        val routes = X01Rules.checkoutRoutes(170)
        assertTrue(routes.isNotEmpty())
        // 路线表已按「镖数少 → 多、最高单镖分降序」排好，首选即最短且最高单镖分最大者。
        assertEquals(listOf("T20", "T20", "BULL"), routes.first().map { it.label() })
    }

    @Test
    fun `超过一七零分与零分都没有路线`() {
        assertFalse(X01Rules.hasCheckoutRoute(0))
        assertFalse(X01Rules.hasCheckoutRoute(171))
        assertFalse(X01Rules.hasCheckoutRoute(300))
    }

    @Test
    fun `每个候选目标的每条路线都在一到三镖内且总分等于目标`() {
        val candidates = CheckoutTargetFactory.candidates()
        assertTrue("候选目标不应为空", candidates.isNotEmpty())
        candidates.forEach { target ->
            val routes = X01Rules.checkoutRoutes(target)
            assertTrue("目标 $target 必须有路线", routes.isNotEmpty())
            routes.forEach { route ->
                assertTrue("路线镖数 1..3：$target", route.size in 1..CheckoutRushMaxDarts)
                assertTrue("必须以双区/牛眼收尾：$target", isLegalFinish(route.last()))
                assertEquals("路线总分必须等于目标：$target", target, route.sumOf { it.score })
            }
        }
    }

    @Test
    fun `候选集合不包含零与一分`() {
        CheckoutTargetFactory.candidates().forEach {
            assertTrue("候选目标必须 >= 2，实际 $it", it >= 2)
        }
    }

    @Test
    fun `常见分数都在题库里`() {
        listOf(2, 3, 19, 20, 32, 40, 50, 60, 100, 121, 141, 167, 170).forEach {
            assertTrue("应存在路线：$it", X01Rules.hasCheckoutRoute(it))
        }
    }

    private companion object {
        /** 3 镖是 X01 一个回合的上限，也是「能不能在一回合内结镖」的前提。 */
        const val CheckoutRushMaxDarts = 3
    }
}
