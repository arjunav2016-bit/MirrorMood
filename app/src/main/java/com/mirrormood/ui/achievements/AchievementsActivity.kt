package com.mirrormood.ui.achievements

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mirrormood.R
import com.mirrormood.data.Milestone
import com.mirrormood.databinding.ActivityAchievementsBinding
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.activity.OnBackPressedCallback

@AndroidEntryPoint
class AchievementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAchievementsBinding
    private val viewModel: AchievementsViewModel by viewModels()
    private val adapter = AchievementAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityAchievementsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.rvAchievements.layoutManager = GridLayoutManager(this, 2)
        binding.rvAchievements.adapter = adapter
        binding.rvAchievements.itemAnimator = null

        binding.btnBack.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            finish()
            slideTransition(forward = false)
        }

        // Consistent back navigation via system gesture/button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                slideTransition(forward = false)
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.milestones.collect { milestones ->
                    renderSummary(milestones)
                    adapter.submitList(milestones)
                }
            }
        }

        playEntranceAnimations()
    }

    private fun playEntranceAnimations() {
        val offsetPx = 40 * resources.displayMetrics.density
        val views = listOfNotNull(
            binding.root.findViewById<View>(R.id.tvUnlockedCount),
            binding.progressOverall,
            binding.rvAchievements
        )
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = offsetPx
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 80).toLong())
                .setDuration(450)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.8f))
                .start()
        }
    }

    private fun renderSummary(milestones: List<Milestone>) {
        val unlocked = milestones.count { it.isUnlocked }
        val total = milestones.size
        binding.tvUnlockedCount.text = unlocked.toString()
        binding.tvTotalCount.text = getString(R.string.achievements_of_total, total)
        binding.progressOverall.max = total * 100
        binding.progressOverall.setProgressCompat(unlocked * 100, true)
    }

    private class AchievementAdapter : ListAdapter<Milestone, AchievementAdapter.VH>(DiffCallback) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_achievement, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val emoji: TextView = view.findViewById(R.id.tvBadgeEmoji)
            private val title: TextView = view.findViewById(R.id.tvAchievementTitle)
            private val desc: TextView = view.findViewById(R.id.tvAchievementDesc)
            private val progress: LinearProgressIndicator = view.findViewById(R.id.progressBar)
            private val label: TextView = view.findViewById(R.id.tvProgressLabel)

            fun bind(milestone: Milestone) {
                emoji.text = milestone.emoji
                title.text = milestone.title
                desc.text = milestone.description

                if (milestone.isUnlocked) {
                    progress.setProgressCompat(100, false)
                    label.text = itemView.context.getString(R.string.achievements_unlocked_label)
                    itemView.alpha = 1f

                    // Glow animation for newly unlocked
                    emoji.scaleX = 1f
                    emoji.scaleY = 1f
                } else {
                    progress.setProgressCompat(milestone.progress, false)
                    label.text = itemView.context.getString(R.string.achievements_progress_label, milestone.currentAmount, milestone.target)
                    itemView.alpha = 0.7f
                }
            }
        }

        object DiffCallback : DiffUtil.ItemCallback<Milestone>() {
            override fun areItemsTheSame(a: Milestone, b: Milestone) = a.id == b.id
            override fun areContentsTheSame(a: Milestone, b: Milestone) = a == b
        }
    }
}
