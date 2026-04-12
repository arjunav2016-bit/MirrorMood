package com.mirrormood.ui.main

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mirrormood.MirrorMoodApp
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.util.MoodUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: MoodRepository
) : AndroidViewModel(application) {

    private val _latestMood = MutableStateFlow<MoodEntry?>(null)
    val latestMood: StateFlow<MoodEntry?> = _latestMood.asStateFlow()

    private val _streakState = MutableStateFlow<StreakState?>(null)
    val streakState: StateFlow<StreakState?> = _streakState.asStateFlow()

    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    init {
        observeArchive()
        refreshMonitoringFromPrefs()
    }

    fun refreshMonitoringFromPrefs() {
        val prefs = getApplication<Application>().getSharedPreferences(
            MirrorMoodApp.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        _isMonitoring.value = prefs.getBoolean(MirrorMoodApp.KEY_MONITORING_SERVICE_RUNNING, false)
    }

    fun setMonitoring(monitoring: Boolean) {
        _isMonitoring.value = monitoring
        getApplication<Application>().getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MirrorMoodApp.KEY_MONITORING_SERVICE_RUNNING, monitoring)
            .apply()
    }

    fun saveReflection(mood: String, note: String) {
        viewModelScope.launch {
            repository.saveMood(
                MoodEntry(
                    mood = mood,
                    smileScore = 0f,
                    eyeOpenScore = 0f,
                    note = note
                )
            )
        }
    }

    private fun observeArchive() {
        viewModelScope.launch {
            repository.getAllMoods().collect { entries ->
                val sorted = entries.sortedByDescending { it.timestamp }
                _latestMood.value = sorted.firstOrNull()
                _homeUiState.value = buildHomeUiState(sorted)
                _streakState.value = buildStreakState(sorted)
            }
        }
    }

    data class HomeUiState(
        val recentEntries: List<MoodEntry> = emptyList(),
        val todayCount: Int = 0,
        val dominantMood: String = "Neutral",
        val dominantPercent: Int = 0,
        val archiveCount: Int = 0,
        val reflectionPrompt: String = MoodUtils.getReflectionPrompt("Neutral"),
        val distribution: List<MoodDistribution> = emptyList(),
        val trendBuckets: List<Int> = List(7) { 0 },
        val stabilityDelta: Int = 0
    )

    data class MoodDistribution(
        val mood: String,
        val percent: Int
    )

    data class StreakState(
        val mood: String,
        val count: Int,
        val subtitle: String
    )

    companion object {

        @JvmStatic
        @VisibleForTesting
        internal fun buildHomeUiState(entries: List<MoodEntry>): HomeUiState {
            if (entries.isEmpty()) {
                return HomeUiState(
                    trendBuckets = List(7) { 0 }
                )
            }

            val startOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todayEntries = entries.filter { it.timestamp >= startOfToday }
            val sourceEntries = if (todayEntries.isNotEmpty()) todayEntries else entries.take(8)
            val dominantMood = sourceEntries.groupBy { it.mood }
                .maxByOrNull { it.value.size }
                ?.key
                ?: "Neutral"
            val dominantCount = sourceEntries.count { it.mood == dominantMood }
            val dominantPercent = if (sourceEntries.isNotEmpty()) {
                (dominantCount * 100) / sourceEntries.size
            } else {
                0
            }
            val groupedCounts = sourceEntries.groupingBy { it.mood }.eachCount()
            val sortedCounts = groupedCounts.values.sortedDescending()
            val firstCount = sortedCounts.getOrElse(0) { 0 }
            val secondCount = sortedCounts.getOrElse(1) { 0 }
            val distribution = buildDistribution(groupedCounts, sourceEntries.size)

            return HomeUiState(
                recentEntries = entries.take(3),
                todayCount = todayEntries.size,
                dominantMood = dominantMood,
                dominantPercent = dominantPercent,
                archiveCount = entries.size,
                reflectionPrompt = MoodUtils.getReflectionPrompt(dominantMood),
                distribution = distribution,
                trendBuckets = buildTrendBuckets(entries),
                stabilityDelta = if (sourceEntries.isNotEmpty()) {
                    (((firstCount - secondCount).coerceAtLeast(0) * 100f) / sourceEntries.size).roundToInt()
                } else {
                    0
                }
            )
        }

        @JvmStatic
        @VisibleForTesting
        internal fun buildDistribution(
            groupedCounts: Map<String, Int>,
            total: Int
        ): List<MoodDistribution> {
            val ranked = groupedCounts.entries
                .sortedByDescending { it.value }
                .map { (mood, count) ->
                    MoodDistribution(
                        mood = mood,
                        percent = if (total > 0) ((count * 100f) / total).roundToInt() else 0
                    )
                }

            val fallbacks = listOf("Neutral", "Focused", "Happy")
                .filterNot { mood -> ranked.any { it.mood == mood } }
                .map { MoodDistribution(mood = it, percent = 0) }

            return (ranked + fallbacks).take(3)
        }

        @JvmStatic
        @VisibleForTesting
        internal fun buildTrendBuckets(entries: List<MoodEntry>): List<Int> {
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val dayMillis = 24L * 60L * 60L * 1000L

            return (6 downTo 0).map { index ->
                val dayStart = todayStart - (index * dayMillis)
                val dayEnd = dayStart + dayMillis
                entries.count { it.timestamp in dayStart until dayEnd }
            }
        }

        @JvmStatic
        @VisibleForTesting
        internal fun buildStreakState(entries: List<MoodEntry>): StreakState? {
            if (entries.isEmpty()) return null

            val monthAgo = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -30)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val recentEntries = entries.filter { it.timestamp >= monthAgo }
            if (recentEntries.isEmpty()) return null

            val dailyDominant = recentEntries.groupBy { entry ->
                Calendar.getInstance().apply {
                    timeInMillis = entry.timestamp
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }.mapValues { (_, dayEntries) ->
                dayEntries.groupBy { it.mood }
                    .maxByOrNull { it.value.size }
                    ?.key
                    ?: "Neutral"
            }.toSortedMap()

            val days = dailyDominant.entries.toList().reversed()
            if (days.isEmpty()) return null

            val streakMood = days.first().value
            var streakCount = 0
            for (day in days) {
                if (day.value == streakMood) {
                    streakCount++
                } else {
                    break
                }
            }

            return if (streakCount >= 2) {
                StreakState(
                    mood = streakMood,
                    count = streakCount,
                    subtitle = MoodUtils.getStreakSubtitle(streakMood)
                )
            } else {
                null
            }
        }
    }
}

