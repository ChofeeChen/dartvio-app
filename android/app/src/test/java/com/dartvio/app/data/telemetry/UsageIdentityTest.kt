package com.dartvio.app.data.telemetry

import com.dartvio.app.data.achievement.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 匿名身份的持久化契约。
 *
 * 这里守的不是「存得对不对」，而是**统计口径会不会被自己污染**：
 * - 票据半截就必须当没身份（否则每次启动都拿坏票据去续期，永远报不上去）；
 * - 票据失效时**不许**重建身份（重建 = 凭空多一个用户，「用户数」直接失真）；
 * - 续期要有提前量（卡在过期那一刻才去续，请求必然打在失效窗口上）。
 *
 * 这几条破坏后界面上完全看不出来 —— 后台只是数字变少或变多，所以只能由测试守住。
 */
class UsageIdentityTest {

    private fun prefs() = InMemorySharedPreferences()

    private fun session(
        userId: String = "11111111-2222-3333-4444-555555555555",
        expiresAt: Long = 1_800_000L,
    ) = UsageIdentity.Session(
        userId = userId,
        accessToken = "access-$userId",
        refreshToken = "refresh-$userId",
        expiresAtMillis = expiresAt,
    )

    @Test
    fun `没建立过身份时读到 null`() {
        assertNull(UsageIdentity.read(prefs()))
    }

    @Test
    fun `落盘后能原样读回`() {
        val prefs = prefs()
        val saved = session()

        UsageIdentity.save(prefs, saved)

        assertEquals(saved, UsageIdentity.read(prefs))
    }

    @Test
    fun `票据不全时视为没有身份`() {
        val prefs = prefs()
        // 手工造出「有 user_id 但没票据」的半截状态：只有绕过 save() 才可能出现，
        // 但一旦出现（旧版本残留 / 别的模块误写），就必须被当成没身份。
        prefs.edit().putString("user_id", "half-baked").apply()

        assertNull(UsageIdentity.read(prefs))
    }

    @Test
    fun `到期前不足提前量就该续期`() {
        val s = session(expiresAt = 100_000L)

        // 正好差一个提前量：此刻就该去续，不能等到 100_000。
        assertTrue(UsageIdentity.isExpired(s, 100_000L - UsageIdentity.REFRESH_SKEW_MILLIS))
        assertFalse(UsageIdentity.isExpired(s, 100_000L - UsageIdentity.REFRESH_SKEW_MILLIS - 1))
    }

    @Test
    fun `过期时间缺失视为该续期`() {
        // 0 是「读不到 / 旧版本写入」的兜底值，当成有效会一路带着坏票据失败下去。
        assertTrue(UsageIdentity.isExpired(session(expiresAt = 0L), 1_000L))
    }

    @Test
    fun `清空身份后读不到`() {
        val prefs = prefs()
        UsageIdentity.save(prefs, session())
        assertEquals("11111111-2222-3333-4444-555555555555", UsageIdentity.read(prefs)!!.userId)

        UsageIdentity.clear(prefs)

        assertNull(UsageIdentity.read(prefs))
    }

    @Test
    fun `展示用的短 ID 只取前 8 位`() {
        assertEquals("11111111", UsageIdentity.shortId("11111111-2222-3333-4444-555555555555"))
        // 太短或为空都不该硬凑出一个假 ID 出来。
        assertEquals("", UsageIdentity.shortId("abc"))
        assertEquals("", UsageIdentity.shortId(null))
    }
}
