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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class WeeklyReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeeklyReportBinding

    @Inject lateinit var repository: MoodRepository

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
            val endDateStr = SimpleDateFormat("MMM dd", Locale.getDefault()).format(now.time)

            now.add(Calendar.DAY_OF_YEAR, -7)
            now.set(Calendar.HOUR_OF_DAY, 0)
            now.set(Calendar.MINUTE, 0)
            now.set(Calendar.SECOND, 0)
            val startMs = now.timeInMillis
            val startDateStr = SimpleDateFormat("MMM dd", Locale.getDefault()).format(now.time)

            binding.tvReportDateRange.text = "$startDateStr – $endDateStr"

            val entries = withContext(Dispatchers.IO) {
                repository.getMoodsForRange(startMs, endMs)
            }

            if (entries.isEmpty()) {
                binding.tvReportEmpty.visibility = View.VISIBLE
                binding.cardReportHero.visibility = View.GONE
                binding.btnShareReport.isEnabled = false
                return@launch
            }

            binding.tvReportEmpty.visibility = View.GONE
            binding.cardReportHero.visibility = View.VISIBLE

            // --- Hero card ---
            val moodCounts = entries.groupingBy { it.mood }.eachCount()
            val (dominantMood, dominantCount) = moodCounts.maxByOrNull { it.value } ?: return@launch
            val dominantPercent = (dominantCount * 100) / entries.size

            binding.tvReportEmoji.text = MoodUtils.getEmoji(dominantMood)
            binding.tvReportDominantMood.text = dominantMood
            binding.tvReportDominantPercent.text = getString(R.string.insights_percent_of_entries, dominantPercent)
            binding.tvReportTotalEntries.text = entries.size.toString()
            binding.tvReportAvgPerDay.text = "~${entries.size / 7}"

            // --- Mood breakdown ---
            val sortedMoods = moodCounts.entries.sortedByDescending { it.value }
            binding.reportBreakdownContainer.removeAllViews()
            for ((mood, count) in sortedMoods) {
                val percent = (count * 100) / entries.size
                val row = LayoutInflater.from(this@WeeklyReportActivity)
                    .inflate(R.layout.item_distribution_bar, binding.reportBreakdownContainer, false)

                row.findViewById<TextView>(R.id.tvMoodLabel)?.text =
                    "${MoodUtils.getEmoji(mood)} $mood"
                row.findViewById<TextView>(R.id.tvMoodPercent)?.text = "$percent%"
                row.findViewById<LinearProgressIndicator>(R.id.progressMood)?.apply {
                    progress = percent
                    setIndicatorColor(ContextCompat.getColor(this@WeeklyReportActivity, MoodUtils.getColorRes(mood)))
                }

                binding.reportBreakdownContainer.addView(row)
            }

            // --- Best & Toughest days ---
            val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
            val dayBuckets = entries.groupBy { entry ->
                val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                cal.get(Calendar.DAY_OF_YEAR)
            }

            // Best day: highest ratio of Happy/Focused entries
            val bestDay = dayBuckets.maxByOrNull { (_, dayEntries) ->
                dayEntries.count { it.mood == "Happy" || it.mood == "Focused" }.toFloat() / dayEntries.size
            }
            if (bestDay != null) {
                val bestDayEntries = bestDay.value
                val bestDominant = bestDayEntries.groupingBy { it.mood }.eachCount().maxByOrNull { it.value }
                val dayName = dayFormat.format(java.util.Date(bestDayEntries.first().timestamp))
                binding.tvBestDayEmoji.text = MoodUtils.getEmoji(bestDominant?.key ?: "Happy")
                binding.tvBestDayLabel.text = dayName
                binding.tvBestDayDetail.text = "${bestDayEntries.size} entries, mostly ${bestDominant?.key ?: "mixed"}"
            }

            // Toughest day: highest ratio of Stressed/Tired entries
            val toughDay = dayBuckets.maxByOrNull { (_, dayEntries) ->
                dayEntries.count { it.mood == "Stressed" || it.mood == "Tired" }.toFloat() / dayEntries.size
            }
            if (toughDay != null) {
                val toughDayEntries = toughDay.value
                val toughDominant = toughDayEntries.groupingBy { it.mood }.eachCount().maxByOrNull { it.value }
                val dayName = dayFormat.format(java.util.Date(toughDayEntries.first().timestamp))
                binding.tvToughDayEmoji.text = MoodUtils.getEmoji(toughDominant?.key ?: "Stressed")
                binding.tvToughDayLabel.text = dayName
                binding.tvToughDayDetail.text = "${toughDayEntries.size} entries, mostly ${toughDominant?.key ?: "mixed"}"
            }
        }
    }

    private var cachedDominantMood = "Neutral"
    private var cachedDominantPercent = 0
    private var cachedTotalEntries = 0
    private var cachedBestDay = "--"

    private fun shareReport() {
        val shareText = getString(
            R.string.weekly_report_share_text,
            binding.tvReportDominantMood.text.toString(),
            cachedDominantPercent,
            cachedTotalEntries,
            binding.tvBestDayLabel.text.toString()
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.weekly_report_title))
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.weekly_report_share)))
    }
}
