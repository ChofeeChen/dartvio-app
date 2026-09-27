package com.dartvio.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cricket 变体枚举的可执行规格（M2 §4.8①、M4 §6.2）。
 *
 * 一期三种变体只差「得分归属」与「胜负比较」两处；这里把这两处口径钉死，
 * 顺便把 [CricketVariant.fromKey] 的宽容解析（旧端 / 新键都不能崩）固化成契约。
 */
class CricketVariantTest {

    @Test
    fun `三种变体的得分归属与胜负比较符合 §4-8`() {
        assertEquals(ScoreSink.SELF, CricketVariant.STANDARD.scoreSink)
        assertEquals(WinCompare.GE, CricketVariant.STANDARD.winCompare)

        assertEquals(ScoreSink.NONE, CricketVariant.NO_SCORE.scoreSink)
        assertEquals(WinCompare.NONE, CricketVariant.NO_SCORE.winCompare)

        assertEquals(ScoreSink.OPPONENTS, CricketVariant.CUT_THROAT.scoreSink)
        assertEquals(WinCompare.LE, CricketVariant.CUT_THROAT.winCompare)
    }

    @Test
    fun `key 与 M2 §4-8 的稳定取值一致`() {
        assertEquals("standard", CricketVariant.STANDARD.key)
        assertEquals("no_score", CricketVariant.NO_SCORE.key)
        assertEquals("cut_throat", CricketVariant.CUT_THROAT.key)
    }

    @Test
    fun `fromKey 忽略大小写与下划线`() {
        assertEquals(CricketVariant.CUT_THROAT, CricketVariant.fromKey("CUT_THROAT"))
        assertEquals(CricketVariant.CUT_THROAT, CricketVariant.fromKey("cutthroat"))
        assertEquals(CricketVariant.NO_SCORE, CricketVariant.fromKey(" No_Score "))
    }

    @Test
    fun `fromKey 对缺失与未知取值一律回落 standard 而不抛异常`() {
        assertEquals(CricketVariant.STANDARD, CricketVariant.fromKey(null))
        assertEquals(CricketVariant.STANDARD, CricketVariant.fromKey(""))
        assertEquals(CricketVariant.STANDARD, CricketVariant.fromKey("   "))
        // 二期预留取值：老端读到新键也不能崩。
        assertEquals(CricketVariant.STANDARD, CricketVariant.fromKey("tactics"))
        assertEquals(CricketVariant.STANDARD, CricketVariant.fromKey("random"))
    }

    @Test
    fun `每个变体都有非空的展示名与解释`() {
        CricketVariant.entries.forEach { variant ->
            assertTrue("${variant.name} 缺少展示名", variant.label.isNotBlank())
            assertTrue("${variant.name} 缺少解释", variant.hint.isNotBlank())
        }
    }

    @Test
    fun `默认变体是 standard`() {
        assertEquals(CricketVariant.STANDARD, CricketVariant.DEFAULT)
        assertNotEquals(CricketVariant.NO_SCORE, CricketVariant.DEFAULT)
    }
}
