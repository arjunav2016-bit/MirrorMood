package com.mirrormood.di

import android.content.Context
import com.mirrormood.data.db.MoodDao
import com.mirrormood.data.db.MoodDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMoodDatabase(@ApplicationContext context: Context): MoodDatabase {
        return MoodDatabase.getDatabase(context)
    }

    @Provides
    fun provideMoodDao(database: MoodDatabase): MoodDao {
        return database.moodDao()
    }
}
