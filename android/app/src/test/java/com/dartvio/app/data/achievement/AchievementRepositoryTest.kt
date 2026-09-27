package com.dartvio.app.data.achievement

import com.dartvio.app.data.local.dao.MatchWithPlayers
import com.dartvio.app.data.local.entity.MatchPlayerEntity
import com.dartvio.app.data.local.entity.MatchRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `AchievementRepository` 编排层测试（③ 期收尾）。
 *
 * 这一层不重算判定口径（已在 [com.dartvio.app.domain.achievement.AchievementCalculatorTest] 覆盖），
 * 只钉死四类**编排不变量** —— 它们是 ③ 期最容易在后续改动中悄悄回归、又最难靠人工发现的：
 *
 * 1. **快照无中间态**：UI 拿到的已解锁项必须有 `unlockedAt`，否则成就墙的「解锁于」会显示异常；
 * 2. **落盘幂等**：已解锁的不重复宣布、首次解锁时刻不被后续重算改写；
 * 3. **补算只由 `backfillIfNeeded` 触发**：普通刷新绝不登记补算提示（决策③的措辞边界）；
 * 4. **红点只读不写**：`countUnseen` 不得顺手落盘，否则会抢走结算页的「新解锁」宣布机会。
 *
 * 用 `recalculate(at)` 而不是 `refresh(matches, at)`：`refresh` 是**纯函数式入口**、不碰 dao，
 * 因此要求调用方显式给出对局列表；编排测试关心的正是「从 dao 取数 → 落盘」这一段，
 * 用 `recalculate` 才覆盖得到。
 *
 * 用 `runBlocking` 而非 `runTest`：本项目测试依赖只有 JUnit（无 kotlinx-coroutines-test），
 * 而被测代码没有 `delay`，`runBlocking` 足够且零新依赖。
 */
class AchievementRepositoryTest {

    private lateinit var dao: FakeMatchRecordDao
    private lateinit var achievementPrefs: InMemorySharedPreferences
    private lateinit var practicePrefs: InMemorySharedPreferences
    private lateinit var repo: AchievementRepository

    @Before
    fun setUp() {
        dao = FakeMatchRecordDao()
        achievementPrefs = InMemorySharedPreferences()
        practicePrefs = InMemorySharedPreferences()
        repo = AchievementRepository(dao, achievementPrefs, practicePrefs)
    }

    // ==================== 1. 快照不变量 ====================

    @Test
    fun `刷新后的快照不会出现已解锁但解锁时刻为空的中间态`() = runBlocking {
        dao.matches = listOf(finishedMatch())

        val snapshot = repo.recalculate(at = 5_000L)

        val unlocked = snapshot.items.filter { it.unlocked }
        assertTrue("至少要解锁一项，否则本断言是空转的", unlocked.isNotEmpty())
        assertTrue(
            "成就墙直接读 unlockedAt 渲染「解锁于」，为 null 会显示成异常值",
            unlocked.all { it.unlockedAt != null },
        )
    }

    @Test
    fun `订阅快照流同样不会拿到解锁时刻为空的项`() = runBlocking {
        dao.matches = listOf(finishedMatch())

        val snapshot = repo.observeSnapshot().first()

        assertTrue(snapshot.items.filter { it.unlocked }.all { it.unlockedAt != null })
    }

    @Test
    fun `刷新会把新解锁写入 prefs 且集合与快照一致`() = runBlocking {
        dao.matches = listOf(finishedMatch())

        val snapshot = repo.recalculate(at = 1_000L)

        assertTrue(snapshot.newlyUnlockedIds.isNotEmpty())
        assertEquals(
            snapshot.newlyUnlockedIds.toSet(),
            AchievementStore.unlockedMap(achievementPrefs).keys,
        )
    }

    // ==================== 2. 落盘幂等 ====================

    @Test
    fun `已落盘的解锁不会在下一次刷新时重复宣布`() = runBlocking {
        dao.matches = listOf(finishedMatch())
        assertTrue(repo.recalculate(at = 1_000L).newlyUnlockedIds.isNotEmpty())

        assertTrue(
            "重复宣布会让用户每次进成就墙都看到一次「新解锁」",
            repo.recalculate(at = 2_000L).newlyUnlockedIds.isEmpty(),
        )
    }

    @Test
    fun `后续刷新不会改写首次解锁时刻`() = runBlocking {
        dao.matches = listOf(finishedMatch())
        val first = repo.recalculate(at = 1_000L)
        val id = first.newlyUnlockedIds.first()

        val again = repo.recalculate(at = 9_999L)

        assertEquals(
            "解锁时刻必须停在首次落盘那一刻，否则成就墙的「解锁于」会随重算漂移",
            1_000L,
            again.progressOf(id)?.unlockedAt,
        )
    }

    // ==================== 3. 补算（决策③） ====================

    @Test
    fun `普通刷新不登记补算提示`() = runBlocking {
        dao.matches = listOf(finishedMatch())

        repo.recalculate(at = 1_000L)

        assertEquals(
            "「清除本地数据后打一局」会走到这里：那 N 项是刚打的，措辞不能变成「历史补算」",
            0,
            repo.consumeBackfillAnnouncement(),
        )
    }

    @Test
    fun `首次启动补算返回快照并标记已补算`() = runBlocking {
        dao.matches = listOf(finishedMatch())

        val snapshot = repo.backfillIfNeeded(at = 1_000L)

        assertNotNull(snapshot)
        assertTrue("首帧要告诉 UI 这是历史补算，好显示提示", snapshot!!.backfilled)
        assertTrue(AchievementStore.isBackfilled(achievementPrefs))
    }

