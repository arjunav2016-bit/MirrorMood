package com.mirrormood

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.animation.PathInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.color.DynamicColors
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.data.repository.WellnessRepository
import com.mirrormood.databinding.ActivityMainBinding
import com.mirrormood.notification.MoodNotificationManager
import com.mirrormood.notification.NotificationScheduler
import com.mirrormood.service.MoodMonitorService
import com.mirrormood.ui.journal.JournalActivity
import com.mirrormood.ui.main.MainViewModel
import com.mirrormood.ui.recommendations.RecommendationsActivity
import com.mirrormood.ui.settings.SettingsActivity
import com.mirrormood.ui.timeline.TimelineActivity
import com.mirrormood.util.BottomNavHelper
import com.mirrormood.util.BottomNavTab
import com.mirrormood.util.MoodUtils
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private var selectedMood: String = "Neutral"
    private var isQuickComposerExpanded: Boolean = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startMonitoring()
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        MoodNotificationManager.createNotificationChannel(this)
        NotificationScheduler.schedule(this)

        setupQuickComposer()
        setupClickListeners()
        observeViewModel()
        BottomNavHelper.setup(this, BottomNavTab.HOME)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshMonitoringFromPrefs()
    }

    private fun setupQuickComposer() {
        // M3 Chips use ChipGroup with checked state — map chip id to mood name
        val chipMoodMap = mapOf(
            R.id.chipHappy to "Happy",
            R.id.chipNeutral to "Neutral",
            R.id.chipFocused to "Focused",
            R.id.chipTired to "Tired",
            R.id.chipStressed to "Stressed",
            R.id.chipBored to "Bored"
        )

        chipMoodMap.forEach { (chipId, mood) ->
            findViewById<Chip>(chipId)?.setOnClickListener {
                selectedMood = mood
            }
        }

        binding.quickNoteHeader.setOnClickListener {
            setQuickComposerExpanded(!isQuickComposerExpanded)
        }

        // Default selection
        binding.chipNeutral.isChecked = true
        selectedMood = "Neutral"
        setQuickComposerExpanded(false)
    }

    private fun setupClickListeners() {
        with(binding) {
            btnToggle.setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                if (viewModel.isMonitoring.value) stopMonitoring() else checkPermissionsAndStart()
            }

            btnSettings.setOnClickListener { navigateTo(SettingsActivity::class.java) }
            btnSaveEntry.setOnClickListener { saveQuickEntry() }
            btnViewTimeline.setOnClickListener { navigateTo(TimelineActivity::class.java) }
            btnOpenJournal.setOnClickListener { navigateTo(JournalActivity::class.java) }

            wellnessCard.setOnClickListener {
                openAdvice(viewModel.homeUiState.value.dominantMood)
            }
            btnOpenAdvice.setOnClickListener {
                openAdvice(viewModel.homeUiState.value.dominantMood)
            }
            btnViewAdvice.setOnClickListener {
                openAdvice(viewModel.homeUiState.value.dominantMood)
            }
        }
    }

    private fun saveQuickEntry() {
        val note = binding.etQuickNote.text?.toString()?.trim().orEmpty()
        if (note.isBlank()) {
            setQuickComposerExpanded(true)
            Toast.makeText(this, R.string.journal_save_prompt, Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.saveReflection(selectedMood, note)
        binding.etQuickNote.text?.clear()
        setQuickComposerExpanded(false)
        Toast.makeText(this, R.string.journal_saved, Toast.LENGTH_SHORT).show()
    }

    private fun openAdvice(mood: String) {
        val intent = Intent(this, RecommendationsActivity::class.java).apply {
            putExtra(RecommendationsActivity.EXTRA_MOOD, mood)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        slideTransition(forward = true)
    }

    private fun <T> navigateTo(activityClass: Class<T>) {
        binding.root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        val intent = Intent(this, activityClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        slideTransition(forward = true)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.latestMood.collect { entry ->
                renderResonanceCard(entry)
            }
        }

        lifecycleScope.launch {
            viewModel.homeUiState.collect { state ->
                renderGreeting()
                renderArchiveCard(state)
                renderDistributionCard(state)
                renderRecentEchoes(state.recentEntries)
                updateWellnessCard(state.dominantMood)
                binding.tvComposerPrompt.text = state.reflectionPrompt
            }
        }

        lifecycleScope.launch {
            viewModel.streakState.collect { streak ->
                renderStreakCard(streak)
            }
        }

        lifecycleScope.launch {
            viewModel.isMonitoring.collect { monitoring ->
                updateMonitoringUI(monitoring)
            }
        }
    }

    private fun setQuickComposerExpanded(expanded: Boolean) {
        isQuickComposerExpanded = expanded
        val transition = AutoTransition().apply {
            duration = 250
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        }
        TransitionManager.beginDelayedTransition(binding.root, transition)
        binding.quickNoteContent.visibility = if (expanded) android.view.View.VISIBLE else android.view.View.GONE
        binding.ivQuickNoteChevron.text = getString(
            if (expanded) R.string.dashboard_chevron_expanded else R.string.dashboard_chevron_collapsed
        )
    }

    private fun renderGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 5  -> R.string.dashboard_good_night
            hour < 12 -> R.string.dashboard_good_morning
            hour < 17 -> R.string.dashboard_good_afternoon
            hour < 21 -> R.string.dashboard_good_evening
            else      -> R.string.dashboard_good_night
        }
        binding.tvGreeting.text = getString(greeting)
        binding.tvSubtitle.text = getString(R.string.dashboard_subtitle)
    }

    private fun renderResonanceCard(entry: MoodEntry?) {
        val mood = entry?.mood ?: "Neutral"
        binding.tvMoodEmoji.text = MoodUtils.getEmoji(mood)
        binding.tvStatus.text = getString(R.string.dashboard_current_mood, mood)
        binding.tvMoodTime.text = if (entry == null) {
            getString(R.string.dashboard_last_check_never)
        } else {
            getString(R.string.dashboard_last_check_time, MoodUtils.formatTime(entry.timestamp).uppercase())
        }
        binding.tvStatusDetail.text = entry?.note?.takeIf { it.isNotBlank() }
            ?: MoodUtils.getReflectionPrompt(mood)
    }

    private fun renderArchiveCard(state: MainViewModel.HomeUiState) {
        binding.tvDominantMood.text = if (state.archiveCount == 0) {
            getString(R.string.dashboard_no_pattern_yet)
        } else {
            getString(R.string.dashboard_dominant_pattern, state.dominantMood)
        }
        binding.tvDominantSummary.text = if (state.archiveCount == 0) {
            getString(R.string.dashboard_trend_after_checkins)
        } else if (state.stabilityDelta > 0) {
            getString(R.string.dashboard_stability_delta, state.stabilityDelta)
        } else {
            getString(R.string.dashboard_stable_baseline)
        }
        renderTrendBars(state.trendBuckets)
    }

    private fun updateMonitoringUI(monitoring: Boolean) {
        binding.btnToggle.text = getString(
            if (monitoring) R.string.dashboard_pause else R.string.dashboard_start
        )
    }

    private fun updateWellnessCard(mood: String) {
        val tip = WellnessRepository.getQuickTip(mood)
        with(binding) {
            tvWellnessEmoji.text = tip.emoji
            tvWellnessTitle.text = tip.title
            tvWellnessHint.text = tip.description
            tvWellnessMeta.text = getString(R.string.dashboard_recommended_for_you)
        }
    }

    private fun renderRecentEchoes(entries: List<MoodEntry>) {
        val container = binding.recentEchoesContainer
        if (entries.isEmpty()) {
            container.removeAllViews()
            binding.tvEchoesEmpty.visibility = android.view.View.VISIBLE
            return
        }

        binding.tvEchoesEmpty.visibility = android.view.View.GONE
        val inflater = LayoutInflater.from(this)

        // Reuse existing child views instead of removing and re-inflating every time
        while (container.childCount > entries.size) {
            container.removeViewAt(container.childCount - 1)
        }

        entries.forEachIndexed { index, entry ->
            val card = if (index < container.childCount) {
                container.getChildAt(index)
            } else {
                val newCard = inflater.inflate(R.layout.item_echo_card, container, false)
                container.addView(newCard)
                newCard
            }
            card.findViewById<TextView>(R.id.tvEchoEmoji).text = MoodUtils.getEmoji(entry.mood)
            card.findViewById<TextView>(R.id.tvEchoTitle).text = entry.mood.uppercase()
            card.findViewById<TextView>(R.id.tvEchoMeta).text =
                MoodUtils.formatTime(entry.timestamp).uppercase()
            val note = entry.note?.takeIf { it.isNotBlank() } ?: MoodUtils.getReflectionPrompt(entry.mood)
            card.findViewById<TextView>(R.id.tvEchoNote).text = getString(R.string.dashboard_quoted_note, note)
            card.setOnClickListener { navigateTo(JournalActivity::class.java) }
        }
    }

    private fun renderStreakCard(streak: MainViewModel.StreakState?) {
        val count = streak?.count ?: 0
        val mood = streak?.mood ?: "Neutral"
        binding.tvStreakEmoji.text = MoodUtils.getEmoji(mood)
        binding.tvStreakCount.text = resources.getQuantityString(R.plurals.dashboard_streak_days, count, count)
        binding.tvStreakTitle.text = getString(R.string.dashboard_reflection_goal, count.coerceAtMost(5))
        binding.streakProgress.progress = ((count.coerceAtMost(5) / 5f) * 100f).roundToInt()
    }

    private fun renderDistributionCard(state: MainViewModel.HomeUiState) {
        val fallback = listOf(
            MainViewModel.MoodDistribution("Neutral", 0),
            MainViewModel.MoodDistribution("Focused", 0),
            MainViewModel.MoodDistribution("Happy", 0)
        )
        val slices = (state.distribution + fallback).distinctBy { it.mood }.take(3)

        // Color map for mood segments
        val moodColorMap = mapOf(
            "Happy" to R.color.mm_mood_happy,
            "Focused" to R.color.mm_mood_focused,
            "Neutral" to R.color.mm_mood_neutral,
            "Stressed" to R.color.mm_mood_stressed,
            "Tired" to R.color.mm_mood_tired,
            "Bored" to R.color.mm_mood_bored
        )

        // Update segment bar weights and colors
        val segmentBars = listOf(binding.segmentBar1, binding.segmentBar2, binding.segmentBar3)
        val dotViews = listOf(binding.dotMood1, binding.dotMood2, binding.dotMood3)

        slices.forEachIndexed { index, slice ->
            val colorRes = moodColorMap[slice.mood] ?: R.color.mm_mood_neutral
            val color = ContextCompat.getColor(this, colorRes)

            // Set segment bar color and weight
            val bar = segmentBars[index]
            val bgDrawable = bar.background
            if (bgDrawable is GradientDrawable) {
                bgDrawable.setColor(color)
            } else {
                val gd = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 6f * resources.displayMetrics.density
                    setColor(color)
                }
                bar.background = gd
            }
            val weight = if (slice.percent > 0) slice.percent.toFloat() else 1f
            val params = bar.layoutParams as android.widget.LinearLayout.LayoutParams
            params.weight = weight
            bar.layoutParams = params

            // Set dot color
            val dot = dotViews[index]
            val dotBg = dot.background
            if (dotBg is GradientDrawable) {
                dotBg.setColor(color)
            } else {
                val dotGd = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                dot.background = dotGd
            }
        }

        binding.tvDistributionMood1.text = slices[0].mood
        binding.tvDistributionPercent1.text = getString(R.string.dashboard_distribution_percent, slices[0].percent)
        binding.tvDistributionMood2.text = slices[1].mood
        binding.tvDistributionPercent2.text = getString(R.string.dashboard_distribution_percent, slices[1].percent)
        binding.tvDistributionMood3.text = slices[2].mood
        binding.tvDistributionPercent3.text = getString(R.string.dashboard_distribution_percent, slices[2].percent)
    }

    private fun renderTrendBars(values: List<Int>) {
        val bars = listOf(
            binding.trendBar1,
            binding.trendBar2,
            binding.trendBar3,
            binding.trendBar4,
            binding.trendBar5,
            binding.trendBar6,
            binding.trendBar7
        )
        val maxValue = values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val density = resources.displayMetrics.density
        val m3Easing = PathInterpolator(0.2f, 0f, 0f, 1f)

        bars.forEachIndexed { index, view ->
            val value = values.getOrElse(index) { 0 }
            val targetDp = 24 + ((value.toFloat() / maxValue) * 52f).roundToInt()
            val targetPx = (targetDp * density).roundToInt()
            val currentHeight = view.layoutParams.height.coerceAtLeast(0)

            if (currentHeight != targetPx) {
                ValueAnimator.ofInt(currentHeight, targetPx).apply {
                    duration = 400
                    interpolator = m3Easing
                    addUpdateListener { animator ->
                        view.layoutParams = view.layoutParams.apply {
                            height = animator.animatedValue as Int
                        }
                    }
                    start()
                }
            }

            val targetAlpha = if (value == 0) 0.5f else 1f
            view.animate().alpha(targetAlpha).setDuration(300).setInterpolator(m3Easing).start()
        }

        // Update day labels to match last 7 days
        val dayLabels = listOf(
            binding.tvDayLabel1, binding.tvDayLabel2, binding.tvDayLabel3,
            binding.tvDayLabel4, binding.tvDayLabel5, binding.tvDayLabel6,
            binding.tvDayLabel7
        )
        val dayNames = arrayOf("S", "M", "T", "W", "T", "F", "S")
        val today = Calendar.getInstance()
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -(6 - i))
            }
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 7=Sat
            dayLabels[i].text = dayNames[dayOfWeek - 1]
            // Bold today's label
            if (cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                && cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
                dayLabels[i].setTextColor(ContextCompat.getColor(this, R.color.mm_primary))
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) startMonitoring() else permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startMonitoring() {
        ContextCompat.startForegroundService(this, Intent(this, MoodMonitorService::class.java))
        viewModel.setMonitoring(true)
        Toast.makeText(this, getString(R.string.monitoring_started), Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        stopService(Intent(this, MoodMonitorService::class.java))
        viewModel.setMonitoring(false)
        Toast.makeText(this, getString(R.string.monitoring_stopped), Toast.LENGTH_SHORT).show()
    }
}
