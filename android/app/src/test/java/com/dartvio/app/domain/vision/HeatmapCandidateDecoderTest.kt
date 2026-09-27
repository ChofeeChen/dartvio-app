package com.dartvio.app.domain.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatmapCandidateDecoderTest {

    @Test
    fun `low peak is returned for confirmation UI to reject`() {
        val result = HeatmapCandidateDecoder.decode(floatArrayOf(0f, 0.2f, 0.1f, 0.3f), width = 2, height = 2)
        assertTrue(result is HeatmapDecodeResult.RejectedLowConfidence)
        assertEquals(0.3f, (result as HeatmapDecodeResult.RejectedLowConfidence).confidence, 0f)
    }

    @Test
    fun `single peak retains its cell location`() {
        val result = HeatmapCandidateDecoder.decode(floatArrayOf(0f, 0f, 0f, 1f), width = 2, height = 2)
        val proposal = (result as HeatmapDecodeResult.Candidate).proposal
        assertEquals(1f, proposal.xCells, 0f)
        assertEquals(1f, proposal.yCells, 0f)
        assertEquals(1f, proposal.confidence, 0f)
    }

    @Test
    fun `positive neighborhood returns weighted subcell center`() {
        val result = HeatmapCandidateDecoder.decode(
            floatArrayOf(0f, 0.2f, 0f, 0.8f),
            width = 2,
            height = 2,
        )
        val proposal = (result as HeatmapDecodeResult.Candidate).proposal
        assertEquals(1f, proposal.xCells, 0.0001f)
        assertEquals(0.8f, proposal.yCells, 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid shape is rejected`() {
        HeatmapCandidateDecoder.decode(floatArrayOf(0f), width = 2, height = 2)
    }
}
