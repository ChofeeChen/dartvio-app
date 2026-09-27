package com.dartvio.app.data.achievement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 成就本地存储契约单测（决策③ 历史补算 / 决策④ 红点）。
 *
 * 这些不是「实现细节测试」而是**产品契约的固化**，三条硬约束：
 * 1. 红点判定必须对系统时钟免疫 —— 用户把时间调前或调后都不能让红点失准；
 * 2. 补算提示必须只出现一次（消费式），且不残留键；
 * 3. 首次解锁时间一旦写下就不能被后续重算覆盖，否则成就墙的「解锁于」会漂移。
 */
class AchievementStoreTest {

    private lateinit var prefs: InMemorySharedPreferences

    @Before
    fun setUp() {
        prefs = InMemorySharedPreferences()
    }

    // ==================== 解锁落盘契约 ====================

    @Test
    fun `未写过的 prefs 读出空解锁表`() {
        assertTrue(AchievementStore.unlockedMap(prefs).isEmpty())
    }

    @Test
    fun `新解锁落盘后可读出对应时刻`() {
        AchievementStore.saveNewlyUnlocked(prefs, listOf("milestone_first_match"), at = 1000L)
        assertEquals(
            mapOf("milestone_first_match" to 1000L),
            AchievementStore.unlockedMap(prefs),
        )
    }

    @Test
    fun `同一成就重复落盘不覆盖首次解锁时间`() {
        AchievementStore.saveNewlyUnlocked(prefs, listOf("a"), at = 1000L)
        AchievementStore.saveNewlyUnlocked(prefs, listOf("a"), at = 2000L)
        assertEquals(
            "首次解锁时间必须稳定，否则成就墙的「解锁于」会随重算漂移",
            1000L,
            AchievementStore.unlockedMap(prefs)["a"],
        )
    }

    @Test
    fun `空列表落盘不写入任何键`() {
        AchievementStore.saveNewlyUnlocked(prefs, emptyList(), at = 1000L)
        assertTrue(prefs.getAll().isEmpty())
    }

    @Test
    fun `unlockedMap 只认 unlocked 前缀，查看态与补算标记不会被当成成就`() {
        // 键空间隔离的回归保护：往这批 prefs 里加新键时，若忘了给 unlockedMap
        // 加前缀过滤，成就墙会凭空多出「成就」，红点计数也会虚高。
        AchievementStore.markBackfilled(prefs)
        AchievementStore.writeBackfillAnnouncement(prefs, 7)
        AchievementStore.markViewed(prefs, listOf("milestone_first_match"))
        assertTrue(AchievementStore.unlockedMap(prefs).isEmpty())
    }

    // ==================== 查看态 / 红点（决策④） ====================

    @Test
    fun `从未查看过时全部解锁都计入未查看`() {
        AchievementStore.saveNewlyUnlocked(prefs, listOf("a", "b"), at = 1000L)
        assertEquals(2, AchievementStore.unseenCount(prefs))
    }

    @Test
    fun `查看后红点归零`() {
        AchievementStore.saveNewlyUnlocked(prefs, listOf("a", "b"), at = 1000L)
        AchievementStore.markViewed(prefs, listOf("a", "b"))
        assertEquals(0, AchievementStore.unseenCount(prefs))
    }

    @Test
    fun `查看后新解锁的只数新增的那些`() {
        AchievementStore.saveNewlyUnlocked(prefs, listOf("a"), at = 1000L)
        AchievementStore.markViewed(prefs, listOf("a"))
        AchievementStore.saveNewlyUnlocked(prefs, listOf("b", "c"), at = 2000L)
        assertEquals(
            "已查看的 a 不能重新计入，未查看的 b/c 必须计入",
            2,
            AchievementStore.unseenCount(prefs),
        )
    }

    @Test
    fun `系统时钟被调前时新解锁仍计入未查看`() {
        // 用户点名的边界。时间戳方案下「上次查看时刻」会是一个未来值，
        // 之后解锁的 unlockedAt 小于它 → 红点从此不亮，一直坏到系统时间追上来。
        // 集合方案只做差集，与时间无关，因此这里必须仍然亮。
        AchievementStore.markViewed(prefs, listOf("milestone_first_match"))
        AchievementStore.saveNewlyUnlocked(prefs, listOf("x01_180"), at = 1L)
        assertEquals(1, AchievementStore.unseenCount(prefs))
    }

    @Test
    fun `系统时钟被调到未来时已查看的解锁不会重新点亮红点`() {
        // 反方向：解锁时刻落在"未来"（时钟被调后），但用户已经看过它。
        // 时间戳方案下 unlockedAt > viewedAt 会把它误判成未查看，红点假亮。
        AchievementStore.saveNewlyUnlocked(prefs, listOf("a"), at = Long.MAX_VALUE)
        AchievementStore.markViewed(prefs, listOf("a"))
        assertEquals(0, AchievementStore.unseenCount(prefs))
    }

