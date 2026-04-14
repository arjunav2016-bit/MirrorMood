package com.mirrormood.data

import com.mirrormood.data.db.AchievementEntity

/**
 * Static definitions for all unlockable achievements in MirrorMood.
 * Each entry defines the criteria and metadata; the actual unlock logic
 * lives in [com.mirrormood.data.repository.AchievementRepository].
 */
object AchievementDefinitions {

    val ALL: List<AchievementEntity> = listOf(
        // ── Volume Achievements ──────────────────────────────
        AchievementEntity(
            id = "first_checkin",
            title = "First Check-In",
            description = "Record your very first mood entry.",
            emoji = "🌱",
            category = "volume",
            target = 1
        ),
        AchievementEntity(
            id = "ten_checkins",
            title = "Getting Started",
            description = "Record 10 mood entries.",
            emoji = "📊",
            category = "volume",
            target = 10
        ),
        AchievementEntity(
            id = "fifty_checkins",
            title = "Half Century",
            description = "Record 50 mood entries.",
            emoji = "🏅",
            category = "volume",
            target = 50
        ),
        AchievementEntity(
            id = "century",
            title = "Century",
            description = "Record 100 mood entries.",
            emoji = "💯",
            category = "volume",
            target = 100
        ),

        // ── Streak Achievements ──────────────────────────────
        AchievementEntity(
            id = "three_day_streak",
            title = "Three-peat",
            description = "Track your mood for 3 consecutive days.",
            emoji = "🔥",
            category = "streak",
            target = 3
        ),
        AchievementEntity(
            id = "week_warrior",
            title = "Week Warrior",
            description = "Track your mood for 7 consecutive days.",
            emoji = "⚔️",
            category = "streak",
            target = 7
        ),
        AchievementEntity(
            id = "thirty_day_streak",
            title = "Monthly Master",
            description = "Track your mood for 30 consecutive days.",
            emoji = "👑",
            category = "streak",
            target = 30
        ),

        // ── Exploration Achievements ─────────────────────────
        AchievementEntity(
            id = "diverse_day",
            title = "Emotional Range",
            description = "Experience 4 or more different moods in a single day.",
            emoji = "🌈",
            category = "exploration",
            target = 4
        ),
        AchievementEntity(
            id = "wellness_explorer",
            title = "Wellness Explorer",
            description = "View wellness recommendations for all 6 mood categories.",
            emoji = "🧭",
            category = "exploration",
            target = 6
        ),
        AchievementEntity(
            id = "journaler",
            title = "Storyteller",
            description = "Write 10 journal entries.",
            emoji = "📝",
            category = "exploration",
            target = 10
        ),

        // ── Special Achievements ─────────────────────────────
        AchievementEntity(
            id = "night_owl",
            title = "Night Owl",
            description = "Record a mood entry after midnight.",
            emoji = "🦉",
            category = "special",
            target = 1
        ),
        AchievementEntity(
            id = "early_bird",
            title = "Early Bird",
            description = "Record a mood entry before 7 AM.",
            emoji = "🐦",
            category = "special",
            target = 1
        ),
        AchievementEntity(
            id = "zen_master",
            title = "Zen Master",
            description = "Complete 10 wellness breathing sessions.",
            emoji = "🧘",
            category = "special",
            target = 10
        ),
        AchievementEntity(
            id = "health_nut",
            title = "Health Nut",
            description = "Link Health Connect to your account.",
            emoji = "💚",
            category = "special",
            target = 1
        ),
        AchievementEntity(
            id = "voice_memo",
            title = "Voice Memo",
            description = "Record your first voice journal entry.",
            emoji = "🎙️",
            category = "special",
            target = 1
        )
    )
}
