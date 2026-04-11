package com.mirrormood.detection

/**
 * Classifies facial expression data into mood categories.
 *
 * Uses smile probability, eye openness, and head rotation angles from ML Kit
 * to determine the most likely mood. Returns a [MoodResult] with the mood
 * label and a confidence score.
 *
 * Head rotation angles improve accuracy:
 * - headEulerAngleX: pitch (nodding up/down) — low values suggest tiredness
 * - headEulerAngleY: yaw (turning left/right) — high values suggest distraction
 * - headEulerAngleZ: roll (head tilt) — slight tilt can indicate focus/curiosity
 */
object MoodClassifier {

    /**
     * Enhanced classification using smile, eye openness, head rotation, and blink states.
     */
    fun classify(
        smileProb: Float,
        leftEyeOpen: Float,
        rightEyeOpen: Float,
        headEulerAngleX: Float = 0f,
        headEulerAngleY: Float = 0f,
        headEulerAngleZ: Float = 0f,
        isWinking: Boolean = false,
        isRapidBlinking: Boolean = false
    ): MoodResult {
        val eyeOpenAvg = (leftEyeOpen + rightEyeOpen) / 2f
        val absYaw = kotlin.math.abs(headEulerAngleY)
        val absPitch = kotlin.math.abs(headEulerAngleX)

        // Score each mood independently, then pick the highest
        val scores = mutableMapOf<String, Float>()

        // Happy: high smile is the strongest signal
        val happyBase = when {
            smileProb > 0.8f -> 0.95f
            smileProb > 0.7f -> 0.80f
            smileProb > 0.6f -> 0.55f
            else -> 0.1f
        }
        val winkingBoost = if (isWinking) 0.15f else 0f
        scores["Happy"] = (happyBase + winkingBoost).coerceAtMost(1f)

        // Tired: low smile + low eye openness + head drooping (pitch down)
        val tiredBase = when {
            smileProb < 0.2f && eyeOpenAvg < 0.3f -> 0.90f
            smileProb < 0.3f && eyeOpenAvg < 0.4f -> 0.70f
            smileProb < 0.4f && eyeOpenAvg < 0.3f -> 0.60f
            else -> 0.1f
        }
        // Head drooping boosts tired score
        val tiredHeadBoost = if (headEulerAngleX < -10f) 0.10f else 0f
        val tiredBlinkBoost = if (isRapidBlinking && eyeOpenAvg < 0.5f) 0.15f else 0f
        scores["Tired"] = (tiredBase + tiredHeadBoost + tiredBlinkBoost).coerceAtMost(1f)

        // Stressed: low smile + wide eyes (high eye openness) + tense posture OR rapid blinking
        val stressedBase = when {
            smileProb < 0.2f && eyeOpenAvg > 0.8f -> 0.90f
            smileProb < 0.3f && eyeOpenAvg > 0.7f -> 0.75f
            smileProb < 0.3f && eyeOpenAvg > 0.6f -> 0.55f
            else -> 0.1f
        }
        val stressedBlinkBoost = if (isRapidBlinking && smileProb < 0.3f) 0.20f else 0f
        scores["Stressed"] = (stressedBase + stressedBlinkBoost).coerceAtMost(1f)

        // Focused: low smile + moderate eye openness + steady head position + slight tilt
        val focusedBase = when {
            smileProb < 0.4f && eyeOpenAvg in 0.5f..0.85f && absYaw < 10f -> 0.80f
            smileProb < 0.4f && eyeOpenAvg in 0.5f..0.8f -> 0.60f
            else -> 0.1f
        }
        // Slight head tilt (curiosity) and steady position (low yaw) boost focus
        val focusedTiltBoost = if (kotlin.math.abs(headEulerAngleZ) in 5f..20f) 0.10f else 0f
        val focusedSteadyBoost = if (absYaw < 5f && absPitch < 5f) 0.05f else 0f
        scores["Focused"] = (focusedBase + focusedTiltBoost + focusedSteadyBoost).coerceAtMost(1f)

        // Bored: mid smile + mid eye openness + looking away (high yaw)
        val boredBase = when {
            smileProb in 0.2f..0.5f && eyeOpenAvg in 0.3f..0.6f -> 0.70f
            smileProb in 0.3f..0.5f && eyeOpenAvg in 0.3f..0.7f -> 0.55f
            else -> 0.1f
        }
        // Looking away suggests boredom
        val boredLookAwayBoost = if (absYaw > 15f) 0.15f else 0f
        scores["Bored"] = (boredBase + boredLookAwayBoost).coerceAtMost(1f)

        // Neutral: default fallback with moderate confidence
        scores["Neutral"] = 0.30f

        // Pick the mood with the highest score
        val best = scores.maxByOrNull { it.value }!!
        return MoodResult(mood = best.key, confidence = best.value)
    }
}