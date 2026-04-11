package com.mirrormood.ui.timeline

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mirrormood.R
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.databinding.ActivityTimelineBinding
import com.mirrormood.databinding.ItemTimelineOverviewBinding
import com.mirrormood.ui.history.HistoryActivity
import com.mirrormood.ui.journal.JournalActivity
import com.mirrormood.util.BottomNavHelper
import com.mirrormood.util.BottomNavTab
import com.mirrormood.util.MoodUtils
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class TimelineActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimelineBinding
    private val viewModel: TimelineViewModel by viewModels()
    private val entryAdapter = TimelineAdapter()
    private val overviewAdapter by lazy {
        TimelineOverviewAdapter(
            onBackClicked = { finishWithTransition() },
            onOpenJournalClicked = { openJournal() },
            onOpenCalendarClicked = { openHistory() }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        binding = ActivityTimelineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        binding.rvTimeline.layoutManager = LinearLayoutManager(this)
        binding.rvTimeline.adapter = ConcatAdapter(overviewAdapter, entryAdapter)
        binding.rvTimeline.setHasFixedSize(false)
        binding.rvTimeline.itemAnimator = null
        binding.rvTimeline.setItemViewCacheSize(12)

        BottomNavHelper.setup(this, BottomNavTab.TIMELINE)
        observeViewModel()
    }

    private fun finishWithTransition() {
        finish()
        slideTransition(forward = false)
    }

    private fun openJournal() {
        startActivity(Intent(this, JournalActivity::class.java))
        slideTransition(forward = true)
    }

    private fun openHistory() {
        val intent = Intent(this, HistoryActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        slideTransition(forward = true)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    overviewAdapter.submitEntries(state.entries)

                    val items = withContext(Dispatchers.Default) {
                        buildTimelineItems(state.entries)
                    }
                    entryAdapter.submitList(items)
                }
            }
        }
    }

    private fun buildTimelineItems(entries: List<MoodEntry>): List<TimelineItem> {
        val headerDateFormat = localizedFormatter("EEEE MMMd")
        val compactDateFormat = localizedFormatter("MMMd")
        val timeFormat = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Locale.getDefault())

        val grouped = entries
            .sortedByDescending { it.timestamp }
            .groupBy { startOfDayMillis(it.timestamp) }

        val generatedItems = mutableListOf<TimelineItem>()

        grouped.forEach { (dayStart, moods) ->
            generatedItems.add(TimelineItem.Header(getTimelineDayLabel(dayStart, headerDateFormat)))

            moods.forEach { mood ->
                generatedItems.add(
                    TimelineItem.Entry(
                        emoji = MoodUtils.getEmoji(mood.mood),
                        mood = mood.mood,
                        time = getString(
                            R.string.timeline_date_time_with_prefix,
                            getTimelineTimePrefix(mood.timestamp, compactDateFormat),
                            timeFormat.format(Date(mood.timestamp)).uppercase(Locale.getDefault())
                        ),
                        note = mood.note
                    )
                )
            }
        }

        return generatedItems
    }

    private fun getTimelineDayLabel(
        dayStart: Long,
        headerDateFormat: SimpleDateFormat
    ): String = when {
        android.text.format.DateUtils.isToday(dayStart) -> getString(R.string.timeline_today)
        isYesterday(dayStart) -> getString(R.string.timeline_yesterday)
        else -> headerDateFormat.format(Date(dayStart))
    }

    private fun getTimelineTimePrefix(
        timestamp: Long,
        compactDateFormat: SimpleDateFormat
    ): String = when {
        android.text.format.DateUtils.isToday(timestamp) -> getString(R.string.timeline_today)
        isYesterday(timestamp) -> getString(R.string.timeline_yesterday)
        else -> compactDateFormat.format(Date(timestamp))
    }

    private fun startOfDayMillis(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun isYesterday(timestamp: Long): Boolean {
        return android.text.format.DateUtils.isToday(
            timestamp + android.text.format.DateUtils.DAY_IN_MILLIS
        )
    }

    private fun localizedFormatter(skeleton: String): SimpleDateFormat {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
        return SimpleDateFormat(pattern, Locale.getDefault())
    }

    sealed class TimelineItem {
        data class Header(val dateLabel: String) : TimelineItem()
        data class Entry(val emoji: String, val mood: String, val time: String, val note: String?) : TimelineItem()
    }

    private class TimelineOverviewAdapter(
        private val onBackClicked: () -> Unit,
        private val onOpenJournalClicked: () -> Unit,
        private val onOpenCalendarClicked: () -> Unit
    ) : RecyclerView.Adapter<TimelineOverviewAdapter.ViewHolder>() {

        private var entries: List<MoodEntry> = emptyList()
        private var attachedHolder: ViewHolder? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemTimelineOverviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            attachedHolder = holder
            holder.bind(entries)
        }

        override fun onViewRecycled(holder: ViewHolder) {
            if (attachedHolder === holder) {
                attachedHolder = null
            }
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int = 1

        fun submitEntries(newEntries: List<MoodEntry>) {
            entries = newEntries
            if (attachedHolder != null) {
                notifyItemChanged(0)
            }
        }

        private inner class ViewHolder(
            private val binding: ItemTimelineOverviewBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            init {
                binding.btnBack.setOnClickListener { onBackClicked() }
                binding.btnOpenJournal.setOnClickListener { onOpenJournalClicked() }
                binding.btnCalendar.setOnClickListener { onOpenCalendarClicked() }
            }

            fun bind(entries: List<MoodEntry>) {
                renderArchiveOverview(entries)
                binding.tvEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            }

            private fun renderArchiveOverview(entries: List<MoodEntry>) {
                if (entries.isEmpty()) {
                    binding.tvArchiveEmoji.text = MoodUtils.getEmoji("Neutral")
                    binding.tvArchiveTitle.text = itemView.context.getString(R.string.timeline_private_archive)
                    binding.tvArchiveCount.text = itemView.context.getString(R.string.timeline_archive_empty)
                    binding.tvSnapshotEmoji.text = MoodUtils.getEmoji("Neutral")
                    binding.tvSnapshotMood.text = itemView.context.getString(R.string.timeline_snapshot_empty)
                    binding.tvSnapshotDate.text = itemView.context.getString(R.string.timeline_snapshot_empty_detail)
                    binding.distributionContainer.removeAllViews()
                    return
                }

                val latest = entries.maxByOrNull { it.timestamp } ?: return
                val dominantMood = entries.groupBy { MoodUtils.normalizeMood(it.mood) }
                    .maxByOrNull { it.value.size }
                    ?.key
                    ?: "Neutral"

                binding.tvArchiveTitle.text = itemView.context.getString(R.string.timeline_archive_title)
                binding.tvArchiveCount.text = itemView.context.resources.getQuantityString(
                    R.plurals.timeline_archive_count_total,
                    entries.size,
                    entries.size
                )
                binding.tvArchiveEmoji.text = MoodUtils.getEmoji(dominantMood)

                binding.tvSnapshotEmoji.text = MoodUtils.getEmoji(latest.mood)
                binding.tvSnapshotMood.text = MoodUtils.getEchoTitle(latest.mood)
                binding.tvSnapshotDate.text = itemView.context.getString(
                    R.string.timeline_snapshot_date_time,
                    MoodUtils.getTimeOfDayLabel(latest.timestamp),
                    MoodUtils.formatTime(latest.timestamp)
                )

                renderDistribution(entries)
            }

            private fun renderDistribution(entries: List<MoodEntry>) {
                binding.distributionContainer.removeAllViews()
                if (entries.isEmpty()) return

                val total = entries.size.coerceAtLeast(1)
                val groupedCounts = entries
                    .groupingBy { MoodUtils.normalizeMood(it.mood) }
                    .eachCount()

                val moodCounts = MoodUtils.supportedMoods.mapNotNull { mood ->
                    val count = groupedCounts[mood] ?: 0
                    if (count > 0) mood to count else null
                }

                moodCounts.forEach { (mood, count) ->
                    val row = LayoutInflater.from(itemView.context)
                        .inflate(R.layout.item_distribution_bar, binding.distributionContainer, false)
                    row.findViewById<TextView>(R.id.tvMoodLabel).text = itemView.context.getString(
                        R.string.timeline_distribution_label,
                        MoodUtils.getEmoji(mood),
                        mood
                    )
                    row.findViewById<TextView>(R.id.tvMoodPercent).text = itemView.context.getString(
                        R.string.timeline_distribution_percent,
                        (count * 100) / total
                    )
                    row.findViewById<LinearProgressIndicator>(R.id.progressMood).apply {
                        progress = ((count * 100) / total).coerceIn(5, 100)
                        setIndicatorColor(
                            ContextCompat.getColor(itemView.context, MoodUtils.getColorRes(mood))
                        )
                    }
                    binding.distributionContainer.addView(row)
                }
            }
        }
    }

    class TimelineAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val differ = androidx.recyclerview.widget.AsyncListDiffer(
            this,
            object : DiffUtil.ItemCallback<TimelineItem>() {
                override fun areItemsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
                    return when {
                        oldItem is TimelineItem.Header && newItem is TimelineItem.Header -> oldItem.dateLabel == newItem.dateLabel
                        oldItem is TimelineItem.Entry && newItem is TimelineItem.Entry -> oldItem.time == newItem.time && oldItem.mood == newItem.mood
                        else -> false
                    }
                }

                override fun areContentsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean =
                    oldItem == newItem
            }
        )

        fun submitList(newItems: List<TimelineItem>) {
            differ.submitList(newItems)
        }

        override fun getItemViewType(position: Int): Int = when (differ.currentList[position]) {
            is TimelineItem.Header -> 0
            is TimelineItem.Entry -> 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                HeaderViewHolder(inflater.inflate(R.layout.item_timeline_header, parent, false))
            } else {
                EntryViewHolder(inflater.inflate(R.layout.item_timeline_entry, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = differ.currentList[position]) {
                is TimelineItem.Header -> (holder as HeaderViewHolder).bind(item)
                is TimelineItem.Entry -> (holder as EntryViewHolder).bind(item)
            }
        }

        override fun getItemCount(): Int = differ.currentList.size

        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvDate: TextView = view.findViewById(R.id.tvHeaderDate)

            fun bind(item: TimelineItem.Header) {
                tvDate.text = item.dateLabel
            }
        }

        class EntryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvMood: TextView = view.findViewById(R.id.tvEntryMood)
            private val tvTime: TextView = view.findViewById(R.id.tvEntryTime)
            private val tvNote: TextView = view.findViewById(R.id.tvEntryNote)
            private val tagsContainer: ChipGroup = view.findViewById(R.id.tagsContainer)
            private val chipMood: Chip = view.findViewById(R.id.chipMood)
            private val chipEmoji: Chip = view.findViewById(R.id.chipEmoji)

            fun bind(item: TimelineItem.Entry) {
                tvMood.text = MoodUtils.getEchoTitle(item.mood)
                tvTime.text = item.time
                tvNote.visibility = View.VISIBLE
                tvNote.text = item.note?.takeIf { it.isNotBlank() } ?: MoodUtils.getReflectionPrompt(item.mood)
                tagsContainer.visibility = View.VISIBLE
                chipMood.text = item.mood
                chipEmoji.text = item.emoji
            }
        }
    }
}
