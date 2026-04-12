package com.mirrormood.data.repository

import com.mirrormood.data.db.MoodDao
import com.mirrormood.data.db.MoodEntry
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class MoodRepository(private val moodDao: MoodDao) {

    fun getLatestMood(): Flow<MoodEntry?> =
        moodDao.getLatestEntry()

    fun getMoodsForDay(startMs: Long, endMs: Long): Flow<List<MoodEntry>> =
        moodDao.getEntriesForDay(startMs, endMs)

    fun getMoodsForDayNewestFirst(startMs: Long, endMs: Long): Flow<List<MoodEntry>> =
        moodDao.getEntriesForDayDesc(startMs, endMs)

    fun getAllMoods(): Flow<List<MoodEntry>> =
        moodDao.getAllEntries()

    suspend fun saveMood(entry: MoodEntry) =
        moodDao.insert(entry)

    suspend fun getAllMoodEntries(): List<MoodEntry> =
        moodDao.getAllEntriesOnce()

    suspend fun importMoods(entries: List<MoodEntry>): Int {
        val knownKeys = moodDao.getAllEntriesOnce().map { it.backupKey() }.toMutableSet()
        val newEntries = entries
            .map { it.copy(id = 0) }
            .filter { knownKeys.add(it.backupKey()) }

        if (newEntries.isNotEmpty()) {
            moodDao.insertAll(newEntries)
        }
        return newEntries.size
    }

    suspend fun getMoodsForRange(startMs: Long, endMs: Long): List<MoodEntry> =
        moodDao.getEntriesForRange(startMs, endMs)

    suspend fun deleteMood(id: Int) =
        moodDao.deleteById(id)

    suspend fun deleteAllMoods() =
        moodDao.deleteAll()

    suspend fun updateNote(id: Int, note: String) = 
        moodDao.updateNote(id, note)

    suspend fun cleanOldData() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)
        moodDao.deleteOldEntries(cutoff)
    }

    private fun MoodEntry.backupKey(): BackupKey {
        return BackupKey(
            timestamp = timestamp,
            mood = mood,
            smileScore = smileScore,
            eyeOpenScore = eyeOpenScore,
            note = note
        )
    }

    private data class BackupKey(
        val timestamp: Long,
        val mood: String,
        val smileScore: Float,
        val eyeOpenScore: Float,
        val note: String?
    )
}
