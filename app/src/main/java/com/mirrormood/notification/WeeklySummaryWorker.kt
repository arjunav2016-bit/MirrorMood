package com.mirrormood.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.util.MoodUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

@HiltWorker
class WeeklySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MoodRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {

        // Get last 7 days of data
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfToday = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, -7)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfWeek = calendar.timeInMillis

        val entries = repository.getMoodsForRange(startOfWeek, endOfToday)

        val summary = if (entries.isEmpty()) {
            "No mood data this week. Let's track more next week! 💪"
        } else {
            val total = entries.size
            val moodCounts = entries.groupBy { it.mood }
            val dominant = moodCounts.maxByOrNull { it.value.size }?.key ?: "Neutral"
            val dominantPercent = ((moodCounts[dominant]?.size ?: 0) * 100 / total)
            val emoji = MoodUtils.getEmoji(dominant)

            // Count unique days with data
            val daysWithData = entries.map { entry ->
                Calendar.getInstance().apply {
                    timeInMillis = entry.timestamp
                }.get(Calendar.DAY_OF_YEAR)
            }.distinct().size

            // Calculate streak
            val streakInfo = calculateStreak(entries)

            buildString {
                append("$emoji This week: mostly $dominant ($dominantPercent%). ")
                append("Tracked $daysWithData day${if (daysWithData != 1) "s" else ""}. ")
                if (streakInfo.isNotEmpty()) append(streakInfo)
            }
        }

        MoodNotificationManager.sendWeeklySummary(applicationContext, summary)
        return Result.success()
    }

    private fun calculateStreak(entries: List<com.mirrormood.data.db.MoodEntry>): String {
        // Group by day and find dominant mood per day
        val dailyDominant = entries.groupBy { entry ->
            Calendar.getInstance().apply {
                timeInMillis = entry.timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.mapValues { (_, dayEntries) ->
            dayEntries.groupBy { it.mood }
                .maxByOrNull { it.value.size }?.key ?: "Neutral"
        }.toSortedMap()

        if (dailyDominant.isEmpty()) return ""

        // Find longest consecutive streak of the same mood
        var bestMood = ""
        var bestStreak = 0
        var currentMood = ""
        var currentStreak = 0

        dailyDominant.values.forEach { mood ->
            if (mood == currentMood) {
                currentStreak++
            } else {
                currentMood = mood
                currentStreak = 1
            }
            if (currentStreak > bestStreak) {
                bestStreak = currentStreak
                bestMood = currentMood
            }
        }

        return if (bestStreak >= 2) {
            val emoji = MoodUtils.getEmoji(bestMood)
            "$emoji $bestStreak-day $bestMood streak!"
        } else ""
    }
}
