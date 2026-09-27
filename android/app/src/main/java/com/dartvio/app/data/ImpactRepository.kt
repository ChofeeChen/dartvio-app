package com.dartvio.app.data

import com.dartvio.app.data.local.dao.DartHitDao
import com.dartvio.app.data.local.dao.MissBandCount
import com.dartvio.app.data.local.entity.DartHitEntity
import com.dartvio.app.domain.impact.ImpactCalculator
import com.dartvio.app.domain.impact.ImpactFrame
import com.dartvio.app.domain.impact.ImpactFrames
import com.dartvio.app.domain.impact.ImpactPrescription
import com.dartvio.app.domain.impact.ImpactSample
import com.dartvio.app.domain.impact.IntentTarget
import com.dartvio.app.domain.impact.Prescription
import com.dartvio.app.domain.impact.PrescriptionMetric
import com.dartvio.app.domain.impact.RoundDart
import com.dartvio.app.domain.impact.SessionMetrics
import com.dartvio.app.domain.impact.SessionSummary
import com.dartvio.app.domain.impact.Verdict
import com.dartvio.app.domain.model.DartSource
import com.dartvio.app.domain.vision.BoardGeometry
import com.dartvio.app.domain.vision.Point2
import java.util.UUID

/**
 * 落点诊断训练（M11 新增子练习）的读写入口。
 *
 * 与 [DartHitRepository] 的区别在**写入时机**：对局是打完才落库（`matchId` 那时才存在），
 * 而诊断训练是**每点一镖立刻写一行** —— 中途退出时已录的镖必须还在，
 * 而且「撤销上一镖」要能真的把它从库里删掉（`sessionId` 就是为这件事引入的）。
 *
 * 会话 id 由本类生成，UI 只负责把它在三个页面之间传递。
 */
class ImpactRepository(private val dao: DartHitDao) {

    /** 新建一次练习会话 id。 */
    fun newSessionId(): String = UUID.randomUUID().toString()

    /** 逐镖即时写库。 */
    suspend fun record(hit: DartHitEntity) = dao.insertAll(listOf(hit))

    /** 撤销：删掉该会话的最后一镖（按写入顺序），返回删除后剩余的镖数。 */
    suspend fun undoLast(sessionId: String): Int {
        dao.deleteLast(sessionId)
        return dao.listBySession(sessionId).size
    }

    /** 一次会话的全部镖（写入顺序）。 */
    suspend fun hitsOf(sessionId: String): List<DartHitEntity> = dao.listBySession(sessionId)

    /**
     * 同一意图 + 同一窗口档位的历史镖（最近在前）。
     *
     * `windowSpanMm` 参与收窄：跨档位的成绩不可比，混合统计会让「趋势」变成
     * 「换了几次窗口」的记录。
     */
    suspend fun historyFor(
        profileId: String,
        target: IntentTarget,
        windowSpanMm: Double,
        limit: Int
    ): List<DartHitEntity> {
        val (number, multiplier) = target.toStored()
        return dao.listByProfileIntent(
            profileId = profileId,
            intentNumber = number,
            intentMultiplier = multiplier,
            windowSpanMm = windowSpanMm.toFloat(),
            limit = limit
        )
    }

    /**
     * 同一窗口档位下**跨目标**的历史镖（最近在前）。
     *
     * 「跨目标合并」是样本累积最快的视图：T20 / T19 / D16 的误差放进同一个局部坐标系，
     * 反映的是**随机散布倾向**（各目标的系统偏移不同，合并视图里会被平均掉）。
     */
    suspend fun mergedHistory(
        profileId: String,
        windowSpanMm: Double,
        limit: Int
    ): List<DartHitEntity> = dao.listByProfileSpan(profileId, windowSpanMm.toFloat(), limit)

    /** 某会话的出框分布（计数，含 `outBand != 0` 的镖）。 */
    suspend fun missBands(sessionId: String): List<MissBandCount> = dao.countMissByBand(sessionId)

