package com.mirrormood.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    fun schedule(context: Context) {
        scheduleMorningNotification(context)
        scheduleEveningNotification(context)
        scheduleWeeklyNotification(context)
        scheduleAnomalyWorker(context)
    }

    private fun scheduleMorningNotification(context: Context) {
        val delay = calculateDelayUntilHour(8) // 8:00 AM

        val request = PeriodicWorkRequestBuilder<MorningWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "MorningMoodReminder",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleEveningNotification(context: Context) {
        val delay = calculateDelayUntilHour(21) // 9:00 PM

        val request = PeriodicWorkRequestBuilder<EveningWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "EveningMoodSummary",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun calculateDelayUntilHour(hour: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    private fun scheduleWeeklyNotification(context: Context) {
        val delay = calculateDelayUntilDayAndHour(Calendar.SUNDAY, 10) // Sunday 10:00 AM

        val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "WeeklyMoodSummary",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun calculateDelayUntilDayAndHour(dayOfWeek: Int, hour: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    private fun scheduleAnomalyWorker(context: Context) {
        val request = PeriodicWorkRequestBuilder<com.mirrormood.worker.AnomalyWorker>(4, TimeUnit.HOURS)
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "AnomalyDetectionObserver",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
