package com.mirrormood.ui.insights

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mirrormood.R
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.databinding.ActivityInsightsBinding
import javax.inject.Inject
import com.mirrormood.util.BottomNavHelper
import com.mirrormood.util.BottomNavTab
import com.mirrormood.util.MoodUtils
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InsightsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInsightsBinding

    @Inject lateinit var repository: MoodRepository

    private var currentTab = Tab.TODAY

    private enum class Tab { TODAY, WEEK }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityInsightsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            finish()
            slideTransition(forward = false)
        }

        setupTabs()
        setupNavigationCards()
        BottomNavHelper.setup(this, BottomNavTab.INSIGHTS)
        loadTab(Tab.TODAY)
    }

    private fun setupNavigationCards() {
        binding.cardPatterns.setOnClickListener {
            val intent = Intent(this, com.mirrormood.ui.correlations.CorrelationsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            slideTransition(forward = true)
        }

        binding.cardHistory.setOnClickListener {
            val intent = Intent(this, com.mirrormood.ui.history.HistoryActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            slideTransition(forward = true)
        }
    }

    // ── Tab Switching ─────────────────────────────────────────────

    private fun setupTabs() {
        binding.togglePeriod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnToday -> loadTab(Tab.TODAY)
                    R.id.btnWeek -> loadTab(Tab.WEEK)
                }
            }
        }
        binding.btnToday.isChecked = true
    }

    private fun loadTab(tab: Tab) {
        currentTab = tab
        when (tab) {
            Tab.TODAY -> {
                loadTodayInsights()
                loadTodayChart()
            }
            Tab.WEEK -> {
                loadWeekInsights()
                loadWeeklyChart()
            }
        }
    }

    // ── Today View ────────────────────────────────────────────────

    private fun loadTodayInsights() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val endOfDay = startOfDay + (24 * 60 * 60 * 1000)

        lifecycleScope.launch {
            repository.getMoodsForDay(startOfDay, endOfDay).collect { entries ->
                if (entries.isEmpty()) {
                    binding.tvDominantMoodLabel.text = getString(R.string.insights_no_data)
                    binding.tvDominantEmoji.text = "😐"
                    binding.tvDominantPercent.text = "Start monitoring to see insights"
                    binding.tvTotalEntries.text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(calendar.time)
                    binding.moodBreakdownContainer.removeAllViews()
                    return@collect
                }
                renderBreakdown(entries, "today")
            }
        }
    }

    private fun loadTodayChart() {
        lifecycleScope.launch {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            val endOfDay = startOfDay + (24 * 60 * 60 * 1000)

            val entries = withContext(Dispatchers.IO) {
                repository.getMoodsForRange(startOfDay, endOfDay)
            }

            if (entries.isEmpty()) {
                binding.chartMood.setNoDataText(getString(R.string.insights_no_chart_data))
                binding.chartMood.invalidate()
                return@launch
            }

            val moods = listOf("Happy", "Neutral", "Stressed", "Tired", "Focused", "Bored")
            val blockLabels = arrayOf("Morning", "Afternoon", "Evening", "Night")

            // Prepare chart data off-main-thread
            val barEntries = withContext(Dispatchers.Default) {
                val blockEntries = entries.groupBy { entry ->
                    val hour = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                        .get(Calendar.HOUR_OF_DAY)
                    when {
                        hour < 6 -> 3
                        hour < 12 -> 0
                        hour < 18 -> 1
                        else -> 2
                    }
                }

                val result = mutableListOf<BarEntry>()
                for (block in 0..3) {
                    val blockData = blockEntries[block] ?: emptyList()
                    val counts = moods.map { mood -> blockData.count { it.mood == mood }.toFloat() }.toFloatArray()
                    result.add(BarEntry(block.toFloat(), counts))
                }
                result
            }

            renderChart(barEntries, moods, blockLabels)
        }
    }

    // ── Week View ─────────────────────────────────────────────────

    private fun loadWeekInsights() {
        lifecycleScope.launch {
            val now = Calendar.getInstance()
            val endMs = now.timeInMillis
            now.add(Calendar.DAY_OF_YEAR, -7)
            now.set(Calendar.HOUR_OF_DAY, 0)
            now.set(Calendar.MINUTE, 0)
            now.set(Calendar.SECOND, 0)
            val startMs = now.timeInMillis

            val entries = withContext(Dispatchers.IO) {
                repository.getMoodsForRange(startMs, endMs)
            }

            if (entries.isEmpty()) {
                binding.tvDominantMoodLabel.text = getString(R.string.insights_no_data)
                binding.tvDominantEmoji.text = "😐"
                binding.tvDominantPercent.text = "No data from the past 7 days"
                binding.tvTotalEntries.text = "0 readings"
                binding.moodBreakdownContainer.removeAllViews()
                return@launch
            }

            renderBreakdown(entries, "this week")
        }
    }

    private fun loadWeeklyChart() {
        lifecycleScope.launch {
            val now = Calendar.getInstance()
            val endMs = now.timeInMillis
            now.add(Calendar.DAY_OF_YEAR, -7)
            now.set(Calendar.HOUR_OF_DAY, 0)
            now.set(Calendar.MINUTE, 0)
            now.set(Calendar.SECOND, 0)
            now.set(Calendar.MILLISECOND, 0)
            val startMs = now.timeInMillis

            val entries = withContext(Dispatchers.IO) {
                repository.getMoodsForRange(startMs, endMs)
            }

            if (entries.isEmpty()) {
                binding.chartMood.setNoDataText(getString(R.string.insights_no_chart_data))
                binding.chartMood.invalidate()
                return@launch
            }

            val moods = listOf("Happy", "Neutral", "Stressed", "Tired", "Focused", "Bored")
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

            // Prepare chart data off-main-thread
            data class ChartPrep(val barEntries: List<BarEntry>, val dayLabels: Array<String>)
            val prep = withContext(Dispatchers.Default) {
                val dayLabels = mutableListOf<String>()
                val cal = Calendar.getInstance().apply { timeInMillis = startMs }
                val barEntries = mutableListOf<BarEntry>()
                for (day in 0..6) {
                    val dayStart = cal.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    val dayEnd = cal.timeInMillis

                    dayLabels.add(dayFormat.format(java.util.Date(dayStart)))

                    val dayData = entries.filter { it.timestamp in dayStart until dayEnd }
                    val counts = moods.map { mood -> dayData.count { it.mood == mood }.toFloat() }.toFloatArray()
                    barEntries.add(BarEntry(day.toFloat(), counts))
                }
                ChartPrep(barEntries, dayLabels.toTypedArray())
            }

            renderChart(prep.barEntries, moods, prep.dayLabels)
        }
    }

    // ── Shared Rendering ──────────────────────────────────────────

    private fun renderBreakdown(entries: List<MoodEntry>, periodLabel: String) {
        val total = entries.size
        binding.tvTotalEntries.text = resources.getQuantityString(
            R.plurals.insights_total_entries,
            total,
            total
        )

        val moodCounts = entries.groupBy { it.mood }
        val dominant = moodCounts.maxByOrNull { it.value.size }?.key ?: "Neutral"
        val dominantPercent = (moodCounts[dominant]?.size ?: 0) * 100 / total

        binding.tvDominantEmoji.text = MoodUtils.getEmoji(dominant)
        binding.tvDominantMoodLabel.text = dominant
        binding.tvDominantPercent.text = "$dominantPercent% of entries"
        binding.tvDominantSummary.text = "Your dominant mood $periodLabel"

        // Build mood breakdown dynamically in the container
        val container = binding.moodBreakdownContainer
        container.removeAllViews()
        container.layoutAnimation = android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_slide_up)

        val inflater = LayoutInflater.from(this)
        val moodColors = mapOf(
            "Happy" to R.color.mm_mood_happy,
            "Focused" to R.color.mm_mood_focused,
            "Neutral" to R.color.mm_mood_neutral,
            "Stressed" to R.color.mm_mood_stressed,
            "Tired" to R.color.mm_mood_tired,
            "Bored" to R.color.mm_mood_bored
        )

        val sorted = moodCounts.entries.sortedByDescending { it.value.size }
        sorted.forEach { (mood, list) ->
            val percent = list.size * 100 / total
            val row = inflater.inflate(R.layout.item_distribution_bar, container, false)
            row.findViewById<TextView>(R.id.tvMoodLabel).text = "${MoodUtils.getEmoji(mood)} $mood"
            row.findViewById<TextView>(R.id.tvMoodPercent).text = "$percent%"
            row.findViewById<LinearProgressIndicator>(R.id.progressMood).apply {
                progress = percent
                setIndicatorColor(ContextCompat.getColor(this@InsightsActivity, moodColors[mood] ?: R.color.mm_primary))
            }
            container.addView(row)
        }
        container.scheduleLayoutAnimation()

        // Peak time
        val happiestEntry = moodCounts["Happy"]?.maxByOrNull { entry ->
            Calendar.getInstance().apply { timeInMillis = entry.timestamp }.get(Calendar.HOUR_OF_DAY)
        }
        binding.tvPeakTimeLabel.text = happiestEntry?.let {
            "Happiest at ${MoodUtils.formatHour(Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY))}"
        } ?: "Most active: --"

        val stressedEntry = moodCounts["Stressed"]?.maxByOrNull { entry ->
            Calendar.getInstance().apply { timeInMillis = entry.timestamp }.get(Calendar.HOUR_OF_DAY)
        }
        binding.tvPeakActivity.text = stressedEntry?.let {
            "Most stressed at ${MoodUtils.formatHour(Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY))}"
        } ?: "No data yet"

        binding.tvConfidence.text = "Detection confidence: ${if (total > 5) "High" else "Building..."}"
    }

    private fun renderChart(barEntries: List<BarEntry>, moods: List<String>, labels: Array<String>) {
        val barChart: BarChart = binding.chartMood
        val colors = listOf(
            ContextCompat.getColor(this, R.color.mm_mood_happy),
            ContextCompat.getColor(this, R.color.mm_mood_neutral),
            ContextCompat.getColor(this, R.color.mm_mood_stressed),
            ContextCompat.getColor(this, R.color.mm_mood_tired),
            ContextCompat.getColor(this, R.color.mm_mood_focused),
            ContextCompat.getColor(this, R.color.mm_mood_bored)
        )

        val dataSet = BarDataSet(barEntries, "").apply {
            setColors(colors)
            stackLabels = moods.toTypedArray()
            setDrawValues(false)
        }

        val textColor = ContextCompat.getColor(this, R.color.mm_on_surface_secondary)
        val gridColor = ContextCompat.getColor(this, R.color.mm_surface_container_high)

        barChart.apply {
            data = BarData(dataSet).apply { barWidth = 0.55f }
            setNoDataTextColor(textColor)
            setTouchEnabled(false)
            description.isEnabled = false
            setDrawGridBackground(false)
            setFitBars(true)
            legend.textColor = textColor
            legend.textSize = 10f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                this.textColor = textColor
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(labels)
            }

            axisLeft.apply {
                this.textColor = textColor
                this.gridColor = gridColor
                axisMinimum = 0f
            }

            axisRight.isEnabled = false
            animateY(1200, com.github.mikephil.charting.animation.Easing.EaseInOutQuart)
            invalidate()
        }
    }
}
