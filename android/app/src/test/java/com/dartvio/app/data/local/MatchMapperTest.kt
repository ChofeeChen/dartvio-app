package com.dartvio.app.data.local

import com.dartvio.app.domain.model.BullMode
import com.dartvio.app.domain.model.CricketTarget
import com.dartvio.app.domain.model.InMode
import com.dartvio.app.domain.model.MatchConfig
import com.dartvio.app.domain.model.MatchMode
import com.dartvio.app.domain.model.MatchType
import com.dartvio.app.domain.model.OutMode
import com.dartvio.app.domain.model.Player
import com.dartvio.app.domain.model.PlayerType
import com.dartvio.app.domain.model.TargetCategory
import com.dartvio.app.domain.rules.CricketRules
import com.dartvio.app.domain.rules.X01Rules
import com.dartvio.app.domain.stats.MatchFacts
import com.dartvio.app.domain.stats.PlayerMatchFacts
import com.dartvio.app.ui.game.GameUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落库身份的改写契约（第③期 ③B 前置 P0）。
 *
 * 改的是**写入值**而不是表结构，所以没有任何迁移报错会提醒我们改错了 ——
 * 一旦这里回归，榜与「我的」统计会安静地少算，只有这些断言能发现。
 */
class MatchMapperTest {

    private val config = MatchConfig(
        matchType = MatchType.X01,
        mode = MatchMode.MULTI_LEG,
        legsToWin = 3,
    )

    private fun state(players: List<Player>, winnerId: String?) = GameUiState(
        config = config,
        players = players,
        winnerPlayerId = winnerId,
        startedAt = 0L,
    )

    private val humanSelf = Player("p1", "我")
    private val humanRival = Player("p2", "对手")
    private val bot = Player("p1", "机器人", type = PlayerType.AI)

    @Test
    fun `本机席位的 player_id 与 winnerPlayerId 换成档案 ID`() {
        val s = state(listOf(humanSelf, humanRival), winnerId = "p1")

        val record = MatchMapper.toMatchEntity(s, "m1", 1_000L, "profile-1")
        val rows = MatchMapper.toPlayerEntities(s, "m1", "profile-1")

        assertEquals("profile-1", record.winnerPlayerId)
        assertEquals(listOf("profile-1", "p2"), rows.map { it.playerId })
        // 昵称、胜负这些「事实」不受身份改写影响
        assertEquals(listOf("我", "对手"), rows.map { it.name })
        assertEquals(listOf(true, false), rows.map { it.isWinner })
        assertTrue(record.isFormal)
    }

    @Test
    fun `AI 排在首位时被替换的是真人席位`() {
        // 设置页若把机器人排在第一位，硬编码 "p1" 会去改写机器人的行、把真人留在位置 ID 上。
        val s = state(listOf(bot, humanRival.copy(id = "p2", name = "我")), winnerId = "p2")

        val record = MatchMapper.toMatchEntity(s, "m1", 1_000L, "profile-1")
        val rows = MatchMapper.toPlayerEntities(s, "m1", "profile-1")

        assertEquals(listOf("p1", "profile-1"), rows.map { it.playerId })
        assertEquals("profile-1", record.winnerPlayerId)
    }

    @Test
    fun `对手行保持位置 ID 不会被误认为本机玩家`() {
        val s = state(listOf(humanSelf, humanRival), winnerId = "p2")

        val record = MatchMapper.toMatchEntity(s, "m1", 1_000L, "profile-1")
        val rows = MatchMapper.toPlayerEntities(s, "m1", "profile-1")

        assertEquals(listOf("profile-1", "p2"), rows.map { it.playerId })
        // 赢的是对手，胜者 ID 不该被本机档案 ID 顶替
        assertEquals("p2", record.winnerPlayerId)
    }

    @Test
    fun `未提供档案 ID 时保持位置 ID 原样`() {
        val s = state(listOf(humanSelf, humanRival), winnerId = "p2")

        val record = MatchMapper.toMatchEntity(s, "m1", 1_000L)
        val rows = MatchMapper.toPlayerEntities(s, "m1")

        assertEquals("p2", record.winnerPlayerId)
        assertEquals(listOf("p1", "p2"), rows.map { it.playerId })
    }

    @Test
    fun `AI 对局仍标记为非正式赛 不进排行榜`() {
        val s = state(listOf(bot, humanRival.copy(id = "p2", name = "我")), winnerId = "p2")

        val record = MatchMapper.toMatchEntity(s, "m1", 1_000L, "profile-1")

        assertFalse(record.isFormal)
        assertTrue(record.containsAi)
    }

