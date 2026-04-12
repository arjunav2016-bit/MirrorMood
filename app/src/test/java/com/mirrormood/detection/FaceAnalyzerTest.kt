package com.mirrormood.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for FaceAnalyzer's consensus logic.
 *
 * The consensus algorithm requires >= CONSENSUS_THRESHOLD (3) occurrences
 * of the same mood in the sliding window before saving a new mood entry.
 */
class FaceAnalyzerTest {

    // ── Basic threshold tests ───────────────────────────────────

    @Test
    fun `no consensus when list is empty`() {
        assertNull(FaceAnalyzer.getConsensusMood(emptyList()))
    }

    @Test
    fun `no consensus with single element`() {
        val moods = listOf(MoodResult("Happy", 1f))
        assertNull(FaceAnalyzer.getConsensusMood(moods))
    }

    @Test
    fun `no consensus when array is smaller than threshold`() {
        val moods = listOf(
            MoodResult("Happy", 1f),
            MoodResult("Happy", 1f)
        )
        assertNull(FaceAnalyzer.getConsensusMood(moods))
    }

    @Test
    fun `returns consensus when threshold is met exactly`() {
        val moods = listOf(
            MoodResult("Happy", 1f),
            MoodResult("Happy", 1f),
            MoodResult("Happy", 1f)
        )
        assertEquals("Happy", FaceAnalyzer.getConsensusMood(moods))
    }

    @Test
    fun `returns consensus when threshold is exceeded`() {
        val moods = listOf(
            MoodResult("Happy", 1f),
            MoodResult("Neutral", 0.5f),
            MoodResult("Happy", 0.9f),
            MoodResult("Happy", 0.8f)
        )
        assertEquals("Happy", FaceAnalyzer.getConsensusMood(moods))
    }

    // ── Multi-mood tests ────────────────────────────────────────

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

    @Test
    fun `no consensus when moods are evenly split below threshold`() {
        val moods = listOf(
            MoodResult("Happy", 0.9f),
            MoodResult("Stressed", 0.8f),
            MoodResult("Happy", 0.9f),
            MoodResult("Stressed", 0.8f),
            MoodResult("Tired", 0.7f)
        )
        // Happy=2, Stressed=2, Tired=1 — none reaches 3
        assertNull(FaceAnalyzer.getConsensusMood(moods))
    }

    @Test
    fun `all same mood in full window returns consensus`() {
        val moods = listOf(
            MoodResult("Focused", 0.8f),
            MoodResult("Focused", 0.9f),
            MoodResult("Focused", 0.7f),
            MoodResult("Focused", 0.85f),
            MoodResult("Focused", 0.8f)
        )
        assertEquals("Focused", FaceAnalyzer.getConsensusMood(moods))
    }

    @Test
    fun `consensus works with different confidence values`() {
        val moods = listOf(
            MoodResult("Tired", 0.1f),
            MoodResult("Tired", 0.5f),
            MoodResult("Tired", 0.99f)
        )
        // Confidence doesn't affect consensus — only mood label counts
        assertEquals("Tired", FaceAnalyzer.getConsensusMood(moods))
    }

    @Test
    fun `consensus favors count over position in list`() {
        val moods = listOf(
            MoodResult("Bored", 0.7f),  // first in list but minority
            MoodResult("Happy", 0.9f),
            MoodResult("Happy", 0.9f),
            MoodResult("Happy", 0.9f)
        )
        assertEquals("Happy", FaceAnalyzer.getConsensusMood(moods))
    }
}
