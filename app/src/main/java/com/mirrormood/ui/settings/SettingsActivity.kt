package com.mirrormood.ui.settings

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.HapticFeedbackConstants
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.mirrormood.MirrorMoodApp
import com.mirrormood.R
import com.mirrormood.data.db.MoodDatabase
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.databinding.ActivitySettingsBinding
import com.mirrormood.notification.NotificationScheduler
import com.mirrormood.security.PinStorage
import com.mirrormood.worker.BackupWorker
import com.mirrormood.ui.privacy.PrivacyActivity
import com.mirrormood.util.MoodUtils
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import com.mirrormood.widget.MoodWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE) }
    private lateinit var repository: MoodRepository
    private lateinit var healthConnectManager: com.mirrormood.health.HealthConnectManager
    private var lockSwitchProgrammatic = false
    private var pendingBackupJson: String? = null

    private val requestHealthPermissions = registerForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(healthConnectManager.permissions)) {
            binding.switchHealthConnect.isChecked = true
            prefs.edit().putBoolean("health_connect_enabled", true).apply()
            Toast.makeText(this, R.string.settings_health_connect_linked, Toast.LENGTH_SHORT).show()
        } else {
            binding.switchHealthConnect.isChecked = false
            prefs.edit().putBoolean("health_connect_enabled", false).apply()
        }
    }

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            writeBackupToUri(uri)
        } else {
            pendingBackupJson = null
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importBackupFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = MoodRepository(MoodDatabase.getDatabase(applicationContext).moodDao())
        healthConnectManager = com.mirrormood.health.HealthConnectManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupProfile()
        setupThemeCards()
        setupGoalCard()
        setupSensitivity()
        setupQuietHours()
        setupNotificationTimes()
        setupSecurity()
        setupPauseToggle()
        setupHealthConnect()
        setupDataRetention()
        setupDataManagement()
        setupSmartNotifications()
        setupAccessibility()
        setupActions()
        renderState()
    }

    private fun setupHealthConnect() {
        if (!healthConnectManager.isAvailable()) {
            binding.switchHealthConnect.isEnabled = false
            binding.switchHealthConnect.isChecked = false
            binding.switchHealthConnect.contentDescription = getString(R.string.settings_health_connect_not_installed)
            return
        }

        binding.switchHealthConnect.setOnCheckedChangeListener { _, checked ->
            if (lockSwitchProgrammatic) return@setOnCheckedChangeListener
            if (checked) {
                requestHealthPermissions.launch(healthConnectManager.permissions)
            } else {
                prefs.edit().putBoolean("health_connect_enabled", false).apply()
                Toast.makeText(this, R.string.settings_health_connect_unlinked, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ───── Feature 1: User Profile ─────

    private fun setupProfile() {
        binding.btnEditName.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            showNameDialog()
        }
        loadProfileStats()
    }

    private fun showNameDialog() {
        val currentName = prefs.getString("user_display_name", null) ?: ""
        val editText = EditText(this).apply {
            setText(currentName)
            hint = getString(R.string.settings_profile_name_hint)
            setPadding(48, 32, 48, 32)
            setSelection(currentName.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_profile_name_title)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = editText.text?.toString()?.trim().orEmpty()
                if (name.isNotBlank()) {
                    prefs.edit().putString("user_display_name", name).apply()
                    binding.tvProfileName.text = name
                    Toast.makeText(this, R.string.settings_profile_name_saved, Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().remove("user_display_name").apply()
                    binding.tvProfileName.text = getString(R.string.settings_profile_name_summary)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun loadProfileStats() {
        val name = prefs.getString("user_display_name", null)
        binding.tvProfileName.text = name ?: getString(R.string.settings_profile_name_summary)

        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                repository.getAllMoods().first()
            }

            if (entries.isEmpty()) {
                binding.tvProfileTotalEntries.text = getString(R.string.settings_profile_no_data_yet)
                binding.tvProfileDaysTracked.text = ""
                binding.tvProfileTopMood.text = ""
                return@launch
            }

            val totalEntries = entries.size
            binding.tvProfileTotalEntries.text = resources.getQuantityString(
                R.plurals.settings_profile_total_entries, totalEntries, totalEntries
            )

            val distinctDays = entries.map { entry ->
                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = entry.timestamp
                cal.get(java.util.Calendar.DAY_OF_YEAR) * 10000 + cal.get(java.util.Calendar.YEAR)
            }.distinct().size
            binding.tvProfileDaysTracked.text = resources.getQuantityString(
                R.plurals.settings_profile_days_tracked, distinctDays, distinctDays
            )

            val topMood = entries.groupBy { it.mood }
                .maxByOrNull { it.value.size }?.key ?: "Neutral"
            binding.tvProfileTopMood.text = getString(
                R.string.settings_profile_top_mood,
                MoodUtils.getEmoji(topMood),
                topMood
            )

            // Also update entry count in data section
            binding.tvEntryCount.text = resources.getQuantityString(
                R.plurals.settings_entry_count, totalEntries, totalEntries
            )
        }
    }

    // ───── Feature 5: Detection Sensitivity ─────

    private fun setupSensitivity() {
        val currentSensitivity = prefs.getInt("detection_sensitivity", 1)
        binding.sliderSensitivity.value = currentSensitivity.toFloat()
        updateSensitivityLabel(currentSensitivity)

        binding.sliderSensitivity.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val level = value.toInt()
            prefs.edit().putInt("detection_sensitivity", level).apply()
            updateSensitivityLabel(level)
        }
    }

    private fun updateSensitivityLabel(level: Int) {
        val (name, desc) = when (level) {
            0 -> getString(R.string.settings_sensitivity_low) to getString(R.string.settings_sensitivity_low_desc)
            2 -> getString(R.string.settings_sensitivity_high) to getString(R.string.settings_sensitivity_high_desc)
            else -> getString(R.string.settings_sensitivity_medium) to getString(R.string.settings_sensitivity_medium_desc)
        }
        binding.tvSensitivityLabel.text = "$name — $desc"
    }

    // ───── Feature 3: Custom Notification Times ─────

    private fun setupNotificationTimes() {
        val morningHour = prefs.getInt("notif_morning_hour", 8)
        val eveningHour = prefs.getInt("notif_evening_hour", 21)
        binding.etMorningTime.setText(formatHour(morningHour))
        binding.etEveningTime.setText(formatHour(eveningHour))

        binding.etMorningTime.setOnClickListener {
            val current = prefs.getInt("notif_morning_hour", 8)
            TimePickerDialog(this, { _, hourOfDay, _ ->
                prefs.edit().putInt("notif_morning_hour", hourOfDay).apply()
                binding.etMorningTime.setText(formatHour(hourOfDay))
                NotificationScheduler.reschedule(this)
                Toast.makeText(this, R.string.settings_notification_times_updated, Toast.LENGTH_SHORT).show()
            }, current, 0, false).show()
        }

        binding.etEveningTime.setOnClickListener {
            val current = prefs.getInt("notif_evening_hour", 21)
            TimePickerDialog(this, { _, hourOfDay, _ ->
                prefs.edit().putInt("notif_evening_hour", hourOfDay).apply()
                binding.etEveningTime.setText(formatHour(hourOfDay))
                NotificationScheduler.reschedule(this)
                Toast.makeText(this, R.string.settings_notification_times_updated, Toast.LENGTH_SHORT).show()
            }, current, 0, false).show()
        }
    }

    // ───── Feature 4: Data Retention ─────

    private fun setupDataRetention() {
        val retentionDays = prefs.getInt("data_retention_days", 90)
        checkRetentionChip(retentionDays)

        binding.chipGroupRetention.setOnCheckedStateChangeListener { _, checkedIds ->
            val chipId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val days = when (chipId) {
                R.id.chipRetention30 -> 30
                R.id.chipRetention90 -> 90
                R.id.chipRetention180 -> 180
                R.id.chipRetention365 -> 365
                R.id.chipRetentionForever -> 0 // 0 = Forever
                else -> 90
            }
            prefs.edit().putInt("data_retention_days", days).apply()
            val label = when (days) {
                30 -> getString(R.string.settings_retention_30)
                90 -> getString(R.string.settings_retention_90)
                180 -> getString(R.string.settings_retention_180)
                365 -> getString(R.string.settings_retention_365)
                else -> getString(R.string.settings_retention_forever)
            }
            Toast.makeText(this, getString(R.string.settings_retention_updated, label), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkRetentionChip(days: Int) {
        val chipId = when (days) {
            30 -> R.id.chipRetention30
            90 -> R.id.chipRetention90
            180 -> R.id.chipRetention180
            365 -> R.id.chipRetention365
            else -> R.id.chipRetentionForever
        }
        binding.chipGroupRetention.check(chipId)
    }

    // ───── Existing features ─────

    private fun setupThemeCards() {
        binding.btnModeLight.setOnClickListener { selectTheme(ThemeHelper.MODE_LIGHT) }
        binding.btnModeDark.setOnClickListener { selectTheme(ThemeHelper.MODE_DARK) }
        binding.btnModeSystem.setOnClickListener { selectTheme(ThemeHelper.MODE_SYSTEM) }
    }

    private fun setupGoalCard() {
        val startingGoal = prefs.getInt("goal_percent", 5).toFloat()
        binding.sliderGoal.value = startingGoal.coerceIn(1f, 10f)
        binding.sliderGoal.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val rounded = value.toInt()
            prefs.edit().putInt("goal_percent", rounded).apply()
            binding.tvGoalValue.text = formatGoalPerDay(rounded)
        }
    }

    private fun setupQuietHours() {
        binding.etQuietFrom.setOnClickListener { showQuietPicker(true) }
        binding.etQuietTo.setOnClickListener { showQuietPicker(false) }
    }

    private fun setupSecurity() {
        binding.switchBiometric.setOnCheckedChangeListener { _, checked ->
            if (lockSwitchProgrammatic) return@setOnCheckedChangeListener
            if (!checked) {
                prefs.edit().putBoolean("lock_enabled", false).apply()
                Toast.makeText(this, R.string.app_lock_disabled, Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            if (PinStorage.hasPin(this)) {
                prefs.edit().putBoolean("lock_enabled", true).apply()
                Toast.makeText(this, R.string.app_lock_enabled, Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            lockSwitchProgrammatic = true
            binding.switchBiometric.isChecked = false
            lockSwitchProgrammatic = false
            showPinSetupForLockDialog()
        }
    }

    private fun showPinSetupForLockDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_pin_setup, null, false)
        val et1 = view.findViewById<EditText>(R.id.etPin1)
        val et2 = view.findViewById<EditText>(R.id.etPin2)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.lock_setup_pin_title)
            .setMessage(R.string.lock_setup_pin_message)
            .setView(view)
            .setPositiveButton(R.string.lock_setup_pin_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p1 = et1.text?.toString().orEmpty()
                val p2 = et2.text?.toString().orEmpty()
                if (p1.length !in 4..6 || p1 != p2) {
                    Toast.makeText(this, R.string.lock_setup_pin_mismatch, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                PinStorage.savePin(this, p1)
                prefs.edit().putBoolean("lock_enabled", true).apply()
                lockSwitchProgrammatic = true
                binding.switchBiometric.isChecked = true
                lockSwitchProgrammatic = false
                Toast.makeText(this, R.string.app_lock_enabled, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun setupPauseToggle() {
        binding.btnPause.setOnClickListener {
            val isPaused = prefs.getBoolean("monitoring_paused", false)
            if (isPaused) {
                prefs.edit().putBoolean("monitoring_paused", false).apply()
                binding.btnPause.text = getString(R.string.settings_pause_today)
                Toast.makeText(this, R.string.settings_monitoring_resumed, Toast.LENGTH_SHORT).show()
            } else {
                val resumeAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
                prefs.edit()
                    .putBoolean("monitoring_paused", true)
                    .putLong("monitoring_resume_at", resumeAt)
                    .apply()
                binding.btnPause.text = getString(R.string.settings_resume_monitoring)
                Toast.makeText(this, R.string.settings_monitoring_paused_for_day, Toast.LENGTH_SHORT).show()
            }
        }
        // Set initial state
        val isPaused = prefs.getBoolean("monitoring_paused", false)
        binding.btnPause.text = getString(
            if (isPaused) R.string.settings_resume_monitoring else R.string.settings_pause_today
        )
    }

    private fun setupDataManagement() {
        binding.btnExport.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            exportAsCsv()
        }

        binding.btnExportBackup.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            exportBackup()
        }

        binding.switchPeriodicBackup.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                prefs.edit().putBoolean("weekly_backup_enabled", true).apply()
                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
                val backupWork = PeriodicWorkRequestBuilder<BackupWorker>(7, java.util.concurrent.TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .build()
                WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "weekly_backup",
                    ExistingPeriodicWorkPolicy.KEEP,
                    backupWork
                )
                Toast.makeText(this, "Automated weekly backups enabled", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putBoolean("weekly_backup_enabled", false).apply()
                WorkManager.getInstance(this).cancelUniqueWork("weekly_backup")
                Toast.makeText(this, "Automated backups disabled", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnImportBackup.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            showImportBackupConfirmation()
        }

        binding.btnDeleteAll.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            showDeleteAllConfirmation()
        }

        binding.btnPrivacy.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            startActivity(Intent(this, PrivacyActivity::class.java))
            slideTransition(forward = true)
        }
    }

    private fun exportAsCsv() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                repository.getAllMoods().first()
            }

            if (entries.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.settings_no_mood_data_export, Toast.LENGTH_SHORT).show()
                return@launch
            }

            withContext(Dispatchers.IO) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val csvHeader = "id,timestamp,date_time,mood,smile_score,eye_open_score,note,triggers\n"
                val csvBody = entries.reversed().joinToString("\n") { entry ->
                    val dateStr = dateFormat.format(Date(entry.timestamp))
                    val escapedNote = (entry.note ?: "").replace("\"", "\"\"")
                    val escapedTriggers = (entry.triggers ?: "").replace("\"", "\"\"")
                    "${entry.id},${entry.timestamp},\"$dateStr\",${entry.mood},${entry.smileScore},${entry.eyeOpenScore},\"$escapedNote\",\"$escapedTriggers\""
                }

                val exportDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "")
                if (!exportDir.exists()) exportDir.mkdirs()

                val fileName = "mirrormood_export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
                val file = File(exportDir, fileName)
                file.writeText(csvHeader + csvBody)

                launch(Dispatchers.Main) {
                    try {
                        val uri = FileProvider.getUriForFile(
                            this@SettingsActivity,
                            "${packageName}.fileprovider",
                            file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_share_mood_data)))
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@SettingsActivity,
                            getString(R.string.settings_exported_to, file.absolutePath),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun exportBackup() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                repository.getAllMoodEntries()
            }

            if (entries.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.settings_no_mood_data_export, Toast.LENGTH_SHORT).show()
                return@launch
            }

            pendingBackupJson = withContext(Dispatchers.IO) {
                buildBackupJson(entries)
            }
            val fileName = "mirrormood_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
            exportBackupLauncher.launch(fileName)
        }
    }

    private fun writeBackupToUri(uri: Uri) {
        val backupJson = pendingBackupJson ?: return
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        output.writer().use { writer ->
                            writer.write(backupJson)
                        }
                    } ?: error("No output stream")
                }.isSuccess
            }
            pendingBackupJson = null
            Toast.makeText(
                this@SettingsActivity,
                if (success) R.string.settings_backup_exported else R.string.settings_backup_export_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showImportBackupConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_backup_import_title)
            .setMessage(R.string.settings_backup_import_message)
            .setPositiveButton(R.string.settings_backup_import_confirm) { _, _ ->
                importBackupLauncher.launch(arrayOf("application/json", "text/json", "application/octet-stream"))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importBackupFromUri(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().use { reader -> reader.readText() }
                    } ?: error("No input stream")
                    val entries = parseBackupJson(json)
                    repository.importMoods(entries)
                }
            }

            val message = when {
                result.isFailure && result.exceptionOrNull() is JSONException -> R.string.settings_backup_invalid
                result.isFailure -> R.string.settings_backup_import_failed
                result.getOrDefault(0) == 0 -> R.string.settings_backup_import_no_new_entries
                else -> null
            }

            if (message != null) {
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()
            } else {
                MoodWidgetProvider.updateAllWidgets(this@SettingsActivity)
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.settings_backup_imported, result.getOrDefault(0)),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun buildBackupJson(entries: List<com.mirrormood.data.db.MoodEntry>): String {
        val entryArray = JSONArray()
        entries.reversed().forEach { entry ->
            entryArray.put(
                JSONObject()
                    .put("timestamp", entry.timestamp)
                    .put("mood", entry.mood)
                    .put("smileScore", entry.smileScore.toDouble())
                    .put("eyeOpenScore", entry.eyeOpenScore.toDouble())
                    .put("note", entry.note)
                    .put("triggers", entry.triggers)
            )
        }

        return JSONObject()
            .put("schemaVersion", 2)
            .put("exportedAt", System.currentTimeMillis())
            .put("entries", entryArray)
            .toString(2)
    }

    private fun parseBackupJson(json: String): List<com.mirrormood.data.db.MoodEntry> {
        val root = JSONObject(json)
        val schemaVersion = root.optInt("schemaVersion", -1)
        if (schemaVersion !in 1..2) {
            throw JSONException("Unsupported MirrorMood backup schema")
        }

        val entries = root.getJSONArray("entries")
        return List(entries.length()) { index ->
            val item = entries.getJSONObject(index)
            val mood = item.getString("mood").trim()
            if (mood.isEmpty()) throw JSONException("Missing mood")

            com.mirrormood.data.db.MoodEntry(
                timestamp = item.getLong("timestamp"),
                mood = mood,
                smileScore = item.getDouble("smileScore").toFloat(),
                eyeOpenScore = item.getDouble("eyeOpenScore").toFloat(),
                note = if (item.isNull("note")) null else item.getString("note"),
                triggers = if (item.has("triggers") && !item.isNull("triggers")) item.getString("triggers") else null
            )
        }
    }

    private fun showDeleteAllConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_delete_all_title)
            .setMessage(R.string.settings_delete_all_message)
            .setPositiveButton(R.string.settings_delete_all_confirm) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        repository.deleteAllMoods()
                    }
                    Toast.makeText(this@SettingsActivity, R.string.settings_all_mood_data_deleted, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupActions() {
        binding.btnBack.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            finish()
            slideTransition(forward = false)
        }

        binding.btnAchievements.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            startActivity(Intent(this, com.mirrormood.ui.achievements.AchievementsActivity::class.java))
            slideTransition(forward = true)
        }
    }

    // ───── Smart Notifications ─────

    private fun setupSmartNotifications() {
        binding.switchSmartNotifications.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("smart_notifications", checked).apply()
            NotificationScheduler.reschedule(this)

            if (checked) {
                val morningH = prefs.getInt("smart_morning_hour", 8)
                val eveningH = prefs.getInt("smart_evening_hour", 21)
                binding.tvSmartTimingComputed.text = getString(
                    R.string.settings_smart_notifications_computed, morningH, eveningH
                )
                binding.tvSmartTimingComputed.visibility = android.view.View.VISIBLE
            } else {
                binding.tvSmartTimingComputed.visibility = android.view.View.GONE
            }
        }
    }

    // ───── Accessibility ─────

    private fun setupAccessibility() {
        binding.switchReducedMotion.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("reduced_motion", checked).apply()
        }

        binding.switchHighContrast.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("high_contrast", checked).apply()
        }
    }

    private fun renderState() {
        val currentMode = ThemeHelper.getCurrentMode(this)
        renderThemeSelection(currentMode)

        val goal = prefs.getInt("goal_percent", 5)
        binding.tvGoalValue.text = formatGoalPerDay(goal)
        binding.sliderGoal.value = goal.toFloat().coerceIn(1f, 10f)

        val quietStart = prefs.getInt("quiet_start", 22)
        val quietEnd = prefs.getInt("quiet_end", 7)
        binding.etQuietFrom.setText(formatHour(quietStart))
        binding.etQuietTo.setText(formatHour(quietEnd))

        lockSwitchProgrammatic = true
        binding.switchBiometric.isChecked = prefs.getBoolean("lock_enabled", false)
        lockSwitchProgrammatic = false
        
        binding.switchPeriodicBackup.isChecked = prefs.getBoolean("weekly_backup_enabled", false)

        lockSwitchProgrammatic = true
        binding.switchHealthConnect.isChecked = prefs.getBoolean("health_connect_enabled", false)
        lockSwitchProgrammatic = false

        // Smart notifications
        val smartEnabled = prefs.getBoolean("smart_notifications", false)
        binding.switchSmartNotifications.isChecked = smartEnabled
        if (smartEnabled) {
            val morningH = prefs.getInt("smart_morning_hour", 8)
            val eveningH = prefs.getInt("smart_evening_hour", 21)
            binding.tvSmartTimingComputed.text = getString(
                R.string.settings_smart_notifications_computed, morningH, eveningH
            )
            binding.tvSmartTimingComputed.visibility = android.view.View.VISIBLE
        }

        // Accessibility
        binding.switchReducedMotion.isChecked = prefs.getBoolean("reduced_motion", false)
        binding.switchHighContrast.isChecked = prefs.getBoolean("high_contrast", false)
    }

    private fun selectTheme(mode: String) {
        ThemeHelper.setThemeMode(this, mode)
        renderThemeSelection(mode)
        recreate()
    }

    private fun renderThemeSelection(mode: String) {
        binding.btnModeLight.isChecked = mode == ThemeHelper.MODE_LIGHT
        binding.btnModeDark.isChecked = mode == ThemeHelper.MODE_DARK
        binding.btnModeSystem.isChecked = mode == ThemeHelper.MODE_SYSTEM
    }

    private fun formatGoalPerDay(goal: Int): String {
        return resources.getQuantityString(R.plurals.settings_goal_per_day, goal, goal)
    }

    private fun showQuietPicker(isStart: Boolean) {
        val key = if (isStart) "quiet_start" else "quiet_end"
        val currentValue = prefs.getInt(key, if (isStart) 22 else 7)
        TimePickerDialog(this, { _, hourOfDay, _ ->
            prefs.edit().putInt(key, hourOfDay).apply()
            if (isStart) {
                binding.etQuietFrom.setText(formatHour(hourOfDay))
            } else {
                binding.etQuietTo.setText(formatHour(hourOfDay))
            }
        }, currentValue, 0, true).show()
    }

    private fun formatHour(hour: Int): String {
        val normalized = hour.coerceIn(0, 23)
        val suffix = if (normalized < 12) "AM" else "PM"
        val twelveHour = when {
            normalized == 0 -> 12
            normalized > 12 -> normalized - 12
            else -> normalized
        }
        return String.format(Locale.getDefault(), "%02d:00 %s", twelveHour, suffix)
    }
}
