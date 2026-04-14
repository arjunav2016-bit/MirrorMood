package com.mirrormood.notification

import com.mirrormood.data.db.MoodEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WeeklySummaryWorkerTest {

    @Test
    fun `buildSmartSummary returns no data string when week is empty`() {
        val summary = WeeklySummaryWorker.buildSmartSummary(emptyList(), emptyList())
        assertEquals("No mood data this week. Let's track more next week! 💪", summary)
    }

    @Test
    fun `buildSmartSummary calculates dominant percentage and counts days accurately`() {
        val c = Calendar.getInstance()
        val timeDay1 = c.timeInMillis
        
        c.add(Calendar.DAY_OF_YEAR, 1)
        val timeDay2 = c.timeInMillis
        
        c.add(Calendar.DAY_OF_YEAR, 1)
        val timeDay3 = c.timeInMillis

        val thisWeek = listOf(
            MoodEntry(mood = "Happy", smileScore = 0.9f, eyeOpenScore = 0.8f, timestamp = timeDay1),
            MoodEntry(mood = "Happy", smileScore = 0.9f, eyeOpenScore = 0.8f, timestamp = timeDay1), // same day
            MoodEntry(mood = "Focused", smileScore = 0.5f, eyeOpenScore = 0.9f, timestamp = timeDay2),
            MoodEntry(mood = "Happy", smileScore = 0.9f, eyeOpenScore = 0.8f, timestamp = timeDay3)
        )
        // Happy = 3 (75%), Focused = 1 (25%). Days tracked = 3.

        val summary = WeeklySummaryWorker.buildSmartSummary(thisWeek, emptyList())
        assertTrue(summary.contains("mostly Happy (75%)"))
        assertTrue(summary.contains("Tracked 3 days"))
    }
    
    @Test
    fun `buildSmartSummary includes tip when dominant mood is Stressed or Tired`() {
        val timeDay1 = Calendar.getInstance().timeInMillis
        val thisWeek = listOf(
            MoodEntry(mood = "Tired", smileScore = 0.1f, eyeOpenScore = 0.2f, timestamp = timeDay1),
            MoodEntry(mood = "Tired", smileScore = 0.1f, eyeOpenScore = 0.2f, timestamp = timeDay1)
        )

        val summary = WeeklySummaryWorker.buildSmartSummary(thisWeek, emptyList())
        assertTrue(summary.contains("mostly Tired (100%)"))
        assertTrue(summary.contains("Weekly tip:"))
    }

    @Test
    fun `buildTrendComparison calculates happier trend`() {
        val thisWeek = List(10) { 
            MoodEntry(mood = if (it < 5) "Happy" else "Neutral", smileScore = 0.9f, eyeOpenScore = 0.8f) // 50% Happy
        }
        val lastWeek = List(10) { 
            MoodEntry(mood = if (it < 2) "Happy" else "Neutral", smileScore = 0.9f, eyeOpenScore = 0.8f) // 20% Happy
        }

        val trend = WeeklySummaryWorker.buildTrendComparison(thisWeek, lastWeek)
        assertTrue(trend.contains("30% happier than last week"))
    }
    
    @Test
    fun `buildTrendComparison calculates less happy trend`() {
        val thisWeek = List(10) { 
            MoodEntry(mood = if (it < 2) "Happy" else "Neutral", smileScore = 0.9f, eyeOpenScore = 0.8f) // 20% Happy
        }
        val lastWeek = List(10) { 
            MoodEntry(mood = if (it < 5) "Happy" else "Neutral", smileScore = 0.9f, eyeOpenScore = 0.8f) // 50% Happy
        }

        val trend = WeeklySummaryWorker.buildTrendComparison(thisWeek, lastWeek)
        assertTrue(trend.contains("30% less happy than last week"))
    }

    @Test
    fun `buildTrendComparison calculates slightly happier trend`() {
        val thisWeek = List(10) { 
            MoodEntry(mood = if (it < 5) "Happy" else "Neutral", smileScore = 0.9f, eyeOpenScore = 0.8f) // 50% Happy
        }
        val lastWeek = List(10) { 
            MoodEntry(mood = if (it < 4) "Happy" else "Neutral", smileScore = 0.9f, eyeOpenScore = 0.8f) // 40% Happy
        }

        val trend = WeeklySummaryWorker.buildTrendComparison(thisWeek, lastWeek)
        assertTrue(trend.contains("Slightly happier than last week (+10%)"))
    }
}
