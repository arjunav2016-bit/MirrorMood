package com.mirrormood.util

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.mirrormood.ui.settings.SettingsActivity
import com.mirrormood.util.MoodUtils.slideTransition

/**
 * Backward-compatible wrapper around the dedicated settings screen.
 */
class SettingsDialogHelper(private val activity: AppCompatActivity) {

    fun showSettingsMenu() {
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
        activity.slideTransition(forward = true)
    }
}