    // ===== 二期 2A：首关分区流水（提示词 §3.1 / §3.2 #10）=====

    /** 只关心首关分区流水，其余事实留默认值。 */
    private fun stateWithFirstClosed(firstClosed: List<CricketTarget>) = state(listOf(humanSelf), null)
        .copy(facts = MatchFacts(byPlayer = mapOf("p1" to PlayerMatchFacts(firstClosed = firstClosed))))

    @Test
    fun `数字分区的首关流水与一期逐字节相同`() {
        val rows = MatchMapper.toPlayerEntities(
            stateWithFirstClosed(
                listOf(
                    CricketTarget.Number(20),
                    CricketTarget.Number(19),
                    CricketTarget.Number(25),
                )
            ),
            "m1",
            "profile-1"
        )

        // 一期写法就是直接 joinToString 号位，结果是 "20,19,25"；改成 .token 之后必须一模一样。
        // 这条串是 C10 全部历史数据的入口，改一个字节就不可回滚（红线 2）。
        assertEquals("20,19,25", rows.single().firstClosedCsv)
    }

    @Test
    fun `含类别档的首关流水按 token 写出并可读回`() {
        val firstClosed = listOf(
            CricketTarget.Number(20),
            CricketTarget.Number(19),
            CricketTarget.Category(TargetCategory.DOUBLES),
        )

        val csv = MatchMapper
            .toPlayerEntities(stateWithFirstClosed(firstClosed), "m1", "profile-1")
            .single()
            .firstClosedCsv

        assertEquals("20,19,D", csv)
        // 读端（StatsCalculator）走 CricketTarget.parse，往返不丢类别。
        assertEquals(firstClosed, csv.split(',').mapNotNull { CricketTarget.parse(it) })
    }

    // ===== 二期 2B：目标集落库（提示词 §4.1）=====

    private fun cricketState(config: MatchConfig) = GameUiState(
        config = config,
        players = listOf(humanSelf, humanRival),
        winnerPlayerId = null,
        startedAt = 0L,
    )

    @Test
    fun `默认目标集写空串`() {
        val record = MatchMapper.toMatchEntity(
            cricketState(MatchConfig(matchType = MatchType.CRICKET)), "m1", 1_000L
        )

        // 空串 = 默认 7 分区：历史行就是这么存的，新行必须逐字节一致，
        // 否则「旧行 == 新行」不成立，任何按列值分叉的统计都会踩到。
        assertEquals("", record.targetSetCsv)
        assertEquals(CricketTarget.DEFAULT_TARGETS, record.cricketTargets)
    }

    @Test
    fun `X01 局同样写空串`() {
        val record = MatchMapper.toMatchEntity(state(listOf(humanSelf), null), "m1", 1_000L)

        // X01 的目标集恒为默认值，落库不能变成一个「有值但无意义」的串。
        assertEquals("", record.targetSetCsv)
    }

    @Test
    fun `裁剪后的目标集写 token 串并能读回`() {
        val trimmed = MatchConfig(
            matchType = MatchType.CRICKET,
            cricketTargets = listOf(CricketTarget.Number(20), CricketTarget.Number(19)),
        )

        val record = MatchMapper.toMatchEntity(cricketState(trimmed), "m1", 1_000L)

        assertEquals("20,19", record.targetSetCsv)
        assertEquals(
            listOf(CricketTarget.Number(20), CricketTarget.Number(19)),
            record.cricketTargets
        )
    }

    @Test
    fun `无法识别的目标集列回落默认 7 分区`() {
        // 老版本读到新版本写入的类别档 token：少算一条即可，
        // 绝不能解析成空集 —— 空集会让 C2 的分母变成 0。
        val row = MatchMapper
            .toMatchEntity(cricketState(MatchConfig(matchType = MatchType.CRICKET)), "m1", 1_000L)
            .copy(targetSetCsv = "X,D20")

        assertEquals(CricketTarget.DEFAULT_TARGETS, row.cricketTargets)
    }

    // ===== 二期 2C：超时终局落库（提示词 §8.11）=====

    @Test
    fun `Cricket 轮数上限终局会落到 endedByRoundLimit`() {
        val config = MatchConfig(matchType = MatchType.CRICKET, maxRounds = 15)
        val leg = CricketRules.newLeg(config, listOf(humanSelf, humanRival), 1)
            .copy(isFinished = true, endedByRoundLimit = true)

        val record = MatchMapper.toMatchEntity(
            cricketState(config).copy(cricketLeg = leg), "m1", 1_000L
        )

        assertTrue("超时终局必须落库，M9 才能说明超时局的关满统计不计入", record.endedByRoundLimit)
    }

