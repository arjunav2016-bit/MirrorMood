package com.mirrormood.util

import com.mirrormood.data.db.MoodEntry
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/**
 * Tests for MoodUtils utility functions.
 * Covers emoji mapping, string formatting, mood normalization,
 * descriptor methods, reflection prompts, and confidence calculation.
 */
class MoodUtilsTest {

    // ── supportedMoods ──────────────────────────────────────────

    @Test
    fun `supportedMoods contains all 6 moods`() {
        val expected = setOf("Happy", "Neutral", "Stressed", "Tired", "Focused", "Bored")
        assertEquals(expected, MoodUtils.supportedMoods.toSet())
    }

    // ── normalizeMood ───────────────────────────────────────────

    @Test
    fun `normalizeMood converts Smile to Happy`() {
        assertEquals("Happy", MoodUtils.normalizeMood("Smile"))
    }

    @Test
    fun `normalizeMood trims whitespace`() {
        assertEquals("Happy", MoodUtils.normalizeMood("  Happy  "))
    }

    @Test
    fun `normalizeMood passes through unknown moods`() {
        assertEquals("Custom", MoodUtils.normalizeMood("Custom"))
    }

    // ── getEmoji ────────────────────────────────────────────────

    @Test
    fun `getEmoji returns correct emoji for Happy`() {
        assertEquals("😊", MoodUtils.getEmoji("Happy"))
    }

    @Test
    fun `getEmoji returns correct emoji for Tired`() {
        assertEquals("😴", MoodUtils.getEmoji("Tired"))
    }

    @Test
    fun `getEmoji returns correct emoji for Stressed`() {
        assertEquals("😰", MoodUtils.getEmoji("Stressed"))
    }

    @Test
    fun `getEmoji returns correct emoji for Focused`() {
        assertEquals("🧠", MoodUtils.getEmoji("Focused"))
    }

    @Test
    fun `getEmoji returns correct emoji for Bored`() {
        assertEquals("😒", MoodUtils.getEmoji("Bored"))
    }

    @Test
    fun `getEmoji returns neutral emoji for unknown mood`() {
        assertEquals("😐", MoodUtils.getEmoji("Neutral"))
        assertEquals("😐", MoodUtils.getEmoji("Unknown"))
        assertEquals("😐", MoodUtils.getEmoji(""))
    }

    @Test
    fun `getEmoji normalizes Smile to Happy emoji`() {
        assertEquals("😊", MoodUtils.getEmoji("Smile"))
    }

    // ── formatHour ──────────────────────────────────────────────

    @Test
    fun `formatHour returns 12 AM for midnight`() {
        assertEquals("12:00 AM", MoodUtils.formatHour(0))
    }

    @Test
    fun `formatHour returns correct AM format`() {
        assertEquals("1:00 AM", MoodUtils.formatHour(1))
        assertEquals("6:00 AM", MoodUtils.formatHour(6))
        assertEquals("11:00 AM", MoodUtils.formatHour(11))
    }

    @Test
    fun `formatHour returns 12 PM for noon`() {
        assertEquals("12:00 PM", MoodUtils.formatHour(12))
    }

    @Test
    fun `formatHour returns correct PM format`() {
        assertEquals("1:00 PM", MoodUtils.formatHour(13))
        assertEquals("6:00 PM", MoodUtils.formatHour(18))
        assertEquals("11:00 PM", MoodUtils.formatHour(23))
    }

    // ── getStreakSubtitle ────────────────────────────────────────

    @Test
    fun `getStreakSubtitle returns themed message for each mood`() {
        assertTrue(MoodUtils.getStreakSubtitle("Happy").isNotEmpty())
        assertTrue(MoodUtils.getStreakSubtitle("Focused").isNotEmpty())
        assertTrue(MoodUtils.getStreakSubtitle("Stressed").isNotEmpty())
        assertTrue(MoodUtils.getStreakSubtitle("Tired").isNotEmpty())
        assertTrue(MoodUtils.getStreakSubtitle("Bored").isNotEmpty())
        assertTrue(MoodUtils.getStreakSubtitle("Neutral").isNotEmpty())
    }

    @Test
    fun `getStreakSubtitle returns distinct messages per mood`() {
        val messages = MoodUtils.supportedMoods.map { MoodUtils.getStreakSubtitle(it) }.toSet()
        assertEquals("Each mood should have a unique streak subtitle", 6, messages.size)
    }

    // ── getMoodDescriptor ───────────────────────────────────────

