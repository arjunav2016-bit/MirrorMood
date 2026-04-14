package com.mirrormood.ui.main

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mirrormood.MirrorMoodApp
import com.mirrormood.data.WellnessRecommendation
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.data.repository.AchievementRepository
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.data.repository.PromptEngine
import com.mirrormood.data.repository.WellnessRepository
import com.mirrormood.health.HealthConnectManager
import com.mirrormood.health.HealthSnapshot
import com.mirrormood.util.MoodUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: MoodRepository,
    private val achievementRepository: AchievementRepository
) : AndroidViewModel(application) {

    private val _latestMood = MutableStateFlow<MoodEntry?>(null)
    val latestMood: StateFlow<MoodEntry?> = _latestMood.asStateFlow()

    private val _streakState = MutableStateFlow<StreakState?>(null)
    val streakState: StateFlow<StreakState?> = _streakState.asStateFlow()

    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _healthState = MutableStateFlow<HealthSnapshot?>(null)
    val healthState: StateFlow<HealthSnapshot?> = _healthState.asStateFlow()

    private val _newlyUnlocked = MutableSharedFlow<String>()
    val newlyUnlocked: SharedFlow<String> = _newlyUnlocked.asSharedFlow()

    init {
        observeArchive()
        refreshMonitoringFromPrefs()
        loadHealthData()
        seedAchievements()
    }

    private fun loadHealthData() {
        viewModelScope.launch {
            try {
                val prefs = getApplication<Application>().getSharedPreferences(
                    MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE
                )
                if (!prefs.getBoolean("health_connect_enabled", false)) return@launch

                val manager = HealthConnectManager(getApplication())
                if (!manager.isAvailable()) return@launch
                _healthState.value = manager.getTodaySnapshot()
            } catch (_: Exception) {
                // Health Connect unavailable; no card shown.
            }
        }
    }

    fun refreshHealthData() {
        loadHealthData()
    }

    private fun seedAchievements() {
        viewModelScope.launch {
            achievementRepository.seedAchievements()
        }
    }

    private fun checkAchievements(entries: List<MoodEntry>) {
        viewModelScope.launch {
            val newlyUnlockedIds = achievementRepository.checkAndUnlock(
                entries, getApplication()
            )
            newlyUnlockedIds.forEach { id ->
                _newlyUnlocked.emit(id)
            }
        }
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

    fun saveReflection(mood: String, note: String, triggers: String? = null) {
        viewModelScope.launch {
            repository.saveMood(
                MoodEntry(
                    mood = mood,
                    smileScore = 0f,
                    eyeOpenScore = 0f,
                    note = note,
                    triggers = triggers
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
                checkAchievements(sorted)
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
        val stabilityDelta: Int = 0,
        val wellnessTip: WellnessRecommendation = WellnessRepository.getContextualTip("Neutral"),
        val smartAction: SmartActionState? = null
    )

    data class SmartActionState(
        val isBreatheMode: Boolean,
        val quoteText: String = "",
        val quoteAuthor: String = "",
        val title: String = "",
        val subtitle: String = "",
        val emoji: String = ""
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
                    trendBuckets = List(7) { 0 },
                    reflectionPrompt = buildReflectionPrompt("Neutral", emptyList()),
                    wellnessTip = buildWellnessTip(emptyList(), "Neutral"),
                    smartAction = buildSmartActionState(emptyList())
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
                reflectionPrompt = buildReflectionPrompt(dominantMood, entries),
                distribution = distribution,
                trendBuckets = buildTrendBuckets(entries),
                stabilityDelta = if (sourceEntries.isNotEmpty()) {
                    (((firstCount - secondCount).coerceAtLeast(0) * 100f) / sourceEntries.size).roundToInt()
                } else {
                    0
                },
                wellnessTip = buildWellnessTip(entries, dominantMood),
                smartAction = buildSmartActionState(entries)
            )
        }

        @JvmStatic
        @VisibleForTesting
        internal fun buildSmartActionState(entries: List<MoodEntry>): SmartActionState {
            val latestMood = entries.firstOrNull()?.mood ?: "Neutral"
            val repeatedTrigger = findRepeatedTrigger(entries)
            val streak = buildStreakState(entries)
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

            if (latestMood == "Stressed" || latestMood == "Tired") {
                val (title, subtitle) = when {
                    repeatedTrigger == "Work" -> "Work Reset" to "Take one calm minute before the next task."
                    repeatedTrigger == "Sleep" -> "Ease Into Rest" to "A slower breath now can make tonight gentler."
                    hour >= 21 || hour < 6 -> "Quiet Reset" to "Give your nervous system a softer landing."
                    else -> "Take a Breath" to "Recenter before the next thing asks for you."
                }
                return SmartActionState(
                    isBreatheMode = true,
                    title = title,
                    subtitle = subtitle,
                    emoji = MoodUtils.getEmoji(latestMood)
                )
            }

            if (latestMood == "Bored") {
                val body = when (repeatedTrigger) {
                    "Work" -> "You have checked in with work a lot lately. Change your input for ten minutes, then come back fresh."
                    "Social" -> "A quick message or short call might shift the tone of the day."
                    else -> "Break the autopilot. Pick one small thing that feels new, curious, or slightly playful."
                }
                return SmartActionState(
                    isBreatheMode = false,
                    title = "Change the Pace",
                    quoteText = body,
                    quoteAuthor = repeatedTrigger?.let { "Matched to your recent $it check-ins" }.orEmpty(),
                    emoji = MoodUtils.getEmoji(latestMood)
                )
            }

            if (latestMood == "Focused") {
                val body = when {
                    repeatedTrigger == "Work" -> "Your attention is lining up. Protect one 25-minute block and stay with a single task."
                    streak?.mood == "Focused" && streak.count >= 2 -> "This focus has been showing up for a few days. Capture what is making it possible."
                    else -> "You have some traction right now. Choose the next meaningful task before distractions choose for you."
                }
                return SmartActionState(
                    isBreatheMode = false,
                    title = "Protect This Focus",
                    quoteText = body,
                    quoteAuthor = if (repeatedTrigger == "Work") "Based on your recent work check-ins" else "",
                    emoji = MoodUtils.getEmoji(latestMood)
                )
            }

            if (latestMood == "Happy") {
                val body = when {
                    streak?.mood == "Happy" && streak.count >= 2 ->
                        "You have been feeling lighter for a few days. Write down what is helping so you can find it again."
                    repeatedTrigger != null ->
                        "There is something worth remembering here. Note what about $repeatedTrigger is supporting you."
                    else ->
                        "You are in a good pocket. Capture one thing that made today feel easier or brighter."
                }
                return SmartActionState(
                    isBreatheMode = false,
                    title = "Capture the Good",
                    quoteText = body,
                    quoteAuthor = "",
                    emoji = MoodUtils.getEmoji(latestMood)
                )
            }

            val body = when {
                repeatedTrigger == "Sleep" -> "Your recent notes keep circling sleep. A short reflection now may reveal what your energy needs."
                repeatedTrigger == "Health" -> "Health has been on your mind. Check in with your body before you push into the next part of the day."
                hour < 12 -> "Start with a simple read on yourself. What kind of day are you heading into?"
                hour < 18 -> "Pause for a clean midpoint check-in. What has shifted since this morning?"
                else -> "Let the day settle a little. What do you want to carry forward, and what can stay here?"
            }
            return SmartActionState(
                isBreatheMode = false,
                title = "Reflect for a Minute",
                quoteText = body,
                quoteAuthor = repeatedTrigger?.let { "Pattern spotted: $it" }.orEmpty(),
                emoji = MoodUtils.getEmoji(latestMood)
            )
        }

        @JvmStatic
        @VisibleForTesting
        internal fun buildReflectionPrompt(
            currentMood: String,
            recentEntries: List<MoodEntry>
        ): String {
            return PromptEngine.generatePrompts(
                currentMood = currentMood,
                recentEntries = recentEntries
            ).firstOrNull() ?: MoodUtils.getReflectionPrompt(currentMood)
        }

        @JvmStatic
        @VisibleForTesting
        internal fun buildWellnessTip(
            entries: List<MoodEntry>,
            fallbackMood: String = entries.firstOrNull()?.mood ?: "Neutral"
        ): WellnessRecommendation {
            val repeatedTrigger = findRepeatedTrigger(entries)
            val streak = buildStreakState(entries)
            val latestMood = entries.firstOrNull()?.mood ?: fallbackMood
            val moodForTip = when {
                latestMood == "Neutral" && repeatedTrigger == "Sleep" -> "Tired"
                latestMood == "Neutral" && repeatedTrigger == "Work" -> "Focused"
                else -> latestMood
            }

            return WellnessRepository.getContextualTip(
                mood = moodForTip,
                repeatedTrigger = repeatedTrigger,
                streakMood = streak?.mood,
                streakCount = streak?.count ?: 0,
                hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
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

        private fun findRepeatedTrigger(entries: List<MoodEntry>): String? {
            return entries.take(5)
                .mapNotNull { it.triggers }
                .flatMap { triggers -> triggers.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.takeIf { it.value >= 2 }
                ?.key
        }
    }
}
