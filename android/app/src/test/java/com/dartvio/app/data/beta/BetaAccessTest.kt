package com.dartvio.app.data.beta

import com.dartvio.app.data.achievement.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 邀请码白名单 + 激活闸门的契约测试（纯 JVM，SharedPreferences 用内存实现）。
 *
 * 白名单是**编译进包的最终事实**，所以这里测的不是「实现细节」而是「发布承诺」：
 * 恰好 100 个、不重复、格式成立、不含易混字符 —— 任何一条破了，
 * 分发台账就会和 App 实际接受的码对不上。
 */
class BetaInviteCodesTest {

    @Test
    fun exactly100CodesAndNoDuplicates() {
        assertEquals(100, BetaInviteCodes.all.size)
        assertEquals(100, BetaInviteCodes.lookup.size)
    }

    @Test
    fun allCodesMatchDeclaredFormat() {
        BetaInviteCodes.all.forEach { code ->
            assertTrue("格式不符: $code", BetaInviteCodes.FORMAT.matches(code))
        }
    }

    @Test
    fun noConfusableCharactersInCodeBody() {
        // I/O/0/1 被字符集刻意排除：码靠微信传递，这四个字符最容易读错抄错。
        BetaInviteCodes.all.forEach { code ->
            val body = code.removePrefix("DT-")
            assertFalse("含易混字符: $code", body.any { it in "IO01" })
        }
    }
}

class BetaAccessTest {

    private fun prefs() = InMemorySharedPreferences()

    @Test
    fun grantAcceptsWhitelistedCodeAndPersists() {
        val p = prefs()
        assertTrue(BetaAccess.grant(p, BetaInviteCodes.all.first()))
        assertEquals(BetaInviteCodes.all.first(), BetaAccess.activatedCode(p))
    }

    @Test
    fun grantNormalizesCaseAndWhitespace() {
        val p = prefs()
        val code = BetaInviteCodes.all.first()
        assertTrue(BetaAccess.grant(p, "  ${code.lowercase()}  "))
        assertEquals(code, BetaAccess.activatedCode(p))
    }

    @Test
    fun grantRejectsUnknownCodeWithoutSideEffects() {
        val p = prefs()
        assertFalse(BetaAccess.grant(p, "DT-AAAAAA"))
        assertFalse(BetaAccess.grant(p, ""))
        assertFalse(BetaAccess.grant(p, "dt-2ee89w-extra"))
        assertNull(BetaAccess.activatedCode(p))
    }

    @Test
    fun expiryBoundaryIsInclusive() {
        assertFalse(BetaAccess.isExpired(BetaAccess.EXPIRE_AT_MILLIS - 1))
        assertTrue(BetaAccess.isExpired(BetaAccess.EXPIRE_AT_MILLIS))
    }

    @Test
    fun notDecidedUntilEitherChoiceIsMade() {
        val p = prefs()
        assertFalse(BetaAccess.hasDecided(p))

        BetaAccess.markSkipped(p)

        assertTrue(BetaAccess.hasDecided(p))
    }

    @Test
    fun grantAlsoCountsAsDecided() {
        val p = prefs()
        assertTrue(BetaAccess.grant(p, BetaInviteCodes.all.first()))

        // 填过码的人不该在下次启动再被首启页拦一次。
        assertTrue(BetaAccess.hasDecided(p))
    }
}
