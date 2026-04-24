package com.mirrormood.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.data.repository.MoodRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WearDataReceiverService : WearableListenerService() {

    @Inject
    lateinit var moodRepository: MoodRepository

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)

        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                if (path == "/quick_log") {
                    val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                    val dataMap = dataMapItem.dataMap
                    
                    val rawMood = dataMap.getString("mood") ?: continue
                    val timestamp = dataMap.getLong("timestamp", System.currentTimeMillis())
                    
                    // Normalize legacy watch moods to phone categories
                    val mood = when (rawMood) {
                        "Calm" -> "Neutral"
                        "Excited" -> "Happy"
                        "Sad" -> "Stressed"
                        else -> rawMood
                    }
                    
                    // We don't have facial markers from the watch,
                    // so we use placeholder scores but full confidence for manual input.
                    val entry = MoodEntry(
                        timestamp = timestamp,
                        mood = mood,
                        smileScore = if (mood == "Happy") 1f else 0f,
                        eyeOpenScore = 0.5f,
                        confidence = 1.0f,
                        note = "Quick logged from Wear OS"
                    )
                    
                    scope.launch {
                        moodRepository.saveMood(entry)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
