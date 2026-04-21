package com.mirrormood.ui.recommendations

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.mirrormood.R
import com.mirrormood.data.WellnessRecommendation
import com.mirrormood.data.repository.WellnessRepository
import com.mirrormood.util.BottomNavHelper
import com.mirrormood.util.BottomNavTab
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper

import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.OnBackPressedCallback

@AndroidEntryPoint
class RecommendationsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MOOD = "extra_mood"
    }

    private lateinit var rvRecommendations: RecyclerView
    private var allTips: List<WellnessRecommendation> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recommendations)

        val mood = intent.getStringExtra(EXTRA_MOOD) ?: "Neutral"

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            finish()
            slideTransition(forward = false)
        }

        setupHeader(mood)
        setupRecycler()
        setupFilterChips()
        BottomNavHelper.setup(this, BottomNavTab.ADVICE)

        // Consistent back navigation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                slideTransition(forward = false)
            }
        })
    }

    private fun setupHeader(mood: String) {
        val headerCard = findViewById<MaterialCardView>(R.id.headerCard)
        val tvEmoji = findViewById<TextView>(R.id.tvHeaderEmoji)
        val tvTitle = findViewById<TextView>(R.id.tvHeaderTitle)
        val tvTipCount = findViewById<TextView>(R.id.tvHeaderTipCount)

        val config = when (mood) {
            "Stressed" -> HeaderConfig("\uD83D\uDE30", "Exhale into steadier ground.", R.color.mm_mood_stressed)
            "Tired" -> HeaderConfig("\uD83D\uDE34", "Restore the signal.", R.color.mm_mood_tired)
            "Bored" -> HeaderConfig("\uD83D\uDE12", "Invite a fresh current.", R.color.mm_mood_bored)
            "Happy" -> HeaderConfig("\uD83D\uDE0A", "Keep the glow moving.", R.color.mm_mood_happy)
            "Focused" -> HeaderConfig("\uD83E\uDDE0", "Protect the clarity.", R.color.mm_mood_focused)
            else -> HeaderConfig("\uD83D\uDE10", "A gentle reset is enough.", R.color.mm_mood_neutral)
        }

        allTips = WellnessRepository.getRecommendations(mood)

        tvEmoji.text = config.emoji
        tvTitle.text = config.title
        tvTipCount.text = resources.getQuantityString(R.plurals.recommendations_tip_count, allTips.size, allTips.size)
        headerCard.alpha = 0f
        headerCard.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    private fun setupRecycler() {
        rvRecommendations = findViewById(R.id.rvRecommendations)
        rvRecommendations.layoutManager = LinearLayoutManager(this)
        rvRecommendations.setItemViewCacheSize(8)
        rvRecommendations.adapter = TipAdapter(allTips)
        rvRecommendations.alpha = 0f
        rvRecommendations.translationY = 50f
        rvRecommendations.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .start()
    }

    private fun setupFilterChips() {
        val chipGroup = findViewById<ChipGroup>(R.id.categoryFilter)
        val filterMap = mapOf(
            R.id.chipAll to null,
            R.id.chipBreathing to "Breathing",
            R.id.chipActivity to "Activity",
            R.id.chipMindset to "Mindset",
            R.id.chipSelfCare to "Self-Care"
        )

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipAll
            val filter = filterMap[checkedId]
            val filtered = if (filter == null) allTips else allTips.filter { it.category == filter }
            rvRecommendations.adapter = TipAdapter(filtered)
        }
    }

    private data class HeaderConfig(
        val emoji: String,
        val title: String,
        val tintRes: Int
    )

    private inner class TipAdapter(
        private val tips: List<WellnessRecommendation>
    ) : RecyclerView.Adapter<TipAdapter.TipViewHolder>() {

        private val categoryColors = mapOf(
            "Breathing" to R.color.mm_mood_tired,
            "Activity" to R.color.mm_mood_happy,
            "Mindset" to R.color.mm_mood_focused,
            "Self-Care" to R.color.mm_mood_bored
        )

        private val categoryDurations = mapOf(
            "Breathing" to "2-5 min",
            "Activity" to "5-15 min",
            "Mindset" to "3-5 min",
            "Self-Care" to "5-10 min"
        )

        inner class TipViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_recommendation, parent, false)
        ) {
            val tvEmoji: TextView = itemView.findViewById(R.id.tvTipEmoji)
            val tvTitle: TextView = itemView.findViewById(R.id.tvTipTitle)
            val tvCategory: TextView = itemView.findViewById(R.id.tvTipCategory)
            val tvDescription: TextView = itemView.findViewById(R.id.tvTipDescription)
            val tvDuration: TextView = itemView.findViewById(R.id.tvTipDuration)
            val viewAccentBar: android.view.View = itemView.findViewById(R.id.viewAccentBar)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipViewHolder = TipViewHolder(parent)

        override fun onBindViewHolder(holder: TipViewHolder, position: Int) {
            val tip = tips[position]
            holder.tvEmoji.text = tip.emoji
            holder.tvTitle.text = tip.title
            holder.tvCategory.text = tip.category
            holder.tvDescription.text = tip.description
            holder.tvDuration.text = categoryDurations[tip.category] ?: "3-5 min"

            val colorRes = categoryColors[tip.category] ?: R.color.mm_primary
            val color = ContextCompat.getColor(this@RecommendationsActivity, colorRes)
            val bg = holder.viewAccentBar.background
            if (bg is android.graphics.drawable.GradientDrawable) {
                bg.setColor(color)
            } else {
                val gd = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 2f * resources.displayMetrics.density
                    setColor(color)
                }
                holder.viewAccentBar.background = gd
            }
        }

        override fun getItemCount(): Int = tips.size
    }
}
