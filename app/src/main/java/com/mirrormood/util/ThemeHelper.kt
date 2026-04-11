package com.mirrormood.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.google.android.material.color.DynamicColors
import com.mirrormood.MirrorMoodApp
import com.mirrormood.R

object ThemeHelper {
    private const val LEGACY_PREFS_NAME = "MirrorMoodPrefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"
    const val MODE_SYSTEM = "system"

    const val THEME_DEFAULT = "default"
    const val THEME_OCEAN = "ocean"
    const val THEME_SUNSET = "sunset"
    const val THEME_FOREST = "forest"

    // In-memory cache to avoid repeated SharedPreferences disk reads on every activity launch
    @Volatile
    private var cachedMode: String? = null

    fun applyTheme(activity: Activity) {
        // Apply dynamic color (Material You) if enabled and available
        if (isDynamicColorEnabled(activity)) {
            DynamicColors.applyToActivityIfAvailable(activity)
        } else {
            activity.setTheme(R.style.Theme_MirrorMood)
        }
        applyNightMode(activity)
    }

    fun applyNightMode(context: Context) {
        val mode = getCurrentMode(context)
        val nightMode = when (mode) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun applySystemBarAppearance(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        val isDark = isCurrentlyDark(activity)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.insetsController?.let { controller ->
                if (isDark) {
                    controller.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                } else {
                    controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = if (isDark) {
                activity.window.decorView.systemUiVisibility and
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                activity.window.decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    fun setThemeMode(context: Context, mode: String) {
        migrateLegacyPrefsIfNeeded(context)
        val prefs = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        cachedMode = mode
        applyNightMode(context)
    }

    fun getCurrentMode(context: Context): String {
        cachedMode?.let { return it }
        migrateLegacyPrefsIfNeeded(context)
        val mode = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, MODE_SYSTEM) ?: MODE_SYSTEM
        cachedMode = mode
        return mode
    }

    fun isCurrentlyDark(context: Context): Boolean {
        return when (getCurrentMode(context)) {
            MODE_DARK -> true
            MODE_LIGHT -> false
            else -> {
                val uiMode = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun isDynamicColorEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val prefs = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DYNAMIC_COLOR, true) // Default ON for Android 12+
    }

    fun setTheme(context: Context, themeAlias: String) {
        val mode = when (themeAlias) {
            THEME_OCEAN, THEME_SUNSET, THEME_FOREST -> MODE_DARK
            else -> MODE_SYSTEM
        }
        setThemeMode(context, mode)
    }

    fun getCurrentTheme(context: Context): String = getCurrentMode(context)

    private fun migrateLegacyPrefsIfNeeded(context: Context) {
        val currentPrefs = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        if (currentPrefs.contains(KEY_THEME_MODE)) return
        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyMode = legacyPrefs.getString(KEY_THEME_MODE, null) ?: return
        currentPrefs.edit().putString(KEY_THEME_MODE, legacyMode).apply()
    }
}
