package com.mirrormood.ui.calibration

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mirrormood.MirrorMoodApp
import com.mirrormood.R
import com.mirrormood.databinding.ActivityCalibrationBinding
import com.mirrormood.detection.CalibrationFaceAnalyzer
import com.mirrormood.ui.lock.LockActivity
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import com.mirrormood.detection.FaceBaseline
import org.json.JSONObject
import android.animation.ObjectAnimator
import android.view.animation.DecelerateInterpolator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var cameraExecutor: ExecutorService
    private val mainHandler = Handler(Looper.getMainLooper())
    private var consecutiveFaceFrames = 0
    private val baselines = mutableListOf<FaceBaseline>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        if (map[Manifest.permission.CAMERA] == true) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnBack.setOnClickListener {
            finish()
            slideTransition(forward = false)
        }
        binding.btnComplete.setOnClickListener { completeCalibration() }
        binding.btnSkip.setOnClickListener { completeCalibration() }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            val perms = mutableListOf(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val analyzer = CalibrationFaceAnalyzer { present, baseline ->
                mainHandler.post { onFacePresence(present, baseline) }
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, analyzer) }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Toast.makeText(this, R.string.calibration_camera_error, Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onFacePresence(present: Boolean, baseline: FaceBaseline?) {
        if (present && baseline != null) {
            if (consecutiveFaceFrames < 10) baselines.add(baseline)
            consecutiveFaceFrames++
            if (consecutiveFaceFrames >= 10) {
                updateStatus(
                    getString(R.string.calibration_face_locked),
                    getString(R.string.calibration_hint_ready)
                )
                binding.btnComplete.isEnabled = true
                binding.progressCalibration.isIndeterminate = false
                animateProgress(100)

                // Pulse the scan overlay as a visual confirmation
                binding.vScanOverlay.animate()
                    .alpha(0.5f).setDuration(200)
                    .withEndAction {
                        binding.vScanOverlay.animate().alpha(1f).setDuration(200).start()
                    }.start()
            } else {
                updateStatus(
                    getString(R.string.calibration_face_detected),
                    getString(R.string.calibration_hint_hold)
                )
                val p = (consecutiveFaceFrames * 100 / 10).coerceAtMost(100)
                animateProgress(p)
            }
        } else {
            consecutiveFaceFrames = 0
            baselines.clear()
            binding.btnComplete.isEnabled = false
            updateStatus(
                getString(R.string.calibration_seek_face),
                getString(R.string.calibration_hint_center)
            )
            animateProgress(0)
        }
    }

    private fun updateStatus(status: String, hint: String) {
        if (binding.tvStatus.text != status) {
            binding.tvStatus.animate().alpha(0f).setDuration(120).withEndAction {
                binding.tvStatus.text = status
                binding.tvStatus.animate().alpha(1f).setDuration(120).start()
            }.start()
        }
        if (binding.tvHint.text != hint) {
            binding.tvHint.animate().alpha(0f).setDuration(120).withEndAction {
                binding.tvHint.text = hint
                binding.tvHint.animate().alpha(1f).setDuration(120).start()
            }.start()
        }
    }

    private fun animateProgress(targetProgress: Int) {
        val current = binding.progressCalibration.progress
        if (current == targetProgress) return
        ObjectAnimator.ofInt(binding.progressCalibration, "progress", current, targetProgress).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun completeCalibration() {
        if (baselines.isNotEmpty()) {
            val avgSmile = baselines.map { it.smileProb }.average().toFloat()
            val avgLeft = baselines.map { it.leftEyeOpen }.average().toFloat()
            val avgRight = baselines.map { it.rightEyeOpen }.average().toFloat()
            val avgPitch = baselines.map { it.headPitch }.average().toFloat()
            val avgYaw = baselines.map { it.headYaw }.average().toFloat()
            val avgRoll = baselines.map { it.headRoll }.average().toFloat()
            
            val json = JSONObject().apply {
                put("smileProb", avgSmile)
                put("leftEyeOpen", avgLeft)
                put("rightEyeOpen", avgRight)
                put("headPitch", avgPitch)
                put("headYaw", avgYaw)
                put("headRoll", avgRoll)
            }.toString()
            
            getSharedPreferences(MirrorMoodApp.PREFS_NAME, MODE_PRIVATE).edit()
                .putString("baseline_metrics", json)
                .apply()
                
            Toast.makeText(this, R.string.calibration_baseline_saved, Toast.LENGTH_SHORT).show()
        }

        getSharedPreferences(MirrorMoodApp.PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(MirrorMoodApp.KEY_CALIBRATION_COMPLETED, true)
            .apply()
        startActivity(Intent(this, LockActivity::class.java))
        slideTransition(forward = true)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
