package com.dartvio.app.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 练习汇总的边界测试。
 *
 * 这份汇总此前在 UI 上的失败模式是「打了一晚上练习，数据页一片空白」
 * （2026-09-27 真机反馈）——根因是练习数据根本不写对局表。
 * 所以这里测的不是算法，而是三件容易算错的事：
 * 分母为 0 时必须给 0 而不是 NaN、有一项就算「练过」、以及各项互不串台。
 */
class PracticeSummaryTest {

    @Test
    fun emptySummaryIsNotPracticedAndRatesAreZeroNotNaN() {
        val s = PracticeSummary()

        assertFalse(s.hasAny)
        assertFalse(s.hasRush)
        assertFalse(s.hasVersus)
        assertEquals(0.0, s.rushHitRate, 0.0)
        assertEquals(0.0, s.versusWinRate, 0.0)
    }

    @Test
    fun rushRateIsCheckoutsOverJudgedAttempts() {
        val s = PracticeSummary(rushAttempts = 10, rushCheckouts = 3)

        assertTrue(s.hasRush)
        assertEquals(0.3, s.rushHitRate, 1e-9)
    }

    @Test
    fun versusRateUsesFinishedMatchesAsDenominator() {
        // 还没打完的场次不能进分母：否则刚开局就显示「胜率 0%」。
        val s = PracticeSummary(versusFinished = 4, versusWins = 1)

        assertTrue(s.hasVersus)
        assertEquals(0.25, s.versusWinRate, 1e-9)
    }

    @Test
    fun anySingleSourceCountsAsPracticed() {
        // 只练过落点诊断的人，也必须看到「我练过」，而不是一片空白。
        assertTrue(PracticeSummary(impactDarts = 30).hasAny)
        assertTrue(PracticeSummary(countUpBestScore = 180).hasAny)
        assertTrue(PracticeSummary(sessions = 1).hasAny)
    }

    @Test
    fun ninetyNineBestCarriesTheSectorItCameFrom() {
        val s = PracticeSummary(ninetyNineBestScore = 420, ninetyNineBestSector = 20)

        assertEquals(420, s.ninetyNineBestScore)
        assertEquals(20, s.ninetyNineBestSector)
    }
}
