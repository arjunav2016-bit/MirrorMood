package com.mirrormood.util

import com.mirrormood.data.db.MoodEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MilestoneEngineTest {

    @Test
    fun `empty logs yields zero progress and not unlocked`() {
        val milestones = MilestoneEngine.generateMilestones(emptyList())

        assertEquals(6, milestones.size)
        milestones.forEach { milestone ->
            assertFalse(milestone.isUnlocked)
            assertEquals(0, milestone.progress)
            assertEquals(0, milestone.currentAmount)
        }
    }

    @Test
    fun `first step milestone unlocks on single entry`() {
        val entry = MoodEntry(
            timestamp = System.currentTimeMillis(),
            mood = "Happy",
            smileScore = 0.5f,
            eyeOpenScore = 0.5f,
            note = null
        )

        val milestones = MilestoneEngine.generateMilestones(listOf(entry))

        val firstStep = milestones.find { it.id == "first_step" }!!
        assertTrue(firstStep.isUnlocked)
        assertEquals(1, firstStep.currentAmount)
        assertEquals(100, firstStep.progress)
    }

    @Test
    fun `observer milestone unlocks on 7 separate days`() {
        val entries = mutableListOf<MoodEntry>()
        val cal = Calendar.getInstance()

        for (i in 0 until 7) {
            entries.add(
                MoodEntry(
                    timestamp = cal.timeInMillis,
                    mood = "Neutral",
                    smileScore = 0.5f,
                    eyeOpenScore = 0.5f,
                    note = null
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        val milestones = MilestoneEngine.generateMilestones(entries)
        
        val observer = milestones.find { it.id == "the_observer" }!!
        assertTrue(observer.isUnlocked)
        assertEquals(7, observer.currentAmount)
        assertEquals(100, observer.progress)
    }

    @Test
    fun `zen master unlocks after 20 neutral entries`() {
        val entries = List(20) {
            MoodEntry(
                timestamp = System.currentTimeMillis(),
                mood = "Neutral",
                smileScore = 0.5f,
                eyeOpenScore = 0.5f,
                note = null
            )
        }

        val milestones = MilestoneEngine.generateMilestones(entries)

        val zenMaster = milestones.find { it.id == "zen_master" }!!
        assertTrue(zenMaster.isUnlocked)
        assertEquals(20, zenMaster.currentAmount)
        assertEquals(100, zenMaster.progress)
    }

    @Test
    fun `radiant unlocks after 10 happy entries`() {
        val entries = List(10) {
            MoodEntry(
                timestamp = System.currentTimeMillis(),
                mood = "Happy",
                smileScore = 0.9f,
                eyeOpenScore = 0.9f,
                note = null
            )
        }

        val milestones = MilestoneEngine.generateMilestones(entries)

        val radiant = milestones.find { it.id == "radiant" }!!
        assertTrue(radiant.isUnlocked)
        assertEquals(10, radiant.currentAmount)
        assertEquals(100, radiant.progress)
    }

    @Test
    fun `eagle eye unlocks after 10 focused entries`() {
        val entries = List(10) {
            MoodEntry(
                timestamp = System.currentTimeMillis(),
                mood = "Focused",
                smileScore = 0.1f,
                eyeOpenScore = 0.9f,
                note = null
            )
        }

        val milestones = MilestoneEngine.generateMilestones(entries)

        val eagleEye = milestones.find { it.id == "eagle_eye" }!!
        assertTrue(eagleEye.isUnlocked)
        assertEquals(10, eagleEye.currentAmount)
        assertEquals(100, eagleEye.progress)
    }

    @Test
    fun `scribe unlocks after 5 journal notes`() {
        val entries = List(5) {
            MoodEntry(
                timestamp = System.currentTimeMillis(),
                mood = "Neutral",
                smileScore = 0.5f,
                eyeOpenScore = 0.5f,
                note = "This is note #$it"
            )
        }

        val milestones = MilestoneEngine.generateMilestones(entries)

        val scribe = milestones.find { it.id == "dedicated_journaler" }!!
        assertTrue(scribe.isUnlocked)
        assertEquals(5, scribe.currentAmount)
        assertEquals(100, scribe.progress)
    }
}
