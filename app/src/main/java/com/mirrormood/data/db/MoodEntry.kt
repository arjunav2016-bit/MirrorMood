package com.mirrormood.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mood_entries")
data class MoodEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val mood: String,
    val smileScore: Float,
    val eyeOpenScore: Float,
    val note: String? = null
)