package com.mirrormood.ui.report

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mirrormood.R
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.databinding.ActivityWeeklyReportBinding
import com.mirrormood.util.MoodUtils
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class WeeklyReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeeklyReportBinding

    @Inject lateinit var repository: MoodRepository

    private var cachedDominantMood = "Neutral"
    private var cachedDominantPercent = 0
    private var cachedTotalEntries = 0
    private var cachedBestDay = "--"

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityWeeklyReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            finish()
            slideTransition(forward = false)
        }

        binding.btnShareReport.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            shareReport()
        }

        loadReport()
    }

    private fun loadReport() {
        lifecycleScope.launch {
            val now = Calendar.getInstance()
            val endMs = now.timeInMillis
            val endDateStr = REPORT_DATE_FORMAT.format(now.time)

            now.add(Calendar.DAY_OF_YEAR, -7)
            now.set(Calendar.HOUR_OF_DAY, 0)
            now.set(Calendar.MINUTE, 0)
            now.set(Calendar.SECOND, 0)
            now.set(Calendar.MILLISECOND, 0)
            val startMs = now.timeInMillis
            val startDateStr = REPORT_DATE_FORMAT.format(now.time)

            binding.tvReportDateRange.text = getString(
                R.string.weekly_report_date_range,
                startDateStr,
                endDateStr
            )

            val entries = withContext(Dispatchers.IO) {
                repository.getMoodsForRange(startMs, endMs)
            }

            if (entries.isEmpty()) {
                renderEmptyReport()
                return@launch
            }

            binding.tvReportEmpty.visibility = View.GONE
            binding.cardReportHero.visibility = View.VISIBLE
            binding.tvMoodBreakdownTitle.visibility = View.VISIBLE
            binding.cardMoodBreakdown.visibility = View.VISIBLE
            binding.dayHighlightsContainer.visibility = View.VISIBLE
            binding.btnShareReport.isEnabled = true

            val moodCounts = entries.groupingBy { it.mood }.eachCount()
            val (dominantMood, dominantCount) = moodCounts.maxByOrNull { it.value } ?: return@launch
            val dominantPercent = (dominantCount * 100) / entries.size
            cachedDominantMood = dominantMood
            cachedDominantPercent = dominantPercent
            cachedTotalEntries = entries.size

            binding.tvReportEmoji.text = MoodUtils.getEmoji(dominantMood)
            binding.tvReportDominantMood.text = dominantMood
            binding.tvReportDominantPercent.text = getString(
                R.string.insights_percent_of_entries,
                dominantPercent
            )
            binding.tvReportTotalEntries.text = entries.size.toString()
            binding.tvReportAvgPerDay.text = getString(
                R.string.weekly_report_avg_per_day,
                entries.size / DAYS_IN_REPORT
            )

            renderMoodBreakdown(moodCounts, entries.size)
            renderDayHighlights(entries)
        }
    }

    private fun renderMoodBreakdown(moodCounts: Map<String, Int>, totalEntries: Int) {
        binding.reportBreakdownContainer.removeAllViews()

        moodCounts.entries.sortedByDescending { it.value }.forEach { (mood, count) ->
            val percent = (count * 100) / totalEntries
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_distribution_bar, binding.reportBreakdownContainer, false)

            row.findViewById<TextView>(R.id.tvMoodLabel)?.text =
                getString(R.string.weekly_report_mood_label, MoodUtils.getEmoji(mood), mood)
            row.findViewById<TextView>(R.id.tvMoodPercent)?.text =
                getString(R.string.dashboard_distribution_percent, percent)
            row.findViewById<LinearProgressIndicator>(R.id.progressMood)?.apply {
                progress = percent
                setIndicatorColor(ContextCompat.getColor(this@WeeklyReportActivity, MoodUtils.getColorRes(mood)))
            }

            binding.reportBreakdownContainer.addView(row)
        }
    }

    private fun renderDayHighlights(entries: List<MoodEntry>) {
        val dayBuckets = entries.groupBy { entry ->
            Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                .get(Calendar.DAY_OF_YEAR)
        }

        val bestDay = dayBuckets.maxByOrNull { (_, dayEntries) ->
            dayEntries.count { it.mood == "Happy" || it.mood == "Focused" }.toFloat() / dayEntries.size
        }
        bestDay?.value?.let { dayEntries ->
            val dominant = dayEntries.groupingBy { it.mood }.eachCount().maxByOrNull { it.value }?.key
            val dayName = DAY_FORMAT.format(Date(dayEntries.first().timestamp))
            cachedBestDay = dayName
            binding.tvBestDayEmoji.text = MoodUtils.getEmoji(dominant ?: "Happy")
            binding.tvBestDayLabel.text = dayName
            binding.tvBestDayDetail.text = getString(
                R.string.weekly_report_day_detail,
                dayEntries.size,
                dominant ?: "mixed"
            )
        }

        val toughDay = dayBuckets.maxByOrNull { (_, dayEntries) ->
            dayEntries.count { it.mood == "Stressed" || it.mood == "Tired" }.toFloat() / dayEntries.size
        }
        toughDay?.value?.let { dayEntries ->
            val dominant = dayEntries.groupingBy { it.mood }.eachCount().maxByOrNull { it.value }?.key
            val dayName = DAY_FORMAT.format(Date(dayEntries.first().timestamp))
            binding.tvToughDayEmoji.text = MoodUtils.getEmoji(dominant ?: "Stressed")
            binding.tvToughDayLabel.text = dayName
            binding.tvToughDayDetail.text = getString(
                R.string.weekly_report_day_detail,
                dayEntries.size,
                dominant ?: "mixed"
            )
        }
    }

    private fun renderEmptyReport() {
        binding.tvReportEmpty.visibility = View.VISIBLE
        binding.cardReportHero.visibility = View.GONE
        binding.tvMoodBreakdownTitle.visibility = View.GONE
        binding.cardMoodBreakdown.visibility = View.GONE
        binding.dayHighlightsContainer.visibility = View.GONE
        binding.btnShareReport.isEnabled = false
        binding.reportBreakdownContainer.removeAllViews()
        cachedDominantMood = "Neutral"
        cachedDominantPercent = 0
        cachedTotalEntries = 0
        cachedBestDay = "--"
    }

    private fun shareReport() {
        val shareText = getString(
            R.string.weekly_report_share_text,
            cachedDominantMood,
            cachedDominantPercent,
            cachedTotalEntries,
            cachedBestDay
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.weekly_report_title))
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.weekly_report_share)))
    }

    private companion object {
        const val DAYS_IN_REPORT = 7
        val REPORT_DATE_FORMAT = SimpleDateFormat("MMM dd", Locale.getDefault())
        val DAY_FORMAT = SimpleDateFormat("EEEE", Locale.getDefault())
    }
}
