package com.mirrormood.ui.wellness

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mirrormood.data.repository.WellnessSessionRepository
import com.mirrormood.databinding.ActivitySessionHistoryBinding
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SessionHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySessionHistoryBinding
    private val adapter = SessionHistoryAdapter()

    @Inject lateinit var repository: WellnessSessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySessionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            finish()
            slideTransition(forward = false)
        }

        binding.rvSessions.layoutManager = LinearLayoutManager(this)
        binding.rvSessions.adapter = adapter

        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            // Load stats
            val totalCount = withContext(Dispatchers.IO) { repository.getTotalCount() }
            val totalMs = withContext(Dispatchers.IO) { repository.getTotalDurationMs() }
            val totalMinutes = totalMs / 60_000

            binding.tvTotalSessions.text = totalCount.toString()
            binding.tvTotalMinutes.text = "${totalMinutes}m"

            // Observe session list
            repository.getAllSessions().collect { sessions ->
                if (sessions.isEmpty()) {
                    binding.rvSessions.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                } else {
                    binding.rvSessions.visibility = View.VISIBLE
                    binding.emptyState.visibility = View.GONE
                    adapter.submitList(sessions)

                    // Favorite type
                    val favorite = sessions.groupingBy { it.type }.eachCount()
                        .maxByOrNull { it.value }?.key ?: "Breathing"
                    binding.tvFavoriteType.text = when (favorite) {
                        "Breathing" -> "🫧"
                        "Body Scan" -> "🧘"
                        "Gratitude" -> "🙏"
                        else -> "✨"
                    }
                }
            }
        }
    }
}
