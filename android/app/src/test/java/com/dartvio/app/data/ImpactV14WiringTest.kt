package com.dartvio.app.data

import com.dartvio.app.data.local.entity.DartHitEntity
import com.dartvio.app.domain.impact.ImpactRounds
import com.dartvio.app.domain.impact.IntentTarget
import com.dartvio.app.domain.impact.PrescriptionMetric
import com.dartvio.app.domain.impact.RoundMetrics
import com.dartvio.app.domain.model.DartSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V1.4 数据层组装口径的纯 JVM 单测（无 Room / 无 Android 依赖）。
 *
 * 对应 A-IMP-26：cEV 与「σ / R95 / KDE」走**两条不同的输入路径** ——
 * `cevSamplesOf` 必须含出框折算点，`frameOf` / `metricsOf` 必须不含。
 * 顺带锁住回合序号、会话聚合、判定现算与 v8 四列的落库形状。
 */
class ImpactV14WiringTest {

    private val target = IntentTarget.triple(20)
    private val anchor = target.anchorMm()

    // ---------------------------------------------------------------- A-IMP-26 两条输入路径

    @Test
    fun `cev输入含出框折算点而散布输入不含`() {
        val hits = List(30) { hit(hitAt = it.toLong()) } +
            List(5) { hit(hitAt = 100L + it, yMm = anchor.y + 30.0, outBand = 1) }

        // cEV：全部落库点都算（miss 是真实得分）。
        assertEquals(35, ImpactRepository.cevSamplesOf(hits).size)

        // 散布：出框镖一律挡在 σ / R95 / KDE 之外（否则窗口边界会堆出假高峰）。
        assertEquals(30, hits.mapNotNull { ImpactRepository.frameOf(it, target) }.size)
        val m = ImpactRepository.metricsOf(hits, target)
        assertEquals(30, m.n)
        // 命中率 / 出框率的分母都是**全部录入**：出框多不能被算成命中率高。
        assertEquals(30.0 / 35.0, m.hitRate, 1e-9)
        assertEquals(5.0 / 35.0, m.outRate, 1e-9)
        assertTrue(m.r95 < 1.0)                                // 只用窗内点：散布接近 0
    }

    @Test
    fun `比率的分母是全部录入而不是窗内镖数`() {
        // 一半出框：如果分母写成窗内镖数，出框多反而会被算成命中率高。
        val hits = List(10) { hit(hitAt = it.toLong()) } +
            List(10) { hit(hitAt = 100L + it, yMm = anchor.y + 30.0, outBand = 2) }

        val m = ImpactRepository.metricsOf(hits, target)

        assertEquals(10, m.n)
        assertEquals(0.5, m.hitRate, 1e-9)
        assertEquals(0.5, m.outRate, 1e-9)
    }

    @Test
    fun `缺意图或出框的镖不进合并样本`() {
        val opponent = hit(hitAt = 1L).copy(intentNumber = 0, intentMultiplier = 0)
        val missed = hit(hitAt = 2L, yMm = anchor.y + 30.0, outBand = 1)
        val normal = hit(hitAt = 3L)

        assertNull(ImpactRepository.mergedSampleOf(opponent))
        assertNull(ImpactRepository.mergedSampleOf(missed))
        assertNotNull(ImpactRepository.mergedSampleOf(normal))
        // 对局镖（空会话号 + 无意图）不能靠合并口径混进训练统计。
        assertEquals("s1", ImpactRepository.mergedSampleOf(normal)!!.sessionId)
    }

    // ---------------------------------------------------------------- 回合序号与会话聚合

    @Test
    fun `回合序号由首镖触发而不是除以三`() {
        val hits = listOf(2, 3, 1, 2, 3).mapIndexed { index, stored ->
            hit(hitAt = index.toLong(), dartIndexInRound = stored)
        }

        val darts = ImpactRepository.roundDartsOf(hits, target)

        assertEquals(listOf(1, 1, 2, 2, 2), darts.map { it.roundOrdinal })
        assertEquals(listOf(2, 3, 1, 2, 3), darts.map { it.dartInRound })
        // 残回合（只有第 2、3 镖）原样交给 domain 剔除，不补零、不造假的完整回合。
        val metrics = ImpactRounds.metricsOf(darts)
        assertEquals(1, metrics.completeRounds)
        assertEquals(0.0, metrics.roundSpread, 1e-9)
    }

    @Test
    fun `会话聚合忽略空会话号且按时间升序`() {
        val hits = listOf(
            hit(sessionId = "b", hitAt = 300L),
            hit(sessionId = "", hitAt = 200L),          // 对局镖：没有会话号
            hit(sessionId = "a", hitAt = 150L),
            hit(sessionId = "a", hitAt = 100L),
            hit(sessionId = "b", hitAt = 310L)
        )

        val sessions = ImpactRepository.sessionsOf(hits)

        assertEquals(listOf("a", "b"), sessions.map { it.first().sessionId })
        assertEquals(listOf(100L, 150L), sessions.first().map { it.hitAt })
        assertEquals(2, sessions.size)
    }

