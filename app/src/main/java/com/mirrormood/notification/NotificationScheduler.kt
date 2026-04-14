package com.mirrormood.notification

import android.content.Context
import com.mirrormood.MirrorMoodApp
import com.mirrormood.data.db.MoodDatabase
import com.mirrormood.data.repository.MoodRepository
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        val smartEnabled = prefs.getBoolean("smart_notifications", false)

        if (smartEnabled) {
            scheduleAdaptive(context)
        } else {
            scheduleMorningNotification(context)
            scheduleEveningNotification(context)
        }
        scheduleWeeklyNotification(context)
        scheduleAnomalyWorker(context)
    }

    /**
     * Re-schedule notifications with updated times from user preferences.
     * Called when user changes notification times in Settings.
     */
    fun reschedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("MorningMoodReminder")
        wm.cancelUniqueWork("EveningMoodSummary")

        val prefs = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        val smartEnabled = prefs.getBoolean("smart_notifications", false)

        if (smartEnabled) {
            scheduleAdaptive(context)
        } else {
            scheduleMorningNotification(context)
            scheduleEveningNotification(context)
        }
    }

    /**
     * Adaptive scheduling uses HabitAnalyzer to determine optimal notification times
     * based on the user's actual check-in patterns.
     */
    private fun scheduleAdaptive(context: Context) {
        try {
            val dao = MoodDatabase.getDatabase(context).moodDao()
            val repository = MoodRepository(dao)
            val entries = runBlocking { repository.getAllMoods().first() }

            val optimalMorning = HabitAnalyzer.findOptimalMorningHour(entries)
            val optimalEvening = HabitAnalyzer.findOptimalEveningHour(entries)

            // Save computed times for display in Settings
            val prefs = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("smart_morning_hour", optimalMorning)
                .putInt("smart_evening_hour", optimalEvening)
                .apply()

            scheduleAtHour(context, "MorningMoodReminder", optimalMorning, MorningWorker::class.java)
            scheduleAtHour(context, "EveningMoodSummary", optimalEvening, EveningWorker::class.java)
        } catch (e: Exception) {
            // Fallback to manual scheduling
            scheduleMorningNotification(context)
            scheduleEveningNotification(context)
        }
    }

    private fun scheduleMorningNotification(context: Context) {
        val prefs = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        val hour = prefs.getInt("notif_morning_hour", 8)
        scheduleAtHour(context, "MorningMoodReminder", hour, MorningWorker::class.java)
    }

    private fun scheduleEveningNotification(context: Context) {
        val prefs = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        val hour = prefs.getInt("notif_evening_hour", 21)
        scheduleAtHour(context, "EveningMoodSummary", hour, EveningWorker::class.java)
    }

    private inline fun <reified T : androidx.work.ListenableWorker> scheduleAtHour(
        context: Context,
        workName: String,
        hour: Int,
        workerClass: Class<T>
    ) {
        val delay = calculateDelayUntilHour(hour)
        val request = PeriodicWorkRequestBuilder<T>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName,
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

