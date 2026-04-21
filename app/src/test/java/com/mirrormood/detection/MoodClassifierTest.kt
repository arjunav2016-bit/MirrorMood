package com.mirrormood.detection

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for MoodClassifier covering all mood categories,
 * boundary conditions, head rotation effects, and confidence scores.
 */
class MoodClassifierTest {

    // ── Happy ───────────────────────────────────────────────────

    @Test
    fun `classify returns Happy for high smile probability`() {
        val result = MoodClassifier.classify(
            smileProb = 0.85f,
            leftEyeOpen = 0.8f,
            rightEyeOpen = 0.8f
        )
        assertEquals("Happy", result.mood)
        assertTrue("Confidence should be high", result.confidence >= 0.8f)
    }

    @Test
    fun `classify returns Happy for moderate-high smile`() {
        val result = MoodClassifier.classify(
            smileProb = 0.75f,
            leftEyeOpen = 0.6f,
            rightEyeOpen = 0.6f
        )
        assertEquals("Happy", result.mood)
    }

    // ── Tired ───────────────────────────────────────────────────

    @Test
    fun `classify returns Tired for low smile and low eye openness`() {
        val result = MoodClassifier.classify(
            smileProb = 0.1f,
            leftEyeOpen = 0.2f,
            rightEyeOpen = 0.2f
        )
        assertEquals("Tired", result.mood)
        assertTrue("Confidence should be high", result.confidence >= 0.8f)
    }

    @Test
    fun `classify returns Tired with head droop boost`() {
        val result = MoodClassifier.classify(
            smileProb = 0.15f,
            leftEyeOpen = 0.25f,
            rightEyeOpen = 0.25f,
            headEulerAngleX = -15f // head drooping
        )
        assertEquals("Tired", result.mood)
        assertTrue("Head droop should increase confidence", result.confidence >= 0.7f)
    }

    // ── Stressed ────────────────────────────────────────────────

    @Test
    fun `classify returns Stressed for low smile and wide eyes`() {
        val result = MoodClassifier.classify(
            smileProb = 0.1f,
            leftEyeOpen = 0.9f,
            rightEyeOpen = 0.9f
        )
        assertEquals("Stressed", result.mood)
        assertTrue("Confidence should be high", result.confidence >= 0.8f)
    }

    @Test
    fun `classify returns Stressed for moderate stress signals`() {
        val result = MoodClassifier.classify(
            smileProb = 0.15f,
            leftEyeOpen = 0.88f,
            rightEyeOpen = 0.88f
        )
        assertEquals("Stressed", result.mood)
    }

    // ── Focused ─────────────────────────────────────────────────

    @Test
    fun `classify returns Focused for low smile and moderate eyes with steady head`() {
        val result = MoodClassifier.classify(
            smileProb = 0.2f,
            leftEyeOpen = 0.65f,
            rightEyeOpen = 0.65f,
            headEulerAngleY = 2f, // looking straight
            headEulerAngleZ = 10f // slight head tilt
        )
        assertEquals("Focused", result.mood)
    }

    @Test
    fun `classify Focused gets boost from head tilt`() {
        val withoutTilt = MoodClassifier.classify(
            smileProb = 0.2f,
            leftEyeOpen = 0.65f,
            rightEyeOpen = 0.65f,
            headEulerAngleZ = 0f
        )
        val withTilt = MoodClassifier.classify(
            smileProb = 0.2f,
            leftEyeOpen = 0.65f,
            rightEyeOpen = 0.65f,
            headEulerAngleZ = 10f
        )
        assertTrue(
            "Head tilt should boost focus confidence",
            withTilt.confidence >= withoutTilt.confidence
        )
    }

    // ── Bored ───────────────────────────────────────────────────

    @Test
    fun `classify returns Bored for mid smile and mid eyes`() {
        val result = MoodClassifier.classify(
            smileProb = 0.35f,
            leftEyeOpen = 0.45f,
            rightEyeOpen = 0.45f
        )
        assertEquals("Bored", result.mood)
    }

