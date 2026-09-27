package com.dartvio.app.domain.impact

import com.dartvio.app.domain.vision.BoardGeometry
import com.dartvio.app.domain.vision.Point2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V1.4 七项新增的领域层纯 JVM 单测（无 Android 依赖）。
 *
 * 对应验收：A-IMP-24 / A-IMP-25（达成判定与跨界提醒）、A-IMP-27（处方建议 + cEV + 结镖率）、
 * A-IMP-28（投掷指纹）、A-IMP-29（三镖回合）、A-IMP-30（双组叠加口径）、A-IMP-31（干预对照）。
 *
 * A-IMP-26 的「两条输入路径」在数据层的 `ImpactV14WiringTest`；A-IMP-23 的迁移三处一致在
 * `DartVioMigrationV8Test`。
 */
class ImpactV14DomainTest {

    // ---------------------------------------------------------------- 处方建议（A-IMP-27）

    @Test
    fun `无上轮数据时建议值落在默认偏移目标`() {
        val p = ImpactPrescription.suggestPrescription(null)

        assertEquals(PrescriptionMetric.BIAS_ABS, p.metric)
        assertEquals(ImpactPrescription.DEFAULT_BIAS_TARGET_MM, p.target, 1e-9)
        assertEquals(3.0, p.target, 1e-9)
        // 非用户自设 ⇒ 不带来源会话。
        assertEquals("", p.fromSessionId)
        assertEquals("3.0 mm", p.targetText())
    }

    @Test
    fun `建议值取上轮实测的一半且永不低于下限`() {
        val prev = metrics(sessionId = "s1", n = 40, bias = 10.0, r95 = 30.0, rmse = 12.0)
        val loose = ImpactPrescription.suggestPrescription(prev)

        assertEquals(5.0, loose.target, 1e-9)          // 10 × 0.5
        assertEquals("s1", loose.fromSessionId)        // 建议来源可追溯
        assertEquals(PrescriptionMetric.BIAS_ABS, loose.metric)

        // 指定口径：散布 / 综合偏离走同一收紧系数。
        assertEquals(15.0, ImpactPrescription.suggestPrescription(prev, PrescriptionMetric.R95).target, 1e-9)
        assertEquals(6.0, ImpactPrescription.suggestPrescription(prev, PrescriptionMetric.RMSE).target, 1e-9)

        // 已经很紧时不许给出比一个环宽还苛刻的目标。
        val tight = metrics(sessionId = "s2", n = 40, bias = 2.0, r95 = 2.6)
        assertEquals(
            ImpactPrescription.MIN_TARGET_MM,
            ImpactPrescription.suggestPrescription(tight).target,
            1e-9
        )                                              // max(1.5, 1.0) = 1.5
        assertEquals(1.5, ImpactPrescription.suggestPrescription(tight, PrescriptionMetric.R95).target, 1e-9)

        // 上轮不足门槛 ⇒ 退回默认（不拿 11 镖的均值当基线）。
        val few = metrics(sessionId = "s3", n = 11, bias = 10.0, r95 = 30.0)
        assertEquals(3.0, ImpactPrescription.suggestPrescription(few).target, 1e-9)
    }

    @Test
    fun `比例类目标走另一套映射且不受毫米下限约束`() {
        val prev = metrics(sessionId = "s1", n = 40, hitRate = 0.5, outRate = 0.2)

        assertEquals(
            0.5 * ImpactPrescription.RATE_TIGHTEN_RATIO,
            ImpactPrescription.suggestPrescription(prev, PrescriptionMetric.HIT_RATE).target,
            1e-9
        )
        assertEquals(
            0.2 / ImpactPrescription.RATE_TIGHTEN_RATIO,
            ImpactPrescription.suggestPrescription(prev, PrescriptionMetric.OUT_RATE).target,
            1e-9
        )

        // 命中率只有 10% 时目标就是 11% —— 绝不能被 1.5 mm 的下限错误改写。
        val low = metrics(sessionId = "s2", n = 40, hitRate = 0.1, outRate = 0.0)
        assertEquals(0.11, ImpactPrescription.suggestPrescription(low, PrescriptionMetric.HIT_RATE).target, 1e-9)
        assertEquals(0.0, ImpactPrescription.suggestPrescription(low, PrescriptionMetric.OUT_RATE).target, 1e-9)

        // 命中率封顶 100%。
        val good = metrics(sessionId = "s3", n = 40, hitRate = 0.98, outRate = 0.3)
        assertEquals(1.0, ImpactPrescription.suggestPrescription(good, PrescriptionMetric.HIT_RATE).target, 1e-9)
    }

