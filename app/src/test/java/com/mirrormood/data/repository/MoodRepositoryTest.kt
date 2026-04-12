package com.mirrormood.data.repository

import com.mirrormood.data.db.MoodDao
import com.mirrormood.data.db.MoodEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for MoodRepository verifying correct delegation to MoodDao
 * and the deduplication logic in importMoods.
 */
class MoodRepositoryTest {

    private lateinit var dao: MoodDao
    private lateinit var repository: MoodRepository

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        repository = MoodRepository(dao)
    }

    // ── Delegation tests ────────────────────────────────────────

    @Test
    fun `getLatestMood delegates to dao getLatestEntry`() {
        val entry = MoodEntry(mood = "Happy", smileScore = 0.9f, eyeOpenScore = 0.8f)
        every { dao.getLatestEntry() } returns flowOf(entry)

        runTest {
            val result = repository.getLatestMood().first()
            assertEquals("Happy", result?.mood)
        }
    }

    @Test
    fun `getAllMoods delegates to dao getAllEntries`() {
        val entries = listOf(
            MoodEntry(mood = "Happy", smileScore = 0.9f, eyeOpenScore = 0.8f),
            MoodEntry(mood = "Tired", smileScore = 0.1f, eyeOpenScore = 0.2f)
        )
        every { dao.getAllEntries() } returns flowOf(entries)

        runTest {
            val result = repository.getAllMoods().first()
            assertEquals(2, result.size)
            assertEquals("Happy", result[0].mood)
            assertEquals("Tired", result[1].mood)
        }
    }

    @Test
    fun `saveMood delegates to dao insert`() = runTest {
        val entry = MoodEntry(mood = "Focused", smileScore = 0.3f, eyeOpenScore = 0.7f)
        repository.saveMood(entry)
        coVerify { dao.insert(entry) }
    }

    @Test
    fun `deleteMood delegates to dao deleteById`() = runTest {
        repository.deleteMood(42)
        coVerify { dao.deleteById(42) }
    }

    @Test
    fun `deleteAllMoods delegates to dao deleteAll`() = runTest {
        repository.deleteAllMoods()
        coVerify { dao.deleteAll() }
    }

    @Test
    fun `updateNote delegates to dao updateNote`() = runTest {
        repository.updateNote(7, "Feeling better now")
        coVerify { dao.updateNote(7, "Feeling better now") }
    }

    @Test
    fun `getMoodsForRange delegates to dao getEntriesForRange`() = runTest {
        val entries = listOf(
            MoodEntry(mood = "Happy", smileScore = 0.9f, eyeOpenScore = 0.8f)
        )
        coEvery { dao.getEntriesForRange(1000L, 2000L) } returns entries

        val result = repository.getMoodsForRange(1000L, 2000L)
        assertEquals(1, result.size)
    }

    // ── Import deduplication tests ──────────────────────────────

    @Test
    fun `importMoods filters out duplicates by backup key`() = runTest {
        val existing = MoodEntry(
            id = 1,
            mood = "Happy",
            smileScore = 0.9f,
            eyeOpenScore = 0.8f,
            note = null,
            timestamp = 1000L
        )
        coEvery { dao.getAllEntriesOnce() } returns listOf(existing)

        // Same content, different id -> should be treated as duplicate
        val duplicate = existing.copy(id = 99)
        val fresh = MoodEntry(
            id = 0,
            mood = "Tired",
            smileScore = 0.1f,
            eyeOpenScore = 0.2f,
            note = "need sleep",
            timestamp = 2000L
        )

        val imported = repository.importMoods(listOf(duplicate, fresh))
        assertEquals(1, imported) // only fresh should be imported
        coVerify { dao.insertAll(match { it.size == 1 && it[0].mood == "Tired" }) }
    }

    @Test
    fun `importMoods returns 0 when all entries are duplicates`() = runTest {
        val existing = MoodEntry(
            mood = "Happy", smileScore = 0.9f, eyeOpenScore = 0.8f, timestamp = 1000L
        )
        coEvery { dao.getAllEntriesOnce() } returns listOf(existing)

        val duplicate = existing.copy(id = 50)
        val imported = repository.importMoods(listOf(duplicate))
        assertEquals(0, imported)
    }

    @Test
    fun `importMoods resets ids to 0 for imported entries`() = runTest {
        coEvery { dao.getAllEntriesOnce() } returns emptyList()

        val entry = MoodEntry(
            id = 999,
            mood = "Focused",
            smileScore = 0.3f,
            eyeOpenScore = 0.7f,
            timestamp = 5000L
        )
        repository.importMoods(listOf(entry))
        coVerify { dao.insertAll(match { it.all { e -> e.id == 0 } }) }
    }
}
