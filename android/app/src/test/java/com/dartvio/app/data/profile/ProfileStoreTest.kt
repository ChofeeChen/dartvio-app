package com.dartvio.app.data.profile

import com.dartvio.app.data.achievement.InMemorySharedPreferences
import com.dartvio.app.domain.model.HumanAvatar
import com.dartvio.app.domain.profile.LocalProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 本机玩家档案的持久化契约（第③期 ③B 前置 P0）。
 *
 * 最要紧的一条是 [ProfileStore.ensure] 的**幂等性**：profileId 一旦落盘，就是历史对局的外键。
 * 假如建档逻辑被改成「每次都重新生成」，用户升级一次战绩就全丢一次 —— 而这种破坏
 * 在界面上完全看不出来（榜单只是变空），所以必须由测试守住。
 */
class ProfileStoreTest {

    private fun prefs() = InMemorySharedPreferences()

    @Test
    fun `首次建档只生成一次 ID 后续调用原样返回`() {
        val prefs = prefs()
        var generated = 0

        val first = ProfileStore.ensure(prefs) { generated++; "id-$generated" }
        val second = ProfileStore.ensure(prefs) { generated++; "id-$generated" }

        assertEquals(1, generated)
        assertEquals("id-1", first.profileId)
        assertEquals(first, second)
        assertEquals(first, ProfileStore.read(prefs))
    }

    @Test
    fun `新档案的默认昵称与默认头像`() {
        val profile = ProfileStore.ensure(prefs()) { "id-1" }

        assertEquals(LocalProfile.DEFAULT_NICKNAME, profile.nickname)
        assertEquals(HumanAvatar.HUMAN_1, profile.avatar)
    }

    @Test
    fun `未建档时 read 返回 null 而不是抛异常`() {
        assertNull(ProfileStore.read(prefs()))
    }

    @Test
    fun `ID 为空白时视为未建档`() {
        val prefs = prefs()
        prefs.edit().putString(ProfileStore.KEY_PROFILE_ID, "   ").apply()

        assertNull(ProfileStore.read(prefs))
    }

    @Test
    fun `昵称的前后空白被去除 空白名兜底为默认名`() {
        val prefs = prefs()
        ProfileStore.ensure(prefs) { "id-1" }

        assertEquals("阿达", ProfileStore.updateNickname(prefs, "  阿达  ")?.nickname)
        assertEquals("阿达", ProfileStore.read(prefs)?.nickname)

        assertEquals(LocalProfile.DEFAULT_NICKNAME, ProfileStore.updateNickname(prefs, "   ")?.nickname)
        assertEquals(LocalProfile.DEFAULT_NICKNAME, ProfileStore.read(prefs)?.nickname)
    }

    @Test
    fun `昵称与头像可以独立更新且互不覆盖`() {
        val prefs = prefs()
        ProfileStore.ensure(prefs) { "id-1" }

        ProfileStore.updateNickname(prefs, "阿达")
        val updated = ProfileStore.updateAvatar(prefs, HumanAvatar.HUMAN_3)

        assertEquals("id-1", updated?.profileId)
        assertEquals("阿达", updated?.nickname)
        assertEquals(HumanAvatar.HUMAN_3, updated?.avatar)
        assertEquals(updated, ProfileStore.read(prefs))
    }

    @Test
    fun `未建档时更新返回 null 且不隐式建档`() {
        val prefs = prefs()

        assertNull(ProfileStore.updateNickname(prefs, "阿达"))
        assertNull(ProfileStore.updateAvatar(prefs, HumanAvatar.HUMAN_2))
        assertNull(ProfileStore.read(prefs))
    }

    @Test
    fun `未知头像 key 回落 HUMAN 1 而不是让整份档案失效`() {
        val prefs = prefs()
        prefs.edit()
            .putString(ProfileStore.KEY_PROFILE_ID, "id-1")
            .putString(ProfileStore.KEY_AVATAR, "AI_4")
            .apply()

        val profile = ProfileStore.read(prefs)

        assertEquals("id-1", profile?.profileId)
        assertEquals(HumanAvatar.HUMAN_1, profile?.avatar)
    }

    @Test
    fun `clear 只清档案键 不碰同一 prefs 下的其它键`() {
        val prefs = prefs()
        ProfileStore.ensure(prefs) { "id-1" }
        prefs.edit().putString("unrelated", "keep").apply()

        ProfileStore.clear(prefs)

        assertNull(ProfileStore.read(prefs))
        assertEquals("keep", prefs.getString("unrelated", null))
    }

    @Test
    fun `清档后重新建档得到新 ID`() {
        val prefs = prefs()
        assertEquals("id-1", ProfileStore.ensure(prefs) { "id-1" }.profileId)

        ProfileStore.clear(prefs)

        assertEquals("id-2", ProfileStore.ensure(prefs) { "id-2" }.profileId)
    }

    @Test
    fun `生成的 ID 去掉了连字符且长度固定`() {
        val id = LocalProfile.newProfileId()

        // UUID 去掉连字符后取前 16 位：短到可以肉眼核对，又不至于在本机规模下碰撞
        assertEquals(16, id.length)
        assertFalse(id.contains('-'))
    }
}