    // ---------------------------------------------------------------- 达成判定（A-IMP-24 / A-IMP-25）

    @Test
    fun `未设目标与样本不足都不给达成与否`() {
        val notSet = ImpactPrescription.judge(m = metrics(n = 40, bias = 2.0), p = null)
        assertEquals(Verdict.NOT_SET, notSet.verdict)
        assertNull(notSet.actual)
        assertNull(notSet.target)
        assertTrue(notSet.note.contains("没设量化目标"))

        val few = ImpactPrescription.judge(
            m = metrics(n = 11, bias = 2.0),
            p = Prescription(PrescriptionMetric.BIAS_ABS, 3.0)
        )
        assertEquals(Verdict.INSUFFICIENT, few.verdict)
        assertNull(few.actual)
        assertEquals(3.0, few.target!!, 1e-9)          // 目标照给，只不判定
        assertTrue(few.note.contains("还差 1 镖"))
    }

    @Test
    fun `判定方向随口径变化且文案带阈值`() {
        val biasTarget = Prescription(PrescriptionMetric.BIAS_ABS, 3.0)

        val achieved = ImpactPrescription.judge(metrics(n = 40, bias = 3.0), biasTarget)
        assertEquals(Verdict.ACHIEVED, achieved.verdict)      // 越小越好 ⇒ ≤ 也算达成
        assertEquals(3.0, achieved.actual!!, 1e-9)
        assertTrue(achieved.note.contains("≤ 3.0 mm"))
        assertTrue(achieved.note.contains("达成。"))

        val missed = ImpactPrescription.judge(metrics(n = 40, bias = 3.1), biasTarget)
        assertEquals(Verdict.MISSED, missed.verdict)
        assertTrue(missed.note.contains("未达标"))

        // 命中率是越大越好 ⇒ 用 ≥，文案用百分号。
        val rate = ImpactPrescription.judge(
            metrics(n = 40, hitRate = 0.8),
            Prescription(PrescriptionMetric.HIT_RATE, 0.75)
        )
        assertEquals(Verdict.ACHIEVED, rate.verdict)
        assertTrue(rate.note.contains("≥ 75%"))
        assertTrue(rate.note.contains("实测 80%"))
        assertEquals(0.8, rate.actual!!, 1e-9)
    }

    @Test
    fun `达成同时散布变大必须并存提醒`() {
        val p = Prescription(PrescriptionMetric.BIAS_ABS, 3.0)

        // 30 → 40 mm：超过 1.25 倍 ⇒ 很可能只是把误差换了个方向。
        val warned = ImpactPrescription.judge(metrics(n = 40, bias = 2.0, r95 = 40.0), p, prevR95 = 30.0)
        assertEquals(Verdict.ACHIEVED, warned.verdict)
        assertNotNull(warned.crossWarning)
        assertTrue(warned.crossWarning!!.contains("把误差换了方向"))
        assertTrue(warned.note.contains("达成。"))      // 并存，不覆盖

        // 正好 1.25 倍（严格大于才提醒）。
        assertNull(ImpactPrescription.judge(metrics(n = 40, bias = 2.0, r95 = 37.5), p, prevR95 = 30.0).crossWarning)
        // R95 自己就是判定口径时不再追问散布。
        assertNull(
            ImpactPrescription.judge(
                metrics(n = 40, r95 = 10.0),
                Prescription(PrescriptionMetric.R95, 12.0),
                prevR95 = 30.0
            ).crossWarning
        )
        // 没达成就不提醒。
        assertNull(ImpactPrescription.judge(metrics(n = 40, bias = 5.0, r95 = 40.0), p, prevR95 = 30.0).crossWarning)
        // 没有上一轮可比。
        assertNull(ImpactPrescription.judge(metrics(n = 40, bias = 2.0, r95 = 40.0), p, prevR95 = null).crossWarning)
    }

