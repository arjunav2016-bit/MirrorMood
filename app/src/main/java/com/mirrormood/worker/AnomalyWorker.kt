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
                // Save anomaly state so the dashboard Smart Action Card can react
                val prefs = context.getSharedPreferences("mirrormood_prefs", Context.MODE_PRIVATE)
                prefs.edit().putLong("anomaly_detected_at", now).apply()

                MoodNotificationManager.sendAnomalyAlert(
                    context = context,
                    title = context.getString(com.mirrormood.R.string.anomaly_notification_title),
                    message = context.getString(com.mirrormood.R.string.anomaly_notification_message)
                )
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
