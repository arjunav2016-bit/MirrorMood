package com.mirrormood.util

import com.mirrormood.data.db.MoodEntry
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class MoodPredictorTest {

    private fun createEntry(mood: String, daysAgo: Int = 0, hour: Int = 14): MoodEntry {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
        }
        return MoodEntry(
            mood = mood,
            timestamp = cal.timeInMillis,
            smileScore = 0.5f,
            eyeOpenScore = 0.5f
        )
    }

    @Test
    fun `predict returns null when insufficient data`() {
        val entries = listOf(createEntry("Happy"))
        assertNull(MoodPredictor.predict(entries))
    }

    @Test
    fun `predict returns null for empty list`() {
        assertNull(MoodPredictor.predict(emptyList()))
    }

    @Test
    fun `predict returns dominant mood from matching time slot`() {
        // Create entries at the current hour/day for the last several weeks
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val entries = (0..6).map { week ->
            createEntry("Happy", daysAgo = week * 7, hour = currentHour)
        } + listOf(
            createEntry("Stressed", daysAgo = 7, hour = currentHour),
            createEntry("Stressed", daysAgo = 14, hour = currentHour)
        )

        val prediction = MoodPredictor.predict(entries)
        assertNotNull(prediction)
        assertEquals("Happy", prediction!!.mood)
        assertTrue(prediction.confidence in 35..95)
        assertTrue(prediction.basedOnCount >= 3)
    }

    @Test
    fun `prediction confidence is bounded`() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val entries = (0..20).map { createEntry("Happy", daysAgo = it, hour = currentHour) }

        val prediction = MoodPredictor.predict(entries)
        assertNotNull(prediction)
        assertTrue(prediction!!.confidence in 35..95)
    }

    @Test
    fun `getExplanation returns non-empty string`() {
        val prediction = MoodPredictor.Prediction(
            mood = "Happy",
            confidence = 78,
            basedOnCount = 12,
            timeSlot = "Afternoon"
        )
        val explanation = MoodPredictor.getExplanation(prediction)
        assertTrue(explanation.isNotEmpty())
        assertTrue(explanation.contains("afternoon") || explanation.contains("Happy") || explanation.contains("happy"))
    }
}
