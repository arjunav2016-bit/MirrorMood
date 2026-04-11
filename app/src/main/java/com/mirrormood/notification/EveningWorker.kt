package com.mirrormood.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mirrormood.data.repository.MoodRepository
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
        val startOfDay = calendar.timeInMillis
        val endOfDay = startOfDay + (24 * 60 * 60 * 1000)

        val entries = repository.getMoodsForDay(startOfDay, endOfDay).first()

        val summary = if (entries.isEmpty()) {
            "No mood data today. Start monitoring tomorrow!"
        } else {
            val total = entries.size
            val moodCounts = entries.groupBy { it.mood }
            val dominant = moodCounts.maxByOrNull { it.value.size }?.key ?: "Neutral"
            val happyPercent = ((moodCounts["Happy"]?.size ?: 0) * 100 / total)
            val emoji = MoodUtils.getEmoji(dominant)
            "$emoji Mostly $dominant today. Happy $happyPercent% of the time!"
        }

        MoodNotificationManager.sendEveningSummary(applicationContext, summary)
        return Result.success()
    }
}
