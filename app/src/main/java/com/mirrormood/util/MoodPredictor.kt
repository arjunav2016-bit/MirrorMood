package com.mirrormood.util

import com.mirrormood.data.db.MoodEntry
import java.util.Calendar

/**
 * Lightweight on-device mood prediction engine.
 *
 * Uses time-of-day and day-of-week historical patterns to predict the most
 * likely next mood. No network calls, no ML model; just statistical analysis
 * over the user's own journal data.
 */
object MoodPredictor {

    data class Prediction(
        val mood: String,
        val confidence: Int,
        val basedOnCount: Int,
        val timeSlot: String
    )

    sealed class Forecast {
        data class Ready(val prediction: Prediction) : Forecast()

        data class Learning(
            val totalEntries: Int,
            val matchingEntries: Int,
            val entriesNeeded: Int,
            val timeSlot: String
        ) : Forecast()
    }

    private fun getTimeSlot(hour: Int): String = when {
        hour < 6 -> "Night"
        hour < 12 -> "Morning"
        hour < 17 -> "Afternoon"
        hour < 21 -> "Evening"
        else -> "Late Night"
    }

    fun predict(
        entries: List<MoodEntry>,
        minSampleSize: Int = 3,
        now: Calendar = Calendar.getInstance()
    ): Prediction? {
        return when (val forecast = forecast(entries, minSampleSize, now)) {
            is Forecast.Ready -> forecast.prediction
            is Forecast.Learning -> null
        }
    }

    fun forecast(
        entries: List<MoodEntry>,
        minSampleSize: Int = 3,
        now: Calendar = Calendar.getInstance()
    ): Forecast {
        val currentDow = now.get(Calendar.DAY_OF_WEEK)
        val currentSlot = getTimeSlot(now.get(Calendar.HOUR_OF_DAY))

        val exactMatch = entries.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            cal.get(Calendar.DAY_OF_WEEK) == currentDow &&
                getTimeSlot(cal.get(Calendar.HOUR_OF_DAY)) == currentSlot
        }

        val slotMatch = entries.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            getTimeSlot(cal.get(Calendar.HOUR_OF_DAY)) == currentSlot
        }

        val dowMatch = entries.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            cal.get(Calendar.DAY_OF_WEEK) == currentDow
        }

        val bestMatchSize = maxOf(exactMatch.size, slotMatch.size, dowMatch.size)
        val dataset = when {
            exactMatch.size >= minSampleSize -> exactMatch
            slotMatch.size >= minSampleSize -> slotMatch
            dowMatch.size >= minSampleSize -> dowMatch
            else -> {
                val readinessCount = if (entries.size < minSampleSize) entries.size else bestMatchSize
                return Forecast.Learning(
                    totalEntries = entries.size,
                    matchingEntries = bestMatchSize,
                    entriesNeeded = (minSampleSize - readinessCount).coerceAtLeast(1),
                    timeSlot = currentSlot
                )
            }
        }

        val moodCounts = dataset.groupingBy { it.mood }.eachCount()
        val (dominantMood, dominantCount) = moodCounts.maxByOrNull { it.value }
            ?: return Forecast.Learning(
                totalEntries = entries.size,
                matchingEntries = bestMatchSize,
                entriesNeeded = minSampleSize,
                timeSlot = currentSlot
            )

        val ratio = dominantCount.toFloat() / dataset.size
        val sizeFactor = dataset.size.coerceAtMost(20).toFloat() / 20f
        val rawConfidence = (ratio * 0.7f + sizeFactor * 0.3f) * 100f
        val confidence = rawConfidence.toInt().coerceIn(35, 95)

        return Forecast.Ready(
            Prediction(
                mood = dominantMood,
                confidence = confidence,
                basedOnCount = dataset.size,
                timeSlot = currentSlot
            )
        )
    }

    fun getExplanation(prediction: Prediction): String {
        return when {
            prediction.confidence >= 75 ->
                "Based on ${prediction.basedOnCount} past ${prediction.timeSlot.lowercase()} entries, you tend to feel ${prediction.mood.lowercase()} at this time."
            prediction.confidence >= 55 ->
                "You've felt ${prediction.mood.lowercase()} in ${prediction.confidence}% of similar ${prediction.timeSlot.lowercase()} sessions."
            else ->
                "Early pattern: ${prediction.mood.lowercase()} appears most often at this time of day."
        }
    }

    fun getLearningExplanation(learning: Forecast.Learning): String {
        return if (learning.totalEntries == 0) {
            "Log a few check-ins and MirrorMood will learn your ${learning.timeSlot.lowercase()} rhythm."
        } else {
            "Add ${learning.entriesNeeded} more similar ${learning.timeSlot.lowercase()} check-in${if (learning.entriesNeeded == 1) "" else "s"} to unlock a reliable forecast."
        }
    }
}
