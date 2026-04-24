package com.mirrormood.data.repository

import com.mirrormood.data.db.WellnessSessionDao
import com.mirrormood.data.db.WellnessSessionEntity
import kotlinx.coroutines.flow.Flow

class WellnessSessionRepository(private val dao: WellnessSessionDao) {

    fun getAllSessions(): Flow<List<WellnessSessionEntity>> = dao.getAllSessions()

    suspend fun saveSession(session: WellnessSessionEntity) = dao.insert(session)

    suspend fun getTotalCount(): Int = dao.getTotalCount()

    suspend fun getTotalDurationMs(): Long = dao.getTotalDurationMs() ?: 0L

    suspend fun getSessionsForRange(startMs: Long, endMs: Long) =
        dao.getSessionsForRange(startMs, endMs)
}
