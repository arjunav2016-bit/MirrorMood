package com.mirrormood.detection

/**
 * Represents a user's neutral-state facial baseline, captured during calibration.
 * Used to normalize runtime readings, improving classification accuracy for
 * individuals with naturally high/low resting metrics.
 */
data class FaceBaseline(
    val smileProb: Float,
    val leftEyeOpen: Float,
    val rightEyeOpen: Float,
    val headPitch: Float,
    val headYaw: Float,
    val headRoll: Float
)
