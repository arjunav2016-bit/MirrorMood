package com.mirrormood.service

import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mirrormood.MirrorMoodApp

class MoodTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        val isPaused = prefs.getBoolean("monitoring_paused", false)
        
        if (isPaused) {
            prefs.edit().putBoolean("monitoring_paused", false).apply()
        } else {
            val resumeAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
            prefs.edit()
                .putBoolean("monitoring_paused", true)
                .putLong("monitoring_resume_at", resumeAt)
                .apply()
        }
        
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        val isPaused = prefs.getBoolean("monitoring_paused", false)

        if (isPaused) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "MirrorMood Paused"
            tile.subtitle = "Tracking suspended"
        } else {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "MirrorMood"
            tile.subtitle = "Tracking active"
        }

        tile.updateTile()
    }
}
