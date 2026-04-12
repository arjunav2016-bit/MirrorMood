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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.mirrormood.MirrorMoodApp
import com.mirrormood.R
import com.mirrormood.data.db.MoodDatabase
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.databinding.ActivitySettingsBinding
import com.mirrormood.security.PinStorage
import com.mirrormood.ui.privacy.PrivacyActivity
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
    private var lockSwitchProgrammatic = false
    private var pendingBackupJson: String? = null

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

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupThemeCards()
        setupGoalCard()
        setupQuietHours()
        setupSecurity()
        setupPauseToggle()
        setupDataManagement()
        setupActions()
        renderState()
    }

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
                val csvHeader = "id,timestamp,date_time,mood,smile_score,eye_open_score,note\n"
                val csvBody = entries.reversed().joinToString("\n") { entry ->
                    val dateStr = dateFormat.format(Date(entry.timestamp))
                    val escapedNote = (entry.note ?: "").replace("\"", "\"\"")
                    "${entry.id},${entry.timestamp},\"$dateStr\",${entry.mood},${entry.smileScore},${entry.eyeOpenScore},\"$escapedNote\""
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
            )
        }

        return JSONObject()
            .put("schemaVersion", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("entries", entryArray)
            .toString(2)
    }

    private fun parseBackupJson(json: String): List<com.mirrormood.data.db.MoodEntry> {
        val root = JSONObject(json)
        if (root.optInt("schemaVersion", -1) != 1) {
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
                note = if (item.isNull("note")) null else item.getString("note")
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