    @Test
    fun `连续三轮达标提示收紧目标`() {
        val p = Prescription(PrescriptionMetric.BIAS_ABS, 3.0)
        val m = metrics(n = 40, bias = 2.0)

        assertNull(ImpactPrescription.judge(m, p, achievedStreak = 1).exitHint)
        val third = ImpactPrescription.judge(m, p, achievedStreak = ImpactPrescription.EXIT_ACHIEVED_STREAK - 1)
        assertEquals(Verdict.ACHIEVED, third.verdict)
        assertNotNull(third.exitHint)
        assertTrue(third.exitHint!!.contains("连续 3 轮达标"))
    }

    // ---------------------------------------------------------------- cEV 与结镖率（A-IMP-27）

    @Test
    fun `cev的样本含出框折算点且不含适应期`() {
        val aim = IntentTarget.triple(20)
        val anchor = aim.anchorMm()
        // 30 镖落在 T20 锚点（60 分）+ 10 个出框折算点（靶外，0 分）。
        val samples = List(30) { Point2(anchor.x, anchor.y) } + List(10) { Point2(0.0, 250.0) }

        val result = ImpactValue.compare(samples, aim)

        assertEquals(40, result.n)
        // miss 是真实得分：期望值必须把 0 分算进分母。
        assertEquals(45.0, result.baseline.cev, 1e-9)

        // 首屏 = 当前目标 + 最优候选，且候选只在同类里比。
        assertEquals(aim, result.shortlist.first().target)
        assertTrue(result.shortlist.size in 1..2)
        assertEquals(4, result.options.size)
        assertTrue(result.options.all { it.target.kind == IntentKind.TRIPLE })
        assertEquals(result.options, result.options.sortedByDescending { it.cev })

        assertTrue(result.note.contains("适应期"))
        assertTrue(result.note.contains("n=40"))
    }

    @Test
    fun `cev样本不足或收益为 0 时都不出结论`() {
        val aim = IntentTarget.triple(20)
        val anchor = aim.anchorMm()

        val few = ImpactValue.compare(List(29) { Point2(anchor.x, anchor.y) }, aim)
        assertEquals(29, few.n)
        assertFalse(few.worthShowing)
        assertFalse(few.significant)
        assertTrue(few.note.contains("低于 ${ImpactValue.MIN_CEV_N}"))
        assertEquals(30, ImpactValue.MIN_CEV_N)

        // 全钉在瞄点上：每个同类候选平移后都还是三倍区 ⇒ 收益为 0，不该打扰用户。
        val flat = ImpactValue.compare(List(40) { Point2(anchor.x, anchor.y) }, aim)
        assertEquals(60.0, flat.baseline.cev, 1e-9)
        assertEquals(0.0, flat.gain, 1e-9)
        assertEquals(0.0, flat.gainRatio, 1e-9)
        assertFalse(flat.worthShowing)
        assertFalse(flat.significant)
        // 契约自洽：gain / gainRatio / 展示门槛三者必须由同一组数推出来。
        assertEquals(flat.best.cev - flat.baseline.cev, flat.gain, 1e-9)
        assertEquals(flat.gain / flat.baseline.cev, flat.gainRatio, 1e-9)
        assertEquals(
            flat.n >= ImpactValue.MIN_CEV_N && flat.gainRatio >= ImpactValue.SHOW_GAIN_RATIO,
            flat.worthShowing
        )
        assertEquals(listOf(flat.baseline, flat.best).distinctBy { it.target }, flat.shortlist)
    }

