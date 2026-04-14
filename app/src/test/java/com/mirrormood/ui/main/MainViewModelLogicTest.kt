package com.mirrormood.ui.main

import com.mirrormood.data.db.MoodEntry
import com.mirrormood.util.MoodUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/**
 * Tests for the pure computation logic extracted from MainViewModel.
 * These functions are now in MainViewModel.Companion and can be tested
 * without an Application context or Hilt injection.
 */
class MainViewModelLogicTest {

    // ── Helper ──────────────────────────────────────────────────

    /** Create a MoodEntry with a specific mood and timestamp offset from now. */
    private fun entry(
        mood: String,
        hoursAgo: Long = 0,
        daysAgo: Int = 0,
        note: String? = null,
        smile: Float = 0.5f,
        eyeOpen: Float = 0.5f,
        confidence: Float = 0.8f
    ): MoodEntry {
        val timestamp = System.currentTimeMillis() -
                (hoursAgo * 3600_000L) -
                (daysAgo * 86_400_000L)
        return MoodEntry(
            mood = mood,
            smileScore = smile,
            eyeOpenScore = eyeOpen,
            confidence = confidence,
            note = note,
            timestamp = timestamp
        )
    }

    // ── buildHomeUiState ────────────────────────────────────────

    @Test
    fun `empty entries produces default HomeUiState`() {
        val state = MainViewModel.buildHomeUiState(emptyList())
        assertEquals(0, state.todayCount)
        assertEquals(0, state.archiveCount)
        assertEquals("Neutral", state.dominantMood)
        assertEquals(emptyList<MoodEntry>(), state.recentEntries)
        assertEquals(List(7) { 0 }, state.trendBuckets)
        assertTrue(state.distribution.isEmpty())
    }

    @Test
    fun `entries from today determine dominant mood`() {
        val entries = listOf(
            entry("Happy", hoursAgo = 1),
            entry("Happy", hoursAgo = 2),
            entry("Stressed", hoursAgo = 3),
        )
        val state = MainViewModel.buildHomeUiState(entries)
        assertEquals("Happy", state.dominantMood)
        assertEquals(3, state.todayCount)
    }

    @Test
    fun `recentEntries takes at most 3`() {
        val entries = (1..10).map { entry("Happy", hoursAgo = it.toLong()) }
        val state = MainViewModel.buildHomeUiState(entries)
        assertEquals(3, state.recentEntries.size)
    }

    @Test
    fun `archiveCount reflects total entry count`() {
        val entries = (1..7).map { entry("Neutral", hoursAgo = it.toLong()) }
        val state = MainViewModel.buildHomeUiState(entries)
        assertEquals(7, state.archiveCount)
    }

    @Test
    fun `dominantPercent is calculated correctly`() {
        // 3 Happy + 1 Stressed = 75% Happy
        val entries = listOf(
            entry("Happy", hoursAgo = 1),
            entry("Happy", hoursAgo = 2),
            entry("Happy", hoursAgo = 3),
            entry("Stressed", hoursAgo = 4),
        )
        val state = MainViewModel.buildHomeUiState(entries)
        assertEquals(75, state.dominantPercent)
    }

    @Test
    fun `reflectionPrompt matches dominant mood`() {
        val entries = listOf(
            entry("Focused", hoursAgo = 1),
            entry("Focused", hoursAgo = 2),
        )
        val state = MainViewModel.buildHomeUiState(entries)
        assertEquals(
            MainViewModel.buildReflectionPrompt("Focused", entries),
            state.reflectionPrompt
        )
    }

    @Test
    fun `reflection prompt uses streak-aware prompt when recent moods repeat`() {
        val entries = listOf(
            entry("Stressed", hoursAgo = 1),
            entry("Stressed", hoursAgo = 2),
            entry("Stressed", hoursAgo = 3),
        )

        val prompt = MainViewModel.buildReflectionPrompt("Stressed", entries)

        assertEquals(
            "You've been feeling stressed for a while. What's the biggest thing weighing on you?",
            prompt
        )
    }

    @Test
    fun `reflection prompt for empty history still returns a useful prompt`() {
        val prompt = MainViewModel.buildReflectionPrompt("Neutral", emptyList())

        assertTrue(prompt.isNotBlank())
    }

