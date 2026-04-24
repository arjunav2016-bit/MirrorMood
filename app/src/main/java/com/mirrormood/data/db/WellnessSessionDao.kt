package com.mirrormood.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WellnessSessionDao {

    @Insert
    suspend fun insert(session: WellnessSessionEntity)

    @Query("SELECT * FROM wellness_sessions ORDER BY completedAt DESC")
    fun getAllSessions(): Flow<List<WellnessSessionEntity>>

    @Query("SELECT * FROM wellness_sessions ORDER BY completedAt DESC")
    suspend fun getAllSessionsOnce(): List<WellnessSessionEntity>

    @Query("SELECT * FROM wellness_sessions WHERE completedAt BETWEEN :startMs AND :endMs ORDER BY completedAt DESC")
    suspend fun getSessionsForRange(startMs: Long, endMs: Long): List<WellnessSessionEntity>

    @Query("SELECT COUNT(*) FROM wellness_sessions")
    suspend fun getTotalCount(): Int

    @Query("SELECT SUM(durationMs) FROM wellness_sessions")
    suspend fun getTotalDurationMs(): Long?

    @Query("DELETE FROM wellness_sessions")
    suspend fun deleteAll()
}
