package com.mirrormood.ui.history

import android.os.Bundle
import com.mirrormood.util.ThemeHelper
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.GridLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mirrormood.R
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.databinding.ActivityHistoryBinding
import com.mirrormood.util.MoodUtils
import com.mirrormood.util.MoodUtils.slideTransition
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: HistoryViewModel by viewModels()

    private val displayedMonth = Calendar.getInstance()
    private val today = Calendar.getInstance()
    private var selectedDay: Int = -1

    private var currentMonthData: Map<Int, List<MoodEntry>> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
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

        binding.btnPrevMonth.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            displayedMonth.add(Calendar.MONTH, -1)
            selectedDay = -1
            requestMonthData()
        }
        binding.btnNextMonth.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            displayedMonth.add(Calendar.MONTH, 1)
            selectedDay = -1
            requestMonthData()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.monthData.collect { data ->
                    currentMonthData = data
                    buildCalendarGrid()
                }
            }
        }

        requestMonthData()
    }

    private fun requestMonthData() {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        binding.tvMonthYear.text = monthFormat.format(displayedMonth.time)

        binding.cardDayDetail.visibility = View.GONE
        binding.cardNoData.visibility = View.GONE

        val cal = displayedMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.timeInMillis

        cal.add(Calendar.MONTH, 1)
        val endOfMonth = cal.timeInMillis

        viewModel.loadMonthData(startOfMonth, endOfMonth)
    }

    // Cache day views to avoid rebuilding entire grid on selection change
    private val dayViews = mutableListOf<CalendarDayView>()
    private var cachedFirstDayOfWeek = 0
    private var cachedDaysInMonth = 0
    private var cachedTodayDay = -1

    private fun buildCalendarGrid() {
        binding.calendarGrid.removeAllViews()
        dayViews.clear()

        val cal = displayedMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)

        cachedFirstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
        cachedDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val isCurrentMonthYear =
            displayedMonth.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            displayedMonth.get(Calendar.MONTH) == today.get(Calendar.MONTH)
        cachedTodayDay = if (isCurrentMonthYear) today.get(Calendar.DAY_OF_MONTH) else -1

        val prevCal = cal.clone() as Calendar
        prevCal.add(Calendar.MONTH, -1)
        val daysInPrevMonth = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val totalCells = 42

        for (i in 0 until totalCells) {
            val dayView = CalendarDayView(this)

            when {
                i < cachedFirstDayOfWeek -> {
                    val prevDay = daysInPrevMonth - cachedFirstDayOfWeek + i + 1
                    dayView.bind(prevDay, currentMonth = false, today = false, dominantMood = null, selected = false)
                }
                i < cachedFirstDayOfWeek + cachedDaysInMonth -> {
                    val day = i - cachedFirstDayOfWeek + 1
                    val dayEntries = currentMonthData[day]
                    val dominant = dayEntries?.groupBy { it.mood }
                        ?.maxByOrNull { it.value.size }?.key
                    val isSelectedDay = day == selectedDay
                    val isTodayCell = day == cachedTodayDay

                    dayView.bind(day, currentMonth = true, today = isTodayCell, dominantMood = dominant, selected = isSelectedDay)
                    dayView.setOnClickListener { onDayClicked(day) }
                }
                else -> {
                    val nextDay = i - cachedFirstDayOfWeek - cachedDaysInMonth + 1
                    dayView.bind(nextDay, currentMonth = false, today = false, dominantMood = null, selected = false)
                }
            }

            val gridParam = GridLayout.LayoutParams().apply {
                columnSpec = GridLayout.spec(i % 7, 1f)
                rowSpec = GridLayout.spec(i / 7)
                width = 0
            }
            binding.calendarGrid.addView(dayView, gridParam)
            dayViews.add(dayView)
        }
    }

    private fun onDayClicked(day: Int) {
        val prevSelected = selectedDay
        selectedDay = day

        // Update only the affected cells instead of rebuilding the entire grid
        if (dayViews.isNotEmpty() && cachedDaysInMonth > 0) {
            // Rebind the previously selected day
            if (prevSelected in 1..cachedDaysInMonth) {
                val prevIndex = cachedFirstDayOfWeek + prevSelected - 1
                if (prevIndex in dayViews.indices) {
                    val prevView = dayViews[prevIndex]
                    val prevEntries = currentMonthData[prevSelected]
                    val prevDominant = prevEntries?.groupBy { it.mood }
                        ?.maxByOrNull { it.value.size }?.key
                    prevView.bind(prevSelected, currentMonth = true, today = prevSelected == cachedTodayDay, dominantMood = prevDominant, selected = false)
                }
            }
            // Rebind the newly selected day
            if (day in 1..cachedDaysInMonth) {
                val newIndex = cachedFirstDayOfWeek + day - 1
                if (newIndex in dayViews.indices) {
                    val newView = dayViews[newIndex]
                    val newEntries = currentMonthData[day]
                    val newDominant = newEntries?.groupBy { it.mood }
                        ?.maxByOrNull { it.value.size }?.key
                    newView.bind(day, currentMonth = true, today = day == cachedTodayDay, dominantMood = newDominant, selected = true)
                }
            }
        }

        val entries = currentMonthData[day]
        if (entries.isNullOrEmpty()) {
            binding.cardDayDetail.visibility = View.GONE
            binding.cardNoData.visibility = View.VISIBLE
            binding.cardNoData.alpha = 0f
            binding.cardNoData.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
            return
        }

        binding.cardNoData.visibility = View.GONE
        binding.cardDayDetail.visibility = View.VISIBLE

        binding.cardDayDetail.alpha = 0f
        binding.cardDayDetail.translationY = 50f
        binding.cardDayDetail.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .start()

        val dateCal = displayedMonth.clone() as Calendar
        dateCal.set(Calendar.DAY_OF_MONTH, day)
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        binding.tvDetailDate.text = dateFormat.format(dateCal.time)

        val total = entries.size
        binding.tvDetailEntryCount.text = "$total mood ${if (total == 1) "entry" else "entries"}"

        val moodCounts = entries.groupBy { it.mood }
        val happyCount = moodCounts["Happy"]?.size ?: 0
        val neutralCount = moodCounts["Neutral"]?.size ?: 0
        val stressedCount = moodCounts["Stressed"]?.size ?: 0
        val tiredCount = moodCounts["Tired"]?.size ?: 0
        val focusedCount = moodCounts["Focused"]?.size ?: 0
        val boredCount = moodCounts["Bored"]?.size ?: 0

        binding.tvDetailHappy.text = "${happyCount * 100 / total}%"
        binding.tvDetailNeutral.text = "${neutralCount * 100 / total}%"
        binding.tvDetailStressed.text = "${stressedCount * 100 / total}%"
        binding.tvDetailTired.text = "${tiredCount * 100 / total}%"
        binding.tvDetailFocused.text = "${focusedCount * 100 / total}%"
        binding.tvDetailBored.text = "${boredCount * 100 / total}%"

        val dominant = moodCounts.maxByOrNull { it.value.size }?.key ?: "Neutral"
        val emoji = MoodUtils.getEmoji(dominant)
        binding.tvDetailDominant.text = "$emoji $dominant"
    }
}
