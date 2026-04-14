package com.mirrormood.health

/**
 * Sleep quality assessment result based on Health Connect data.
 */
data class SleepQualityResult(
    val score: Int,                    // 0-100 overall quality score
    val avgDurationHours: Float,       // Average hours slept per night
    val consistencyPercent: Int,       // How consistent sleep schedule is (0-100)
    val totalNights: Int,              // Number of nights with data
    val recommendation: String         // Contextual sleep advice
) {
    companion object {
        val EMPTY = SleepQualityResult(
            score = 0,
            avgDurationHours = 0f,
            consistencyPercent = 0,
            totalNights = 0,
            recommendation = "Link Health Connect to see sleep insights."
        )

        fun calculate(
            sleepDurations: List<Float> // hours per night
        ): SleepQualityResult {
            if (sleepDurations.isEmpty()) return EMPTY

            val avgHours = sleepDurations.average().toFloat()
            val totalNights = sleepDurations.size

            // Duration score: 7-9 hours is optimal
            val durationScore = when {
                avgHours in 7f..9f -> 100
                avgHours in 6f..7f || avgHours in 9f..10f -> 75
                avgHours in 5f..6f || avgHours in 10f..11f -> 50
                else -> 25
            }

            // Consistency: low standard deviation = consistent
            val mean = sleepDurations.average()
            val variance = sleepDurations.map { (it - mean) * (it - mean) }.average()
            val stdDev = kotlin.math.sqrt(variance).toFloat()
            val consistencyScore = when {
                stdDev < 0.5f -> 100
                stdDev < 1.0f -> 80
                stdDev < 1.5f -> 60
                stdDev < 2.0f -> 40
                else -> 20
            }

            val overallScore = ((durationScore * 0.6f) + (consistencyScore * 0.4f)).toInt()
                .coerceIn(0, 100)

            val recommendation = when {
                overallScore >= 80 -> "Your sleep is excellent! Keep it up."
                overallScore >= 60 && avgHours < 7f -> "Try to get at least 7 hours — it correlates with better moods."
                overallScore >= 60 -> "Good sleep! A more consistent bedtime could improve things further."
                avgHours < 6f -> "You're under-sleeping. Even 30 extra minutes can boost your mood significantly."
                stdDev > 1.5f -> "Your sleep schedule varies a lot. Try a consistent bedtime for better mood stability."
                else -> "Focus on both duration and consistency for optimal mood support."
            }

            return SleepQualityResult(
                score = overallScore,
                avgDurationHours = avgHours,
                consistencyPercent = consistencyScore,
                totalNights = totalNights,
                recommendation = recommendation
            )
        }
    }
}