    @Test
    fun `classify Bored gets boost from looking away`() {
        val lookingStraight = MoodClassifier.classify(
            smileProb = 0.35f,
            leftEyeOpen = 0.45f,
            rightEyeOpen = 0.45f,
            headEulerAngleY = 2f
        )
        val lookingAway = MoodClassifier.classify(
            smileProb = 0.35f,
            leftEyeOpen = 0.45f,
            rightEyeOpen = 0.45f,
            headEulerAngleY = 25f
        )
        assertTrue(
            "Looking away should boost bored confidence",
            lookingAway.confidence >= lookingStraight.confidence
        )
    }

    // ── Neutral ─────────────────────────────────────────────────

    @Test
    fun `classify returns Neutral for ambiguous signals`() {
        // Values outside all specific mood ranges
        val result = MoodClassifier.classify(
            smileProb = 0.55f,
            leftEyeOpen = 0.5f,
            rightEyeOpen = 0.5f
        )
        assertEquals("Neutral", result.mood)
    }

    // ── Confidence ──────────────────────────────────────────────

    @Test
    fun `confidence is always between 0 and 1`() {
        val cases = listOf(
            Triple(0.0f, 0.0f, 0.0f),
            Triple(1.0f, 1.0f, 1.0f),
            Triple(0.5f, 0.5f, 0.5f),
            Triple(0.1f, 0.9f, 0.9f),
            Triple(0.9f, 0.1f, 0.1f)
        )
        for ((smile, leftEye, rightEye) in cases) {
            val result = MoodClassifier.classify(smile, leftEye, rightEye)
            assertTrue(
                "Confidence should be >= 0 for ($smile, $leftEye, $rightEye)",
                result.confidence >= 0f
            )
            assertTrue(
                "Confidence should be <= 1 for ($smile, $leftEye, $rightEye)",
                result.confidence <= 1f
            )
        }
    }

    @Test
    fun `classify always returns a non-empty mood string`() {
        val result = MoodClassifier.classify(0.5f, 0.5f, 0.5f)
        assertTrue("Mood should not be empty", result.mood.isNotEmpty())
    }

    // ── Backward compatibility ──────────────────────────────────

    @Test
    fun `classify works without head rotation parameters`() {
        // Should not crash when head angles are omitted (defaults to 0)
        val result = MoodClassifier.classify(0.9f, 0.8f, 0.8f)
        assertEquals("Happy", result.mood)
    }

    // ── Winking boost ───────────────────────────────────────────

    @Test
    fun `winking boosts Happy confidence`() {
        val withoutWink = MoodClassifier.classify(
            smileProb = 0.65f,
            leftEyeOpen = 0.7f,
            rightEyeOpen = 0.7f,
            isWinking = false
        )
        val withWink = MoodClassifier.classify(
            smileProb = 0.65f,
            leftEyeOpen = 0.7f,
            rightEyeOpen = 0.7f,
            isWinking = true
        )
        assertEquals("Happy", withWink.mood)
        assertTrue(
            "Winking should boost Happy confidence",
            withWink.confidence > withoutWink.confidence
        )
    }

    // ── Rapid blinking boost ────────────────────────────────────

    @Test
    fun `rapid blinking boosts Stressed when eyes are wide`() {
        val withoutBlink = MoodClassifier.classify(
            smileProb = 0.1f,
            leftEyeOpen = 0.85f,
            rightEyeOpen = 0.85f,
            isRapidBlinking = false
        )
        val withBlink = MoodClassifier.classify(
            smileProb = 0.1f,
            leftEyeOpen = 0.85f,
            rightEyeOpen = 0.85f,
            isRapidBlinking = true
        )
        assertEquals("Stressed", withBlink.mood)
        assertTrue(
            "Rapid blinking should boost Stressed confidence",
            withBlink.confidence > withoutBlink.confidence
        )
    }

