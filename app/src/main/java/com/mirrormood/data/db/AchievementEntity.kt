package com.mirrormood.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val category: String,          // "streak", "volume", "exploration", "special"
    val unlockedAt: Long? = null,  // null = still locked
    val progress: Int = 0,
    val target: Int = 1
)
