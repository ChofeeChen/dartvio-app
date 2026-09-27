package com.dartvio.app.ui.setup

import com.dartvio.app.domain.model.AiAvatar
import com.dartvio.app.domain.model.CricketTarget
import com.dartvio.app.domain.model.MatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 设置状态的两层切换语义（纯 JVM，不需要 Robolectric）。
 *
 * 这张测试保护的是「一级页改了对手 → 切玩法 → 对手不许变」这条体验承诺：
 *  - 玩法层（赛制 / 局数 / 轮数上限 / Overkill）跟着玩法走；
 *  - 比赛层（对手 / AI 强度）跨玩法不动；
 *  - 本会话改过但还没落盘的值，切走再切回时不许被盘上那一份盖掉。
 */
class CricketSetupStateTest {

    @Test
    fun `切玩法时玩法层设置跟着走、对手不动`() {
        val state = CricketSetupState()
        state.applyMatchDefaults(
            MatchDefaults(versusAi = true, aiOpponentKeys = AiAvatar.AI_2.key)
        )
        state.applyRuleDefaults(CricketRuleDefaults(legsToWin = 5))
        val opponents = state.opponents

        state.selectMode(CricketMode.TACTICS, diskRules = CricketRuleDefaults(legsToWin = 2))

        assertEquals(CricketMode.TACTICS, state.cricketMode)
        assertEquals(2, state.legsToWin)
        assertTrue(state.versus == VersusMode.HUMAN_VS_AI)
        assertEquals("对手属于比赛层，切玩法不该动", opponents, state.opponents)
    }

    @Test
    fun `切走再切回时本会话的改动不丢`() {
        val state = CricketSetupState()
        // 本会话改了局数但还没点「保存设置」
        state.legsToWin = 5

        state.selectMode(CricketMode.TACTICS, diskRules = CricketRuleDefaults(legsToWin = 2))
        assertEquals(2, state.legsToWin)

        state.selectMode(CricketMode.STANDARD, diskRules = CricketRuleDefaults(legsToWin = 3))
        assertEquals("本会话改过的优先于盘上那一档", 5, state.legsToWin)
    }

    @Test
    fun `重复点当前玩法卡不重放盘上那一档`() {
        val state = CricketSetupState()
        state.legsToWin = 5

        state.selectMode(CricketMode.STANDARD, diskRules = CricketRuleDefaults(legsToWin = 3))

        assertEquals(5, state.legsToWin)
    }

    @Test
    fun `恢复时即便上次用的就是默认玩法也能套用盘上那一档`() {
        val state = CricketSetupState()

        state.restore(
            mode = CricketMode.STANDARD,
            matchDefaults = MatchDefaults(versusAi = true),
            ruleDefaults = CricketRuleDefaults(matchMode = MatchMode.CASUAL, legsToWin = 4)
        )

        assertEquals(CricketMode.STANDARD, state.cricketMode)
        assertEquals(MatchMode.CASUAL, state.mode)
        assertEquals(4, state.legsToWin)
        assertTrue(state.versus == VersusMode.HUMAN_VS_AI)
    }

    @Test
    fun `恢复时没存过玩法也保住初始玩法并恢复比赛层`() {
        val state = CricketSetupState()

        state.restore(
            mode = null,
            matchDefaults = MatchDefaults(versusAi = true, aiOpponentKeys = AiAvatar.AI_3.key),
            ruleDefaults = CricketRuleDefaults.DEFAULT
        )

        assertEquals(CricketMode.STANDARD, state.cricketMode)
        assertTrue(state.versus == VersusMode.HUMAN_VS_AI)
        assertEquals(listOf(AiAvatar.AI_3), state.opponents.ais)
    }

    @Test
    fun `恢复为随机目标时必须在那一刻重抽`() {
        val state = CricketSetupState()

        state.restore(
            mode = CricketMode.RANDOM,
            matchDefaults = MatchDefaults.DEFAULT,
            ruleDefaults = CricketRuleDefaults.DEFAULT,
            random = Random(7)
        )

        assertEquals(CricketTarget.pickRandomTargets(Random(7)), state.targets)
    }
}