    @Test
    fun `markViewed 是合并语义，分次调用不会丢掉已有记录`() {
        AchievementStore.saveNewlyUnlocked(prefs, listOf("a", "b"), at = 1000L)
        AchievementStore.markViewed(prefs, listOf("a"))
        AchievementStore.markViewed(prefs, listOf("b"))
        assertEquals(0, AchievementStore.unseenCount(prefs))
    }

    @Test
    fun `markViewed 传空集合时不改变已有查看态`() {
        AchievementStore.saveNewlyUnlocked(prefs, listOf("a"), at = 1000L)
        AchievementStore.markViewed(prefs, listOf("a"))
        AchievementStore.markViewed(prefs, emptyList())
        assertEquals(0, AchievementStore.unseenCount(prefs))
    }

    @Test
    fun `查看态不受成就之外的数据影响`() {
        // 成就墙首次进入时 snapshot 可能还没加载完，调用方传的是空列表；
        // 只要 markViewed 用的是 prefs 里的权威快照，就不会漏标。
        AchievementStore.saveNewlyUnlocked(prefs, listOf("a", "b", "c"), at = 1000L)
        AchievementStore.markViewed(prefs, AchievementStore.unlockedMap(prefs).keys)
        assertEquals(0, AchievementStore.unseenCount(prefs))
    }

    // ==================== 清空本地数据 ====================

    @Test
    fun `清空本地数据后红点归零且不残留幻影未查看`() {
        AchievementStore.saveNewlyUnlocked(prefs, listOf("a", "b"), at = 1000L)
        assertEquals(2, AchievementStore.unseenCount(prefs))

        AchievementStore.clear(prefs)

        // 解锁记录与查看态必须一起清掉：只清其一都会留下不一致状态。
        assertTrue(AchievementStore.unlockedMap(prefs).isEmpty())
        assertTrue(AchievementStore.viewedIds(prefs).isEmpty())
        assertEquals(0, AchievementStore.unseenCount(prefs))
    }

    @Test
    fun `清空本地数据后补算标记复位，下次启动会重新补算`() {
        AchievementStore.markBackfilled(prefs)
        assertTrue(AchievementStore.isBackfilled(prefs))

        AchievementStore.clear(prefs)

        assertFalse(
            "清空后必须允许重新补算，否则用户新打的数据永远不会补出成就",
            AchievementStore.isBackfilled(prefs),
        )
    }

    @Test
    fun `清空本地数据会一并清掉未消费的补算提示`() {
        AchievementStore.writeBackfillAnnouncement(prefs, 9)
        AchievementStore.clear(prefs)
        assertEquals(0, AchievementStore.consumeBackfillAnnouncement(prefs))
    }

    // ==================== 补算标记与提示（决策③） ====================

    @Test
    fun `补算标记初始为未补算`() {
        assertFalse(AchievementStore.isBackfilled(prefs))
    }

    @Test
    fun `标记补算后可读出`() {
        AchievementStore.markBackfilled(prefs)
        assertTrue(AchievementStore.isBackfilled(prefs))
    }

    @Test
    fun `未登记补算提示时读出 0`() {
        assertEquals(0, AchievementStore.consumeBackfillAnnouncement(prefs))
    }

    @Test
    fun `补算提示是消费式读取，只出现一次`() {
        AchievementStore.writeBackfillAnnouncement(prefs, 15)
        assertEquals(15, AchievementStore.consumeBackfillAnnouncement(prefs))
        assertEquals(
            "决策③要求这句提示只弹一次，第二次读必须为空",
            0,
            AchievementStore.consumeBackfillAnnouncement(prefs),
        )
    }

    @Test
    fun `消费后再次补算可以再次提示`() {
        AchievementStore.writeBackfillAnnouncement(prefs, 3)
        assertEquals(3, AchievementStore.consumeBackfillAnnouncement(prefs))

        AchievementStore.writeBackfillAnnouncement(prefs, 5)
        assertEquals(5, AchievementStore.consumeBackfillAnnouncement(prefs))
    }

    @Test
    fun `消费补算提示后不残留任何键`() {
        AchievementStore.writeBackfillAnnouncement(prefs, 0)
        AchievementStore.consumeBackfillAnnouncement(prefs)

        // 无条件清理：即使读到的是 0 也要删键，避免 prefs 里留下无意义的残留
        assertTrue(prefs.getAll().isEmpty())
    }
}
