package com.mirrormood.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodDatabaseMigrationTest {

    @Test
    fun `all migrations cover every database version step`() {
        val versionSteps = MoodDatabase.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }

        assertEquals(
            listOf(2 to 3, 3 to 4, 4 to 5, 5 to 6),
            versionSteps
        )
    }

    @Test
    fun `migration 2 to 3 adds confidence to mood entries`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        MoodDatabase.MIGRATION_2_3.migrate(database)

        verify {
            database.execSQL("ALTER TABLE mood_entries ADD COLUMN confidence REAL NOT NULL DEFAULT 0.0")
        }
    }

    @Test
    fun `migration 3 to 4 adds triggers to mood entries`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        MoodDatabase.MIGRATION_3_4.migrate(database)

        verify {
            database.execSQL("ALTER TABLE mood_entries ADD COLUMN triggers TEXT DEFAULT NULL")
        }
    }

    @Test
    fun `migration 4 to 5 creates achievements table`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        MoodDatabase.MIGRATION_4_5.migrate(database)

        verify {
            database.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS achievements (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        emoji TEXT NOT NULL,
                        category TEXT NOT NULL,
                        unlockedAt INTEGER DEFAULT NULL,
                        progress INTEGER NOT NULL DEFAULT 0,
                        target INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent()
            )
        }
    }

    @Test
    fun `migration 5 to 6 creates wellness sessions table`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        MoodDatabase.MIGRATION_5_6.migrate(database)

        verify {
            database.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS wellness_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        durationMs INTEGER NOT NULL,
                        completedAt INTEGER NOT NULL
                    )
                """.trimIndent()
            )
        }
    }
}
