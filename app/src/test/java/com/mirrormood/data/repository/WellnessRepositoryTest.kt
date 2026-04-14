package com.mirrormood.data.repository

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for WellnessRepository verifying all mood categories
 * return valid, non-empty wellness recommendations.
 */
class WellnessRepositoryTest {

    private val allMoods = listOf("Happy", "Stressed", "Tired", "Focused", "Bored", "Neutral")

    @Test
    fun `getRecommendations returns non-empty list for every mood`() {
        allMoods.forEach { mood ->
            val tips = WellnessRepository.getRecommendations(mood)
            assertTrue("$mood should have tips", tips.isNotEmpty())
        }
    }

    @Test
    fun `getRecommendations returns neutral tips for unknown mood`() {
        val tips = WellnessRepository.getRecommendations("UNKNOWN_MOOD")
        assertTrue("Unknown mood should fall back to tips", tips.isNotEmpty())
    }

    @Test
    fun `getQuickTip returns a single recommendation`() {
        allMoods.forEach { mood ->
            val tip = WellnessRepository.getQuickTip(mood)
            assertTrue("Tip emoji should not be blank", tip.emoji.isNotBlank())
            assertTrue("Tip title should not be blank", tip.title.isNotBlank())
            assertTrue("Tip description should not be blank", tip.description.isNotBlank())
            assertTrue("Tip category should not be blank", tip.category.isNotBlank())
        }
    }

    @Test
    fun `recommendations are shuffled between calls`() {
        // Not a deterministic test, but verifies shuffling doesn't crash
        // and returns the same number of items
        val first = WellnessRepository.getRecommendations("Stressed")
        val second = WellnessRepository.getRecommendations("Stressed")
        assertEquals("Count should be consistent", first.size, second.size)
    }

    @Test
    fun `all tips have valid categories`() {
        val validCategories = setOf("Breathing", "Activity", "Mindset", "Self-Care")
        allMoods.forEach { mood ->
            val tips = WellnessRepository.getRecommendations(mood)
            tips.forEach { tip ->
                assertTrue(
                    "Category '${tip.category}' for mood '$mood' should be valid",
                    tip.category in validCategories
                )
            }
        }
    }

    @Test
    fun `each mood has at least 5 unique tips`() {
        allMoods.forEach { mood ->
            val tips = WellnessRepository.getRecommendations(mood)
            val uniqueTitles = tips.map { it.title }.toSet()
            assertTrue(
                "$mood should have at least 5 unique tips, has ${uniqueTitles.size}",
                uniqueTitles.size >= 5
            )
        }
    }

    @Test
    fun `contextual tip is deterministic for same inputs`() {
        val first = WellnessRepository.getContextualTip(
            mood = "Stressed",
            repeatedTrigger = "Work",
            streakMood = "Stressed",
            streakCount = 2,
            hourOfDay = 10
        )
        val second = WellnessRepository.getContextualTip(
            mood = "Stressed",
            repeatedTrigger = "Work",
            streakMood = "Stressed",
            streakCount = 2,
            hourOfDay = 10
        )

        assertEquals(first.title, second.title)
        assertEquals(first.category, second.category)
    }

    @Test
    fun `contextual tip prefers self care for sleep related tired mood`() {
        val tip = WellnessRepository.getContextualTip(
            mood = "Tired",
            repeatedTrigger = "Sleep",
            hourOfDay = 22
        )

        assertEquals("Self-Care", tip.category)
    }
}