    @Test
    fun `rapid blinking boosts Tired when eyes are low`() {
        val withoutBlink = MoodClassifier.classify(
            smileProb = 0.15f,
            leftEyeOpen = 0.3f,
            rightEyeOpen = 0.3f,
            isRapidBlinking = false
        )
        val withBlink = MoodClassifier.classify(
            smileProb = 0.15f,
            leftEyeOpen = 0.3f,
            rightEyeOpen = 0.3f,
            isRapidBlinking = true
        )
        assertEquals("Tired", withBlink.mood)
        assertTrue(
            "Rapid blinking with low eyes should boost Tired confidence",
            withBlink.confidence > withoutBlink.confidence
        )
    }

    // ── Head position boosts ────────────────────────────────────

    @Test
    fun `steady head position boosts Focused confidence`() {
        val unsteady = MoodClassifier.classify(
            smileProb = 0.2f,
            leftEyeOpen = 0.65f,
            rightEyeOpen = 0.65f,
            headEulerAngleX = 12f,
            headEulerAngleY = 15f
        )
        val steady = MoodClassifier.classify(
            smileProb = 0.2f,
            leftEyeOpen = 0.65f,
            rightEyeOpen = 0.65f,
            headEulerAngleX = 2f,
            headEulerAngleY = 2f
        )
        assertTrue(
            "Steady head should produce higher focus confidence",
            steady.confidence >= unsteady.confidence
        )
    }

    @Test
    fun `head droop combined with rapid blink maximizes Tired`() {
        val result = MoodClassifier.classify(
            smileProb = 0.1f,
            leftEyeOpen = 0.2f,
            rightEyeOpen = 0.2f,
            headEulerAngleX = -15f,
            isRapidBlinking = true
        )
        assertEquals("Tired", result.mood)
        assertTrue("All tiredness signals should yield very high confidence", result.confidence >= 0.9f)
    }

    // ── Combined signals ────────────────────────────────────────

    @Test
    fun `looking away with mid-range signals favors Bored over Focused`() {
        val result = MoodClassifier.classify(
            smileProb = 0.35f,
            leftEyeOpen = 0.45f,
            rightEyeOpen = 0.45f,
            headEulerAngleY = 20f
        )
        assertEquals("Bored", result.mood)
    }

    @Test
    fun `high smile overrides rapid blinking for Happy`() {
        val result = MoodClassifier.classify(
            smileProb = 0.9f,
            leftEyeOpen = 0.8f,
            rightEyeOpen = 0.8f,
            isRapidBlinking = true
        )
        assertEquals("Happy", result.mood)
    }

    @Test
    fun `combineResults returns heuristic when model is unavailable`() {
        val heuristic = MoodResult("Focused", 0.72f)

        val result = MoodClassifier.combineResults(
            heuristic = heuristic,
            model = null
        )

        assertEquals(heuristic, result)
    }

    @Test
    fun `combineResults returns model when heuristic is unavailable`() {
        val model = MoodResult("Happy", 0.81f)

        val result = MoodClassifier.combineResults(
            heuristic = null,
            model = model
        )

        assertEquals(model, result)
    }

    @Test
    fun `combineResults keeps heuristic winner with default weighting`() {
        val result = MoodClassifier.combineResults(
            heuristic = MoodResult("Stressed", 0.9f),
            model = MoodResult("Happy", 0.6f)
        )

        assertEquals("Stressed", result?.mood)
        assertEquals(0.54f, result?.confidence ?: 0f, 0.0001f)
    }

    @Test
    fun `combineResults can favor model when configured heavier`() {
        val result = MoodClassifier.combineResults(
            heuristic = MoodResult("Neutral", 0.6f),
            model = MoodResult("Happy", 0.8f),
            config = MoodClassifier.EnsembleConfig(
                heuristicWeight = 0.2f,
                modelWeight = 0.8f
            )
        )

        assertEquals("Happy", result?.mood)
        assertTrue(result != null)
        assertTrue((result?.confidence ?: 0f) > 0.6f)
    }
}
