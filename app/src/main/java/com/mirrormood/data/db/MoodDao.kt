package com.mirrormood.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {

    @Insert
    suspend fun insert(entry: MoodEntry)

    // Get all entries for a specific day
    @Query("SELECT * FROM mood_entries WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp ASC")
    fun getEntriesForDay(startMs: Long, endMs: Long): Flow<List<MoodEntry>>

    @Query("SELECT * FROM mood_entries WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp DESC")
    fun getEntriesForDayDesc(startMs: Long, endMs: Long): Flow<List<MoodEntry>>

    // Get all entries ever
    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<MoodEntry>>

    // Get the latest single entry (for home screen)
    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC LIMIT 1")
    fun getLatestEntry(): Flow<MoodEntry?>

    // Get entries for a date range (used by calendar)
    @Query("SELECT * FROM mood_entries WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp ASC")
    suspend fun getEntriesForRange(startMs: Long, endMs: Long): List<MoodEntry>

    // Delete entries older than 90 days
    @Query("DELETE FROM mood_entries WHERE timestamp < :cutoffMs")
    suspend fun deleteOldEntries(cutoffMs: Long)

    // Delete all entries
    @Query("DELETE FROM mood_entries")
    suspend fun deleteAll()

    // Update note on an entry
    @Query("UPDATE mood_entries SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Int, note: String)

    // Delete a single entry by ID
    @Query("DELETE FROM mood_entries WHERE id = :id")
    suspend fun deleteById(id: Int)
}