    @Test
    fun `配对检验在收益真实时才会打开显著开关`() {
        val aim = IntentTarget.triple(20)
        val candidate = IntentTarget.triple(17)
        val anchor = aim.anchorMm()
        val shiftX = candidate.anchorMm().x - anchor.x
        val shiftY = candidate.anchorMm().y - anchor.y

        // 20 个「基线 0 分（在靶外）、整体平移到候选后变成 60 分」的点：
        // 取候选三倍环上的点 q，反推 p = q − 平移量。坐标由域自己算，不写死。
        val zeroThenTriple = (0 until 360).mapNotNull { degree ->
            val rad = Math.toRadians(degree.toDouble())
            val q = Point2(103.0 * kotlin.math.sin(rad), 103.0 * kotlin.math.cos(rad))
            val p = Point2(q.x - shiftX, q.y - shiftY)
            if (BoardGeometry.dartAt(p).score == 0) p else null
        }.take(20)
        assertEquals(20, zeroThenTriple.size)

        // 另外 20 镖钉在瞄点上（任何三倍候选都给 60 分）。
        val result = ImpactValue.compare(zeroThenTriple + List(20) { Point2(anchor.x, anchor.y) }, aim)

        assertEquals(40, result.n)
        assertEquals(30.0, result.baseline.cev, 1e-9)      // (20×0 + 20×60) / 40
        assertTrue(result.gain > 0)
        assertTrue(result.worthShowing)                    // 收益比例远超 5%
        assertTrue(result.significant)                     // 逐镖差值参差且均值远离 0
    }

    @Test
    fun `平移到候选锚点后才判命中`() {
        val aim = IntentTarget.triple(20)
        val d16 = IntentTarget.double(16)
        val anchor = aim.anchorMm()

        // 瞄点上的那一镖，平移到 D16 后正好落在 D16 锚点 ⇒ 命中。
        assertTrue(ImpactValue.hitsAfterShift(anchor, aim, d16))
        // 径向内移 40 mm 后不再落在双倍环。
        assertFalse(ImpactValue.hitsAfterShift(Point2(anchor.x, anchor.y - 40.0), aim, d16))
    }

    @Test
    fun `结镖命中率的分母是全部样本`() {
        val aim = IntentTarget.triple(20)
        val d20 = IntentTarget.double(20)
        val aimAnchor = aim.anchorMm()
        // 20 镖钉在瞄点（平移到 D20 正好落在双倍环锚点），20 镖在瞄点径向内移 43 mm
        // （平移后落在单倍区，算不中）。
        val samples = List(20) { Point2(aimAnchor.x, aimAnchor.y) } +
            List(20) { Point2(aimAnchor.x, aimAnchor.y - 43.0) }

        val rates = ImpactValue.finishRates(samples, aim)
        val d20Rate = rates.single { it.target == d20 }

        assertEquals(20, d20Rate.hits)
        assertEquals(40, d20Rate.n)
        assertEquals(0.5, d20Rate.rate, 1e-9)
        assertEquals(d20, rates.first().target)         // 结镖率榜首就是当前最该练的那个
        assertTrue(rates.all { it.n == 40 })
        assertEquals(4, rates.size)
    }

    // ---------------------------------------------------------------- 投掷指纹（A-IMP-28）

    @Test
    fun `指纹门槛与占比口径`() {
        val frames = List(30) { ImpactFrame(eRad = 3.0, eTan = 1.0) }

        // 整体镖数不足 30 ⇒ 连占比条都不给。
        assertNull(ImpactFingerprintCalculator.fingerprint(frames, outCount = 0, total = 29))

        val fp = ImpactFingerprintCalculator.fingerprint(frames, outCount = 3, total = 33)!!
        assertEquals(33, fp.n)
        assertEquals(0.75, fp.radialShare, 1e-9)       // 3 / (3 + 1)
        assertEquals(0.25, fp.tangentShare, 1e-9)
        assertEquals(1.0 - fp.radialShare, fp.tangentShare, 1e-12)
        assertEquals(3.0 / 33, fp.outShare, 1e-9)
        assertEquals(3.0, fp.meanERad, 1e-9)
        assertEquals(1.0, fp.meanETan, 1e-9)
        assertNull(fp.dominant)                        // 33 < 100 只给占比
        assertTrue(fp.portrait().contains("到 ${ImpactFingerprintCalculator.MIN_DOMINANT_N} 镖"))
    }

