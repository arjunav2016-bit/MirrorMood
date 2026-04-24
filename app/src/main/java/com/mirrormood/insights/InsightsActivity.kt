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
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import com.mirrormood.util.MilestoneEngine
import com.mirrormood.util.MilestoneAdapter

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InsightsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInsightsBinding

    @Inject lateinit var repository: MoodRepository

    private var currentTab = Tab.TODAY
    private val milestoneAdapter = MilestoneAdapter()

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
        
        binding.rvMilestones.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvMilestones.adapter = milestoneAdapter
        
        BottomNavHelper.setup(this, BottomNavTab.INSIGHTS)
        loadTab(Tab.TODAY)
        loadMilestones()
        playEntranceAnimations()
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

        binding.cardWeeklyReport.setOnClickListener {
            val intent = Intent(this, com.mirrormood.ui.report.WeeklyReportActivity::class.java).apply {
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
                binding.cardWeekComparison.visibility = View.GONE
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
                    binding.tvDominantPercent.text = getString(R.string.insights_start_monitoring)
                    binding.tvTotalEntries.text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(calendar.time)
                    binding.moodBreakdownContainer.removeAllViews()
                    return@collect
                }
                renderBreakdown(entries, "today")
            }
        }
    }

    private fun loadMilestones() {
        lifecycleScope.launch {
            val allEntries = withContext(Dispatchers.IO) {
                repository.getAllMoodEntries()
            }
            val milestones = MilestoneEngine.generateMilestones(allEntries)
            milestoneAdapter.submitList(milestones)
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
                binding.tvDominantPercent.text = getString(R.string.insights_no_week_data)
                binding.tvTotalEntries.text = getString(R.string.insights_zero_readings)
                binding.moodBreakdownContainer.removeAllViews()
                binding.cardWeekComparison.visibility = View.GONE
                return@launch
            }

            renderBreakdown(entries, "this week")

            // Week-over-week comparison
            val lastWeekEnd = startMs
            val lastWeekCal = Calendar.getInstance().apply { timeInMillis = lastWeekEnd }
            lastWeekCal.add(Calendar.DAY_OF_YEAR, -7)
            val lastWeekStart = lastWeekCal.timeInMillis

            val lastWeekEntries = withContext(Dispatchers.IO) {
                repository.getMoodsForRange(lastWeekStart, lastWeekEnd)
            }

            if (lastWeekEntries.size < 3) {
                binding.cardWeekComparison.visibility = View.GONE
                return@launch
            }

            binding.cardWeekComparison.visibility = View.VISIBLE

            // Compute happy % and stress % for both weeks
            val thisHappyPct = if (entries.isNotEmpty()) (entries.count { it.mood == "Happy" } * 100) / entries.size else 0
            val thisStressPct = if (entries.isNotEmpty()) (entries.count { it.mood == "Stressed" } * 100) / entries.size else 0
            val lastHappyPct = (lastWeekEntries.count { it.mood == "Happy" } * 100) / lastWeekEntries.size
            val lastStressPct = (lastWeekEntries.count { it.mood == "Stressed" } * 100) / lastWeekEntries.size

            val happyDelta = thisHappyPct - lastHappyPct
            val stressDelta = thisStressPct - lastStressPct

            binding.tvCompHappyDelta.text = when {
                happyDelta > 0 -> getString(R.string.insights_comparison_more, happyDelta)
                happyDelta < 0 -> getString(R.string.insights_comparison_less, happyDelta)
                else -> "→ 0%"
            }
            binding.tvCompHappyDelta.setTextColor(ContextCompat.getColor(this@InsightsActivity,
                if (happyDelta >= 0) R.color.mm_mood_happy else R.color.mm_mood_stressed
            ))

            binding.tvCompStressDelta.text = when {
                stressDelta > 0 -> getString(R.string.insights_comparison_more, stressDelta)
                stressDelta < 0 -> getString(R.string.insights_comparison_less, stressDelta)
                else -> "→ 0%"
            }
            binding.tvCompStressDelta.setTextColor(ContextCompat.getColor(this@InsightsActivity,
                if (stressDelta <= 0) R.color.mm_mood_happy else R.color.mm_mood_stressed
            ))

            // Overall trend summary
            binding.tvCompTrend.text = when {
                happyDelta > 5 && stressDelta < 0 -> getString(R.string.insights_comparison_improved)
                happyDelta < -5 || stressDelta > 5 -> getString(R.string.insights_comparison_declined)
                else -> getString(R.string.insights_comparison_stable)
            }
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
        binding.tvDominantPercent.text = getString(R.string.insights_dominant_percent, dominantPercent)
        binding.tvDominantSummary.text = getString(R.string.insights_dominant_summary, periodLabel)

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
            getString(R.string.insights_happiest_at, MoodUtils.formatHour(Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)))
        } ?: getString(R.string.insights_no_peak)

        val stressedEntry = moodCounts["Stressed"]?.maxByOrNull { entry ->
            Calendar.getInstance().apply { timeInMillis = entry.timestamp }.get(Calendar.HOUR_OF_DAY)
        }
        binding.tvPeakActivity.text = stressedEntry?.let {
            getString(R.string.insights_stressed_at, MoodUtils.formatHour(Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)))
        } ?: getString(R.string.insights_no_stress_data)

        binding.tvConfidence.text = if (total > 5) getString(R.string.insights_confidence_high) else getString(R.string.insights_confidence_building)
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
    private fun playEntranceAnimations() {
        val cards = listOf(
            binding.dominantMoodCard,
            binding.cardPatterns,
            binding.cardHistory
        )
        val offsetPx = (40 * resources.displayMetrics.density)
        cards.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = offsetPx
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 80).toLong())
                .setDuration(450)
                .setInterpolator(DecelerateInterpolator(1.8f))
                .start()
        }
    }
}