    @Test
    fun `getMoodDescriptor returns correct descriptor for each mood`() {
        assertEquals("Bright", MoodUtils.getMoodDescriptor("Happy"))
        assertEquals("Focused", MoodUtils.getMoodDescriptor("Focused"))
        assertEquals("Under pressure", MoodUtils.getMoodDescriptor("Stressed"))
        assertEquals("Low energy", MoodUtils.getMoodDescriptor("Tired"))
        assertEquals("Restless", MoodUtils.getMoodDescriptor("Bored"))
        assertEquals("Calm", MoodUtils.getMoodDescriptor("Neutral"))
    }

    // ── getEchoTitle ────────────────────────────────────────────

    @Test
    fun `getEchoTitle returns unique title for each mood`() {
        val titles = MoodUtils.supportedMoods.map { MoodUtils.getEchoTitle(it) }.toSet()
        assertEquals("Each mood should have a unique echo title", 6, titles.size)
    }

    @Test
    fun `getEchoTitle returns non-empty titles`() {
        MoodUtils.supportedMoods.forEach { mood ->
            assertTrue(
                "Echo title for $mood should not be blank",
                MoodUtils.getEchoTitle(mood).isNotBlank()
            )
        }
    }

    // ── getReflectionPrompt ─────────────────────────────────────

    @Test
    fun `getReflectionPrompt returns unique prompt for each mood`() {
        val prompts = MoodUtils.supportedMoods.map { MoodUtils.getReflectionPrompt(it) }.toSet()
        assertEquals("Each mood should have a unique reflection prompt", 6, prompts.size)
    }

    @Test
    fun `getReflectionPrompt ends with question mark`() {
        MoodUtils.supportedMoods.forEach { mood ->
            val prompt = MoodUtils.getReflectionPrompt(mood)
            assertTrue(
                "Reflection prompt for $mood should end with '?': $prompt",
                prompt.endsWith("?")
            )
        }
    }

    // ── getRitualTitle ──────────────────────────────────────────

    @Test
    fun `getRitualTitle returns non-empty title for each mood`() {
        MoodUtils.supportedMoods.forEach { mood ->
            assertTrue(
                "Ritual title for $mood should not be blank",
                MoodUtils.getRitualTitle(mood).isNotBlank()
            )
        }
    }

    // ── getTimeOfDayLabel ───────────────────────────────────────

    @Test
    fun `getTimeOfDayLabel returns Night for early morning hours`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
        }
        assertEquals("Night", MoodUtils.getTimeOfDayLabel(cal.timeInMillis))
    }

    @Test
    fun `getTimeOfDayLabel returns Morning for AM hours`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }
        assertEquals("Morning", MoodUtils.getTimeOfDayLabel(cal.timeInMillis))
    }

    @Test
    fun `getTimeOfDayLabel returns Afternoon for early PM`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }
        assertEquals("Afternoon", MoodUtils.getTimeOfDayLabel(cal.timeInMillis))
    }

    @Test
    fun `getTimeOfDayLabel returns Evening for late PM`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
        }
        assertEquals("Evening", MoodUtils.getTimeOfDayLabel(cal.timeInMillis))
    }

    @Test
    fun `getTimeOfDayLabel returns Late Night for after 9 PM`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
        }
        assertEquals("Late Night", MoodUtils.getTimeOfDayLabel(cal.timeInMillis))
    }

    // ── getConfidencePercent ────────────────────────────────────

    @Test
    fun `getConfidencePercent returns 92 for null entry`() {
        assertEquals(92, MoodUtils.getConfidencePercent(null))
    }

    @Test
    fun `getConfidencePercent calculates from scores when available`() {
        val entry = MoodEntry(
            mood = "Happy", smileScore = 0.8f, eyeOpenScore = 0.6f
        )
        // avg = (0.8 + 0.6) / 2 = 0.7 -> 70%
        // clamped to 72..98 range -> 72
        val result = MoodUtils.getConfidencePercent(entry)
        assertTrue("Should be in range 72..98, was $result", result in 72..98)
    }

    @Test
    fun `getConfidencePercent uses mood fallback when scores are zero`() {
        val entry = MoodEntry(
            mood = "Happy", smileScore = 0f, eyeOpenScore = 0f
        )
        assertEquals(94, MoodUtils.getConfidencePercent(entry))
    }

    @Test
    fun `getConfidencePercent clamps to range 72 to 98`() {
        // High scores
        val high = MoodEntry(mood = "Happy", smileScore = 1f, eyeOpenScore = 1f)
        assertTrue(MoodUtils.getConfidencePercent(high) <= 98)

        // Very low non-zero scores
        val low = MoodEntry(mood = "Tired", smileScore = 0.01f, eyeOpenScore = 0.01f)
        assertTrue(MoodUtils.getConfidencePercent(low) >= 72)
    }
}
