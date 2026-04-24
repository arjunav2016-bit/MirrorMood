package com.mirrormood.ui.wellness

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mirrormood.R
import com.mirrormood.data.db.WellnessSessionEntity
import com.mirrormood.data.repository.WellnessSessionRepository
import com.mirrormood.databinding.ActivitySessionHistoryBinding
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
        binding.btnStartFirstSession.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            startActivity(Intent(this, WellnessSessionActivity::class.java))
            slideTransition(forward = true)
        }

        binding.rvSessions.layoutManager = LinearLayoutManager(this)
        binding.rvSessions.adapter = adapter

        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            repository.getAllSessions().collect { sessions ->
                renderStats(sessions)
                adapter.submitList(sessions)

                if (sessions.isEmpty()) {
                    binding.rvSessions.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                } else {
                    binding.rvSessions.visibility = View.VISIBLE
                    binding.emptyState.visibility = View.GONE
                }
            }
        }
    }

    private fun renderStats(sessions: List<WellnessSessionEntity>) {
        val totalMinutes = sessions.sumOf { it.durationMs } / 60_000
        val favorite = sessions.groupingBy { it.type }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        binding.tvTotalSessions.text = sessions.size.toString()
        binding.tvTotalMinutes.text = getString(R.string.session_history_minutes_value, totalMinutes)
        binding.tvFavoriteType.text = favorite
            ?.let(WellnessSessionDisplay::emojiFor)
            ?: getString(R.string.session_history_no_favorite)
    }
}