    @Test
    fun `占比定性阈值与偏内偏外文案`() {
        assertEquals(FingerprintAxis.RADIAL, ImpactFingerprintCalculator.axisOf(0.565))
        assertEquals(FingerprintAxis.RADIAL, ImpactFingerprintCalculator.axisOf(0.9))
        assertEquals(FingerprintAxis.TANGENT, ImpactFingerprintCalculator.axisOf(0.435))
        assertEquals(FingerprintAxis.TANGENT, ImpactFingerprintCalculator.axisOf(0.1))
        assertEquals(FingerprintAxis.BALANCED, ImpactFingerprintCalculator.axisOf(0.5))

        val radial = ImpactFingerprintCalculator.fingerprint(
            frames = List(100) { ImpactFrame(4.0, 1.0) },
            outCount = 0,
            total = 100
        )!!
        assertEquals(FingerprintAxis.RADIAL, radial.dominant)
        val portrait = radial.portrait()
        assertTrue(portrait.contains("径向为主（80%）"))
        assertTrue(portrait.contains("偏外"))           // meanERad > 0
        // 三条禁令之一：不打分。
        assertFalse(portrait.contains("评分"))
        assertFalse(portrait.contains("排名"))
        assertFalse(portrait.contains("星级"))

        // 偏内型：径向均值为负。
        val inward = ImpactFingerprintCalculator.fingerprint(
            frames = List(100) { ImpactFrame(-4.0, 1.0) },
            outCount = 0,
            total = 100
        )!!
        assertTrue(inward.portrait().contains("偏内"))
    }

    @Test
    fun `出框镖只进outShare不进占比`() {
        val samples = List(30) { ImpactSample("s1", it.toLong(), ImpactFrame(1.0, 1.0), hit = true) } +
            List(10) { ImpactSample("s1", 100L + it, frame = null, hit = false) }

        val fp = ImpactFingerprintCalculator.of(samples)!!

        assertEquals(40, fp.n)
        assertEquals(0.25, fp.outShare, 1e-9)
        assertEquals(0.5, fp.radialShare, 1e-9)        // 只用 30 个窗内帧
        assertNull(fp.dominant)
    }

    // ---------------------------------------------------------------- 三镖回合（A-IMP-29）

    @Test
    fun `只有完整回合进指标且极值可定位`() {
        val darts = round("s1", 1, spreadERad = 3.0) +                  // 回合内散布 2.0
            round("s1", 2, spreadERad = 6.0, allHit = false) +          // 回合内散布 4.0
            listOf(                                                       // 少了第 3 镖 ⇒ 不完整
                RoundDart("s1", 3000L, 3, 1, ImpactFrame(0.0, 0.0), true),
                RoundDart("s1", 3001L, 3, 2, ImpactFrame(0.0, 0.0), true)
            ) +
            listOf(                                                       // 有出框 ⇒ 不完整
                RoundDart("s1", 4000L, 4, 1, ImpactFrame(0.0, 0.0), true),
                RoundDart("s1", 4001L, 4, 2, ImpactFrame(0.0, 0.0), true),
                RoundDart("s1", 4002L, 4, 3, null, true)
            )

        val m = ImpactRounds.metricsOf(darts)

        assertEquals(2, m.completeRounds)
        assertEquals(3.0, m.roundSpread, 1e-9)
        assertEquals(2.0, m.roundSpreadMin, 1e-9)
        assertEquals(4.0, m.roundSpreadMax, 1e-9)
        assertEquals("s1#1", m.tightRoundKey)
        assertEquals("s1#2", m.looseRoundKey)
        assertEquals(1, m.perfectRounds)
        assertEquals(listOf(true, false), m.rounds.map { it.allHit })
    }

    @Test
    fun `残回合被剔除且回合数不足只给极值`() {
        // 撤销过 / 中途退出：dartInRound 不是 1、2、3 的回合直接排除，不补零。
        val broken = listOf(
            RoundDart("s1", 1L, 1, 2, ImpactFrame(0.0, 0.0), true),
            RoundDart("s1", 2L, 1, 3, ImpactFrame(0.0, 0.0), true)
        )
        assertEquals(RoundMetrics.EMPTY, ImpactRounds.metricsOf(emptyList()))
        assertEquals(RoundMetrics.EMPTY, ImpactRounds.metricsOf(broken))

        val m = ImpactRounds.metricsOf(round("s1", 1, spreadERad = 3.0))
        assertEquals(1, m.completeRounds)
        val advice = ImpactRounds.advice(stats = null, metrics = m)
        assertFalse(advice.ready)
        assertTrue(advice.text.contains("还差 ${ImpactRounds.MIN_COMPLETE_ROUNDS - 1} 个"))
        assertEquals(8, ImpactRounds.MIN_COMPLETE_ROUNDS)
    }

