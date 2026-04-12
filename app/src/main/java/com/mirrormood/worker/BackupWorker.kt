package com.mirrormood.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mirrormood.data.repository.MoodRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MoodRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val entries = repository.getAllMoodEntries()
            if (entries.isEmpty()) return@withContext Result.success()

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

            val backupJson = JSONObject()
                .put("schemaVersion", 1)
                .put("exportedAt", System.currentTimeMillis())
                .put("entries", entryArray)
                .toString(2)

            val exportDir = File(context.getExternalFilesDir(null), "backups")
            if (!exportDir.exists()) exportDir.mkdirs()

            val fileName = "mirrormood_auto_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
            val file = File(exportDir, fileName)
            file.writeText(backupJson)

            // Keep only the 5 most recent backups
            val backups = exportDir.listFiles { f -> f.name.startsWith("mirrormood_auto_backup_") }
            if (backups != null && backups.size > 5) {
                backups.sortBy { it.lastModified() }
                backups.take(backups.size - 5).forEach { it.delete() }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