    @Test
    fun `smart action returns breathing flow for stressed work pattern`() {
        val entries = listOf(
            entry("Stressed", hoursAgo = 1, note = "Crunching", confidence = 0.9f).copy(triggers = "Work"),
            entry("Stressed", hoursAgo = 3, note = "More meetings", confidence = 0.8f).copy(triggers = "Work"),
            entry("Focused", hoursAgo = 5, confidence = 0.7f).copy(triggers = "Work")
        )

        val state = MainViewModel.buildSmartActionState(entries)

        assertTrue(state.isBreatheMode)
        assertEquals("Work Reset", state.title)
        assertEquals("Take one calm minute before the next task.", state.subtitle)
    }

    @Test
    fun `smart action protects focus when work trigger repeats`() {
        val entries = listOf(
            entry("Focused", hoursAgo = 1).copy(triggers = "Work"),
            entry("Focused", hoursAgo = 2).copy(triggers = "Work"),
            entry("Happy", hoursAgo = 4).copy(triggers = "Exercise")
        )

        val state = MainViewModel.buildSmartActionState(entries)

        assertFalse(state.isBreatheMode)
        assertEquals("Protect This Focus", state.title)
        assertTrue(state.quoteText.contains("25-minute block"))
    }

    @Test
    fun `smart action captures good on happy streak`() {
        val entries = listOf(
            entry("Happy", daysAgo = 0),
            entry("Happy", daysAgo = 1),
            entry("Happy", daysAgo = 2)
        )

        val state = MainViewModel.buildSmartActionState(entries)

        assertFalse(state.isBreatheMode)
        assertEquals("Capture the Good", state.title)
        assertTrue(state.quoteText.contains("Write down what is helping"))
    }

    @Test
    fun `wellness tip leans toward focused guidance for repeated work trigger`() {
        val entries = listOf(
            entry("Neutral", hoursAgo = 1).copy(triggers = "Work"),
            entry("Neutral", hoursAgo = 2).copy(triggers = "Work"),
            entry("Focused", hoursAgo = 4).copy(triggers = "Work")
        )

        val tip = MainViewModel.buildWellnessTip(entries)

        assertTrue(tip.title.isNotBlank())
        assertTrue(tip.category in setOf("Mindset", "Activity", "Self-Care", "Breathing"))
    }

    @Test
    fun `stabilityDelta is zero when only one mood exists`() {
        val entries = listOf(
            entry("Happy", hoursAgo = 1),
            entry("Happy", hoursAgo = 2),
            entry("Happy", hoursAgo = 3),
        )
        val state = MainViewModel.buildHomeUiState(entries)
        // All same mood: firstCount = 3, secondCount = 0
        // delta = (3-0)*100/3 = 100
        assertEquals(100, state.stabilityDelta)
    }

    @Test
    fun `falls back to recent entries when no entries from today`() {
        // Create entries from yesterday only
        val entries = (1..5).map { entry("Tired", daysAgo = 1) }
        val state = MainViewModel.buildHomeUiState(entries)
        assertEquals(0, state.todayCount)
        assertEquals("Tired", state.dominantMood)
    }

    // ── buildDistribution ───────────────────────────────────────

    @Test
    fun `distribution returns top 3 moods by count`() {
        val counts = mapOf("Happy" to 5, "Stressed" to 3, "Tired" to 2, "Bored" to 1)
        val dist = MainViewModel.buildDistribution(counts, 11)
        assertEquals(3, dist.size)
        assertEquals("Happy", dist[0].mood)
        assertEquals("Stressed", dist[1].mood)
        assertEquals("Tired", dist[2].mood)
    }

    @Test
    fun `distribution fills fallbacks when fewer than 3 moods`() {
        val counts = mapOf("Happy" to 5)
        val dist = MainViewModel.buildDistribution(counts, 5)
        assertEquals(3, dist.size)
        assertEquals("Happy", dist[0].mood)
        assertEquals(100, dist[0].percent)
        // Fallbacks should be from [Neutral, Focused, Happy] excluding Happy
        assertTrue(dist[1].percent == 0)
        assertTrue(dist[2].percent == 0)
    }