    @Test
    fun `summariesOf的达成由本轮自己的处方现算`() {
        val achieved = sessionOf("s1", darts = 12, firstHitAt = 1000L, metric = PrescriptionMetric.BIAS_ABS, target = 5.0, note = "换握法")
        val noGoal = sessionOf("s2", darts = 12, firstHitAt = 2000L, yMm = anchor.y - 10.0)

        val rows = ImpactRepository.summariesOf(
            ImpactRepository.sessionsOf(achieved + noGoal),
            target
        )

        assertEquals(2, rows.size)
        assertEquals("s1", rows[0].sessionId)
        assertEquals(12, rows[0].n)
        assertEquals(0.0, rows[0].bias, 1e-9)
        assertEquals(true, rows[0].achieved)                 // bias 0 ≤ 5.0
        assertEquals("换握法", rows[0].note)
        assertNull(rows[1].achieved)                         // 没设目标 ⇒ 不算达成也不算未达成
        assertEquals(10.0, rows[1].bias, 1e-9)
    }

    // ---------------------------------------------------------------- v8 四列落库形状

    @Test
    fun `newHit原样落库第八版四列`() {
        val h = ImpactRepository.newHit(
            sessionId = "s1",
            profileId = "p1",
            target = target,
            xMm = anchor.x,
            yMm = anchor.y,
            dartIndexInRound = 1,
            windowSpanMm = 60.0,
            hitAt = 1234L,
            prescriptionMetric = PrescriptionMetric.BIAS_ABS,
            prescriptionTarget = 5.0,
            interventionNote = "换握法",
            pressureMode = 1
        )

        assertEquals("BIAS_ABS", h.prescriptionMetric)
        assertEquals(PrescriptionMetric.BIAS_ABS, PrescriptionMetric.fromKey(h.prescriptionMetric))
        assertEquals(5.0, h.prescriptionTarget, 1e-9)
        assertEquals("换握法", h.interventionNote)
        assertEquals(1, h.pressureMode)

        // 其余列照旧：v8 只加列，不改老列语义。
        assertEquals(DartSource.IMPACT_DRILL.name, h.source)
        assertEquals("", h.matchId)
        assertEquals(0, h.legNumber)
        assertEquals(20, h.intentNumber)
        assertEquals(3, h.intentMultiplier)
        assertEquals(1, h.dartIndexInRound)
        assertEquals(60.0f, h.windowSpanMm, 1e-6f)
        assertEquals(0, h.outBand)
        assertEquals(0, h.outLevel)
        // 窗内锚点：这镖确实记成三倍 20。
        assertEquals(20, h.number)
        assertEquals(3, h.multiplier)
    }

    @Test
    fun `处方口径未知或缺失时退回无目标`() {
        // 白名单外/空白键 = 没设目标，绝不能猜一个口径出来。
        assertNull(PrescriptionMetric.fromKey(""))
        assertNull(PrescriptionMetric.fromKey("UNKNOWN_METRIC"))

        val rows = ImpactRepository.summariesOf(listOf(sessionOf("s3", darts = 12, firstHitAt = 1L)), target)
        assertNull(rows.single().achieved)
    }

    // ---------------------------------------------------------------- 构造器

    private fun sessionOf(
        sessionId: String,
        darts: Int,
        firstHitAt: Long,
        yMm: Double = anchor.y,
        metric: PrescriptionMetric? = null,
        target: Double = 0.0,
        note: String = ""
    ): List<DartHitEntity> = (0 until darts).map { index ->
        hit(
            sessionId = sessionId,
            hitAt = firstHitAt + index,
            yMm = yMm,
            dartIndexInRound = index % 3 + 1,
            prescriptionMetric = if (index == 0) metric else null,
            prescriptionTarget = if (index == 0) target else 0.0,
            interventionNote = if (index == 0) note else ""
        )
    }

    private fun hit(
        sessionId: String = "s1",
        hitAt: Long = 0L,
        xMm: Double = anchor.x,
        yMm: Double = anchor.y,
        dartIndexInRound: Int = 1,
        outBand: Int = 0,
        prescriptionMetric: PrescriptionMetric? = null,
        prescriptionTarget: Double = 0.0,
        interventionNote: String = ""
    ): DartHitEntity = ImpactRepository.newHit(
        sessionId = sessionId,
        profileId = "p1",
        target = target,
        xMm = xMm,
        yMm = yMm,
        dartIndexInRound = dartIndexInRound,
        windowSpanMm = 60.0,
        outBand = outBand,
        outLevel = if (outBand == 0) 0 else 1,
        hitAt = hitAt,
        prescriptionMetric = prescriptionMetric,
        prescriptionTarget = prescriptionTarget,
        interventionNote = interventionNote
    )

    @Test
    fun `空输入不抛异常`() {
        assertTrue(ImpactRepository.cevSamplesOf(emptyList()).isEmpty())
        assertTrue(ImpactRepository.sessionsOf(emptyList()).isEmpty())
        assertTrue(ImpactRepository.summariesOf(emptyList(), target).isEmpty())
        assertEquals(RoundMetrics.EMPTY, ImpactRounds.metricsOf(ImpactRepository.roundDartsOf(emptyList(), target)))
        val m = ImpactRepository.metricsOf(emptyList(), target)
        assertEquals(0, m.n)
        assertEquals(0.0, m.hitRate, 1e-9)
        assertEquals(0.0, m.outRate, 1e-9)
        assertFalse(m.n > 0)
    }
}
