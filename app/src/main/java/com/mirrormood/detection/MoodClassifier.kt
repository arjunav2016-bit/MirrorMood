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

    data class EnsembleConfig(
        val heuristicWeight: Float = 0.6f,
        val modelWeight: Float = 0.4f
    )

    /**
     * Primary entry point. Delegates to the heuristic classifier.
     * When TFLiteMoodClassifier is available, FaceAnalyzer uses an ensemble
     * of both classifiers. This method remains the heuristic-only path.
     */
    fun classify(
        smileProb: Float,
        leftEyeOpen: Float,
        rightEyeOpen: Float,
        headEulerAngleX: Float = 0f,
        headEulerAngleY: Float = 0f,
        headEulerAngleZ: Float = 0f,
        isWinking: Boolean = false,
        isRapidBlinking: Boolean = false,
        baseline: FaceBaseline? = null
    ): MoodResult = classifyHeuristic(
        smileProb, leftEyeOpen, rightEyeOpen,
        headEulerAngleX, headEulerAngleY, headEulerAngleZ,
        isWinking, isRapidBlinking, baseline
    )

    /**
     * Heuristic classification using smile, eye openness, head rotation, and blink states.
     * Kept as a named method so FaceAnalyzer can blend it with TFLite output.
     */
    fun classifyHeuristic(
        smileProb: Float,
        leftEyeOpen: Float,
        rightEyeOpen: Float,
        headEulerAngleX: Float = 0f,
        headEulerAngleY: Float = 0f,
        headEulerAngleZ: Float = 0f,
        isWinking: Boolean = false,
        isRapidBlinking: Boolean = false,
        baseline: FaceBaseline? = null
    ): MoodResult {
        
        val normSmile = if (baseline != null) (smileProb - baseline.smileProb + 0.3f).coerceIn(0f, 1f) else smileProb
        val normLeft = if (baseline != null) (leftEyeOpen - baseline.leftEyeOpen + 0.5f).coerceIn(0f, 1f) else leftEyeOpen
        val normRight = if (baseline != null) (rightEyeOpen - baseline.rightEyeOpen + 0.5f).coerceIn(0f, 1f) else rightEyeOpen
        
        val eyeOpenAvg = (normLeft + normRight) / 2f
        val absYaw = kotlin.math.abs(headEulerAngleY)
        val absPitch = kotlin.math.abs(headEulerAngleX)

        val scores = mutableMapOf<String, Float>()

        val happyBase = when {
            normSmile > 0.8f -> 0.95f
            normSmile > 0.7f -> 0.80f
            normSmile > 0.6f -> 0.55f
            else -> 0.1f
        }
        val winkingBoost = if (isWinking) 0.15f else 0f
        scores["Happy"] = (happyBase + winkingBoost).coerceAtMost(1f)

        val tiredBase = when {
            normSmile < 0.2f && eyeOpenAvg < 0.3f -> 0.90f
            normSmile < 0.3f && eyeOpenAvg < 0.4f -> 0.70f
            normSmile < 0.4f && eyeOpenAvg < 0.3f -> 0.60f
            else -> 0.1f
        }
        val tiredHeadBoost = if (headEulerAngleX < -10f) 0.10f else 0f
        val tiredBlinkBoost = if (isRapidBlinking && eyeOpenAvg < 0.5f) 0.15f else 0f
        scores["Tired"] = (tiredBase + tiredHeadBoost + tiredBlinkBoost).coerceAtMost(1f)

        val stressedBase = when {
            normSmile < 0.2f && eyeOpenAvg > 0.8f -> 0.90f
            normSmile < 0.3f && eyeOpenAvg > 0.7f -> 0.75f
            normSmile < 0.3f && eyeOpenAvg > 0.6f -> 0.55f
            else -> 0.1f
        }
        val stressedBlinkBoost = if (isRapidBlinking && normSmile < 0.3f) 0.20f else 0f
        scores["Stressed"] = (stressedBase + stressedBlinkBoost).coerceAtMost(1f)

        val focusedBase = when {
            normSmile < 0.4f && eyeOpenAvg in 0.5f..0.85f && absYaw < 10f -> 0.80f
            normSmile < 0.4f && eyeOpenAvg in 0.5f..0.8f -> 0.60f
            else -> 0.1f
        }
        val focusedTiltBoost = if (kotlin.math.abs(headEulerAngleZ) in 5f..20f) 0.10f else 0f
        val focusedSteadyBoost = if (absYaw < 5f && absPitch < 5f) 0.05f else 0f
        scores["Focused"] = (focusedBase + focusedTiltBoost + focusedSteadyBoost).coerceAtMost(1f)

        val boredBase = when {
            normSmile in 0.2f..0.5f && eyeOpenAvg in 0.3f..0.6f -> 0.70f
            normSmile in 0.3f..0.5f && eyeOpenAvg in 0.3f..0.7f -> 0.55f
            else -> 0.1f
        }
        val boredLookAwayBoost = if (absYaw > 15f) 0.15f else 0f
        scores["Bored"] = (boredBase + boredLookAwayBoost).coerceAtMost(1f)

        // Neutral: default fallback with moderate confidence
        scores["Neutral"] = 0.30f

        // Pick the mood with the highest score
        val best = scores.maxByOrNull { it.value }!!
        return MoodResult(mood = best.key, confidence = best.value)
    }

    /**
     * Blend heuristic and model outputs when both are available.
     * Falls back to the non-null result if either side is missing.
     */
    fun combineResults(
        heuristic: MoodResult?,
        model: MoodResult?,
        config: EnsembleConfig = EnsembleConfig()
    ): MoodResult? {
        if (heuristic == null) return model
        if (model == null) return heuristic

        val normalizedWeights = normalizeWeights(config)
        val scores = linkedMapOf<String, Float>()
        scores[heuristic.mood] = heuristic.confidence * normalizedWeights.first
        scores[model.mood] = (scores[model.mood] ?: 0f) + model.confidence * normalizedWeights.second

        val best = scores.maxByOrNull { it.value } ?: return heuristic
        return MoodResult(
            mood = best.key,
            confidence = best.value.coerceIn(0f, 1f)
        )
    }

    private fun normalizeWeights(config: EnsembleConfig): Pair<Float, Float> {
        val heuristicWeight = config.heuristicWeight.coerceAtLeast(0f)
        val modelWeight = config.modelWeight.coerceAtLeast(0f)
        val total = heuristicWeight + modelWeight
        return if (total <= 0f) {
            0.5f to 0.5f
        } else {
            heuristicWeight / total to modelWeight / total
        }
    }
}
