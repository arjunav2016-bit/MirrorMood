package com.mirrormood.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.notification.MoodNotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@HiltWorker
class AnomalyWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MoodRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val fourHoursAgo = now - (4 * 60 * 60 * 1000L)

            val recentEntries = repository.getMoodsForRange(fourHoursAgo, now)
            if (recentEntries.size < 4) {
                // Not enough data in the last 4 hours to establish a strong trend
                return@withContext Result.success()
            }

            // Check if majority of readings indicate stress or fatigue
            val negativeCount = recentEntries.count { it.mood == "Stressed" || it.mood == "Tired" }
            val ratio = negativeCount.toFloat() / recentEntries.size

            if (ratio >= 0.75f) {
                MoodNotificationManager.sendAnomalyAlert(
                    context = context,
                    title = "Take a breath? 💙",
                    message = "We noticed you've been feeling stressed or tired lately. Consider taking a 5-minute break."
                )
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