    @Test
    fun `回合内散布与整体R95放在一起解读`() {
        val m = ImpactRounds.metricsOf((1..8).flatMap { round("s1", it, spreadERad = 3.0) })
        assertEquals(8, m.completeRounds)
        assertEquals(2.0, m.roundSpread, 1e-9)

        // R95 远大于回合内散布 ⇒ 回合间漂移（位置在移动），不是手抖。
        val drift = ImpactRounds.advice(stats(40, r95 = 6.0), m)
        assertTrue(drift.ready)
        assertTrue(drift.text.contains("回合间漂移"))

        // 两者接近 ⇒ 主要就是手法抖动。
        val shake = ImpactRounds.advice(stats(40, r95 = 2.5), m)
        assertFalse(shake.text.contains("回合间漂移"))
        assertTrue(shake.text.contains("手法抖动"))
        assertEquals(1.5, ImpactRounds.ROUND_DRIFT_RATIO, 1e-9)
    }

    // ---------------------------------------------------------------- 双组叠加（A-IMP-30）

    @Test
    fun `叠加图的点数上限与结论门槛`() {
        val loose = List(40) { ImpactFrame(if (it % 2 == 0) 1.0 else -1.0, 0.0) }   // r95 = 1.0
        val tight = List(40) { ImpactFrame(if (it % 2 == 0) 0.5 else -0.5, 0.0) }   // r95 = 0.5
        val rounds = (1..3).map { roundWithFrames("old$it", it * 1000L, loose) } +
            (1..3).map { roundWithFrames("new$it", 10_000L + it * 1000L, tight) }

        val overlay = ImpactTrend.overlay(rounds)

        assertEquals(120, overlay.previous.n)
        assertEquals(120, overlay.recent.n)
        assertEquals(120, overlay.previous.frames.size)
        assertEquals(1.0, overlay.previous.r95, 1e-9)
        assertEquals(0.5, overlay.recent.r95, 1e-9)
        assertEquals(0.5, overlay.deltaR95, 1e-9)
        assertTrue(overlay.conclusionReady)                          // 两侧各 ≥12 镖且合计 ≥30
        assertTrue(overlay.dense)                                    // 240 > 200 ⇒ 改画质心 + r95 圆
        assertFalse(overlay.mixedSpans)
        assertEquals(200, ImpactTrend.MAX_OVERLAY_POINTS)
    }

    @Test
    fun `轮数不足或跨档位时叠加作废`() {
        val frames = List(20) { ImpactFrame(if (it % 2 == 0) 1.0 else -1.0, 0.0) }

        val notEnough = ImpactTrend.overlay((1..4).map { roundWithFrames("s$it", it * 1000L, frames) })
        assertFalse(notEnough.conclusionReady)                       // 需要 2 × 3 轮
        assertEquals(0, notEnough.previous.n)
        assertEquals(0, notEnough.recent.n)
        assertEquals(0.0, notEnough.deltaR95, 1e-9)

        val mixed = (1..3).map { roundWithFrames("a$it", it * 1000L, frames, spanMm = 60.0) } +
            (1..3).map { roundWithFrames("b$it", 10_000L + it * 1000L, frames, spanMm = 120.0) }
        assertTrue(ImpactTrend.overlay(mixed).mixedSpans)             // 60 与 120 不同尺，不得叠在一起
        assertFalse(
            ImpactTrend.overlay((1..6).map { roundWithFrames("c$it", it * 1000L, frames, spanMm = 60.0) })
                .mixedSpans
        )
    }

    // ---------------------------------------------------------------- 干预对照（A-IMP-31）

    @Test
    fun `干预对照只在有效轮次给中性对比`() {
        val rows = listOf(
            summary("a", 1L, note = "", r95 = 20.0, n = 20),
            summary("b", 2L, note = "换握法", r95 = 10.0, n = 20),
            summary("c", 3L, note = "换握法", r95 = 10.0, n = 20),
            summary("d", 4L, note = "换握法", r95 = 10.0, n = 20)
        )

        val report = ImpactPrescription.interventionCompare(rows)
        val cmp = report.comparisons.single { it.note == "换握法" }

        assertEquals(3, cmp.rounds)
        assertEquals(60, cmp.darts)
        assertNotNull(cmp.text)
        assertTrue(cmp.text!!.contains("R95 均值 10.0 mm"))
        assertTrue(cmp.text!!.contains("未标注轮 R95 均值 20.0 mm"))
        // 因果词是硬红线：整张卡的信任度都在这一句上。
        assertFalse(cmp.text!!.contains("因为"))
        assertFalse(cmp.text!!.contains("证明"))
        assertTrue(report.footnote.contains("不作因果结论"))
        assertEquals(listOf("a", "b", "c", "d"), report.rows.map { it.sessionId })   // 按时间升序
    }

