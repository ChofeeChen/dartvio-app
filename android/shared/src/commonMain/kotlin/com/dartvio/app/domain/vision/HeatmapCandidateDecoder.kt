package com.dartvio.app.domain.vision

/**
 * Model output decoder shared by the future ONNX adapter and the confirmation UI.
 *
 * It deliberately produces a proposal only. A proposal must be shown to the
 * player and confirmed before any match state changes.
 */
data class VisionTipProposal(
    val xCells: Float,
    val yCells: Float,
    val confidence: Float,
)

sealed interface HeatmapDecodeResult {
    data class Candidate(val proposal: VisionTipProposal) : HeatmapDecodeResult
    data class RejectedLowConfidence(val confidence: Float) : HeatmapDecodeResult
}

object HeatmapCandidateDecoder {

    const val DEFAULT_WIDTH = 32
    const val DEFAULT_HEIGHT = 32
    const val DEFAULT_THRESHOLD = 0.35f

    /**
     * Decodes a row-major, single-channel heatmap with the same 3×3 positive
     * weighted centroid used by DartVision/tools/heatmap_decode.py.
     */
    fun decode(
        values: FloatArray,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        threshold: Float = DEFAULT_THRESHOLD,
    ): HeatmapDecodeResult {
        require(width > 0 && height > 0) { "Heatmap dimensions must be positive" }
        require(values.size == width * height) { "Heatmap data size does not match dimensions" }
        require(values.all { it.isFinite() }) { "Heatmap values must be finite" }

        val peakIndex = values.indices.maxBy { values[it] }
        val confidence = values[peakIndex]
        if (confidence < threshold) return HeatmapDecodeResult.RejectedLowConfidence(confidence)

        val peakX = peakIndex % width
        val peakY = peakIndex / width
        var totalWeight = 0f
        var weightedX = 0f
        var weightedY = 0f
        for (y in maxOf(0, peakY - 1)..minOf(height - 1, peakY + 1)) {
            for (x in maxOf(0, peakX - 1)..minOf(width - 1, peakX + 1)) {
                val weight = maxOf(values[y * width + x], 0f)
                totalWeight += weight
                weightedX += x * weight
                weightedY += y * weight
            }
        }
        check(totalWeight > 0f) { "Accepted heatmap peak must have positive local weight" }
        return HeatmapDecodeResult.Candidate(
            VisionTipProposal(
                xCells = weightedX / totalWeight,
                yCells = weightedY / totalWeight,
                confidence = confidence,
            ),
        )
    }
}
