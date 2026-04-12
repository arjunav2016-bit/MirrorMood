package com.mirrormood.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.data.repository.WellnessRepository
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
        val startOfThisWeek = calendar.timeInMillis

        // Get previous week (days 8-14 ago) for comparison
        val endOfLastWeek = startOfThisWeek
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val startOfLastWeek = calendar.timeInMillis

        val thisWeekEntries = repository.getMoodsForRange(startOfThisWeek, endOfToday)
        val lastWeekEntries = repository.getMoodsForRange(startOfLastWeek, endOfLastWeek)

        val summary = buildSmartSummary(thisWeekEntries, lastWeekEntries)

        MoodNotificationManager.sendWeeklySummary(applicationContext, summary)
        return Result.success()
    }

    companion object {

        internal fun buildSmartSummary(
            thisWeekEntries: List<MoodEntry>,
            lastWeekEntries: List<MoodEntry>
        ): String {
            if (thisWeekEntries.isEmpty()) {
                return "No mood data this week. Let's track more next week! 💪"
            }

            val total = thisWeekEntries.size
            val moodCounts = thisWeekEntries.groupBy { it.mood }
            val dominant = moodCounts.maxByOrNull { it.value.size }?.key ?: "Neutral"
            val dominantPercent = ((moodCounts[dominant]?.size ?: 0) * 100) / total
            val emoji = MoodUtils.getEmoji(dominant)

            // Count unique days with data
            val daysWithData = thisWeekEntries.map { entry ->
                Calendar.getInstance().apply {
                    timeInMillis = entry.timestamp
                }.get(Calendar.DAY_OF_YEAR)
            }.distinct().size

            // Calculate streak
            val streakInfo = calculateStreak(thisWeekEntries)

            // Week-over-week comparison
            val trendLine = buildTrendComparison(thisWeekEntries, lastWeekEntries)

            // Wellness tip for concerning dominant mood
            val tipLine = if (dominant in listOf("Stressed", "Tired")) {
                val tip = WellnessRepository.getQuickTip(dominant)
                "\n${tip.emoji} Weekly tip: ${tip.title}"
            } else {
                ""
            }

            return buildString {
                append("$emoji This week: mostly $dominant ($dominantPercent%). ")
                append("Tracked $daysWithData day${if (daysWithData != 1) "s" else ""}. ")
                if (streakInfo.isNotEmpty()) append(streakInfo)
                if (trendLine.isNotEmpty()) append("\n$trendLine")
                if (tipLine.isNotEmpty()) append(tipLine)
            }
        }

        internal fun buildTrendComparison(
            thisWeek: List<MoodEntry>,
            lastWeek: List<MoodEntry>
        ): String {
            if (lastWeek.isEmpty()) return ""

            val thisHappy = (thisWeek.count { it.mood == "Happy" } * 100) / thisWeek.size
            val lastHappy = (lastWeek.count { it.mood == "Happy" } * 100) / lastWeek.size
            val delta = thisHappy - lastHappy

            return when {
                delta > 10 -> "📈 $delta% happier than last week — great progress!"
                delta > 0 -> "📈 Slightly happier than last week (+$delta%)."
                delta < -10 -> "📉 ${-delta}% less happy than last week. Be kind to yourself."
                delta < 0 -> "📉 Slightly lower than last week (${delta}%)."
                else -> "➡️ Similar mood balance to last week."
            }
        }

        private fun calculateStreak(entries: List<MoodEntry>): String {
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
}
