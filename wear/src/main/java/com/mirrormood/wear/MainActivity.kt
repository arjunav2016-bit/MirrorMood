package com.mirrormood.wear

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.mirrormood.wear.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Mood labels aligned with phone app categories
        val moods = listOf(
            binding.btnHappy to "Happy",
            binding.btnCalm to "Neutral",
            binding.btnExcited to "Focused",
            binding.btnTired to "Tired",
            binding.btnSad to "Bored",
            binding.btnStressed to "Stressed"
        )

        for ((btn, mood) in moods) {
            btn.setOnClickListener {
                logMood(mood)
            }
        }
    }

    private fun logMood(mood: String) {
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.wear_syncing)
        binding.tvStatus.setTextColor(android.graphics.Color.YELLOW)

        scope.launch {
            try {
                val dataClient = Wearable.getDataClient(this@MainActivity)
                val putDataMapReq = PutDataMapRequest.create("/quick_log")
                
                // Add a unique timestamp so the event is always considered "changed"
                putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())
                putDataMapReq.dataMap.putString("mood", mood)
                
                val putDataReq = putDataMapReq.asPutDataRequest()
                putDataReq.setUrgent()

                Tasks.await(dataClient.putDataItem(putDataReq))
                
                runOnUiThread {
                    binding.tvStatus.text = getString(R.string.wear_logged, mood)
                    binding.tvStatus.setTextColor(android.graphics.Color.GREEN)
                    hideStatusDelayed()
                }
            } catch (e: Exception) {
                Log.e("Wear", "Sync failed", e)
                runOnUiThread {
                    binding.tvStatus.text = getString(R.string.wear_sync_failed)
                    binding.tvStatus.setTextColor(android.graphics.Color.RED)
                    hideStatusDelayed()
                }
            }
        }
    }

    private fun hideStatusDelayed() {
        Handler(Looper.getMainLooper()).postDelayed({
            binding.tvStatus.visibility = View.GONE
        }, 2000)
    }
}