    @Test
    fun `轮次或镖数不够时只列数据不说话`() {
        val rows = listOf(
            summary("a", 1L, note = "", r95 = 20.0, n = 20),
            summary("b", 2L, note = "换握法", r95 = 10.0, n = 20),
            summary("c", 3L, note = "换握法", r95 = 10.0, n = 20),
            summary("d", 4L, note = "换握法", r95 = 10.0, n = 20)
        )

        // 只有 2 轮标注 ⇒ 不足以对比。
        val fewRounds = ImpactPrescription.interventionCompare(rows.take(3))
        val twoRounds = fewRounds.comparisons.single()
        assertEquals(2, twoRounds.rounds)
        assertNull(twoRounds.text)

        // 轮数够但镖数不够（3 × 9 = 27 < 30）。
        val fewDarts = ImpactPrescription.interventionCompare(rows.map { it.copy(n = 9) })
        assertEquals(27, fewDarts.comparisons.single().darts)
        assertNull(fewDarts.comparisons.single().text)

        // 没有未标注轮作对照 ⇒ 只列数据。
        val noBaseline = ImpactPrescription.interventionCompare(rows.drop(1))
        assertNull(noBaseline.comparisons.single().text)

        assertEquals(3, ImpactPrescription.MIN_INTERVENTION_ROUNDS)
        assertEquals(30, ImpactPrescription.MIN_INTERVENTION_DARTS)
    }

    // ---------------------------------------------------------------- 构造器

    /** 一个完整三镖回合：第 2 镖沿径向偏 [spreadERad]，两两距离均值 = `2/3 × spreadERad`。 */
    private fun round(
        sessionId: String,
        ordinal: Int,
        spreadERad: Double,
        allHit: Boolean = true
    ): List<RoundDart> = (1..3).map { index ->
        RoundDart(
            sessionId = sessionId,
            hitAt = ordinal * 1000L + index,
            roundOrdinal = ordinal,
            dartInRound = index,
            frame = ImpactFrame(eRad = if (index == 2) spreadERad else 0.0, eTan = 0.0),
            hit = allHit
        )
    }

    private fun roundWithFrames(
        sessionId: String,
        firstHitAt: Long,
        frames: List<ImpactFrame>,
        spanMm: Double = 60.0
    ): ImpactRound = ImpactRound(
        sessionId = sessionId,
        firstHitAt = firstHitAt,
        dartCount = frames.size,
        hitCount = frames.size,
        stats = ImpactCalculator.of(frames),
        frames = frames,
        spanMm = spanMm
    )

    private fun metrics(
        sessionId: String = "s1",
        n: Int = 40,
        bias: Double = 0.0,
        r95: Double = 10.0,
        rmse: Double = 1.0,
        hitRate: Double = 0.5,
        outRate: Double = 0.0
    ): SessionMetrics = SessionMetrics(
        sessionId = sessionId,
        hitAt = 0L,
        n = n,
        bias = bias,
        r95 = r95,
        rmse = rmse,
        hitRate = hitRate,
        outRate = outRate
    )

    private fun stats(n: Int, r95: Double): ImpactStats = ImpactStats(
        n = n,
        biasRad = 0.0,
        biasTan = 0.0,
        sigmaRad = 1.0,
        sigmaTan = 1.0,
        r95 = r95,
        rmse = 1.0
    )

    private fun summary(
        sessionId: String,
        hitAt: Long,
        note: String,
        r95: Double,
        n: Int
    ): SessionSummary = SessionSummary(
        sessionId = sessionId,
        hitAt = hitAt,
        note = note,
        n = n,
        bias = 2.0,
        r95 = r95,
        hitRate = 0.5,
        achieved = null
    )
}