    /** 落点诊断累计录入的镖数（练习入口卡展示用）。 */
    suspend fun totalDarts(): Int = dao.countBySource(DartSource.IMPACT_DRILL.name)

    /** 最近的若干镖（落点诊断来源），给入口卡算「最近 R95」。 */
    suspend fun recentDarts(limit: Int): List<DartHitEntity> =
        dao.listRecentBySource(DartSource.IMPACT_DRILL.name, limit)

    /** 某意图曾用过的窗口档位（去重）。 */
    suspend fun windowSpansOf(profileId: String, target: IntentTarget): List<Float> {
        val (number, multiplier) = target.toStored()
        return dao.listWindowSpans(profileId, number, multiplier)
    }

    /**
     * 该人**全部**落点诊断镖（跨目标 / 跨档位，最近在前）—— 投掷指纹的数据源（§4.7）。
     *
     * 取回后在领域层按 `outBand` 分出「窗内 / 出框」，仓储不替统计口径做过滤。
     */
    suspend fun allHistory(profileId: String, limit: Int = HISTORY_LIMIT): List<DartHitEntity> =
        dao.listAllByProfile(profileId, limit)

    /** 设过本轮目标的镖（最近在前）—— 干预对照表的数据源。 */
    suspend fun prescriptionHistory(profileId: String, limit: Int = HISTORY_LIMIT): List<DartHitEntity> =
        dao.listSessionsWithPrescription(profileId, limit)

    /**
     * 投掷指纹的输入（§4.7）：**同一窗口档位**的全部历史，每镖用**它自己的**意图做误差分解。
     *
     * 为什么必须同档位、却可以跨目标：占比是尺度无关的（见 [ImpactFingerprint]），
     * 但不同档位的"出框"含义完全不同，混在一起会让 `outShare` 失真。
     * 出框镖**保留**在结果里（`frame == null`）—— 它们是 `outShare` 的分母。
     */
    suspend fun fingerprintSamples(
        profileId: String,
        spanMm: Double,
        limit: Int = HISTORY_LIMIT
    ): List<ImpactSample> {
        val hits = dao.listAllByProfile(profileId, limit)
        return hits.mapNotNull { hit ->
            if (hit.windowSpanMm.toDouble() != spanMm) return@mapNotNull null
            val intent = IntentTarget.fromStored(hit.intentNumber, hit.intentMultiplier)
                ?: return@mapNotNull null
            val frame = frameOf(hit, intent)
            ImpactSample(
                sessionId = hit.sessionId,
                hitAt = hit.hitAt,
                frame = frame,
                hit = frame != null &&
                    intent.isHit(BoardGeometry.dartAt(hit.xMm.toDouble(), hit.yMm.toDouble())),
                spanMm = spanMm
            )
        }
    }

