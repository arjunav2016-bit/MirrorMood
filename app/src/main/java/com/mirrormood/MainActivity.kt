package com.mirrormood

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
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
import com.google.android.material.snackbar.Snackbar
import com.mirrormood.data.WellnessRecommendation
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.databinding.ActivityMainBinding
import com.mirrormood.notification.MoodNotificationManager
import com.mirrormood.notification.NotificationScheduler
import com.mirrormood.service.MoodMonitorService
import com.mirrormood.ui.achievements.AchievementsActivity
import com.mirrormood.ui.correlations.CorrelationsActivity
import com.mirrormood.ui.journal.JournalActivity
import com.mirrormood.ui.main.MainViewModel
import com.mirrormood.ui.recommendations.RecommendationsActivity
import com.mirrormood.ui.settings.SettingsActivity
import com.mirrormood.ui.timeline.TimelineActivity
import com.mirrormood.ui.wellness.WellnessSessionActivity
import com.mirrormood.util.BottomNavHelper
import com.mirrormood.util.BottomNavTab
import com.mirrormood.util.MoodUtils
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.MoodPredictor
import com.mirrormood.util.ThemeHelper
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.DecelerateInterpolator
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private var selectedMood: String = "Neutral"
    private val selectedTriggers = mutableSetOf<String>()
    private var isQuickComposerExpanded: Boolean = false
    private var breathingAnimator: ValueAnimator? = null

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
        playEntranceAnimations()
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

        // Trigger chips — multi-select
        val triggerChipMap = mapOf(
            R.id.chipTriggerWork to "Work",
            R.id.chipTriggerExercise to "Exercise",
            R.id.chipTriggerSocial to "Social",
            R.id.chipTriggerSleep to "Sleep",
            R.id.chipTriggerWeather to "Weather",
            R.id.chipTriggerFood to "Food",
            R.id.chipTriggerHealth to "Health",
            R.id.chipTriggerTravel to "Travel"
        )

        triggerChipMap.forEach { (chipId, trigger) ->
            findViewById<Chip>(chipId)?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedTriggers.add(trigger)
                else selectedTriggers.remove(trigger)
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

            // Polish: Surface hidden screens from Home dashboard
            root.findViewById<View>(R.id.btnOpenWellnessStudio)?.setOnClickListener {
                navigateTo(WellnessSessionActivity::class.java)
            }
            root.findViewById<View>(R.id.btnViewAchievements)?.setOnClickListener {
                navigateTo(AchievementsActivity::class.java)
            }
            root.findViewById<View>(R.id.btnOpenCorrelations)?.setOnClickListener {
                navigateTo(CorrelationsActivity::class.java)
            }
        }
    }

    private fun saveQuickEntry() {
        val note = binding.etQuickNote.text?.toString()?.trim().orEmpty()
        val triggers = if (selectedTriggers.isNotEmpty()) selectedTriggers.joinToString(",") else null

        // Allow mood-only saves (note is optional on the quick composer)
        viewModel.saveReflection(selectedMood, note, triggers)
        binding.etQuickNote.text?.clear()
        selectedTriggers.clear()
        setQuickComposerExpanded(false)

        val toastRes = if (note.isBlank()) R.string.dashboard_mood_logged else R.string.journal_saved
        Toast.makeText(this, toastRes, Toast.LENGTH_SHORT).show()
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
                renderSmartActionCard(state.smartAction)
                renderPredictionCard(state.forecast)
                renderArchiveCard(state)
                renderDistributionCard(state)
                renderRecentEchoes(state.recentEntries)
                updateWellnessCard(state.wellnessTip)
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

        // Achievement unlock notifications — show enriched Snackbar with emoji + name
        lifecycleScope.launch {
            viewModel.newlyUnlocked.collect { milestone ->
                val message = getString(
                    R.string.achievements_unlocked_detail,
                    milestone.emoji,
                    milestone.title
                )
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.achievements_view)) {
                        navigateTo(AchievementsActivity::class.java)
                    }
                    .show()
            }
        }

        // Health snapshot — render compact health card on dashboard
        lifecycleScope.launch {
            viewModel.healthState.collect { snapshot ->
                renderHealthCard(snapshot)
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

        val prefs = getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        val userName = prefs.getString("user_display_name", null) ?: "Analyst"

        binding.tvGreeting.text = getString(greeting, userName)
        binding.tvSubtitle.text = getString(R.string.dashboard_subtitle)
    }

    private fun renderSmartActionCard(state: MainViewModel.SmartActionState?) {
        if (breathingAnimator?.isRunning == true) {
            // Do not interrupt an active breathing session
            return
        }

        val smartActionCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.smartActionCard) ?: return
        if (state == null) {
            smartActionCard.visibility = View.GONE
            return
        }
        smartActionCard.visibility = View.VISIBLE
        
        val breatheContainer = findViewById<View>(R.id.breatheContainer)
        val quoteContainer = findViewById<View>(R.id.quoteContainer)
        
        if (state.isBreatheMode) {
            breatheContainer.visibility = View.VISIBLE
            quoteContainer.visibility = View.GONE
            
            findViewById<TextView>(R.id.tvSmartBreatheTitle)?.text = state.title
            findViewById<TextView>(R.id.tvSmartBreatheSubtitle)?.text = state.subtitle
            findViewById<TextView>(R.id.tvSmartBreatheEmoji)?.text = state.emoji
            
            findViewById<View>(R.id.viewBreathingRing)?.apply {
                scaleX = 1f
                scaleY = 1f
            }
            findViewById<TextView>(R.id.tvBreathingInstruction)?.text =
                getString(R.string.dashboard_smart_action_tap_to_start)
            
            findViewById<View>(R.id.btnStartBreathing)?.setOnClickListener {
                startBreathingAnimation()
            }
            findViewById<View>(R.id.frameBreathingArea)?.setOnClickListener {
                startBreathingAnimation()
            }
        } else {
            breatheContainer.visibility = View.GONE
            quoteContainer.visibility = View.VISIBLE
            
            findViewById<TextView>(R.id.tvSmartQuoteLabel)?.text = state.title
            findViewById<TextView>(R.id.tvSmartQuoteText)?.text = state.quoteText
            findViewById<TextView>(R.id.tvSmartQuoteAuthor)?.apply {
                text = state.quoteAuthor
                visibility = if (state.quoteAuthor.isBlank()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun renderPredictionCard(forecast: MoodPredictor.Forecast) {
        val card = findViewById<com.google.android.material.card.MaterialCardView>(R.id.predictionCard) ?: return
        card.visibility = View.VISIBLE

        when (forecast) {
            is MoodPredictor.Forecast.Ready -> {
                val prediction = forecast.prediction
                findViewById<TextView>(R.id.tvPredictionEmoji)?.text = MoodUtils.getEmoji(prediction.mood)
                findViewById<TextView>(R.id.tvPredictionMood)?.text =
                    getString(R.string.prediction_likely_mood, prediction.mood)
                findViewById<TextView>(R.id.tvPredictionConfidence)?.text =
                    getString(R.string.prediction_confidence, prediction.confidence)
                findViewById<TextView>(R.id.tvPredictionExplanation)?.text =
                    MoodPredictor.getExplanation(prediction)
            }
            is MoodPredictor.Forecast.Learning -> {
                findViewById<TextView>(R.id.tvPredictionEmoji)?.text = getString(R.string.prediction_learning_icon)
                findViewById<TextView>(R.id.tvPredictionMood)?.text =
                    getString(R.string.prediction_learning_title)
                findViewById<TextView>(R.id.tvPredictionConfidence)?.text =
                    getString(R.string.prediction_learning_count, forecast.matchingEntries)
                findViewById<TextView>(R.id.tvPredictionExplanation)?.text =
                    MoodPredictor.getLearningExplanation(forecast)
            }
        }
    }

    private fun startBreathingAnimation() {
        val ring = findViewById<View>(R.id.viewBreathingRing) ?: return
        val text = findViewById<TextView>(R.id.tvBreathingInstruction) ?: return
        val btn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStartBreathing) ?: return
        
        btn.isEnabled = false
        breathingAnimator?.cancel()
        
        breathingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 19000L
            repeatMode = ValueAnimator.RESTART
            repeatCount = 3 
            interpolator = android.view.animation.LinearInterpolator()
            
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                val elapsed = fraction * 19000f
                val scale: Float
                
                // Helper for smoothstep easing
                fun easeInOut(t: Float) = t * t * (3 - 2 * t)
                
                when {
                    elapsed < 4000f -> {
                        val subFraction = elapsed / 4000f
                        scale = 1f + (1.5f * easeInOut(subFraction))
                        text.text = getString(R.string.wellness_inhale)
                    }
                    elapsed < 11000f -> {
                        scale = 2.5f
                        text.text = getString(R.string.wellness_hold)
                    }
                    else -> {
                        val subFraction = (elapsed - 11000f) / 8000f
                        scale = 2.5f - (1.5f * easeInOut(subFraction))
                        text.text = getString(R.string.wellness_exhale)
                    }
                }
                
                ring.scaleX = scale
                ring.scaleY = scale
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    text.text = getString(R.string.wellness_session_complete)
                    btn.isEnabled = true
                    btn.text = getString(R.string.dashboard_try_full_session)
                    btn.setOnClickListener { navigateTo(WellnessSessionActivity::class.java) }
                    ring.animate().scaleX(1f).scaleY(1f).setDuration(500).start()
                }
            })
            start()
        }
    }

    private fun renderHealthCard(snapshot: com.mirrormood.health.HealthSnapshot?) {
        val healthCard = binding.root.findViewById<View>(R.id.healthSnapshotCard)
        val healthLinkPrompt = binding.root.findViewById<View>(R.id.tvHealthLinkPrompt)

        if (healthCard == null) return  // Layout doesn't have the card yet

        val prefs = getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        val isLinked = prefs.getBoolean("health_connect_enabled", false)

        if (!isLinked) {
            healthCard.visibility = View.GONE
            healthLinkPrompt?.apply {
                visibility = View.VISIBLE
                setOnClickListener { navigateTo(SettingsActivity::class.java) }
            }
            return
        }

        healthLinkPrompt?.visibility = View.GONE

        if (snapshot != null) {
            healthCard.visibility = View.VISIBLE
            healthCard.findViewById<TextView>(R.id.tvHealthSteps)?.text =
                getString(R.string.health_card_steps, String.format("%,d", snapshot.steps))
            healthCard.findViewById<TextView>(R.id.tvHealthSleep)?.text =
                getString(R.string.health_card_sleep, snapshot.sleepHours)
            healthCard.findViewById<TextView>(R.id.tvHealthQuality)?.text =
                getString(R.string.health_card_quality, snapshot.sleepQualityScore)
            healthCard.setOnClickListener { navigateTo(CorrelationsActivity::class.java) }
        } else {
            healthCard.visibility = View.GONE
        }
    }

    private var currentResonanceMood: String? = null

    private fun renderResonanceCard(entry: MoodEntry?) {
        val mood = entry?.mood ?: "Neutral"
        
        if (currentResonanceMood != null && currentResonanceMood != mood) {
            val transition = com.google.android.material.transition.MaterialSharedAxis(
                com.google.android.material.transition.MaterialSharedAxis.Z, true
            ).apply {
                duration = 300
            }
            androidx.transition.TransitionManager.beginDelayedTransition(binding.moodCard, transition)
        }
        currentResonanceMood = mood

        // Dynamic gradient tint based on mood
        tintMoodCardGradient(mood)

        binding.tvMoodEmoji.text = MoodUtils.getEmoji(mood)
        binding.tvStatus.text = getString(R.string.dashboard_current_mood, mood)
        binding.tvMoodTime.text = if (entry == null) {
            getString(R.string.dashboard_last_check_never)
        } else {
            getString(R.string.dashboard_last_check_time, MoodUtils.formatTime(entry.timestamp).uppercase())
        }
        
        if (entry != null && entry.confidence > 0f) {
            binding.tvConfidence.visibility = View.VISIBLE
            val percent = (entry.confidence * 100f).roundToInt()
            binding.tvConfidence.text = getString(R.string.dashboard_confidence, percent)
        } else {
            binding.tvConfidence.visibility = View.GONE
        }
        
        binding.tvStatusDetail.text = entry?.note?.takeIf { it.isNotBlank() }
            ?: MoodUtils.getReflectionPrompt(mood)
    }

    private fun tintMoodCardGradient(mood: String) {
        val moodColor = ContextCompat.getColor(this, MoodUtils.getColorRes(mood))
        val endColor = com.google.android.material.color.MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurfaceContainerHigh, moodColor
        )
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(moodColor, endColor)
        )
        gradient.cornerRadius = resources.getDimension(R.dimen.card_corner_radius)
        binding.moodCardInner.background = gradient
    }

    private fun playEntranceAnimations() {
        val cards = listOf(
            binding.moodCard,
            binding.streakCard,
            binding.wellnessCard
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

    private fun updateWellnessCard(tip: WellnessRecommendation) {
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