    @Test
    fun `已补算过再启动不再重复补算`() = runBlocking {
        dao.matches = listOf(finishedMatch())
        repo.backfillIfNeeded(at = 1_000L)

        assertNull(
            "每次冷启动都全量重算，对局多了以后是纯浪费的 IO",
            repo.backfillIfNeeded(at = 2_000L),
        )
    }

    @Test
    fun `历史补算登记待提示数量且只提示一次`() = runBlocking {
        dao.matches = listOf(finishedMatch())

        val snapshot = repo.backfillIfNeeded(at = 1_000L)!!

        assertEquals(snapshot.newlyUnlockedIds.size, repo.consumeBackfillAnnouncement())
        assertEquals("决策③要求这句提示只弹一次", 0, repo.consumeBackfillAnnouncement())
    }

    @Test
    fun `没有历史对局时补算不登记提示`() = runBlocking {
        dao.matches = emptyList()

        val snapshot = repo.backfillIfNeeded(at = 1_000L)!!

        assertTrue(snapshot.newlyUnlockedIds.isEmpty())
        assertEquals(0, repo.consumeBackfillAnnouncement())
    }

    // ==================== 4. 红点（决策④） ====================

    @Test
    fun `统计红点不会提前落盘解锁状态`() {
        dao.matches = listOf(finishedMatch())

        assertEquals(0, repo.countUnseen())

        assertTrue(
            "红点若顺手重算落盘，会抢走对局结算页的「新解锁」宣布机会",
            AchievementStore.unlockedMap(achievementPrefs).isEmpty(),
        )
    }

    @Test
    fun `红点数量等于已解锁数且查看后归零`() = runBlocking {
        dao.matches = listOf(finishedMatch())
        val snapshot = repo.recalculate(at = 1_000L)
        assertEquals(snapshot.unlockedCount, repo.countUnseen())

        repo.markAllViewed()

        assertEquals(0, repo.countUnseen())
    }

    @Test
    fun `先查看后解锁时红点亮起`() = runBlocking {
        dao.matches = listOf(finishedMatch())
        repo.markAllViewed() // 老用户升级后的真实时序：先于任何解锁进入过成就墙

        val snapshot = repo.recalculate(at = 1_000L)

        assertTrue(snapshot.newlyUnlockedIds.isNotEmpty())
        assertEquals(
            "查看态是集合差集，先查看不会吞掉后来的解锁",
            snapshot.newlyUnlockedIds.size,
            repo.countUnseen(),
        )
    }

    @Test
    fun `查看后追加对局解锁新成就会重新点亮红点`() = runBlocking {
        dao.matches = List(1) { finishedMatch(matchId = "m$it", endedAt = 1_000L + it) }
        repo.recalculate(at = 5_000L)
        repo.markAllViewed()
        assertEquals(0, repo.countUnseen())

        // 补到 10 场，越过 milestone_match_10 的阈值
        dao.matches = List(10) { finishedMatch(matchId = "m$it", endedAt = 1_000L + it) }
        val after = repo.recalculate(at = 6_000L)

        assertTrue(
            "补到 10 场必须解锁新成就，否则本用例失去意义",
            after.newlyUnlockedIds.isNotEmpty(),
        )
        assertEquals(after.newlyUnlockedIds.size, repo.countUnseen())
    }

    @Test
    fun `清空本地数据后红点归零且解锁表为空`() = runBlocking {
        dao.matches = listOf(finishedMatch())
        repo.recalculate(at = 1_000L)
        assertTrue("前置条件：清空前必须存在未查看的解锁", repo.countUnseen() > 0)

        repo.clearAll()

        assertEquals(0, repo.countUnseen())
        assertTrue(AchievementStore.unlockedMap(achievementPrefs).isEmpty())
    }

    // ==================== 夹具 ====================

    /** 一场已结束的 X01 对局（我获胜、含 AI），足以解锁 `milestone_first_match` 与 `milestone_first_win`。 */
    private fun finishedMatch(
        matchId: String = "m1",
        endedAt: Long = 1_000L,
    ): MatchWithPlayers = MatchWithPlayers(
        match = MatchRecordEntity(
            matchId = matchId,
            gameType = "X01",
            matchType = "SINGLE",
            matchTypeLabel = "单局",
            isFormal = false,
            containsAi = true,
            playerCount = 2,
            startedAt = endedAt - 600_000L,
            endedAt = endedAt,
            durationMs = 600_000L,
            winnerPlayerId = "local_me",
            legCount = 1,
            legsToWin = 1,
            startScore = 501,
            x01Mode = "STRAIGHT",
            doubleOut = true,
            doubleIn = false,
            overtimeRule = null,
            totalDarts = 60,
        ),
        players = listOf(
            player(matchId = matchId, playerId = "local_me", isWinner = true),
            player(
                matchId = matchId,
                playerId = "p2",
                name = "对手",
                orderIndex = 1,
                isWinner = false,
                legsWon = 0,
            ),
        ),
    )

    private fun player(
        matchId: String,
        playerId: String,
        name: String = "我",
        orderIndex: Int = 0,
        isWinner: Boolean = true,
        legsWon: Int = 1,
    ): MatchPlayerEntity = MatchPlayerEntity(
        matchId = matchId,
        playerId = playerId,
        name = name,
        isAi = false,
        aiDifficulty = null,
        orderIndex = orderIndex,
        isWinner = isWinner,
        legsWon = legsWon,
        dartsThrown = 60,
        turnsPlayed = 20,
        totalScore = 501,
        maxTurnScore = 100,
        remaining = 0,
        busts = 0,
        count180 = 0,
        bestCheckout = 0,
        checkoutAttempts = 5,
        marksTotal = 0,
        tripleHits = 0,
        bullHits = 0,
        closedAllSectionLegs = 0,
        turnsInClosedLegs = 0,
        closedSectionsTotal = 0,
        firstClosedCsv = "",
    )
}