    companion object {

        /** 单次会话默认最多读多少历史镖（趋势只需最近若干组）。 */
        const val HISTORY_LIMIT = 300

        /**
         * 一镖 → 误差分解。
         *
         * **出框镖返回 `null`**：它的坐标是被 clamp / 外推出来的，喂进 σ 与 KDE 会沿窗口
         * 边界堆出一条假高峰（设计 §4.1 的硬红线），所以这里就把它挡在统计之外。
         */
        fun frameOf(hit: DartHitEntity, target: IntentTarget): ImpactFrame? =
            if (hit.outBand != 0) {
                null
            } else {
                ImpactFrames.of(target, hit.xMm.toDouble(), hit.yMm.toDouble())
            }

        /**
         * 一镖 → 跨目标合并样本：意图由该行自己的 `(intentNumber, intentMultiplier)` 还原。
         *
         * @return `null` = 无意图（对局镖）或出框镖 —— 两种都不能进合并散布。
         */
        fun mergedSampleOf(hit: DartHitEntity): ImpactSample? {
            val target = IntentTarget.fromStored(hit.intentNumber, hit.intentMultiplier)
                ?: return null
            val frame = frameOf(hit, target) ?: return null
            val dart = BoardGeometry.dartAt(hit.xMm.toDouble(), hit.yMm.toDouble())
            return ImpactSample(
                sessionId = hit.sessionId,
                hitAt = hit.hitAt,
                frame = frame,
                hit = target.isHit(dart),
                spanMm = hit.windowSpanMm.toDouble()
            )
        }

        /** 一镖 → 趋势样本（`frame == null` 表示出框）。 */
        fun sampleOf(hit: DartHitEntity, target: IntentTarget): ImpactSample {
            val frame = frameOf(hit, target)
            val dart = BoardGeometry.dartAt(hit.xMm.toDouble(), hit.yMm.toDouble())
            return ImpactSample(
                sessionId = hit.sessionId,
                hitAt = hit.hitAt,
                frame = frame,
                hit = frame != null && target.isHit(dart),
                spanMm = hit.windowSpanMm.toDouble()
            )
        }

        /**
         * cEV 的输入：**全部**落库点（含出框折算点）。
         *
         * 这里刻意**不**过滤 `outBand`：miss 是真实得分，算「换瞄点能涨多少分」时必须算进去；
         * 需要「不含 miss」的调用方（σ / R95 / KDE）请走 [frameOf]。
         */
        fun cevSamplesOf(hits: List<DartHitEntity>): List<Point2> =
            hits.map { Point2(it.xMm.toDouble(), it.yMm.toDouble()) }

        /** 一轮训练 → 判定用的实测值（`n` = **窗内**镖数；比率分母 = 全部录入）。 */
        fun metricsOf(hits: List<DartHitEntity>, target: IntentTarget): SessionMetrics {
            val frames = ArrayList<ImpactFrame>(hits.size)
            var hitCount = 0
            hits.forEach { hit ->
                val frame = frameOf(hit, target) ?: return@forEach
                frames.add(frame)
                if (target.isHit(BoardGeometry.dartAt(hit.xMm.toDouble(), hit.yMm.toDouble()))) {
                    hitCount++
                }
            }
            val stats = ImpactCalculator.of(frames)
            val total = hits.size
            return SessionMetrics(
                sessionId = hits.firstOrNull()?.sessionId.orEmpty(),
                hitAt = hits.maxOfOrNull { it.hitAt } ?: 0L,
                n = frames.size,
                bias = stats?.bias ?: 0.0,
                r95 = stats?.r95 ?: 0.0,
                rmse = stats?.rmse ?: 0.0,
                hitRate = if (total == 0) 0.0 else hitCount.toDouble() / total,
                outRate = if (total == 0) 0.0 else (total - frames.size).toDouble() / total
            )
        }

        /**
         * 一轮训练 → 三镖回合样本（§4.3）。
         *
         * 回合序号**由 `dartIndexInRound == 1` 触发递增**，而不是 `index / 3`：
         * 后者在「撤销过 / 中途退出」时会造出一个从第 2 镖开始的假回合，
         * 而前者会把这种残回合原样留给 [com.dartvio.app.domain.impact.ImpactRounds] 剔除。
         */
        fun roundDartsOf(hits: List<DartHitEntity>, target: IntentTarget): List<RoundDart> {
            var ordinal = 0
            val result = ArrayList<RoundDart>(hits.size)
            hits.forEach { hit ->
                val stored = hit.dartIndexInRound
                if (ordinal == 0 || stored == 1) ordinal++
                val frame = frameOf(hit, target)
                result.add(
                    RoundDart(
                        sessionId = hit.sessionId,
                        hitAt = hit.hitAt,
                        roundOrdinal = ordinal,
                        dartInRound = if (stored in 1..3) stored else 0,
                        frame = frame,
                        hit = frame != null &&
                            target.isHit(BoardGeometry.dartAt(hit.xMm.toDouble(), hit.yMm.toDouble()))
                    )
                )
            }
            return result
        }

        /** 把「按时间倒序读回的一批镖」切成一轮一轮（轮内保持写入顺序，轮间按时间升序）。 */
        fun sessionsOf(hits: List<DartHitEntity>): List<List<DartHitEntity>> {
            val buckets = LinkedHashMap<String, MutableList<DartHitEntity>>()
            hits.forEach { hit ->
                if (hit.sessionId.isBlank()) return@forEach
                buckets.getOrPut(hit.sessionId) { mutableListOf() }.add(hit)
            }
            return buckets.values
                .map { list -> list.sortedBy { it.hitAt } }
                .sortedBy { list -> list.firstOrNull()?.hitAt ?: 0L }
        }

        /**
         * 多轮 → 干预对照表行（§4.8 ⑥）。`achieved` 由**该轮自己的**处方现算，不读库里的判定结果。
         */
        fun summariesOf(
            sessions: List<List<DartHitEntity>>,
            target: IntentTarget
        ): List<SessionSummary> {
            val metrics = sessions.map { metricsOf(it, target) }
            return metrics.mapIndexed { index, m ->
                val head = sessions[index].firstOrNull()
                val metric = PrescriptionMetric.fromKey(head?.prescriptionMetric.orEmpty())
                val prescription = metric?.let {
                    Prescription(it, head?.prescriptionTarget ?: 0.0)
                }
                val verdict = ImpactPrescription.judge(
                    m = m,
                    p = prescription,
                    prevR95 = metrics.getOrNull(index - 1)?.r95
                )
                SessionSummary(
                    sessionId = m.sessionId,
                    hitAt = m.hitAt,
                    note = head?.interventionNote.orEmpty(),
                    n = m.n,
                    bias = m.bias,
                    r95 = m.r95,
                    hitRate = m.hitRate,
                    achieved = when (verdict.verdict) {
                        Verdict.ACHIEVED -> true
                        Verdict.MISSED -> false
                        else -> null
                    }
                )
            }
        }

        /**
         * 组装一镖（点在预览上的那一下）。
         *
         * [outBand] / [outLevel] 为 `0` 时是窗内点；否则坐标是
         * [com.dartvio.app.domain.impact.ImpactMissBand.missPointMm] 折算出来的 clamp + 外推点。
         */
        fun newHit(
            sessionId: String,
            profileId: String,
            target: IntentTarget,
            xMm: Double,
            yMm: Double,
            dartIndexInRound: Int,
            windowSpanMm: Double,
            outBand: Int = 0,
            outLevel: Int = 0,
            hitAt: Long = System.currentTimeMillis(),
            prescriptionMetric: PrescriptionMetric? = null,
            prescriptionTarget: Double = 0.0,
            interventionNote: String = "",
            pressureMode: Int = 0
        ): DartHitEntity {
            val dart = BoardGeometry.dartAt(xMm, yMm)
            val (intentNumber, intentMultiplier) = target.toStored()
            return DartHitEntity(
                matchId = "",
                profileId = profileId,
                legNumber = 0,
                xMm = xMm.toFloat(),
                yMm = yMm.toFloat(),
                number = dart.number,
                multiplier = dart.multiplier,
                source = DartSource.IMPACT_DRILL.name,
                hitAt = hitAt,
                sessionId = sessionId,
                intentNumber = intentNumber,
                intentMultiplier = intentMultiplier,
                dartIndexInRound = dartIndexInRound,
                windowSpanMm = windowSpanMm.toFloat(),
                outBand = outBand,
                outLevel = outLevel,
                prescriptionMetric = prescriptionMetric?.name.orEmpty(),
                prescriptionTarget = prescriptionTarget,
                interventionNote = interventionNote,
                pressureMode = pressureMode
            )
        }
    }
}
