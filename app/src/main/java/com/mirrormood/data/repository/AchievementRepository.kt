package com.mirrormood.data.repository

import android.content.Context
import com.mirrormood.data.AchievementDefinitions
import com.mirrormood.data.Milestone
import com.mirrormood.data.db.AchievementDao
import com.mirrormood.data.db.AchievementEntity
import com.mirrormood.data.db.MoodEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject

class AchievementRepository @Inject constructor(
    private val achievementDao: AchievementDao
) {

    fun getAllMilestones(): Flow<List<Milestone>> {
        return achievementDao.getAll().map { entities ->
            entities.map { it.toMilestone() }
        }
    }

    /**
     * Ensures all achievement definitions exist in the DB.
     * Called once on app startup.
     */
    suspend fun seedAchievements() {
        achievementDao.insertAll(AchievementDefinitions.ALL)
    }

    /**
     * Evaluates all milestones against current mood data and unlocks any that qualify.
     * Returns list of newly unlocked achievement IDs.
     */
    suspend fun checkAndUnlock(
        entries: List<MoodEntry>,
        context: Context
    ): List<String> {
        val newlyUnlocked = mutableListOf<String>()
        val existing = achievementDao.getAllOnce().associateBy { it.id }
        val now = System.currentTimeMillis()

        // ── Volume checks ────────────────────────────────
        val totalEntries = entries.size
        checkVolume("first_checkin", 1, totalEntries, existing, newlyUnlocked, now)
        checkVolume("ten_checkins", 10, totalEntries, existing, newlyUnlocked, now)
        checkVolume("fifty_checkins", 50, totalEntries, existing, newlyUnlocked, now)
        checkVolume("century", 100, totalEntries, existing, newlyUnlocked, now)

        // ── Streak checks ────────────────────────────────
        val trackingStreak = calculateTrackingStreak(entries)
        checkVolume("three_day_streak", 3, trackingStreak, existing, newlyUnlocked, now)
        checkVolume("week_warrior", 7, trackingStreak, existing, newlyUnlocked, now)
        checkVolume("thirty_day_streak", 30, trackingStreak, existing, newlyUnlocked, now)

        // ── Diverse day ──────────────────────────────────
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayMoods = entries.filter { it.timestamp >= todayStart }.map { it.mood }.distinct().size
        checkVolume("diverse_day", 4, todayMoods, existing, newlyUnlocked, now)

        // ── Journal entries ──────────────────────────────
        val journalCount = entries.count { !it.note.isNullOrBlank() }
        checkVolume("journaler", 10, journalCount, existing, newlyUnlocked, now)

        // ── Time-based specials ──────────────────────────
        val latestEntry = entries.maxByOrNull { it.timestamp }
        if (latestEntry != null) {
            val hour = Calendar.getInstance().apply { timeInMillis = latestEntry.timestamp }
                .get(Calendar.HOUR_OF_DAY)
            if (hour in 0..4) {
                checkVolume("night_owl", 1, 1, existing, newlyUnlocked, now)
            }
            if (hour in 5..6) {
                checkVolume("early_bird", 1, 1, existing, newlyUnlocked, now)
            }
        }

        // ── Wellness sessions ────────────────────────────
        val prefs = context.getSharedPreferences("mirrormood_prefs", Context.MODE_PRIVATE)
        val sessionsCompleted = prefs.getInt("wellness_sessions_completed", 0)
        checkVolume("zen_master", 10, sessionsCompleted, existing, newlyUnlocked, now)

        // ── Health Connect ───────────────────────────────
        val healthConnected = prefs.getBoolean("health_connect_enabled", false)
        if (healthConnected) {
            checkVolume("health_nut", 1, 1, existing, newlyUnlocked, now)
        }

        // ── Voice memo ───────────────────────────────────
        val voiceJournalCount = prefs.getInt("voice_journal_count", 0)
        if (voiceJournalCount > 0) {
            checkVolume("voice_memo", 1, 1, existing, newlyUnlocked, now)
        }

        return newlyUnlocked
    }

    private suspend fun checkVolume(
        id: String,
        target: Int,
        current: Int,
        existing: Map<String, AchievementEntity>,
        newlyUnlocked: MutableList<String>,
        now: Long
    ) {
        val entity = existing[id] ?: return
        if (entity.unlockedAt != null) return // Already unlocked

        // Update progress
        val clampedProgress = current.coerceAtMost(target)
        achievementDao.updateProgress(id, clampedProgress)

        if (current >= target) {
            achievementDao.unlockAchievement(id, now)
            newlyUnlocked.add(id)
        }
    }

    /**
     * Calculates the number of consecutive days (ending today) that have at least one entry.
     */
    private fun calculateTrackingStreak(entries: List<MoodEntry>): Int {
        if (entries.isEmpty()) return 0

        val dayMillis = 24L * 60 * 60 * 1000
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val trackedDays = entries.map { entry ->
            // Normalize to start of day
            val cal = Calendar.getInstance().apply {
                timeInMillis = entry.timestamp
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        }.distinct().sortedDescending()

        if (trackedDays.isEmpty()) return 0

        // Check if the most recent tracked day is today or yesterday
        val mostRecent = trackedDays.first()
        if (mostRecent < today - dayMillis) return 0 // streak broken

        var streak = 0
        var expectedDay = today
        for (day in trackedDays) {
            if (day == expectedDay || day == expectedDay - dayMillis) {
                streak++
                expectedDay = day - dayMillis
            } else if (day < expectedDay - dayMillis) {
                break
            }
        }
        return streak
    }

    private fun AchievementEntity.toMilestone(): Milestone {
        return Milestone(
            id = id,
            title = title,
            description = description,
            emoji = emoji,
            isUnlocked = unlockedAt != null,
            progress = if (target > 0) ((progress.toFloat() / target) * 100).toInt().coerceIn(0, 100) else 0,
            target = target,
            currentAmount = progress
        )
    }
}
