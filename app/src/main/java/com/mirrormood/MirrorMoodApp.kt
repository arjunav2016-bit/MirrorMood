package com.mirrormood

import android.app.Application
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mirrormood.security.PinStorage
import com.mirrormood.util.ThemeHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MirrorMoodApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        const val PREFS_NAME = "mirrormood_prefs"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_CALIBRATION_COMPLETED = "calibration_completed"
        const val KEY_MONITORING_SERVICE_RUNNING = "monitoring_service_running"
        // Kept for backward compat with SettingsDialogHelper
        const val KEY_THEME = "theme_mode"
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_SYSTEM = 2
    }

    override fun onCreate() {
        super.onCreate()

        migrateLegacyPrefs()
        markOnboardingCompleteForExistingUsers()
        markCalibrationCompleteForExistingUsers()
        migrateLegacyLockState()

        // Apply saved theme preference using the unified ThemeHelper
        ThemeHelper.applyNightMode(this)

        // Schedule periodic database cleanup
        scheduleDatabaseCleanup()

        // Note: DynamicColors removed intentionally — the Ethereal Archive
        // design system uses its own curated palette and should not be
        // overridden by Material You wallpaper-based colors.
    }

    private fun scheduleDatabaseCleanup() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiresCharging(true)
            .build()

        val cleanupWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.mirrormood.worker.DatabaseCleanupWorker>(
            7, java.util.concurrent.TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DatabaseCleanup",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            cleanupWorkRequest
        )
        
        val backupConstraints = androidx.work.Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()
            
        val backupWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.mirrormood.worker.BackupWorker>(
            7, java.util.concurrent.TimeUnit.DAYS
        )
            .setConstraints(backupConstraints)
            .build()
            
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AutoBackup",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            backupWorkRequest
        )
    }

    private fun migrateLegacyPrefs() {
        val legacyPrefs = getSharedPreferences("MirrorMoodPrefs", Context.MODE_PRIVATE)
        if (legacyPrefs.all.isEmpty()) return

        val currentPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = currentPrefs.edit()

        listOf("theme_mode", "lock_enabled", "quiet_start", "quiet_end", "goal_mood", "goal_percent")
            .forEach { key ->
                if (currentPrefs.contains(key) || !legacyPrefs.contains(key)) return@forEach
                when (val value = legacyPrefs.all[key]) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Long -> editor.putLong(key, value)
                }
            }

        editor.apply()
    }

    /**
     * Users upgrading from builds without onboarding should not be forced through it.
     */
    private fun markOnboardingCompleteForExistingUsers() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_ONBOARDING_COMPLETED)) return

        val legacy = getSharedPreferences("MirrorMoodPrefs", Context.MODE_PRIVATE)
        if (legacy.all.isNotEmpty()) {
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        }
    }

    private fun markCalibrationCompleteForExistingUsers() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_CALIBRATION_COMPLETED)) return

        val legacy = getSharedPreferences("MirrorMoodPrefs", Context.MODE_PRIVATE)
        if (legacy.all.isNotEmpty()) {
            prefs.edit().putBoolean(KEY_CALIBRATION_COMPLETED, true).apply()
        }
    }

    /**
     * App lock without PIN and without biometrics cannot be satisfied — disable lock.
     */
    private fun migrateLegacyLockState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("lock_enabled", false)) return
        if (PinStorage.hasPin(this)) return
        val bm = BiometricManager.from(this)
        val ok = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        if (ok != BiometricManager.BIOMETRIC_SUCCESS) {
            prefs.edit().putBoolean("lock_enabled", false).apply()
        }
    }
}
