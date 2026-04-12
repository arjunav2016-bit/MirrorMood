package com.mirrormood.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceAnalyzerTest {

    @Test
    fun `no consensus when array is smaller than threshold`() {
        val moods = listOf(
            MoodResult("Happy", 1f),
            MoodResult("Happy", 1f)
        )
        assertNull(FaceAnalyzer.getConsensusMood(moods))
    }

    @Test
    fun `returns consensus when threshold is met`() {
        val moods = listOf(
            MoodResult("Happy", 1f),
            MoodResult("Neutral", 0.5f),
            MoodResult("Happy", 0.9f),
            MoodResult("Happy", 0.8f)
        )
        assertEquals("Happy", FaceAnalyzer.getConsensusMood(moods))
    }

    @Test
    fun `returns best consensus when multiple moods exist`() {
        val moods = listOf(
            MoodResult("Neutral", 0.5f),
            MoodResult("Stressed", 0.5f),
            MoodResult("Stressed", 0.5f),
            MoodResult("Stressed", 0.5f),
            MoodResult("Happy", 1f)
        )
        assertEquals("Stressed", FaceAnalyzer.getConsensusMood(moods))
    }
}
