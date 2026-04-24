package com.mirrormood.ui.privacy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.mirrormood.MirrorMoodApp
import com.mirrormood.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PrivacySnapshot(
    val generatedAt: String,
    val appVersion: String,
    val cameraStatus: String,
    val notificationStatus: String,
    val appLockStatus: String,
    val monitoringPauseStatus: String,
    val processingSummary: String,
    val storageSummary: String,
    val exportsSummary: String
)

data class PrivacyDataAudit(
    val moodEntryCount: Int,
    val wellnessSessionCount: Int,
    val estimatedStorageKb: Int,
    val oldestEntrySummary: String
)

object PrivacySnapshotFactory {

    fun create(context: Context): PrivacySnapshot {
        val prefs = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        return PrivacySnapshot(
            generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
            appVersion = appVersionName(context),
            cameraStatus = permissionStatus(context, Manifest.permission.CAMERA),
            notificationStatus = notificationStatus(context),
            appLockStatus = booleanStatus(context, prefs.getBoolean("lock_enabled", false)),
            monitoringPauseStatus = booleanStatus(context, prefs.getBoolean("monitoring_paused", false)),
            processingSummary = context.getString(R.string.privacy_report_processing_value),
            storageSummary = context.getString(R.string.privacy_report_storage_value),
            exportsSummary = context.getString(R.string.privacy_report_exports_value)
        )
    }

    fun buildReportText(
        context: Context,
        snapshot: PrivacySnapshot,
        audit: PrivacyDataAudit? = null
    ): String {
        return buildString {
            appendLine(context.getString(R.string.app_name))
            appendLine(context.getString(R.string.privacy_transparency_report))
            appendLine()
            appendLine("${context.getString(R.string.privacy_report_generated_at)}: ${snapshot.generatedAt}")
            appendLine("${context.getString(R.string.privacy_report_version)}: ${snapshot.appVersion}")
            appendLine()
            appendLine("${context.getString(R.string.privacy_report_permissions)}:")
            appendLine("- ${context.getString(R.string.privacy_report_camera)}: ${snapshot.cameraStatus}")
            appendLine("- ${context.getString(R.string.privacy_report_notifications)}: ${snapshot.notificationStatus}")
            appendLine()
            appendLine("${context.getString(R.string.privacy_report_data_handling)}:")
            appendLine("- ${context.getString(R.string.privacy_report_app_lock)}: ${snapshot.appLockStatus}")
            appendLine("- ${context.getString(R.string.privacy_report_monitoring_pause)}: ${snapshot.monitoringPauseStatus}")
            appendLine("- ${context.getString(R.string.privacy_report_processing)}: ${snapshot.processingSummary}")
            appendLine("- ${context.getString(R.string.privacy_report_storage)}: ${snapshot.storageSummary}")
            appendLine("- ${context.getString(R.string.privacy_report_exports)}: ${snapshot.exportsSummary}")
            if (audit != null) {
                appendLine()
                appendLine("${context.getString(R.string.privacy_audit_title)}:")
                appendLine("- ${context.getString(R.string.privacy_audit_mood_entries_label)}: ${audit.moodEntryCount}")
                appendLine("- ${context.getString(R.string.privacy_audit_sessions_label)}: ${audit.wellnessSessionCount}")
                appendLine("- ${context.getString(R.string.privacy_audit_storage_label)}: ${context.getString(R.string.privacy_storage_estimate, audit.estimatedStorageKb)}")
                appendLine("- ${audit.oldestEntrySummary}")
            }
        }.trim()
    }

    private fun notificationStatus(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionStatus(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.getString(R.string.privacy_status_not_required)
        }
    }

    private fun permissionStatus(context: Context, permission: String): String {
        return if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            context.getString(R.string.privacy_status_granted)
        } else {
            context.getString(R.string.privacy_status_not_granted)
        }
    }

    private fun booleanStatus(context: Context, enabled: Boolean): String {
        return if (enabled) {
            context.getString(R.string.privacy_status_enabled)
        } else {
            context.getString(R.string.privacy_status_disabled)
        }
    }

    private fun appVersionName(context: Context): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return packageInfo.versionName ?: context.getString(R.string.privacy_status_unknown)
    }
}
