package com.mirrormood.data.repository

import com.mirrormood.data.db.MoodEntry
import java.util.Calendar

/**
 * Generates contextual, dynamic journal prompts based on mood history,
 * current mood, streak data, and time of day.
 */
object PromptEngine {

    /**
     * Generate 3 smart prompts tailored to the user's current context.
     */
    fun generatePrompts(
        currentMood: String,
        recentEntries: List<MoodEntry> = emptyList()
    ): List<String> {
        val prompts = mutableListOf<String>()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // 1. Streak-aware prompt
        val streakPrompt = getStreakPrompt(currentMood, recentEntries)
        if (streakPrompt != null) prompts.add(streakPrompt)

        // 2. Time-of-day prompt
        prompts.add(getTimeAwarePrompt(currentMood, hour))

        // 3. Mood-specific deep prompt
        prompts.add(getMoodDeepPrompt(currentMood))

        // 4. Trigger-aware prompt (if recent entries have triggers)
        val triggerPrompt = getTriggerPrompt(recentEntries)
        if (triggerPrompt != null) prompts.add(triggerPrompt)

        // 5. Contrast prompt (mood changed recently)
        val contrastPrompt = getContrastPrompt(currentMood, recentEntries)
        if (contrastPrompt != null) prompts.add(contrastPrompt)

        return prompts.distinct().take(3)
    }

    private fun getStreakPrompt(mood: String, entries: List<MoodEntry>): String? {
        if (entries.size < 3) return null

        val lastThree = entries.take(3)
        val sameMoodCount = lastThree.count { it.mood == mood }

        if (sameMoodCount >= 3) {
            return when (mood) {
                "Stressed" -> "You've been feeling stressed for a while. What's the biggest thing weighing on you?"
                "Tired" -> "Three tired readings in a row. Are you getting enough rest, or is something draining you?"
                "Happy" -> "You've been on a happy streak! What's fueling this positive energy?"
                "Focused" -> "You're locked in. What's keeping you so engaged right now?"
                "Bored" -> "Boredom streak — what would genuinely excite you right now?"
                else -> "You've been feeling ${mood.lowercase()} consistently. What's keeping you steady?"
            }
        }
        return null
    }

    private fun getTimeAwarePrompt(mood: String, hour: Int): String {
        return when {
            hour < 6 -> when (mood) {
                "Tired" -> "Late night or early morning? What's keeping you up?"
                else -> "You're up at an unusual hour. What's on your mind?"
            }
            hour < 10 -> when (mood) {
                "Happy" -> "Great morning energy! What kicked things off well today?"
                "Stressed" -> "Starting the day stressed — what's the first thing you could let go of?"
                else -> "How do you want the rest of your morning to feel?"
            }
            hour < 14 -> when (mood) {
                "Focused" -> "Midday focus — what project has your attention?"
                "Tired" -> "Post-lunch slump? What's one thing that would give you a second wind?"
                else -> "Halfway through the day. How's it going so far?"
            }
            hour < 18 -> when (mood) {
                "Bored" -> "Afternoon drag — what would make the rest of today interesting?"
                "Stressed" -> "The afternoon can build up. What would feel like a win before evening?"
                else -> "Afternoon check-in: what's been the highlight of today?"
            }
            else -> when (mood) {
                "Happy" -> "Ending the day happy! What went right?"
                "Stressed" -> "Evening stress can carry into sleep. What can you release right now?"
                "Tired" -> "Your body is telling you something. What kind of rest do you need tonight?"
                else -> "As the day winds down, what are you grateful for?"
            }
        }
    }

    private fun getMoodDeepPrompt(mood: String): String {
        return when (mood) {
            "Happy" -> "Name one thing you did today that contributed to this mood."
            "Stressed" -> "If you could change one thing about today, what would it be?"
            "Tired" -> "What would recharging look like for you right now?"
            "Focused" -> "What's driving your focus — passion, deadline, or something else?"
            "Bored" -> "What's something you've been curious about but haven't explored yet?"
            else -> "What are you noticing about yourself right now?"
        }
    }

    private fun getTriggerPrompt(entries: List<MoodEntry>): String? {
        val recentTriggers = entries.take(5)
            .mapNotNull { it.triggers }
            .flatMap { it.split(",") }
            .map { it.trim() }
            .groupBy { it }
            .maxByOrNull { it.value.size }

        return recentTriggers?.let { (trigger, occurrences) ->
            if (occurrences.size >= 2) {
                when (trigger) {
                    "Work" -> "Work has been a recurring theme. What's the biggest thing on your plate?"
                    "Social" -> "Social interactions keep popping up. Are they energizing or draining you?"
                    "Sleep" -> "Sleep has been on your mind lately. How did you sleep last night?"
                    "Exercise" -> "You've been noting exercise. How is movement affecting your mood?"
                    "Health" -> "Health is on your radar. What would support your wellbeing today?"
                    else -> "\"$trigger\" keeps coming up. What's going on there?"
                }
            } else null
        }
    }

    private fun getContrastPrompt(currentMood: String, entries: List<MoodEntry>): String? {
        if (entries.isEmpty()) return null
        val previousMood = entries.first().mood
        if (previousMood == currentMood) return null

        return "You shifted from $previousMood to $currentMood. What changed?"
    }
}
