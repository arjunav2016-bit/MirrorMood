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
    private var cachedAudit: PrivacyDataAudit? = null

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
            val audit = loadAudit()
            cachedAudit = audit

            binding.tvAuditMoodCount.text = audit.moodEntryCount.toString()
            binding.tvAuditSessionCount.text = audit.wellnessSessionCount.toString()
            binding.tvAuditStorageSize.text = getString(
                R.string.privacy_storage_estimate,
                audit.estimatedStorageKb
            )
            binding.tvAuditOldest.text = audit.oldestEntrySummary
        }
    }

    private suspend fun loadAudit(): PrivacyDataAudit {
        return withContext(Dispatchers.IO) {
            val moodEntries = moodRepository.getAllMoodEntries()
            val sessionCount = wellnessSessionRepository.getTotalCount()
            val estimatedKB = ((moodEntries.size * BYTES_PER_MOOD_ENTRY) +
                (sessionCount * BYTES_PER_WELLNESS_SESSION)) / BYTES_PER_KB
            val oldestSummary = if (moodEntries.isNotEmpty()) {
                val oldest = moodEntries.minByOrNull { it.timestamp }
                val dateStr = AUDIT_DATE_FORMAT.format(Date(oldest!!.timestamp))
                getString(R.string.privacy_oldest_entry, dateStr)
            } else {
                getString(R.string.privacy_no_data)
            }
            PrivacyDataAudit(
                moodEntryCount = moodEntries.size,
                wellnessSessionCount = sessionCount,
                estimatedStorageKb = estimatedKB.toInt(),
                oldestEntrySummary = oldestSummary
            )
        }
    }

    private fun shareReport() {
        lifecycleScope.launch {
            val snapshot = PrivacySnapshotFactory.create(this@PrivacyReportActivity)
            val audit = cachedAudit ?: loadAudit().also { cachedAudit = it }
            val report = PrivacySnapshotFactory.buildReportText(
                this@PrivacyReportActivity,
                snapshot,
                audit
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.privacy_transparency_report))
                putExtra(Intent.EXTRA_TEXT, report)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.privacy_share_report_chooser)))
        }
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

    private companion object {
        const val BYTES_PER_MOOD_ENTRY = 200L
        const val BYTES_PER_WELLNESS_SESSION = 50L
        const val BYTES_PER_KB = 1024L
        val AUDIT_DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    }
}
