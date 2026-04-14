package com.mirrormood.di

import com.mirrormood.data.db.AchievementDao
import com.mirrormood.data.db.MoodDao
import com.mirrormood.data.repository.AchievementRepository
import com.mirrormood.data.repository.MoodRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideMoodRepository(dao: MoodDao): MoodRepository {
        return MoodRepository(dao)
    }

    @Provides
    @Singleton
    fun provideAchievementRepository(dao: AchievementDao): AchievementRepository {
        return AchievementRepository(dao)
    }
}

