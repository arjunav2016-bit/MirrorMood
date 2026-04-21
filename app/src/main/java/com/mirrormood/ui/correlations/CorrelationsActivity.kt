package com.mirrormood.ui.correlations

import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.mirrormood.R
import com.mirrormood.databinding.ActivityCorrelationsBinding
import com.mirrormood.health.HealthConnectManager
import com.mirrormood.ui.main.MainViewModel
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback

@AndroidEntryPoint
class CorrelationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCorrelationsBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var healthConnectManager: HealthConnectManager

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
            binding.root.findViewById<View>(R.id.cardHealthStatus)
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
