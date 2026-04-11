package com.mirrormood.ui.onboarding

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mirrormood.MirrorMoodApp
import com.mirrormood.R
import com.mirrormood.databinding.ActivityOnboardingBinding
import com.mirrormood.ui.calibration.CalibrationActivity
import com.mirrormood.ui.lock.LockActivity
import com.mirrormood.ui.privacy.PrivacyActivity
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper

import dagger.hilt.android.AndroidEntryPoint
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(MirrorMoodApp.PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(MirrorMoodApp.KEY_ONBOARDING_COMPLETED, false)) {
            navigateForward()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        applyItalicHeadline()

        binding.btnGetStarted.setOnClickListener {
            prefs.edit().putBoolean(MirrorMoodApp.KEY_ONBOARDING_COMPLETED, true).apply()
            navigateForward()
        }

        binding.btnLearnPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java))
            slideTransition(forward = true)
        }
    }

    private fun applyItalicHeadline() {
        val full = getString(R.string.onboarding_headline)
        val italicWord = "Private"
        val start = full.indexOf(italicWord)
        if (start < 0) return
        val span = SpannableString(full)
        span.setSpan(
            StyleSpan(Typeface.ITALIC),
            start,
            start + italicWord.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvHeadline.text = span
    }

    private fun navigateForward() {
        val prefs = getSharedPreferences(MirrorMoodApp.PREFS_NAME, MODE_PRIVATE)
        val next = if (!prefs.getBoolean(MirrorMoodApp.KEY_CALIBRATION_COMPLETED, false)) {
            Intent(this, CalibrationActivity::class.java)
        } else {
            Intent(this, LockActivity::class.java)
        }
        startActivity(next)
        slideTransition(forward = true)
        finish()
    }
}
