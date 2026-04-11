package com.mirrormood.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.mirrormood.MirrorMoodApp
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.detection.FaceAnalyzer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class MoodMonitorService : LifecycleService() {

    private lateinit var cameraExecutor: ExecutorService
    
    @Inject lateinit var repository: MoodRepository
    private var cameraRunning = false
    private val quietCheckHandler = Handler(Looper.getMainLooper())
    private var screenOn = true
    private val smartHandler = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    startSmartCycle()
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    smartHandler.removeCallbacksAndMessages(null)
                    if (!cameraRunning && !shouldSuspendCamera()) {
                        startCamera()
                    }
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "MoodMonitorChannel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        getSharedPreferences(MirrorMoodApp.PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(MirrorMoodApp.KEY_MONITORING_SERVICE_RUNNING, true)
            .apply()
        cameraExecutor = Executors.newSingleThreadExecutor()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!shouldSuspendCamera()) {
            startCamera()
        }

        // Check quiet hours every 15 minutes
        startQuietHoursCheck()

        // Register screen on/off receiver for smart monitoring
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, FaceAnalyzer(repository, lifecycleScope, applicationContext))
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
        cameraRunning = true
    }

    private fun stopCamera() {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                cameraProviderFuture.get().unbindAll()
            }, ContextCompat.getMainExecutor(this))
        } catch (_: Exception) {}
        cameraRunning = false
    }

    private fun isQuietHours(): Boolean {
        val prefs = getSharedPreferences(MirrorMoodApp.PREFS_NAME, MODE_PRIVATE)
        val quietStart = prefs.getInt("quiet_start", -1)
        val quietEnd = prefs.getInt("quiet_end", -1)
        if (quietStart == -1 || quietEnd == -1) return false

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (quietStart <= quietEnd) {
            currentHour in quietStart until quietEnd
        } else {
            // e.g., 22:00 to 07:00
            currentHour >= quietStart || currentHour < quietEnd
        }
    }

    private fun isPaused(): Boolean {
        val prefs = getSharedPreferences(MirrorMoodApp.PREFS_NAME, MODE_PRIVATE)
        val paused = prefs.getBoolean("monitoring_paused", false)
        if (!paused) return false

        val resumeAt = prefs.getLong("monitoring_resume_at", 0)
        if (resumeAt > 0 && System.currentTimeMillis() >= resumeAt) {
            // 24h has elapsed — auto-resume
            prefs.edit().putBoolean("monitoring_paused", false).apply()
            return false
        }
        return true
    }

    private fun shouldSuspendCamera(): Boolean = isQuietHours() || isPaused()

    private fun startQuietHoursCheck() {
        val checkRunnable = object : Runnable {
            override fun run() {
                if (shouldSuspendCamera() && cameraRunning) {
                    stopCamera()
                } else if (!shouldSuspendCamera() && !cameraRunning) {
                    startCamera()
                }
                quietCheckHandler.postDelayed(this, 15 * 60 * 1000L) // 15 min
            }
        }
        quietCheckHandler.postDelayed(checkRunnable, 15 * 60 * 1000L)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mirror Mood")
            .setContentText("Mood monitoring is active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mood Monitor",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    // Smart monitoring: when screen off, run camera 5s every 60s
    private fun startSmartCycle() {
        smartHandler.removeCallbacksAndMessages(null)
        if (shouldSuspendCamera()) return

        // Stop camera to save battery
        if (cameraRunning) stopCamera()

        // Schedule brief capture every 60 seconds
        val cycleRunnable = object : Runnable {
            override fun run() {
                if (!screenOn && !shouldSuspendCamera()) {
                    startCamera()
                    // Stop again after 5 seconds
                    smartHandler.postDelayed({
                        if (!screenOn) stopCamera()
                    }, 5000L)
                    smartHandler.postDelayed(this, 60000L)
                }
            }
        }
        smartHandler.postDelayed(cycleRunnable, 60000L)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        getSharedPreferences(MirrorMoodApp.PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(MirrorMoodApp.KEY_MONITORING_SERVICE_RUNNING, false)
            .apply()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        getSharedPreferences(MirrorMoodApp.PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(MirrorMoodApp.KEY_MONITORING_SERVICE_RUNNING, false)
            .apply()
        super.onDestroy()
        quietCheckHandler.removeCallbacksAndMessages(null)
        smartHandler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        cameraExecutor.shutdown()
    }
}