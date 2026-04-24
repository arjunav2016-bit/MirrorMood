package com.mirrormood.ui.wellness

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mirrormood.MirrorMoodApp
import com.mirrormood.R
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.data.db.WellnessSessionEntity
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.data.repository.WellnessSessionRepository
import com.mirrormood.databinding.ActivityWellnessSessionBinding
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import dagger.hilt.android.AndroidEntryPoint
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WellnessSessionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWellnessSessionBinding
    private var sessionAnimator: ValueAnimator? = null
    private var currentSession: SessionType = SessionType.BREATHING
    private var isSessionActive = false

    @Inject
    lateinit var moodRepository: MoodRepository

    @Inject
    lateinit var wellnessSessionRepository: WellnessSessionRepository

    private var sessionStartTimeMs: Long = 0L

    enum class SessionType { BREATHING, BODY_SCAN, GRATITUDE }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityWellnessSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Apply gradient background
        val primaryColor = ContextCompat.getColor(this, R.color.mm_primary)
        val surfaceColor = com.google.android.material.color.MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurface, primaryColor
        )
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(primaryColor, surfaceColor)
        )
        binding.viewGradientBg.background = gradient

        setupSessionChips()
        setupButtons()
        updateSessionUI()
        playEntranceAnimations()

        binding.btnHistory.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            val intent = android.content.Intent(this, SessionHistoryActivity::class.java)
            startActivity(intent)
            slideTransition(forward = true)
        }

        // Consistent back navigation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                stopSession()
                finish()
                slideTransition(forward = false)
            }
        })
    }

    private fun playEntranceAnimations() {
        val views = listOfNotNull(
            binding.tvSessionType,
            binding.frameRingArea,
            binding.btnStartSession
        )
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

    private fun setupSessionChips() {
        binding.chipGroupSession.setOnCheckedStateChangeListener { _, checkedIds ->
            if (isSessionActive) return@setOnCheckedStateChangeListener
            val chipId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentSession = when (chipId) {
                R.id.chipBodyScan -> SessionType.BODY_SCAN
                R.id.chipGratitude -> SessionType.GRATITUDE
                else -> SessionType.BREATHING
            }
            updateSessionUI()
        }
    }

    private fun setupButtons() {
        binding.btnClose.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            stopSession()
            finish()
            slideTransition(forward = false)
        }

        binding.btnStartSession.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (isSessionActive) {
                stopSession()
            } else {
                startSession()
            }
        }

        binding.frameRingArea.setOnClickListener {
            if (!isSessionActive) {
                startSession()
            }
        }
    }

    private fun updateSessionUI() {
        when (currentSession) {
            SessionType.BREATHING -> {
                binding.tvSessionType.text = getString(R.string.wellness_breathing_title)
                binding.tvSessionEmoji.text = WellnessSessionDisplay.emojiFor(WellnessSessionDisplay.TYPE_BREATHING)
                binding.tvStepDescription.text = getString(R.string.wellness_breathe_desc)
                binding.tvInstruction.text = getString(R.string.wellness_tap_to_start)
            }
            SessionType.BODY_SCAN -> {
                binding.tvSessionType.text = getString(R.string.wellness_body_scan_title)
                binding.tvSessionEmoji.text = WellnessSessionDisplay.emojiFor(WellnessSessionDisplay.TYPE_BODY_SCAN)
                binding.tvStepDescription.text = getString(R.string.wellness_body_scan_desc)
                binding.tvInstruction.text = getString(R.string.wellness_tap_to_start)
            }
            SessionType.GRATITUDE -> {
                binding.tvSessionType.text = getString(R.string.wellness_gratitude_title)
                binding.tvSessionEmoji.text = WellnessSessionDisplay.emojiFor(WellnessSessionDisplay.TYPE_GRATITUDE)
                binding.tvStepDescription.text = getString(R.string.wellness_gratitude_desc)
                binding.tvInstruction.text = getString(R.string.wellness_tap_to_start)
            }
        }
    }

    private fun startSession() {
        isSessionActive = true
        sessionStartTimeMs = System.currentTimeMillis()
        binding.btnStartSession.text = getString(R.string.wellness_stop_session)
        binding.tvTimer.visibility = View.VISIBLE
        binding.chipGroupSession.isEnabled = false

        // Check reduced motion preference
        val prefs = getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        val reducedMotion = prefs.getBoolean("reduced_motion", false)

        when (currentSession) {
            SessionType.BREATHING -> startBreathingSession(reducedMotion)
            SessionType.BODY_SCAN -> startBodyScanSession(reducedMotion)
            SessionType.GRATITUDE -> startGratitudeSession(reducedMotion)
        }
    }

    private fun startBreathingSession(reducedMotion: Boolean) {
        val cycleDuration = 19000L // 4s inhale + 7s hold + 8s exhale
        val totalCycles = 4

        sessionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = cycleDuration
            repeatMode = ValueAnimator.RESTART
            repeatCount = totalCycles - 1
            interpolator = LinearInterpolator()

            var completedCycles = 0

            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                val elapsed = fraction * cycleDuration

                fun easeInOut(t: Float) = t * t * (3 - 2 * t)

                val (instruction, scale) = when {
                    elapsed < 4000f -> {
                        val sub = elapsed / 4000f
                        getString(R.string.wellness_inhale) to (1f + 1.5f * easeInOut(sub))
                    }
                    elapsed < 11000f -> {
                        getString(R.string.wellness_hold) to 2.5f
                    }
                    else -> {
                        val sub = (elapsed - 11000f) / 8000f
                        getString(R.string.wellness_exhale) to (2.5f - 1.5f * easeInOut(sub))
                    }
                }

                binding.tvInstruction.text = instruction

                if (!reducedMotion) {
                    binding.viewInnerRing.scaleX = scale
                    binding.viewInnerRing.scaleY = scale
                }

                val totalElapsed = completedCycles * cycleDuration + elapsed
                val remaining = (totalCycles * cycleDuration - totalElapsed) / 1000
                binding.tvTimer.text = getString(R.string.wellness_time_remaining,
                    (remaining / 60).toInt(), (remaining % 60).toInt())
            }

            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationRepeat(animation: android.animation.Animator) {
                    completedCycles++
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    completeSession()
                }
            })
            start()
        }
    }

    private fun startBodyScanSession(reducedMotion: Boolean) {
        val steps = listOf(
            getString(R.string.wellness_body_scan_step1),
            getString(R.string.wellness_body_scan_step2),
            getString(R.string.wellness_body_scan_step3),
            getString(R.string.wellness_body_scan_step4),
            getString(R.string.wellness_body_scan_step5),
            getString(R.string.wellness_body_scan_step6)
        )
        val stepDuration = 10000L // 10 seconds per step

        sessionAnimator = ValueAnimator.ofFloat(0f, steps.size.toFloat()).apply {
            duration = stepDuration * steps.size
            interpolator = LinearInterpolator()

            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                val stepIndex = value.toInt().coerceAtMost(steps.size - 1)

                binding.tvInstruction.text = getString(R.string.wellness_body_scan_focus)
                binding.tvStepDescription.text = steps[stepIndex]

                val remaining = ((steps.size * stepDuration - value * stepDuration) / 1000).toLong()
                binding.tvTimer.text = getString(R.string.wellness_time_remaining,
                    (remaining / 60).toInt(), (remaining % 60).toInt())

                if (!reducedMotion) {
                    val pulse = 1f + 0.05f * kotlin.math.sin(value * Math.PI * 2).toFloat()
                    binding.viewInnerRing.scaleX = pulse
                    binding.viewInnerRing.scaleY = pulse
                }
            }

            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    completeSession()
                }
            })
            start()
        }
    }

    private fun startGratitudeSession(reducedMotion: Boolean) {
        val prompts = listOf(
            getString(R.string.wellness_gratitude_step1),
            getString(R.string.wellness_gratitude_step2),
            getString(R.string.wellness_gratitude_step3),
            getString(R.string.wellness_gratitude_step4),
            getString(R.string.wellness_gratitude_step5)
        )
        val stepDuration = 12000L

        sessionAnimator = ValueAnimator.ofFloat(0f, prompts.size.toFloat()).apply {
            duration = stepDuration * prompts.size
            interpolator = LinearInterpolator()

            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                val stepIndex = value.toInt().coerceAtMost(prompts.size - 1)

                binding.tvInstruction.text = getString(R.string.wellness_reflect)
                binding.tvStepDescription.text = prompts[stepIndex]

                val remaining = ((prompts.size * stepDuration - value * stepDuration) / 1000).toLong()
                binding.tvTimer.text = getString(R.string.wellness_time_remaining,
                    (remaining / 60).toInt(), (remaining % 60).toInt())
            }

            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    completeSession()
                }
            })
            start()
        }
    }

    private fun completeSession() {
        isSessionActive = false
        binding.btnStartSession.text = getString(R.string.wellness_start_session)
        binding.tvInstruction.text = getString(R.string.wellness_session_complete)
        binding.tvTimer.visibility = View.GONE
        binding.chipGroupSession.isEnabled = true

        // Reset ring
        binding.viewInnerRing.animate().scaleX(1f).scaleY(1f).setDuration(500).start()

        // Track completion for gamification
        val prefs = getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt("wellness_sessions_completed", 0) + 1
        prefs.edit().putInt("wellness_sessions_completed", count).apply()

        // Persist session to Room for history tracking
        val sessionLabel = when (currentSession) {
            SessionType.BREATHING -> WellnessSessionDisplay.TYPE_BREATHING
            SessionType.BODY_SCAN -> WellnessSessionDisplay.TYPE_BODY_SCAN
            SessionType.GRATITUDE -> WellnessSessionDisplay.TYPE_GRATITUDE
        }
        val durationMs = System.currentTimeMillis() - sessionStartTimeMs
        lifecycleScope.launch(Dispatchers.IO) {
            wellnessSessionRepository.saveSession(
                WellnessSessionEntity(
                    type = sessionLabel,
                    durationMs = durationMs
                )
            )
        }
        val autoNote = getString(R.string.wellness_auto_journal_note, sessionLabel)
        val entry = MoodEntry(
            mood = "Neutral",
            smileScore = 0.5f,
            eyeOpenScore = 0.5f,
            confidence = 1.0f,
            note = autoNote,
            triggers = "Wellness:$sessionLabel"
        )
        lifecycleScope.launch(Dispatchers.IO) {
            moodRepository.saveMood(entry)
        }

        Toast.makeText(this, getString(R.string.wellness_completed_toast, count), Toast.LENGTH_SHORT).show()
    }

    private fun stopSession() {
        sessionAnimator?.cancel()
        sessionAnimator = null
        isSessionActive = false
        binding.btnStartSession.text = getString(R.string.wellness_start_session)
        binding.tvTimer.visibility = View.GONE
        binding.chipGroupSession.isEnabled = true
        binding.viewInnerRing.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
        updateSessionUI()
    }

    override fun onDestroy() {
        sessionAnimator?.cancel()
        super.onDestroy()
    }
}
