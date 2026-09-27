package com.dartvio.app.ui.setup

import com.dartvio.app.data.achievement.InMemorySharedPreferences
import com.dartvio.app.domain.model.AiAvatar
import com.dartvio.app.domain.model.AiDifficulty
import com.dartvio.app.domain.model.HumanAvatar
import com.dartvio.app.domain.model.MatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「保存设置」的读写口径。
 *
 * 覆盖点集中在**回落规则与作用域边界**：
 *  - 这些键会随着玩法增减长期漂移，一个坏掉的键不应该把玩家改过的另外几项一起废掉；
 *  - 「哪一层的东西存在哪一档」直接决定玩家切玩法时会不会看到自己的设置被改
 *    （对手 / AI 强度 = 比赛层，跨玩法一份；赛制 / 玩法规则 = 玩法层，按玩法各一份）；
 *  - 键名与旧版一字未改，只是位置从玩法档挪到了比赛层，所以旧存档必须还能读回来。
 */
class SetupDefaultsStoreTest {

    private val prefs = InMemorySharedPreferences()

    @Test
    fun `未保存过时读出默认值且没有上次玩法`() {
        assertEquals(MatchDefaults.DEFAULT, SetupDefaultsStore.readMatch(prefs))
        assertEquals(
            CricketRuleDefaults.DEFAULT,
            SetupDefaultsStore.readRules(prefs, CricketMode.TACTICS)
        )
        assertNull(SetupDefaultsStore.readLastMode(prefs))
    }

    @Test
    fun `保存后原样读回并记住上次玩法`() {
        // AI 难度与对手头像分开存：头像答「是谁」、难度答「多强」，
        // 少了这一档，「机器人四 + 专业难度」这种组合就存不住。
        val match = MatchDefaults(
            versusAi = true,
            humanOpponentKeys = "${HumanAvatar.HUMAN_2.key},${HumanAvatar.HUMAN_4.key}",
            aiOpponentKeys = "${AiAvatar.AI_3.key},${AiAvatar.AI_4.key}",
            aiDifficulty = AiDifficulty.ADVANCED,
            smartAi = false,
        )
        val rules = CricketRuleDefaults(
            matchMode = MatchMode.CASUAL,
            legsToWin = 5,
            tacticsRoundLimit = 20,
            overkill = true,
        )
        SetupDefaultsStore.writeMatch(prefs, match)
        SetupDefaultsStore.writeRules(prefs, CricketMode.TACTICS, rules)

        assertEquals(match, SetupDefaultsStore.readMatch(prefs))
        assertEquals(rules, SetupDefaultsStore.readRules(prefs, CricketMode.TACTICS))
        assertEquals(CricketMode.TACTICS, SetupDefaultsStore.readLastMode(prefs))
    }

    @Test
    fun `玩法层各玩法各自独立互不串味`() {
        SetupDefaultsStore.writeRules(
            prefs,
            CricketMode.STANDARD,
            CricketRuleDefaults(legsToWin = 2)
        )
        SetupDefaultsStore.writeRules(
            prefs,
            CricketMode.TACTICS,
            CricketRuleDefaults(legsToWin = 5)
        )

        assertEquals(2, SetupDefaultsStore.readRules(prefs, CricketMode.STANDARD).legsToWin)
        assertEquals(5, SetupDefaultsStore.readRules(prefs, CricketMode.TACTICS).legsToWin)
        // lastMode 跟随最后一次写入
        assertEquals(CricketMode.TACTICS, SetupDefaultsStore.readLastMode(prefs))
    }

    @Test
    fun `对手在比赛层，换玩法不影响它`() {
        SetupDefaultsStore.writeMatch(
            prefs,
            MatchDefaults(versusAi = true, aiOpponentKeys = "${AiAvatar.AI_2.key},${AiAvatar.AI_3.key}")
        )
        SetupDefaultsStore.writeRules(
            prefs,
            CricketMode.STANDARD,
            CricketRuleDefaults(legsToWin = 2)
        )

        // 换到没存过的玩法：规则回默认，但对手仍是那一份
        // —— 这是「对战卡提到一级页后，切玩法不会把玩家挑好的对手换掉」的存储侧保证。
        assertEquals(
            CricketRuleDefaults.DEFAULT,
            SetupDefaultsStore.readRules(prefs, CricketMode.TACTICS)
        )
        val match = SetupDefaultsStore.readMatch(prefs)
        assertTrue(match.versusAi)
        assertEquals(
            2,
            OpponentSelection.fromKeys(match.humanOpponentKeys, match.aiOpponentKeys).ais.size
        )
    }

