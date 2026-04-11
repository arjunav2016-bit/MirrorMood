package com.mirrormood.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for MoodUtils utility functions.
 */
class MoodUtilsTest {

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
}
