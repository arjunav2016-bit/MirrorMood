package com.mirrormood.ui.main

import org.junit.Test
import org.junit.Assert.assertNotNull

class MainViewModelTest {

    @Test
    fun `HomeUiState has sensible defaults`() {
        val state = MainViewModel.HomeUiState()
        assertNotNull(state)
        assert(state.todayCount == 0)
        assert(state.dominantMood == "Neutral")
        assert(state.archiveCount == 0)
        assert(state.trendBuckets.size == 7)
        assert(state.distribution.isEmpty())
    }

    @Test
    fun `StreakState holds mood and count`() {
        val streak = MainViewModel.StreakState(mood = "Happy", count = 3, subtitle = "Keep going!")
        assert(streak.mood == "Happy")
        assert(streak.count == 3)
    }

    @Test
    fun `MoodDistribution holds mood and percent`() {
        val dist = MainViewModel.MoodDistribution(mood = "Focused", percent = 42)
        assert(dist.mood == "Focused")
        assert(dist.percent == 42)
    }
}
