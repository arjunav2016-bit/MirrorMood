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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var cameraExecutor: ExecutorService
    private val mainHandler = Handler(Looper.getMainLooper())
    private var consecutiveFaceFrames = 0

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

            val analyzer = CalibrationFaceAnalyzer { present ->
                mainHandler.post { onFacePresence(present) }
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

    private fun onFacePresence(present: Boolean) {
        if (present) {
            consecutiveFaceFrames++
            if (consecutiveFaceFrames >= 10) {
                binding.tvStatus.text = getString(R.string.calibration_face_locked)
                binding.tvHint.text = getString(R.string.calibration_hint_ready)
                binding.btnComplete.isEnabled = true
                binding.progressCalibration.isIndeterminate = false
                binding.progressCalibration.progress = 100
            } else {
                binding.tvStatus.text = getString(R.string.calibration_face_detected)
                binding.tvHint.text = getString(R.string.calibration_hint_hold)
                val p = (consecutiveFaceFrames * 100 / 10).coerceAtMost(100)
                binding.progressCalibration.progress = p
            }
        } else {
            consecutiveFaceFrames = 0
            binding.btnComplete.isEnabled = false
            binding.tvStatus.text = getString(R.string.calibration_seek_face)
            binding.tvHint.text = getString(R.string.calibration_hint_center)
            binding.progressCalibration.progress = 0
        }
    }

    private fun completeCalibration() {
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