    @Test
    fun `distribution calculates correct percentages`() {
        val counts = mapOf("Happy" to 2, "Stressed" to 2, "Tired" to 1)
        val dist = MainViewModel.buildDistribution(counts, 5)
        assertEquals(40, dist[0].percent) // 2/5 = 40%
        assertEquals(40, dist[1].percent) // 2/5 = 40%
        assertEquals(20, dist[2].percent) // 1/5 = 20%
    }

    @Test
    fun `distribution handles zero total gracefully`() {
        val counts = mapOf<String, Int>()
        val dist = MainViewModel.buildDistribution(counts, 0)
        assertEquals(3, dist.size)
        assertTrue(dist.all { it.percent == 0 })
    }

    // ── buildTrendBuckets ───────────────────────────────────────

    @Test
    fun `trend buckets returns 7 values`() {
        val buckets = MainViewModel.buildTrendBuckets(emptyList())
        assertEquals(7, buckets.size)
    }

    @Test
    fun `trend buckets are all zero for empty entries`() {
        val buckets = MainViewModel.buildTrendBuckets(emptyList())
        assertTrue(buckets.all { it == 0 })
    }

    @Test
    fun `trend buckets count today entries in last position`() {
        val entries = listOf(
            entry("Happy", hoursAgo = 1),
            entry("Happy", hoursAgo = 2),
        )
        val buckets = MainViewModel.buildTrendBuckets(entries)
        assertEquals(2, buckets.last())
    }

    @Test
    fun `trend buckets count yesterday entries in second to last position`() {
        val entries = listOf(
            entry("Happy", daysAgo = 1),
        )
        val buckets = MainViewModel.buildTrendBuckets(entries)
        assertEquals(1, buckets[buckets.size - 2])
        assertEquals(0, buckets.last())
    }

    // ── buildStreakState ─────────────────────────────────────────

    @Test
    fun `streak returns null for empty entries`() {
        assertNull(MainViewModel.buildStreakState(emptyList()))
    }

    @Test
    fun `streak returns null when streak is less than 2 days`() {
        // Only one day of entries
        val entries = listOf(entry("Happy", hoursAgo = 1))
        assertNull(MainViewModel.buildStreakState(entries))
    }

    @Test
    fun `streak returns state for 2+ consecutive days with same mood`() {
        val entries = listOf(
            entry("Happy", daysAgo = 0),
            entry("Happy", daysAgo = 0),
            entry("Happy", daysAgo = 1),
            entry("Happy", daysAgo = 1),
        )
        val streak = MainViewModel.buildStreakState(entries)
        assertNotNull(streak)
        assertEquals("Happy", streak!!.mood)
        assertEquals(2, streak.count)
        assertEquals(MoodUtils.getStreakSubtitle("Happy"), streak.subtitle)
    }

    @Test
    fun `streak breaks when mood changes between days`() {
        val entries = listOf(
            entry("Stressed", daysAgo = 0),
            entry("Happy", daysAgo = 1),
            entry("Happy", daysAgo = 2),
        )
        // Today is Stressed, yesterday+day before are Happy -> streak of 1 (Stressed), no streak
        val streak = MainViewModel.buildStreakState(entries)
        assertNull(streak) // streak count = 1, which is < 2
    }

    @Test
    fun `streak ignores entries older than 30 days`() {
        val entries = listOf(
            entry("Happy", daysAgo = 35),
            entry("Happy", daysAgo = 36),
        )
        val streak = MainViewModel.buildStreakState(entries)
        assertNull(streak) // All entries are beyond the 30-day window
    }

    @Test
    fun `streak uses daily dominant mood, not raw entries`() {
        // Day 0: 2 Happy, 1 Stressed -> dominant Happy
        // Day 1: 2 Happy, 1 Tired    -> dominant Happy
        // Day 2: 3 Stressed           -> breaks streak
        val entries = listOf(
            entry("Happy", daysAgo = 0),
            entry("Happy", daysAgo = 0),
            entry("Stressed", daysAgo = 0),
            entry("Happy", daysAgo = 1),
            entry("Happy", daysAgo = 1),
            entry("Tired", daysAgo = 1),
            entry("Stressed", daysAgo = 2),
            entry("Stressed", daysAgo = 2),
            entry("Stressed", daysAgo = 2),
        )
        val streak = MainViewModel.buildStreakState(entries)
        assertNotNull(streak)
        assertEquals("Happy", streak!!.mood)
        assertEquals(2, streak.count)
    }
}
