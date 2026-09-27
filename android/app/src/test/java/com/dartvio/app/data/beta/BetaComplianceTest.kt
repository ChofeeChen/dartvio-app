package com.dartvio.app.data.beta

import com.dartvio.app.data.achievement.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Beta 合规三件套（隐私同意 / 试用期 / 邀请码后台校验）的契约测试。
 *
 * 测的都是**边界**，不是实现细节：这三处的边界一旦算错，
 * 要么用户被多拦一次、要么本该拦住的没拦住 —— 两者都比功能缺失更难发现。
 */
class BetaPhaseTest {

    private val now = BetaAccess.EXPIRE_AT_MILLIS
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun expiredOnlyAtTheBoundaryItself() {
        assertFalse(BetaAccess.usable(now))
        assertTrue(BetaAccess.usable(now - 1))
    }

    @Test
    fun expiringWindowStartsSevenDaysBefore() {
        assertEquals(BetaAccess.Phase.OK, BetaAccess.phase(now - 8 * day))
        assertEquals(BetaAccess.Phase.EXPIRING, BetaAccess.phase(now - 7 * day))
        assertEquals(BetaAccess.Phase.EXPIRING, BetaAccess.phase(now - 1))
        assertEquals(BetaAccess.Phase.EXPIRED, BetaAccess.phase(now))
    }

    @Test
    fun daysLeftRoundsUpSoItNeverSaysZeroDaysWhileUsable() {
        // 还剩 3 小时也必须说「还剩 1 天」：说 0 天会被读成已经到期。
        assertEquals(1, BetaAccess.daysLeft(now - 3 * 60L * 60L * 1000L))
        assertEquals(7, BetaAccess.daysLeft(now - 7 * day))
        assertEquals(0, BetaAccess.daysLeft(now))
    }
}

class BetaConsentTest {

    private fun prefs() = InMemorySharedPreferences()

    @Test
    fun consentRequiredUntilAnswered() {
        val p = prefs()
        assertTrue(BetaConsent.needsConsent(p))

        BetaConsent.save(p, granted = true)

        assertFalse(BetaConsent.needsConsent(p))
        assertTrue(BetaConsent.granted(p))
    }

    @Test
    fun decliningIsRememberedButDoesNotGrantAnything() {
        val p = prefs()

        // 拒绝必须也记账：不落盘的话每次冷启动都会再弹一次，
        // 反复在被人拒绝的地方弹同一个框，会被当成 bug 反馈回来。
        BetaConsent.save(p, granted = false)

        assertFalse(BetaConsent.needsConsent(p))
        assertFalse(BetaConsent.granted(p))
    }

    @Test
    fun answeringAPolicyVersionDoesNotAnswerTheNextOne() {
        val p = prefs()
        // 直接写下一份曾经同意过的版本号 —— 政策改版后必须重新征求同意。
        p.edit().putInt("answered_policy_version", BetaConsent.POLICY_VERSION + 1).commit()

        assertTrue(BetaConsent.needsConsent(p))
    }
}

class BetaInviteApiTest {

    @Test
    fun acceptedCarriesTheRankWithoutExposingItAsANumberInTheUi() {
        val result = BetaInviteApi.parse("""{"ok":true,"rank":7}""")
        assertTrue(result is BetaInviteApi.Result.Accepted)
        assertEquals(7, (result as BetaInviteApi.Result.Accepted).rank)
    }

    @Test
    fun rejectionCarriesTheMachineReadableReason() {
        assertTrue(BetaInviteApi.parse("""{"ok":false,"reason":"USED_ELSEWHERE"}""")
            is BetaInviteApi.Result.Rejected)
    }

    @Test
    fun unparsableBodyIsOurProblemNotTheUsers() {
        // 解析失败必须落回 Unavailable（离线放行），而不是 Rejected ——
        // 我们后端出问题，不该让用户看到「邀请码无效」。
        assertTrue(BetaInviteApi.parse("not json") is BetaInviteApi.Result.Unavailable)
        assertTrue(BetaInviteApi.parse(null) is BetaInviteApi.Result.Unavailable)
    }

    @Test
    fun rejectionReasonsHaveChineseCopy() {
        BetaInviteApi.messageFor("NOT_FOUND").let { assertTrue(it.contains("不在名单")) }
        BetaInviteApi.messageFor("USED_ELSEWHERE").let { assertTrue(it.contains("另一台设备")) }
        BetaInviteApi.messageFor("REVOKED").let { assertTrue(it.contains("停用")) }
    }
}
