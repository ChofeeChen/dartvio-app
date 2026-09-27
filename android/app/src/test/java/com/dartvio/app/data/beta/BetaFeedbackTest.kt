package com.dartvio.app.data.beta

import com.dartvio.app.data.achievement.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 反馈闭环（本地计数 + 包内采纳清单 + 模板正文）的契约测试。
 *
 * 测的重点不是「能不能存进去」，而是**这个数字值不值得信任**：
 * 用户会因为「我提了、数字涨了、下一版真的改了」而继续提；反过来，
 * 一个会自己往上爬、或者退不回来的计数器，第二次发现就没人再信了。
 */
class BetaFeedbackTest {

    private fun prefs() = InMemorySharedPreferences()

    @Test
    fun nothingSentYetReadsZero() {
        val p = prefs()
        assertEquals(0, BetaFeedback.sentCount(p))
        assertNull(BetaFeedback.lastSentAtMillis(p))
    }

    @Test
    fun eachConfirmedFeedbackCountsOnce() {
        val p = prefs()
        assertEquals(1, BetaFeedback.recordSent(p, atMillis = 1_000L))
        assertEquals(2, BetaFeedback.recordSent(p, atMillis = 2_000L))
        assertEquals(2, BetaFeedback.sentCount(p))
        assertEquals(2_000L, BetaFeedback.lastSentAtMillis(p))
    }

    @Test
    fun doubleTapInTheSameMillisecondCountsOnce() {
        // 手抖点两下不能算两条：这是给人看的进度感，不是精确审计。
        val p = prefs()
        BetaFeedback.recordSent(p, atMillis = 5_000L)
        assertEquals(1, BetaFeedback.recordSent(p, atMillis = 5_000L))
    }

    @Test
    fun undoBringsTheNumberBackDown() {
        // 用户说「那条没发出去」时必须能退回来 —— 只会往上走的计数器没有信用。
        val p = prefs()
        BetaFeedback.recordSent(p, atMillis = 1_000L)
        BetaFeedback.recordSent(p, atMillis = 2_000L)

        assertEquals(1, BetaFeedback.undoLast(p))
        assertEquals(1_000L, BetaFeedback.lastSentAtMillis(p))

        assertEquals(0, BetaFeedback.undoLast(p))
        assertEquals(0, BetaFeedback.undoLast(p))
    }

    @Test
    fun prefsWouldRatherCorruptThanCountWrong() {
        // 万一以后有人往这个 key 里塞了别的东西，宁可读成 0 也不要崩 / 不要虚高。
        val p = prefs()
        p.edit().putString("sent_at_millis", "not_a_number").commit()
        assertEquals(0, BetaFeedback.sentCount(p))
    }
}

class BetaFeedbackMessageTest {

    @Test
    fun messageCarriesTheContextWeNeedToReproduceAndLeavesTheRestBlank() {
        val body = BetaFeedback.buildMessage(
            usageId = "a1b2c3d4",
            versionName = "0.1.12-beta-demo",
            dateText = "2026-09-26",
        )

        // 能据此定位到人、版本与时间，我们才可能复现。
        assertTrue(body.contains("a1b2c3d4"))
        assertTrue(body.contains("0.1.12-beta-demo"))
        assertTrue(body.contains("2026-09-26"))

        // 九个字段一个都不能少：少一个，收回来的反馈就进不了汇总表。
        listOf(
            "1. 发生位置", "2. 类型", "3. 严重程度",
            "4. 我原本想做的事", "5. 实际发生了", "6. 期望是",
            "7. 能否复现", "8. 截图或录屏",
        ).forEach { assertTrue("缺少字段：$it", body.contains(it)) }
    }

    @Test
    fun missingUsageIdIsStatedNotSilentlyBlank() {
        // 没开统计的包确实没有体验 ID —— 留一个空格让人猜，不如直接说明。
        val body = BetaFeedback.buildMessage(
            usageId = null,
            versionName = "0.1.12",
            dateText = "2026-09-26",
        )
        assertTrue(body.contains("留空即可"))
        assertFalse(body.contains("null"))
    }
}

class BetaFeedbackAckTest {

    @Test
    fun everyAdoptionNamesTheVersionItLandedIn() {
        // 「已采纳」的价值全在于能被核对：说「已采纳」却不写落在哪一版，等于没说。
        BetaFeedbackAck.items.forEach {
            assertTrue(it.shippedIn.isNotBlank() && it.shippedIn.startsWith("v"))
            assertTrue(it.what.isNotBlank())
            assertTrue(it.date.isNotBlank())
        }
    }

    @Test
    fun listIsNotEmptyOrTheProfileEntryWouldVanish() {
        // 「我的」页靠这份清单决定是否显示入口；空清单 = 这一版无采纳可回报。
        assertTrue(BetaFeedbackAck.items.isNotEmpty())
    }
}
