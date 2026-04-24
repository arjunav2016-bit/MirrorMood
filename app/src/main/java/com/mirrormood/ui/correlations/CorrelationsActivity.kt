package com.mirrormood.ui.correlations

import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mirrormood.R
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.databinding.ActivityCorrelationsBinding
import com.mirrormood.health.HealthConnectManager
import com.mirrormood.ui.main.MainViewModel
import com.mirrormood.util.MoodUtils
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import javax.inject.Inject

@AndroidEntryPoint
class CorrelationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCorrelationsBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var healthConnectManager: HealthConnectManager

    @Inject
    lateinit var moodRepository: MoodRepository

    private val requestHealthPermissions = registerForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(healthConnectManager.permissions)) {
            getSharedPreferences("mirrormood_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("health_connect_enabled", true).apply()
            viewModel.refreshHealthData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCorrelationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        healthConnectManager = HealthConnectManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            finish()
            slideTransition(forward = false)
        }

        binding.btnLinkHealth.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            requestHealthPermissions.launch(healthConnectManager.permissions)
        }

        observeViewModel()
        loadTriggerPatterns()
        playEntranceAnimations()

        // Consistent back navigation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                slideTransition(forward = false)
            }
        })
    }

    private fun playEntranceAnimations() {
        val views = listOfNotNull(
            binding.root.findViewById<View>(R.id.cardNotLinked),
            binding.root.findViewById<View>(R.id.cardHealthStatus),
            binding.cardTriggerPatterns
        ).filter { it.visibility == View.VISIBLE }
        val offsetPx = 40 * resources.displayMetrics.density
        views.forEachIndexed { index, view ->
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

    /**
     * Loads all mood entries and computes trigger-mood correlations.
     * Shows which triggers are most common and their mood breakdown.
     */
    private fun loadTriggerPatterns() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                moodRepository.getAllMoodEntries()
            }

            // Extract all trigger tags from entries
            val triggerEntries = entries.filter { !it.triggers.isNullOrBlank() }
            if (triggerEntries.isEmpty()) {
                binding.tvTriggersEmpty.visibility = View.VISIBLE
                binding.layoutTriggerBars.visibility = View.GONE
                return@launch
            }

            binding.tvTriggersEmpty.visibility = View.GONE
            binding.layoutTriggerBars.visibility = View.VISIBLE

            // Build trigger → mood breakdown map
            data class TriggerStats(
                val trigger: String,
                val totalCount: Int,
                val moodBreakdown: Map<String, Int>
            )

            val triggerStats = withContext(Dispatchers.Default) {
                val triggerMoodMap = mutableMapOf<String, MutableList<String>>()
                triggerEntries.forEach { entry ->
                    entry.triggers!!.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
                        triggerMoodMap.getOrPut(tag) { mutableListOf() }.add(entry.mood)
                    }
                }

                triggerMoodMap.map { (trigger, moods) ->
                    TriggerStats(
                        trigger = trigger,
                        totalCount = moods.size,
                        moodBreakdown = moods.groupingBy { it }.eachCount()
                    )
                }.sortedByDescending { it.totalCount }
            }

            // Render trigger patterns
            val container = binding.layoutTriggerBars
            container.removeAllViews()
            val inflater = LayoutInflater.from(this@CorrelationsActivity)
            val maxCount = triggerStats.maxOfOrNull { it.totalCount } ?: 1

            val triggerEmoji = mapOf(
                "Work" to "💼", "Exercise" to "🏃", "Social" to "👥",
                "Sleep" to "😴", "Weather" to "🌤️", "Food" to "🍽️",
                "Health" to "💊", "Travel" to "✈️"
            )

            val moodColors = mapOf(
                "Happy" to R.color.mm_mood_happy,
                "Focused" to R.color.mm_mood_focused,
                "Neutral" to R.color.mm_mood_neutral,
                "Stressed" to R.color.mm_mood_stressed,
                "Tired" to R.color.mm_mood_tired,
                "Bored" to R.color.mm_mood_bored
            )

            triggerStats.take(6).forEach { stats ->
                val row = inflater.inflate(R.layout.item_distribution_bar, container, false)
                val emoji = triggerEmoji[stats.trigger] ?: "🏷️"
                row.findViewById<TextView>(R.id.tvMoodLabel).text = "$emoji ${stats.trigger}"

                // Show the dominant mood percentage for this trigger
                val dominantMood = stats.moodBreakdown.maxByOrNull { it.value }
                val dominantPercent = if (dominantMood != null) {
                    (dominantMood.value * 100) / stats.totalCount
                } else 0
                row.findViewById<TextView>(R.id.tvMoodPercent).text =
                    getString(R.string.correlations_trigger_percent, dominantPercent, dominantMood?.key ?: "")

                row.findViewById<LinearProgressIndicator>(R.id.progressMood).apply {
                    progress = (stats.totalCount * 100) / maxCount
                    val colorRes = moodColors[dominantMood?.key] ?: R.color.mm_primary
                    setIndicatorColor(ContextCompat.getColor(this@CorrelationsActivity, colorRes))
                }
                container.addView(row)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.healthState.collect { snapshot ->
                val prefs = getSharedPreferences("mirrormood_prefs", Context.MODE_PRIVATE)
                val isLinked = prefs.getBoolean("health_connect_enabled", false)

                if (isLinked) {
                    binding.cardNotLinked.visibility = View.GONE
                    binding.cardHealthStatus.visibility = View.VISIBLE
                    
                    if (snapshot != null) {
                        // Populate sleep data
                        binding.tvSleepDuration.text = String.format("%.1fh", snapshot.sleepHours)
                        binding.tvSleepQuality.text = "${snapshot.sleepQualityScore}%"
                        binding.tvSleepInsight.text = getString(R.string.correlations_sleep_insight_value)
                        
                        // Populate steps data
                        binding.tvStepsAvg.text = String.format("%,d", snapshot.steps)
                        binding.tvBestMoodSteps.text = String.format("%,d", snapshot.steps)
                        binding.tvStepsInsight.text = getString(R.string.correlations_activity_insight_value)
                    }
                } else {
                    binding.cardNotLinked.visibility = View.VISIBLE
                    binding.cardHealthStatus.visibility = View.GONE
                    binding.tvSleepDuration.text = "--"
                    binding.tvSleepQuality.text = "--"
                    binding.tvStepsAvg.text = "--"
                    binding.tvBestMoodSteps.text = "--"
                }
            }
        }
    }
}
