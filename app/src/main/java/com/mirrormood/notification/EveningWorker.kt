package com.mirrormood.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.data.repository.WellnessRepository
import com.mirrormood.util.MoodUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar

@HiltWorker
class EveningWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MoodRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {

        // Get today's mood data
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis
        val endOfToday = startOfToday + DAY_MILLIS

        val todayEntries = repository.getMoodsForDay(startOfToday, endOfToday).first()

        // Get yesterday's data for comparison
        val startOfYesterday = startOfToday - DAY_MILLIS
        val yesterdayEntries = repository.getMoodsForRange(startOfYesterday, startOfToday)

        val summary = buildSmartSummary(todayEntries, yesterdayEntries)

        MoodNotificationManager.sendEveningSummary(applicationContext, summary)
        return Result.success()
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

        internal fun buildSmartSummary(
            todayEntries: List<com.mirrormood.data.db.MoodEntry>,
            yesterdayEntries: List<com.mirrormood.data.db.MoodEntry>
        ): String {
            if (todayEntries.isEmpty()) {
                return "No mood data today. Start monitoring tomorrow! 💪"
            }

            val total = todayEntries.size
            val moodCounts = todayEntries.groupBy { it.mood }
            val dominant = moodCounts.maxByOrNull { it.value.size }?.key ?: "Neutral"
            val happyPercent = ((moodCounts["Happy"]?.size ?: 0) * 100) / total
            val emoji = MoodUtils.getEmoji(dominant)

            val mainLine = "$emoji Mostly $dominant today — Happy $happyPercent% of the time."

            // Build trend comparison with yesterday
            val trendLine = if (yesterdayEntries.isNotEmpty()) {
                val yesterdayHappy = ((yesterdayEntries.count { it.mood == "Happy" } * 100) /
                        yesterdayEntries.size)
                val delta = happyPercent - yesterdayHappy
                when {
                    delta > 5 -> " ↑ ${delta}% happier than yesterday!"
                    delta < -5 -> " ↓ ${-delta}% less happy than yesterday."
                    else -> " About the same as yesterday."
                }
            } else {
                ""
            }

            // Add a wellness tip for non-positive moods
            val tipLine = if (dominant in listOf("Stressed", "Tired", "Bored")) {
                val tip = WellnessRepository.getQuickTip(dominant)
                "\n${tip.emoji} Try: ${tip.title}"
            } else {
                ""
            }

            return mainLine + trendLine + tipLine
        }
    }
}