    @Test
    fun `Random 只记住标量不记住目标集`() {
        SetupDefaultsStore.writeRules(
            prefs,
            CricketMode.RANDOM,
            CricketRuleDefaults(legsToWin = 4)
        )

        val keys = prefs.all.keys.filter { it.startsWith("cricket.RANDOM.") }
        assertTrue("存下的键不该含任何目标集痕迹：$keys", keys.none { it.contains("target", true) })
        // 且读回来的时候不带上一次抽出的目标集（由状态层保证每次重抽，这里只保证没存）
        assertEquals(4, SetupDefaultsStore.readRules(prefs, CricketMode.RANDOM).legsToWin)
    }

    @Test
    fun `单个键越界只回落该键其余照读`() {
        prefs.edit()
            .putInt("cricket.STANDARD.legsToWin", 99)
            .putInt("cricket.STANDARD.roundLimit", 7)
            .putString("cricket.STANDARD.matchMode", MatchMode.CASUAL.name)
            // 枚举键写一个不存在的名字：必须回落默认档，而不是抛异常或留空
            .putString("match.aiDifficulty", "NOT_A_DIFFICULTY")
            .putBoolean("match.versusAi", true)
            .apply()

        val rules = SetupDefaultsStore.readRules(prefs, CricketMode.STANDARD)
        assertEquals(CricketRuleDefaults.DEFAULT.legsToWin, rules.legsToWin)
        assertEquals(CricketRuleDefaults.DEFAULT.tacticsRoundLimit, rules.tacticsRoundLimit)
        assertEquals(MatchMode.CASUAL, rules.matchMode)

        val match = SetupDefaultsStore.readMatch(prefs)
        assertEquals(MatchDefaults.DEFAULT.aiDifficulty, match.aiDifficulty)
        assertTrue("合法的键必须保住", match.versusAi)
    }

    @Test
    fun `玩法名失效时上次玩法返回 null`() {
        prefs.edit().putString("cricket.lastMode", "SOMETHING_GONE").apply()
        assertNull(SetupDefaultsStore.readLastMode(prefs))
    }

    @Test
    fun `对手 key 失效时回落默认对手`() {
        // 整档都活不下来 ⇒ 回落默认（空选择会让设置页一个选中态都没有，比给默认对手更糟）
        val allUnknown = OpponentSelection.fromKeys("nope", "nope")
        assertEquals(listOf(HumanAvatar.HUMAN_2), allUnknown.humans)
        assertEquals(listOf(AiAvatar.AI_1), allUnknown.ais)

        // 一串里坏一个只丢那一个，其余照留
        val partial = OpponentSelection.fromKeys("${HumanAvatar.HUMAN_3.key},nope", "")
        assertEquals(listOf(HumanAvatar.HUMAN_3), partial.humans)
        assertEquals(listOf(AiAvatar.AI_1), partial.ais)
    }

    // ---- 旧存档迁移：对手以前跟玩法存在一起（只读迁移源） ----

    @Test
    fun `旧版按玩法存的对手能迁移读回`() {
        prefs.edit()
            .putString("cricket.lastMode", CricketMode.TACTICS.name)
            .putBoolean("cricket.TACTICS.versusAi", true)
            .putString("cricket.TACTICS.aiOpponents", AiAvatar.AI_2.key)
            .putBoolean("cricket.STANDARD.versusAi", false)
            .putString("cricket.STANDARD.aiOpponents", AiAvatar.AI_1.key)
            .apply()

        // 取的是「上次用过的玩法」那一份，而不是枚举里第一个存过的档
        val match = SetupDefaultsStore.readMatch(prefs)
        assertTrue(match.versusAi)
        assertEquals(
            listOf(AiAvatar.AI_2),
            OpponentSelection.fromKeys(null, match.aiOpponentKeys).ais
        )
    }

    @Test
    fun `旧版没记玩法时按枚举顺序取第一个存过的档`() {
        prefs.edit()
            .putBoolean("cricket.CUT_THROAT.versusAi", true)
            .putString("cricket.CUT_THROAT.aiOpponents", AiAvatar.AI_4.key)
            .apply()

        val match = SetupDefaultsStore.readMatch(prefs)
        assertTrue(match.versusAi)
        assertEquals(
            listOf(AiAvatar.AI_4),
            OpponentSelection.fromKeys(null, match.aiOpponentKeys).ais
        )
    }

    @Test
    fun `比赛层新键写下后以新键为准`() {
        prefs.edit()
            .putBoolean("cricket.STANDARD.versusAi", false)
            .putString("cricket.STANDARD.aiOpponents", AiAvatar.AI_1.key)
            .apply()
        SetupDefaultsStore.writeMatch(
            prefs,
            MatchDefaults(versusAi = true, aiOpponentKeys = AiAvatar.AI_3.key)
        )

        val match = SetupDefaultsStore.readMatch(prefs)
        assertTrue(match.versusAi)
        assertEquals(
            listOf(AiAvatar.AI_3),
            OpponentSelection.fromKeys(null, match.aiOpponentKeys).ais
        )
    }
}
