package com.mirrormood.notification

import com.mirrormood.data.db.MoodEntry
import java.util.Calendar

/**
 * Analyzes user mood entry patterns to determine optimal notification timing.
 * Used by [NotificationScheduler] when "Smart Timing" is enabled.
 */
object HabitAnalyzer {

    /**
     * Find the optimal morning notification hour based on when the user
     * typically records their first mood entry of the day.
     * Falls back to [defaultHour] if insufficient data.
     */
    fun findOptimalMorningHour(entries: List<MoodEntry>, defaultHour: Int = 8): Int {
        val morningEntries = entries.filter { entry ->
            val hour = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                .get(Calendar.HOUR_OF_DAY)
            hour in 5..12
        }

        if (morningEntries.size < 7) return defaultHour // Need at least a week of data

        // Group by day, take the first entry of each day (earliest check-in)
        val firstCheckInHours = morningEntries
            .groupBy { entry ->
                Calendar.getInstance().apply {
                    timeInMillis = entry.timestamp
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            .mapValues { (_, dayEntries) ->
                dayEntries.minByOrNull { it.timestamp }?.let { entry ->
                    Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                        .get(Calendar.HOUR_OF_DAY)
                } ?: defaultHour
            }
            .values
            .toList()

        if (firstCheckInHours.isEmpty()) return defaultHour

        // Use median for robustness against outliers
        val sorted = firstCheckInHours.sorted()
        val median = sorted[sorted.size / 2]

        // Send notification 30 min before typical check-in, clamped to 5-12
        return (median - 1).coerceIn(5, 12)
    }

    /**
     * Find the optimal evening notification hour based on when the user
     * typically records their last mood entry of the day.
     * Falls back to [defaultHour] if insufficient data.
     */
    fun findOptimalEveningHour(entries: List<MoodEntry>, defaultHour: Int = 21): Int {
        val eveningEntries = entries.filter { entry ->
            val hour = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                .get(Calendar.HOUR_OF_DAY)
            hour in 17..23
        }

        if (eveningEntries.size < 7) return defaultHour

        val lastCheckInHours = eveningEntries
            .groupBy { entry ->
                Calendar.getInstance().apply {
                    timeInMillis = entry.timestamp
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            .mapValues { (_, dayEntries) ->
                dayEntries.maxByOrNull { it.timestamp }?.let { entry ->
                    Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                        .get(Calendar.HOUR_OF_DAY)
                } ?: defaultHour
            }
            .values
            .toList()

        if (lastCheckInHours.isEmpty()) return defaultHour

        val sorted = lastCheckInHours.sorted()
        val median = sorted[sorted.size / 2]

        // Send notification around the typical evening wind-down time
        return median.coerceIn(18, 23)
    }

    /**
     * Checks whether the user has different engagement patterns on weekends.
     * Returns true if weekend check-in count is meaningfully different (>30%) from weekdays.
     */
    fun hasWeekendPattern(entries: List<MoodEntry>): Boolean {
        if (entries.size < 14) return false

        val grouped = entries.groupBy { entry ->
            val dayOfWeek = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                .get(Calendar.DAY_OF_WEEK)
            dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        }

        val weekdayAvg = (grouped[false]?.size ?: 0) / 5f
        val weekendAvg = (grouped[true]?.size ?: 0) / 2f

        if (weekdayAvg == 0f) return false
        val ratio = weekendAvg / weekdayAvg
        return ratio < 0.7f || ratio > 1.3f
    }
}
