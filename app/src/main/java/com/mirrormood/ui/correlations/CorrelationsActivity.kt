package com.mirrormood.ui.correlations

import android.os.Bundle
import com.mirrormood.util.ThemeHelper
import android.view.HapticFeedbackConstants
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.databinding.ActivityCorrelationsBinding
import com.mirrormood.util.MoodUtils
import com.mirrormood.util.MoodUtils.slideTransition
import kotlinx.coroutines.launch
import java.util.Calendar

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CorrelationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCorrelationsBinding
    private val viewModel: CorrelationsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCorrelationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge support
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            finish()
            slideTransition(forward = false)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.entries.collect { entries ->
                    analyzePatterns(entries)
                }
            }
        }
    }

    private fun analyzePatterns(entries: List<MoodEntry>) {
        if (entries.isEmpty()) {
            binding.tvTimePattern.text = "Not enough data yet. Keep tracking!"
            return
        }

        // 1. Time-of-day patterns
        val timePatterns = findTimePatterns(entries)
        binding.tvTimePattern.text = timePatterns

        // 2. Day-of-week patterns
        val dayPatterns = findDayPatterns(entries)
        binding.tvDayPattern.text = dayPatterns

        // 3. Morning vs Afternoon vs Evening
        val periodPatterns = findPeriodPatterns(entries)
        binding.tvPeriodPattern.text = periodPatterns

        // 4. Summary stats
        val totalDays = entries.groupBy { entry ->
            Calendar.getInstance().apply {
                timeInMillis = entry.timestamp
            }.get(Calendar.DAY_OF_YEAR)
        }.size
        binding.tvDataSummary.text = "${entries.size} readings across $totalDays days"
    }

    private fun findTimePatterns(entries: List<MoodEntry>): String {
        val hourMoods = entries.groupBy { entry ->
            Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                .get(Calendar.HOUR_OF_DAY)
        }

        val happyHours = hourMoods.mapValues { (_, es) ->
            es.count { it.mood == "Happy" }.toFloat() / es.size
        }.maxByOrNull { it.value }

        val stressedHours = hourMoods.mapValues { (_, es) ->
            es.count { it.mood == "Stressed" }.toFloat() / es.size
        }.maxByOrNull { it.value }

        val lines = mutableListOf<String>()
        happyHours?.let {
            if (it.value > 0.2f) lines.add("😊 You're happiest around ${MoodUtils.formatHour(it.key)}")
        }
        stressedHours?.let {
            if (it.value > 0.2f) lines.add("😰 You tend to feel stressed around ${MoodUtils.formatHour(it.key)}")
        }

        val focusedHours = hourMoods.mapValues { (_, es) ->
            es.count { it.mood == "Focused" }.toFloat() / es.size
        }.maxByOrNull { it.value }
        focusedHours?.let {
            if (it.value > 0.2f) lines.add("🧠 Peak focus time: ${MoodUtils.formatHour(it.key)}")
        }

        return if (lines.isEmpty()) "No clear time patterns yet" else lines.joinToString("\n")
    }

    private fun findDayPatterns(entries: List<MoodEntry>): String {
        val dayNames = arrayOf("", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val dayMoods = entries.groupBy { entry ->
            Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                .get(Calendar.DAY_OF_WEEK)
        }

        val happiestDay = dayMoods.mapValues { (_, es) ->
            es.count { it.mood == "Happy" }.toFloat() / es.size
        }.maxByOrNull { it.value }

        val stressedDay = dayMoods.mapValues { (_, es) ->
            es.count { it.mood == "Stressed" }.toFloat() / es.size
        }.maxByOrNull { it.value }

        val lines = mutableListOf<String>()
        happiestDay?.let {
            lines.add("😊 ${dayNames[it.key]}s are your happiest days")
        }
        stressedDay?.let {
            if (it.key != happiestDay?.key) {
                lines.add("😰 ${dayNames[it.key]}s tend to be more stressful")
            }
        }

        return if (lines.isEmpty()) "No clear day patterns yet" else lines.joinToString("\n")
    }

    private fun findPeriodPatterns(entries: List<MoodEntry>): String {
        val periods = entries.groupBy { entry ->
            val hour = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                .get(Calendar.HOUR_OF_DAY)
            when {
                hour in 5..11 -> "Morning"
                hour in 12..16 -> "Afternoon"
                hour in 17..20 -> "Evening"
                else -> "Night"
            }
        }

        val periodDominant = periods.mapValues { (_, es) ->
            es.groupBy { it.mood }.maxByOrNull { it.value.size }?.key ?: "Neutral"
        }

        val emojis = mapOf("Happy" to "😊", "Stressed" to "😰", "Tired" to "😴",
            "Focused" to "🧠", "Bored" to "😒", "Neutral" to "😐")

        return periodDominant.entries.joinToString("\n") { (period, mood) ->
            "${emojis[mood] ?: "😐"} $period: mostly $mood"
        }
    }
}
