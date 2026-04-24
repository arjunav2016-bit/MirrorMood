package com.mirrormood.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wellness_sessions")
data class WellnessSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String,               // "Breathing", "Body Scan", "Gratitude"
    val durationMs: Long,           // actual session duration in milliseconds
    val completedAt: Long = System.currentTimeMillis()
)
