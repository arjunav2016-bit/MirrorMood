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
import androidx.lifecycle.lifecycleScope
import com.mirrormood.R
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.data.repository.WellnessSessionRepository
import com.mirrormood.databinding.ActivityPrivacyReportBinding
import com.mirrormood.util.BottomNavHelper
import com.mirrormood.util.BottomNavTab
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class PrivacyReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacyReportBinding

    @Inject lateinit var moodRepository: MoodRepository
    @Inject lateinit var wellnessSessionRepository: WellnessSessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacyReportBinding.inflate(layoutInflater)
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

        binding.btnShareReport.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            shareReport()
        }

        binding.btnManagePermissions.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            openAppPermissionSettings()
        }

        BottomNavHelper.setup(this, BottomNavTab.NONE)
    }

    override fun onResume() {
        super.onResume()
        bindReport()
        loadDataAudit()
    }

    private fun bindReport() {
        val snapshot = PrivacySnapshotFactory.create(this)
        binding.tvGeneratedAtValue.text = snapshot.generatedAt
        binding.tvVersionValue.text = snapshot.appVersion
        binding.tvCameraValue.text = snapshot.cameraStatus
        binding.tvNotificationsValue.text = snapshot.notificationStatus
        binding.tvAppLockValue.text = snapshot.appLockStatus
        binding.tvMonitoringPauseValue.text = snapshot.monitoringPauseStatus
        binding.tvProcessingValue.text = snapshot.processingSummary
        binding.tvStorageValue.text = snapshot.storageSummary
        binding.tvExportsValue.text = snapshot.exportsSummary
    }

    private fun loadDataAudit() {
        lifecycleScope.launch {
            val moodEntries = withContext(Dispatchers.IO) {
                moodRepository.getAllMoodEntries()
            }
            val sessionCount = withContext(Dispatchers.IO) {
                wellnessSessionRepository.getTotalCount()
            }

            binding.tvAuditMoodCount.text = moodEntries.size.toString()
            binding.tvAuditSessionCount.text = sessionCount.toString()

            // Estimate storage: ~200 bytes per mood entry + ~50 bytes per session
            val estimatedKB = ((moodEntries.size * 200L) + (sessionCount * 50L)) / 1024
            binding.tvAuditStorageSize.text = getString(R.string.privacy_storage_estimate, estimatedKB.toInt())

            // Oldest entry
            if (moodEntries.isNotEmpty()) {
                val oldest = moodEntries.minByOrNull { it.timestamp }
                val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    .format(Date(oldest!!.timestamp))
                binding.tvAuditOldest.text = getString(R.string.privacy_oldest_entry, dateStr)
            } else {
                binding.tvAuditOldest.text = getString(R.string.privacy_no_data)
            }
        }
    }

    private fun shareReport() {
        val snapshot = PrivacySnapshotFactory.create(this)
        val report = PrivacySnapshotFactory.buildReportText(this, snapshot)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.privacy_transparency_report))
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.privacy_share_report_chooser)))
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

