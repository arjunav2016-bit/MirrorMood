package com.mirrormood.util

import android.app.Activity
import android.os.Build
import com.mirrormood.R
import com.mirrormood.data.db.MoodEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object MoodUtils {

    val supportedMoods: List<String> = listOf("Happy", "Neutral", "Stressed", "Tired", "Focused", "Bored")

    fun normalizeMood(mood: String): String {
        return when (mood.trim()) {
            "Smile" -> "Happy"
            else -> mood.trim()
        }
    }

    fun getEmoji(mood: String): String {
        return when (normalizeMood(mood)) {
            "Happy" -> "\uD83D\uDE0A"
            "Tired" -> "\uD83D\uDE34"
            "Stressed" -> "\uD83D\uDE30"
            "Focused" -> "\uD83E\uDDE0"
            "Bored" -> "\uD83D\uDE12"
            else -> "\uD83D\uDE10"
        }
    }

    fun getColorRes(mood: String): Int {
        return when (normalizeMood(mood)) {
            "Happy" -> R.color.mm_mood_happy
            "Stressed" -> R.color.mm_mood_stressed
            "Tired" -> R.color.mm_mood_tired
            "Focused" -> R.color.mm_mood_focused
            "Bored" -> R.color.mm_mood_bored
            else -> R.color.mm_mood_neutral
        }
    }

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatHour(hour: Int): String {
        return when {
            hour == 0 -> "12:00 AM"
            hour < 12 -> "$hour:00 AM"
            hour == 12 -> "12:00 PM"
            else -> "${hour - 12}:00 PM"
        }
    }

    fun getStreakSubtitle(mood: String): String {
        return when (normalizeMood(mood)) {
            "Happy" -> "Keep doing what is helping."
            "Focused" -> "Your attention has been steady."
            "Stressed" -> "A reset might help break the cycle."
            "Tired" -> "Rest and pacing matter right now."
            "Bored" -> "A small change could lift the day."
            else -> "Notice what has stayed consistent."
        }
    }

    fun getMoodDescriptor(mood: String): String {
        return when (normalizeMood(mood)) {
            "Happy" -> "Bright"
            "Focused" -> "Focused"
            "Stressed" -> "Under pressure"
            "Tired" -> "Low energy"
            "Bored" -> "Restless"
            else -> "Calm"
        }
    }

    fun getEchoTitle(mood: String): String {
        return when (normalizeMood(mood)) {
            "Happy" -> "Positive note"
            "Focused" -> "Focus check-in"
            "Stressed" -> "Stress check-in"
            "Tired" -> "Low-energy note"
            "Bored" -> "Restless note"
            else -> "Recent reflection"
        }
    }

    fun getReflectionPrompt(mood: String): String {
        return when (normalizeMood(mood)) {
            "Happy" -> "What helped you feel good today?"
            "Focused" -> "What is going well, and what do you want to finish next?"
            "Stressed" -> "What feels heaviest right now?"
            "Tired" -> "What would help you recharge today?"
            "Bored" -> "What would make the next hour more engaging?"
            else -> "What are you noticing right now?"
        }
    }

    fun getRitualTitle(mood: String): String {
        return when (normalizeMood(mood)) {
            "Happy" -> "Keep the momentum"
            "Focused" -> "Protect your focus"
            "Stressed" -> "Take a reset"
            "Tired" -> "Recharge gently"
            "Bored" -> "Change the pace"
            else -> "Small reset"
        }
    }

    fun getTimeOfDayLabel(timestamp: Long): String {
        val hour = SimpleDateFormat("H", Locale.getDefault()).format(Date(timestamp)).toInt()
        return when {
            hour < 6 -> "Night"
            hour < 12 -> "Morning"
            hour < 17 -> "Afternoon"
            hour < 21 -> "Evening"
            else -> "Late Night"
        }
    }

    fun getConfidencePercent(entry: MoodEntry?): Int {
        if (entry == null) return 92
        val inferred = if (entry.smileScore > 0f || entry.eyeOpenScore > 0f) {
            (((entry.smileScore.coerceIn(0f, 1f) + entry.eyeOpenScore.coerceIn(0f, 1f)) / 2f) * 100f)
                .roundToInt()
        } else {
            when (normalizeMood(entry.mood)) {
                "Happy" -> 94
                "Focused" -> 91
                "Stressed" -> 88
                "Tired" -> 86
                "Bored" -> 84
                else -> 89
            }
        }
        return inferred.coerceIn(72, 98)
    }

    @Suppress("DEPRECATION")
    fun Activity.slideTransition(forward: Boolean) {
        if (forward) {
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_OPEN,
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
            } else {
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        } else {
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_CLOSE,
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
            } else {
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }
    }
}
