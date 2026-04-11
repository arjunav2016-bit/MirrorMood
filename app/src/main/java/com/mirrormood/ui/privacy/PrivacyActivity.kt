package com.mirrormood.ui.privacy

import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mirrormood.R
import com.mirrormood.databinding.ActivityPrivacyBinding
import com.mirrormood.util.BottomNavHelper
import com.mirrormood.util.BottomNavTab
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PrivacyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            finish()
            slideTransition(forward = false)
        }

        binding.btnTransparencyReport.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            startActivity(Intent(this, PrivacyReportActivity::class.java))
            slideTransition(forward = true)
        }

        binding.btnManagePermissions.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            openAppPermissionSettings()
        }

        BottomNavHelper.setup(this, BottomNavTab.NONE)
    }

    override fun onResume() {
        super.onResume()
        renderCurrentStatus()
    }

    private fun renderCurrentStatus() {
        val snapshot = PrivacySnapshotFactory.create(this)
        binding.tvCameraStatus.text = snapshot.cameraStatus
        binding.tvNotificationStatus.text = snapshot.notificationStatus
        binding.tvAppLockStatus.text = snapshot.appLockStatus
        binding.tvMonitoringPauseStatus.text = snapshot.monitoringPauseStatus
    }

    private fun openAppPermissionSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.privacy_open_settings_error, Toast.LENGTH_SHORT).show()
        }
    }
}
