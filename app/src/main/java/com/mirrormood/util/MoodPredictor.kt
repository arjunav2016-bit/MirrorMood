package com.mirrormood.util

import com.mirrormood.data.db.MoodEntry
import java.util.Calendar

/**
 * Lightweight on-device mood prediction engine.
 *
 * Uses time-of-day and day-of-week historical patterns to predict the most
 * likely next mood. No network calls, no ML model — pure statistical analysis
 * over the user's own journal data.
 *
 * Algorithm:
 * 1. Bucket all entries by (dayOfWeek, timeSlot)
 * 2. Find the bucket matching "now"
 * 3. Return the most common mood in that bucket
 * 4. Confidence = (dominant count / total in bucket) weighted by sample size
 */
object MoodPredictor {

    data class Prediction(
        val mood: String,
        val confidence: Int,       // 0–100
        val basedOnCount: Int,     // how many entries the prediction is based on
        val timeSlot: String       // "Morning", "Afternoon", etc.
    )

    private fun getTimeSlot(hour: Int): String = when {
        hour < 6  -> "Night"
        hour < 12 -> "Morning"
        hour < 17 -> "Afternoon"
        hour < 21 -> "Evening"
        else      -> "Late Night"
    }

    /**
     * Predict mood for the current time and day of week.
     *
     * @param entries All mood entries, sorted by timestamp descending.
     * @param minSampleSize Minimum number of matching entries required.
     * @return A [Prediction] or null if insufficient data.
     */
    fun predict(
        entries: List<MoodEntry>,
        minSampleSize: Int = 3
    ): Prediction? {
        if (entries.size < minSampleSize) return null

        val now = Calendar.getInstance()
        val currentDow = now.get(Calendar.DAY_OF_WEEK)
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentSlot = getTimeSlot(currentHour)

        // Strategy 1: Same day-of-week + same time slot (strongest signal)
        val exactMatch = entries.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            cal.get(Calendar.DAY_OF_WEEK) == currentDow &&
                getTimeSlot(cal.get(Calendar.HOUR_OF_DAY)) == currentSlot
        }

        // Strategy 2: Same time slot any day (broader signal)
        val slotMatch = entries.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            getTimeSlot(cal.get(Calendar.HOUR_OF_DAY)) == currentSlot
        }

        // Strategy 3: Same day-of-week any time
        val dowMatch = entries.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            cal.get(Calendar.DAY_OF_WEEK) == currentDow
        }

        // Pick the best dataset: prefer exact match, fall back to broader
        val dataset = when {
            exactMatch.size >= minSampleSize -> exactMatch
            slotMatch.size >= minSampleSize  -> slotMatch
            dowMatch.size >= minSampleSize   -> dowMatch
            else -> return null
        }

        val moodCounts = dataset.groupingBy { it.mood }.eachCount()
        val (dominantMood, dominantCount) = moodCounts.maxByOrNull { it.value }
            ?: return null

        // Confidence = ratio of dominant mood × sample size factor
        val ratio = dominantCount.toFloat() / dataset.size
        val sizeFactor = (dataset.size.coerceAtMost(20).toFloat() / 20f) // max out at 20 samples
        val rawConfidence = (ratio * 0.7f + sizeFactor * 0.3f) * 100f
        val confidence = rawConfidence.toInt().coerceIn(35, 95)

        return Prediction(
            mood = dominantMood,
            confidence = confidence,
            basedOnCount = dataset.size,
            timeSlot = currentSlot
        )
    }

    /**
     * Generate a human-readable explanation of the prediction.
     */
    fun getExplanation(prediction: Prediction): String {
        val emoji = MoodUtils.getEmoji(prediction.mood)
        return when {
            prediction.confidence >= 75 ->
                "Based on ${prediction.basedOnCount} past ${prediction.timeSlot.lowercase()} entries, you tend to feel ${prediction.mood.lowercase()} at this time."
            prediction.confidence >= 55 ->
                "You've felt ${prediction.mood.lowercase()} in ${prediction.confidence}% of similar ${prediction.timeSlot.lowercase()} sessions."
            else ->
                "Early pattern: ${prediction.mood.lowercase()} appears most often at this time of day."
        }
    }
}