    @Test
    fun `常规 Cricket 局与 X01 局都不落超时标记`() {
        val cricket = MatchMapper.toMatchEntity(
            cricketState(MatchConfig(matchType = MatchType.CRICKET)), "m1", 1_000L
        )
        val x01 = MatchMapper.toMatchEntity(state(listOf(humanSelf), null), "m1", 1_000L)

        assertFalse(cricket.endedByRoundLimit)
        // X01 读的是自己的 X01 局状态：常规收分局（不是打满轮数）绝不能被写成 true。
        assertFalse(x01.endedByRoundLimit)
    }

    @Test
    fun `X01 超时终局也落到 endedByRoundLimit`() {
        // 补齐前只读 cricketLeg ⇒ X01「打满 maxRounds 才赢」的收分局在库里恒 false，
        // 与常规收分局无法区分。
        val config = MatchConfig(matchType = MatchType.X01, maxRounds = 15)
        val leg = X01Rules.newLeg(config, listOf(humanSelf, humanRival), 1)
            .copy(isFinished = true, endedByRoundLimit = true)

        val record = MatchMapper.toMatchEntity(
            state(listOf(humanSelf), null).copy(config = config, x01Leg = leg), "m1", 1_000L
        )

        assertTrue("X01 超时终局必须落库，与 Cricket 同口径", record.endedByRoundLimit)
        assertEquals(15, record.maxRounds)
    }

    @Test
    fun `轮数上限列对 Cricket 局同样落值`() {
        // 2026-09-12 字段合并后这一列的含义是「本局轮数上限」，不再只服务 X01。
        val config = MatchConfig(matchType = MatchType.CRICKET, maxRounds = 50)

        val record = MatchMapper.toMatchEntity(cricketState(config), "m1", 1_000L)

        assertEquals(50, record.maxRounds)
        // 老布尔 overtimeRule 仍是 X01 专属：不能被共用字段带着写成 true。
        assertEquals(null, record.overtimeRule)
    }

    // ===== 2026-09-12：X01 三组规则档位落库（v4→v5）=====

    @Test
    fun `X01 局把三组规则档位与最多轮数落库`() {
        val config = MatchConfig(
            matchType = MatchType.X01,
            outMode = OutMode.MASTER_OUT,
            inMode = InMode.MASTER_IN,
            bullMode = BullMode.BULL_50_50,
            maxRounds = 20,
        )

        val record = MatchMapper.toMatchEntity(
            state(listOf(humanSelf), null).copy(config = config), "m1", 1_000L
        )

        assertEquals("MASTER_OUT", record.outMode)
        assertEquals("MASTER_IN", record.inMode)
        assertEquals("BULL_50_50", record.bullMode)
        assertEquals(20, record.maxRounds)
        // 宽容读取也要拿得回枚举（历史行读到未知值时回落宽松档，不抛）。
        assertEquals(OutMode.MASTER_OUT, record.x01OutMode)
        assertEquals(InMode.MASTER_IN, record.x01InMode)
        assertEquals(BullMode.BULL_50_50, record.x01BullMode)
    }

    @Test
    fun `大师出不会被老布尔吞成双倍出`() {
        // 「新字段不得塞进老字段」的证据：老布尔只有两态，大师出也满足 doubleOut = true，
        // 只落布尔的话读回来就变成双倍出，历史记录显示错误且不可逆。
        val config = MatchConfig(matchType = MatchType.X01, outMode = OutMode.MASTER_OUT)
        val record = MatchMapper.toMatchEntity(
            state(listOf(humanSelf), null).copy(config = config), "m1", 1_000L
        )

        assertTrue("老布尔仍是 true（大师出也按倍区收尾）", record.doubleOut)
        assertEquals("档位列必须原样保留大师出", OutMode.MASTER_OUT, record.x01OutMode)
    }

    @Test
    fun `Cricket 行的 X01 规则档位列回落缺省口径`() {
        val record = MatchMapper.toMatchEntity(
            cricketState(MatchConfig(matchType = MatchType.CRICKET)), "m1", 1_000L
        )

        assertEquals(OutMode.STRAIGHT_OUT, record.x01OutMode)
        assertEquals(InMode.STRAIGHT_IN, record.x01InMode)
        assertEquals(BullMode.STANDARD_25_50, record.x01BullMode)
        assertEquals(0, record.maxRounds)
    }
}
