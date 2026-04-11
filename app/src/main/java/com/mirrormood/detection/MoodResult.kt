package com.mirrormood.detection

/**
 * Represents a mood classification result with a confidence score.
 *
 * @param mood The classified mood label (e.g., "Happy", "Stressed", "Tired", etc.)
 * @param confidence A value between 0.0 and 1.0 indicating how certain the classifier
 *                   is about this mood. Higher values mean the input signals strongly
 *                   matched the mood's criteria.
 */
data class MoodResult(
    val mood: String,
    val confidence: Float
)
