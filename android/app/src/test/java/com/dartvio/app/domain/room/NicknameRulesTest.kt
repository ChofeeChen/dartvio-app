package com.dartvio.app.domain.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 昵称与短码规则。
 *
 * 这里守的是两件事：**昵称不唯一**（允许两个人叫 ABC），以及**身份必须稳定**
 * （同一个 playerId 永远得到同一个短码）。前者是产品决策，后者是用户能靠它认人的前提。
 */
class NicknameRulesTest {

    @Test
    fun `sanitize 去空白并截断到上限`() {
        assertEquals("ABC", NicknameRules.sanitize("  ABC  "))
        assertEquals("123456789012", NicknameRules.sanitize("1234567890123456"))
        assertEquals(12, NicknameRules.sanitize("啊".repeat(50)).length)
    }

    @Test
    fun `sanitize 去掉控制字符`() {
        assertEquals("AB", NicknameRules.sanitize("A\n\tB"))
    }

    @Test
    fun `空昵称被拒`() {
        val result = NicknameRules.check("   ")
        assertTrue(result is NicknameRules.Check.Rejected)
        assertEquals(NicknameRules.Reject.BLANK, (result as NicknameRules.Check.Rejected).reason)
    }

    @Test
    fun `超长昵称被拒`() {
        val result = NicknameRules.check("1234567890123")
        assertTrue(result is NicknameRules.Check.Rejected)
        assertEquals(NicknameRules.Reject.TOO_LONG, (result as NicknameRules.Check.Rejected).reason)
    }

    @Test
    fun `敏感词被拒`() {
        assertTrue(NicknameRules.check("代练第一") is NicknameRules.Check.Rejected)
        // 插空格与点号不该绕过：清洗时先去掉分隔符。
        assertTrue(NicknameRules.check("加 微。信") is NicknameRules.Check.Rejected)
    }

    @Test
    fun `通过时返回清洗后的值`() {
        val result = NicknameRules.check("  ABC  ")
        assertTrue(result is NicknameRules.Check.Ok)
        assertEquals("ABC", (result as NicknameRules.Check.Ok).value)
    }

    @Test
    fun `短码长度固定且不含易混字符`() {
        val code = NicknameRules.shortCode("dv_0f1i8l5o2ab3c4d5e6f")
        assertEquals(4, code.length)
        code.forEach { assertTrue("短码只能含字母数字：$code", it.isLetterOrDigit()) }
        code.forEach { assertTrue("短码含易混字符：$code", it !in "IO01L5") }
    }

    @Test
    fun `短码对同一身份稳定`() {
        val id = "dv_9a8b7c6d5e4f3a2b1c0d"
        assertEquals(NicknameRules.shortCode(id), NicknameRules.shortCode(id))
    }

    @Test
    fun `短码能区分不同身份`() {
        assertTrue(
            NicknameRules.shortCode("dv_aaaaaaaaaaaaaaaaaaaa") !=
                NicknameRules.shortCode("dv_bbbbbbbbbbbbbbbbbbbb")
        )
    }

    @Test
    fun `展示名是昵称加短码`() {
        assertEquals("ABC·${NicknameRules.shortCode("dv_abc")}", NicknameRules.display("ABC", "dv_abc"))
        // 昵称没填时用兜底，展示不应出现「昵称·短码」里昵称缺失的空档。
        assertTrue(NicknameRules.display("", "dv_abc").startsWith(NicknameRules.FALLBACK))
    }

    @Test
    fun `仅限好友需要房间号`() {
        assertTrue(RoomJoinPolicy.FRIENDS_ONLY.requiresRoomCode)
        assertTrue(!RoomJoinPolicy.OPEN.requiresRoomCode)
    }

    @Test
    fun `未知 key 按开放招募处理`() {
        // 旧端 / 缺列时不能把房间藏起来：那表现为「明明有人在建房，大厅却是空的」。
        assertEquals(RoomJoinPolicy.OPEN, RoomJoinPolicy.fromKey(null))
        assertEquals(RoomJoinPolicy.OPEN, RoomJoinPolicy.fromKey("???"))
        assertEquals(RoomJoinPolicy.FRIENDS_ONLY, RoomJoinPolicy.fromKey("friends"))
    }

    @Test
    fun `重名只在同一个房间里才算`() {
        val members = listOf(
            RoomMember("dv_a", "ABC", "HUMAN_1"),
            RoomMember("dv_b", "ABC ", "HUMAN_2"), // 尾部空格算同名
            RoomMember("dv_c", "XYZ", "HUMAN_3")
        )
        assertEquals(listOf("ABC"), NicknameRules.duplicates(members))
        assertEquals(
            emptyList<String>(),
            NicknameRules.duplicates(listOf(RoomMember("dv_a", "ABC", "HUMAN_1")))
        )
    }

    @Test
    fun `没填昵称的人不互相算重名`() {
        // 两个都空着不是"重名"，是"都还没填" —— 提示改昵称在这里没有意义。
        val members = listOf(
            RoomMember("dv_a", " ", "HUMAN_1"),
            RoomMember("dv_b", "", "HUMAN_2")
        )
        assertEquals(emptyList<String>(), NicknameRules.duplicates(members))
    }
}
