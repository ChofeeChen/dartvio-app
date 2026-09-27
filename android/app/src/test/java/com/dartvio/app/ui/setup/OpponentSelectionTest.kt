package com.dartvio.app.ui.setup

import com.dartvio.app.domain.model.AiAvatar
import com.dartvio.app.domain.model.AiDifficulty
import com.dartvio.app.domain.model.HumanAvatar
import com.dartvio.app.domain.model.PlayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「点选头像 = 人数」的口径。
 *
 * 这套规则换掉了原来那个独立的「玩家人数」控件，所以三条边界必须钉死：
 * 至少留 1 名对手、最多 3 名对手、真人档与 AI 档互不串味。
 */
class OpponentSelectionTest {

    @Test
    fun `默认是 1 名真人对手和 1 名 AI 对手`() {
        val selection = OpponentSelection()
        assertEquals(listOf(HumanAvatar.HUMAN_2), selection.humans)
        assertEquals(listOf(AiAvatar.AI_1), selection.ais)
    }

    @Test
    fun `点选未选中的头像会加入对手`() {
        val selection = OpponentSelection().toggleHuman(HumanAvatar.HUMAN_3)
        assertEquals(listOf(HumanAvatar.HUMAN_2, HumanAvatar.HUMAN_3), selection.humans)
        assertEquals(3, selection.playerCount(VersusMode.HUMAN_VS_HUMAN))
    }

    @Test
    fun `再点一次已选中的头像会移除对手`() {
        val selection = OpponentSelection(humans = listOf(HumanAvatar.HUMAN_2, HumanAvatar.HUMAN_3))
            .toggleHuman(HumanAvatar.HUMAN_3)
        assertEquals(listOf(HumanAvatar.HUMAN_2), selection.humans)
    }

    @Test
    fun `最后一名对手不能被取消`() {
        val selection = OpponentSelection(humans = listOf(HumanAvatar.HUMAN_4))
            .toggleHuman(HumanAvatar.HUMAN_4)
        assertEquals("取消掉最后一名对手会得到一场没有对手的对局", listOf(HumanAvatar.HUMAN_4), selection.humans)
        assertFalse(OpponentSelection.canToggleHuman(selection, HumanAvatar.HUMAN_4))
    }

    @Test
    fun `对手最多 3 名且触顶后不可再点`() {
        val full = OpponentSelection(
            humans = listOf(HumanAvatar.HUMAN_2, HumanAvatar.HUMAN_3, HumanAvatar.HUMAN_4)
        )
        assertEquals(MAX_OPPONENTS, full.humans.size)
        assertEquals(MAX_PLAYERS, full.playerCount(VersusMode.HUMAN_VS_HUMAN))

        // 触顶后加第 4 名对手无效，而且 UI 应当把它显示为不可点（而不是点了没反应）
        val stillFull = full.toggleHuman(HumanAvatar.HUMAN_1)
        assertEquals(full.humans, stillFull.humans)
        assertFalse(OpponentSelection.canToggleHuman(full, HumanAvatar.HUMAN_1))
    }

    @Test
    fun `真人档与 AI 档各存一份互不影响`() {
        val selection = OpponentSelection()
            .toggleHuman(HumanAvatar.HUMAN_3)
            .toggleHuman(HumanAvatar.HUMAN_4) // 真人档 ⇒ 3 名对手
            .toggleAi(AiAvatar.AI_4) // AI 档 ⇒ 2 名对手

        assertEquals(3, selection.opponentCount(VersusMode.HUMAN_VS_HUMAN))
        assertEquals(2, selection.opponentCount(VersusMode.HUMAN_VS_AI))
        assertEquals(4, selection.playerCount(VersusMode.HUMAN_VS_HUMAN))
        assertEquals(3, selection.playerCount(VersusMode.HUMAN_VS_AI))
        // 切回真人档时之前挑好的人还在
        assertEquals(
            listOf(HumanAvatar.HUMAN_2, HumanAvatar.HUMAN_3, HumanAvatar.HUMAN_4),
            selection.humans
        )
        assertEquals(listOf(AiAvatar.AI_1, AiAvatar.AI_4), selection.ais)
    }

    @Test
    fun `key 往返后选择不变`() {
        val selection = OpponentSelection()
            .toggleHuman(HumanAvatar.HUMAN_4)
            .toggleAi(AiAvatar.AI_3)

        val restored = OpponentSelection.fromKeys(selection.humanKeys(), selection.aiKeys())
        assertEquals(selection.humans, restored.humans)
        assertEquals(selection.ais, restored.ais)
    }

    @Test
    fun `本人固定占第 1 席且名字头像取自档案`() {
        val players = buildSetupPlayers(
            versus = VersusMode.HUMAN_VS_HUMAN,
            selection = OpponentSelection(humans = listOf(HumanAvatar.HUMAN_3)),
            selfName = "阿镖",
            selfAvatar = HumanAvatar.HUMAN_4,
            // 真人对战下难度不参与成席，但仍是必填参数：调用方必须显式表态用哪一档。
            aiDifficulty = AiDifficulty.INTERMEDIATE
        )

        assertEquals(2, players.size)
        val self = players.first()
        assertEquals("第 1 席必须是本人：落库时按「第一个非 AI 席位」挂到本机档案上", SELF_SEAT_ID, self.id)
        assertEquals("阿镖", self.name)
        assertEquals(HumanAvatar.HUMAN_4.key, self.avatar)
        assertEquals(PlayerType.HUMAN, self.type)

        assertEquals("玩家 2", players[1].name)
        assertEquals(HumanAvatar.HUMAN_3.key, players[1].avatar)
        assertEquals(PlayerType.HUMAN, players[1].type)
    }

    @Test
    fun `AI 对手按选择顺序成席且共用同一档难度`() {
        val players = buildSetupPlayers(
            versus = VersusMode.HUMAN_VS_AI,
            selection = OpponentSelection(ais = listOf(AiAvatar.AI_3, AiAvatar.AI_4)),
            selfName = "阿镖",
            selfAvatar = HumanAvatar.HUMAN_1,
            aiDifficulty = AiDifficulty.PRO
        )

        assertEquals(3, players.size)
        assertEquals(listOf("p1", "p2", "p3"), players.map { it.id })
        assertEquals(listOf(PlayerType.HUMAN, PlayerType.AI, PlayerType.AI), players.map { it.type })
        // 难度不再从头像推导（2026-09-12 拆分）：设置页那张「AI 难度」卡管本局**全部** AI 对手，
        // 所以 AI_3 / AI_4 两席必须拿到同一档，与它们各自的 emoji 无关。
        assertEquals(
            listOf(AiDifficulty.PRO, AiDifficulty.PRO),
            players.drop(1).map { it.aiDifficulty }
        )
    }

    @Test
    fun `本人昵称空白时回落默认昵称`() {
        val players = buildSetupPlayers(
            versus = VersusMode.HUMAN_VS_HUMAN,
            selection = OpponentSelection(),
            selfName = "   ",
            selfAvatar = HumanAvatar.HUMAN_1,
            aiDifficulty = AiDifficulty.INTERMEDIATE
        )
        assertTrue("空白昵称不该把第 1 席显示成空字符串", players.first().name.isNotBlank())
    }
}
