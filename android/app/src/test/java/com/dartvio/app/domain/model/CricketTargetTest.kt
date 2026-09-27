package com.dartvio.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 目标位标识的可执行规格（M2 §4.8⑦ 二期 2A；提示词 §2.1、§2.2）。
 *
 * 这里钉的是**持久化与协议契约**：token 一经发布就不能改。所以每个取值都写死在断言里，
 * 而不是「编码后能解回来就行」—— 后者对任何自洽的编码都成立，等于没测。
 */
class CricketTargetTest {

    @Test
    fun `数字分区的 token 与一期落库值逐字节相同`() {
        assertEquals("20", CricketTarget.Number(20).token)
        assertEquals("15", CricketTarget.Number(15).token)
        // Bull 的 token 是一期就写进 firstClosedCsv 的 "25"，**不是** "BULL"；
        // 「Bull」是界面文案，不是标识。这两件事混起来会让历史数据读不出来。
        assertEquals("25", CricketTarget.BULL.token)
    }

    @Test
    fun `默认目标集顺序与一期一致`() {
        // 顺序承载语义（UI 行序、targetSetCsv 空串语义），不要顺手改成升序。
        assertEquals(
            listOf("20", "19", "18", "17", "16", "15", "25"),
            CricketTarget.DEFAULT_TARGETS.map { it.token }
        )
    }

    @Test
    fun `类别档 token 是前缀`() {
        assertEquals("D", CricketTarget.Category(TargetCategory.DOUBLES).token)
        assertEquals("T", CricketTarget.Category(TargetCategory.TRIPLES).token)
    }

    @Test
    fun `parse 忽略大小写与空白`() {
        assertEquals(CricketTarget.Number(20), CricketTarget.parse(" 20 "))
        assertEquals(CricketTarget.Number(15), CricketTarget.parse("15"))
        assertEquals(CricketTarget.Category(TargetCategory.DOUBLES), CricketTarget.parse("d"))
        assertEquals(CricketTarget.Category(TargetCategory.TRIPLES), CricketTarget.parse(" T "))
    }

    @Test
    fun `parse 不识别复合 token 也不报错`() {
        // "D20" / "T20" 的含义要到 2C 才冻结。若这里按前缀匹配收成 DOUBLES，
        // 就等于把一个含义未定的 token 静默当成已定义语义用 ——
        // 宁可少一条数据（跳过），也不要一条看着正常、含义是错的数据。
        assertNull(CricketTarget.parse("D20"))
        assertNull(CricketTarget.parse("T20"))
        assertNull(CricketTarget.parse("X"))
        assertNull(CricketTarget.parse(""))
        assertNull(CricketTarget.parse("   "))
        assertNull(CricketTarget.parse(null))
    }

    @Test
    fun `display 给出数据层默认展示名`() {
        // 界面可以再套自己的板面标签（如对局内把 Bull 写成 "BULL"），
        // 但数据层的默认口径就是号位文本。
        assertEquals("20", CricketTarget.Number(20).display)
        assertEquals("25", CricketTarget.BULL.display)
        assertEquals("三倍档", CricketTarget.Category(TargetCategory.TRIPLES).display)
    }

    @Test
    fun `默认目标集编码为空串`() {
        // 空串 = 默认 7 分区，是 targetSetCsv 的历史形态；新的 standard 行必须与之逐字节一致。
        assertEquals("", encodeCricketTargets(CricketTarget.DEFAULT_TARGETS))
    }

    @Test
    fun `CSV 往返保留顺序与类别`() {
        val tactics = listOf(
            CricketTarget.Category(TargetCategory.TRIPLES),
            CricketTarget.Number(19),
            CricketTarget.BULL,
        )

        assertEquals("T,19,25", encodeCricketTargets(tactics))
        assertEquals(tactics, parseCricketTargets(encodeCricketTargets(tactics)))
    }

    @Test
    fun `空串与缺列一律解析为默认目标集`() {
        // 历史行 / 新 standard 行都写空串，语义必须是默认 7 分区；
        // 若解析成空集，hasClosedAll 对任何玩家立刻成立，「关满即胜」会在开局瞬间判定。
        assertEquals(CricketTarget.DEFAULT_TARGETS, parseCricketTargets(""))
        assertEquals(CricketTarget.DEFAULT_TARGETS, parseCricketTargets(null))
        assertEquals(CricketTarget.DEFAULT_TARGETS, parseCricketTargets("   "))
    }

    @Test
    fun `全部无法识别时回落默认目标集`() {
        assertEquals(CricketTarget.DEFAULT_TARGETS, parseCricketTargets("X,D20"))
    }

    @Test
    fun `部分可识别时保留可识别的部分`() {
        // 老版本读到新版本写入的 token：少算一条即可，不能整体丢弃。
        assertEquals(
            listOf(CricketTarget.Number(20), CricketTarget.BULL),
            parseCricketTargets("20,X,25")
        )
    }

    // ============================================ 二期 2C（M2 §4.9）

    @Test
    fun `Tactics 目标集顺序与落盘串冻结`() {
        // 9 档，且 **Bull 收尾**：写成 DEFAULT_TARGETS + [D, T] 会把 Bull 插到两个类别档前面，
        // 行序与落盘串都会与冻结文本不符。
        assertEquals(
            listOf("20", "19", "18", "17", "16", "15", "D", "T", "25"),
            CricketTarget.TACTICS_TARGETS.map { it.token }
        )
        assertEquals("20,19,18,17,16,15,D,T,25", encodeCricketTargets(CricketTarget.TACTICS_TARGETS))
        // 往返：协议 / 落库读回来必须是同一个集与同一个顺序。
        assertEquals(
            CricketTarget.TACTICS_TARGETS,
            parseCricketTargets("20,19,18,17,16,15,D,T,25")
        )
        // 非默认集 ⇒ 落盘串非空（统计准入口就是靠这一点与标准局区分）。
        assertTrue(encodeCricketTargets(CricketTarget.TACTICS_TARGETS).isNotEmpty())
    }

    @Test
    fun `Random 抽取固定 5 个且全部来自默认 7 分区`() {
        repeat(50) { seed ->
            val picked = CricketTarget.pickRandomTargets(Random(seed))

            assertEquals(CricketTarget.RANDOM_TARGET_COUNT, picked.size)
            assertEquals("不得重复", picked.size, picked.toSet().size)
            // 池 = 标准 7 分区，不引入 1–14（§4.9.5④）。
            assertTrue("不得抽出默认池之外的号位", picked.all { it in CricketTarget.DEFAULT_TARGETS })
            // 规范化：顺序必须回到默认顺序，否则同一场抽签会因为顺序不同写出不同的 targetSetCsv。
            assertEquals(
                CricketTarget.DEFAULT_TARGETS.filter { it in picked.toSet() },
                picked
            )
            // 抽 5 个 ≠ 默认 7 个 ⇒ 必然写出非空串，从而被统计准入挡在标准局之外。
            assertTrue(encodeCricketTargets(picked).isNotEmpty())
        }
    }

    @Test
    fun `Random 数量非法时回落默认目标集`() {
        // 空目标集会让 hasClosedAll 对任何玩家立刻成立 —— 开局瞬间结束，比抽签失败严重得多。
        assertEquals(CricketTarget.DEFAULT_TARGETS, CricketTarget.pickRandomTargets(Random(1), count = 0))
        assertEquals(CricketTarget.DEFAULT_TARGETS, CricketTarget.pickRandomTargets(Random(1), count = -3))
    }
}
