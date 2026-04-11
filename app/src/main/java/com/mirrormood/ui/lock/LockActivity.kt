package com.mirrormood.ui.lock

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mirrormood.MainActivity
import com.mirrormood.MirrorMoodApp
import com.mirrormood.R
import com.mirrormood.databinding.ActivityLockBinding
import com.mirrormood.security.PinStorage
import com.mirrormood.util.ThemeHelper

import dagger.hilt.android.AndroidEntryPoint
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

@AndroidEntryPoint
class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private val prefs by lazy { getSharedPreferences(MirrorMoodApp.PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        if (!prefs.getBoolean("lock_enabled", false)) {
            goToMain()
            return
        }

        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val hasPin = PinStorage.hasPin(this)
        val biometricManager = BiometricManager.from(this)
        val canUseBiometric = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (canUseBiometric && !hasPin) {
            binding.btnPin.visibility = View.GONE
        }
        if (!canUseBiometric && hasPin) {
            binding.btnBiometric.visibility = View.GONE
        }
        if (!canUseBiometric && !hasPin) {
            binding.btnBiometric.alpha = 0.35f
        }

        binding.btnBiometric.setOnClickListener { showBiometricPrompt() }

        binding.btnPin.setOnClickListener {
            if (hasPin) {
                togglePinPanel()
            } else {
                Toast.makeText(this, R.string.lock_pin_not_set, Toast.LENGTH_LONG).show()
            }
        }

        binding.btnSubmitPin.setOnClickListener { tryUnlockWithPin() }

        binding.etPin.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryUnlockWithPin()
                true
            } else false
        }

        when {
            !hasPin && !canUseBiometric -> {
                // Emergency fallback — disable lock
                confirmEmergencyDisable()
            }
            canUseBiometric -> {
                showBiometricPrompt()
            }
            hasPin -> {
                binding.pinInputLayout.visibility = View.VISIBLE
                binding.btnSubmitPin.visibility = View.VISIBLE
                binding.etPin.requestFocus()
            }
        }
    }

    private fun togglePinPanel() {
        val visible = binding.pinInputLayout.visibility == View.VISIBLE
        binding.pinInputLayout.visibility = if (visible) View.GONE else View.VISIBLE
        binding.btnSubmitPin.visibility = if (visible) View.GONE else View.VISIBLE
        if (!visible) binding.etPin.requestFocus()
    }

    private fun tryUnlockWithPin() {
        val pin = binding.etPin.text?.toString().orEmpty()
        if (pin.length < 4) {
            Toast.makeText(this, R.string.lock_pin_too_short, Toast.LENGTH_SHORT).show()
            return
        }
        if (PinStorage.verifyPin(this, pin)) {
            goToMain()
        } else {
            Toast.makeText(this, R.string.lock_pin_incorrect, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val canUseBiometric = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
        if (!canUseBiometric) return

        val executor = ContextCompat.getMainExecutor(this)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                goToMain()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON && PinStorage.hasPin(this@LockActivity)) {
                    binding.pinInputLayout.visibility = View.VISIBLE
                    binding.btnSubmitPin.visibility = View.VISIBLE
                    binding.etPin.requestFocus()
                    return
                }
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    return
                }
                Toast.makeText(this@LockActivity, errString, Toast.LENGTH_SHORT).show()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(this@LockActivity, R.string.lock_biometric_failed, Toast.LENGTH_SHORT).show()
            }
        }

        val biometricPrompt = BiometricPrompt(this, executor, callback)

        val negText = if (PinStorage.hasPin(this)) {
            getString(R.string.lock_use_pin_instead)
        } else {
            getString(android.R.string.cancel)
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_biometric_title))
            .setSubtitle(getString(R.string.lock_biometric_subtitle))
            .setNegativeButtonText(negText)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun confirmEmergencyDisable() {
        AlertDialog.Builder(this)
            .setTitle(R.string.lock_emergency_title)
            .setMessage(R.string.lock_emergency_message)
            .setPositiveButton(R.string.lock_emergency_confirm) { _, _ ->
                prefs.edit().putBoolean("lock_enabled", false).apply()
                PinStorage.clearPin(this)
                goToMain()
            }
            .setNegativeButton(R.string.lock_emergency_cancel, null)
            .show()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
