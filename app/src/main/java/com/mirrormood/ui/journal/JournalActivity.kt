package com.mirrormood.ui.journal

import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mirrormood.R
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.databinding.ActivityJournalBinding
import com.mirrormood.databinding.ItemJournalEntryBinding
import com.mirrormood.databinding.ItemJournalHeaderBinding
import com.mirrormood.util.BottomNavHelper
import com.mirrormood.util.BottomNavTab
import com.mirrormood.util.MoodUtils
import com.mirrormood.util.MoodUtils.slideTransition
import com.mirrormood.util.ThemeHelper
import com.mirrormood.util.VoiceJournalHelper
import com.mirrormood.data.repository.PromptEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class JournalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJournalBinding
    private val viewModel: JournalViewModel by viewModels()
    private lateinit var voiceHelper: VoiceJournalHelper
    private val entryAdapter = JournalAdapter(
        onNoteSaved = { entryId, note -> viewModel.updateNote(entryId, note) },
        onDeleteRequested = { entry -> showDeleteConfirmation(entry) }
    )
    private val headerAdapter by lazy {
        JournalHeaderAdapter(
            dateLabel = currentJournalDateLabel(),
            onBackClicked = { finishWithTransition() },
            onSaveClicked = { mood, note -> saveEntry(mood, note) },
            onSearchChanged = { query -> viewModel.setSearchQuery(query) },
            onFilterChanged = { mood -> viewModel.setMoodFilter(mood) },
            formatWordCount = { count ->
                resources.getQuantityString(R.plurals.journal_word_count, count, count)
            },
            countWords = ::fastWordCount,
            onVoiceClicked = { toggleVoiceInput() },
            onPromptClicked = { prompt -> applyPromptToEditor(prompt) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityJournalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.rvJournal.layoutManager = LinearLayoutManager(this)
        binding.rvJournal.adapter = ConcatAdapter(headerAdapter, entryAdapter)
        binding.rvJournal.setHasFixedSize(false)
        binding.rvJournal.itemAnimator = null
        binding.rvJournal.setItemViewCacheSize(8)

        observeEntries()
        BottomNavHelper.setup(this, BottomNavTab.NONE)

        voiceHelper = VoiceJournalHelper(this)
    }

    override fun onDestroy() {
        voiceHelper.destroy()
        super.onDestroy()
    }

    private fun toggleVoiceInput() {
        if (!voiceHelper.isAvailable) {
            Toast.makeText(this, R.string.voice_journal_not_available, Toast.LENGTH_SHORT).show()
            return
        }

        if (voiceHelper.isCurrentlyListening()) {
            voiceHelper.stopListening()
        } else {
            voiceHelper.startListening(
                onResult = { text ->
                    headerAdapter.appendDraftText(text)
                    val prefs = getSharedPreferences("mirrormood_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt(
                        "voice_journal_count",
                        prefs.getInt("voice_journal_count", 0) + 1
                    ).apply()
                },
                onPartial = { /* could show partial in UI */ },
                onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() },
                onListeningStateChanged = { listening ->
                    headerAdapter.setVoiceListening(listening)
                }
            )
        }
    }

    private fun applyPromptToEditor(prompt: String) {
        headerAdapter.appendDraftText(prompt)
    }

    private fun saveEntry(mood: String, note: String): Boolean {
        if (note.isBlank()) {
            Toast.makeText(this, R.string.journal_save_prompt, Toast.LENGTH_SHORT).show()
            return false
        }

        viewModel.saveEntry(mood, note)
        Toast.makeText(this, R.string.journal_saved, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun finishWithTransition() {
        finish()
        slideTransition(forward = false)
    }

    private fun currentJournalDateLabel(): String {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(
            Locale.getDefault(),
            "EEEE MMMM d"
        )
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }

    private fun observeEntries() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.entries.collect { entries ->
                    headerAdapter.setArchiveEmpty(entries.isEmpty())
                    entryAdapter.submitList(entries)
                }
            }
        }
    }

    private fun showDeleteConfirmation(entry: MoodEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.journal_delete_entry_title)
            .setMessage(getString(R.string.journal_delete_entry_message, entry.mood))
            .setPositiveButton(R.string.journal_delete_confirm) { _, _ ->
                viewModel.deleteEntry(entry.id)
                Toast.makeText(this, R.string.journal_entry_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun fastWordCount(text: String): Int {
        var count = 0
        var inWord = false
        text.forEach { char ->
            if (char.isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                count++
            }
        }
        return count
    }

    private class JournalHeaderAdapter(
        private val dateLabel: String,
        private val onBackClicked: () -> Unit,
        private val onSaveClicked: (String, String) -> Boolean,
        private val onSearchChanged: (String) -> Unit,
        private val onFilterChanged: (String?) -> Unit,
        private val formatWordCount: (Int) -> String,
        private val countWords: (String) -> Int,
        private val onVoiceClicked: () -> Unit,
        private val onPromptClicked: (String) -> Unit
    ) : RecyclerView.Adapter<JournalHeaderAdapter.ViewHolder>() {

        private var isArchiveEmpty = true
        private var selectedMood = "Happy"
        private var draftNote = ""
        private var attachedHolder: ViewHolder? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemJournalHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            attachedHolder = holder
            holder.bind()
        }

        override fun onViewRecycled(holder: ViewHolder) {
            if (attachedHolder === holder) {
                attachedHolder = null
            }
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int = 1

        fun setArchiveEmpty(isEmpty: Boolean) {
            if (isArchiveEmpty != isEmpty) {
                isArchiveEmpty = isEmpty
                attachedHolder?.updateEmptyState(isEmpty)
            }
        }

        fun appendDraftText(text: String) {
            draftNote = if (draftNote.isBlank()) text else "$draftNote $text"
            attachedHolder?.syncDraft(draftNote)
        }

        fun setVoiceListening(listening: Boolean) {
            attachedHolder?.updateVoiceState(listening)
        }

        private val filterChipMoodMap = mapOf(
            R.id.chipFilterAll to null,
            R.id.chipFilterHappy to "Happy",
            R.id.chipFilterFocused to "Focused",
            R.id.chipFilterNeutral to "Neutral",
            R.id.chipFilterStressed to "Stressed",
            R.id.chipFilterTired to "Tired",
            R.id.chipFilterBored to "Bored"
        )

        private inner class ViewHolder(
            private val binding: ItemJournalHeaderBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            private val chipMoodMap = mapOf(
                R.id.chipHappy to "Happy",
                R.id.chipFocused to "Focused",
                R.id.chipNeutral to "Neutral",
                R.id.chipStressed to "Stressed",
                R.id.chipTired to "Tired",
                R.id.chipBored to "Bored"
            )
            private var isSyncingComposer = false

            init {
                binding.btnBack.setOnClickListener { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onBackClicked()
                }

                binding.chipGroupMood.setOnCheckedStateChangeListener { _, checkedIds ->
                    if (isSyncingComposer) return@setOnCheckedStateChangeListener
                    val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
                    selectedMood = chipMoodMap[checkedId] ?: "Happy"
                }

                binding.etJournalText.doAfterTextChanged { editable ->
                    if (isSyncingComposer) return@doAfterTextChanged
                    draftNote = editable?.toString().orEmpty()
                    binding.tvWordCount.text = formatWordCount(countWords(draftNote))
                }

                binding.btnSaveEntry.setOnClickListener { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    val trimmedNote = draftNote.trim()
                    if (onSaveClicked(selectedMood, trimmedNote)) {
                        selectedMood = "Happy"
                        draftNote = ""
                        bindComposerState()
                    }
                }

                // Voice journaling
                binding.btnVoiceJournal.setOnClickListener { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onVoiceClicked()
                }

                // Search bar
                binding.etSearch.doAfterTextChanged { editable ->
                    onSearchChanged(editable?.toString().orEmpty())
                }

                // Filter chips
                binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
                    val checkedId = checkedIds.firstOrNull()
                    if (checkedId == null || checkedId == R.id.chipFilterAll) {
                        // No chip or "All" chip selected -> clear filter
                        if (checkedId == null) {
                            // Re-select "All" when everything is deselected
                            binding.chipGroupFilter.check(R.id.chipFilterAll)
                        }
                        onFilterChanged(null)
                    } else {
                        onFilterChanged(filterChipMoodMap[checkedId])
                    }
                }
            }

            fun bind() {
                binding.tvJournalDate.text = dateLabel
                updateEmptyState(isArchiveEmpty)
                bindComposerState()
                populateSmartPrompts()
            }

            fun syncDraft(text: String) {
                isSyncingComposer = true
                binding.etJournalText.setText(text)
                binding.etJournalText.setSelection(text.length)
                binding.tvWordCount.text = formatWordCount(countWords(text))
                isSyncingComposer = false
            }

            fun updateVoiceState(listening: Boolean) {
                binding.tvVoiceStatus.visibility = if (listening) View.VISIBLE else View.GONE
                binding.tvVoiceStatus.text = if (listening) {
                    binding.root.context.getString(R.string.voice_journal_listening)
                } else {
                    binding.root.context.getString(R.string.voice_journal_info)
                }
            }

            fun updateEmptyState(isEmpty: Boolean) {
                binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            }

            private fun bindComposerState() {
                val selectedChipId = chipMoodMap.entries
                    .firstOrNull { it.value == selectedMood }
                    ?.key
                    ?: R.id.chipHappy

                isSyncingComposer = true
                if (binding.chipGroupMood.checkedChipId != selectedChipId) {
                    binding.chipGroupMood.check(selectedChipId)
                }

                if (binding.etJournalText.text?.toString() != draftNote) {
                    binding.etJournalText.setText(draftNote)
                    binding.etJournalText.setSelection(draftNote.length)
                }
                binding.tvWordCount.text = formatWordCount(countWords(draftNote))
                isSyncingComposer = false
            }

            private fun populateSmartPrompts() {
                val prompts = PromptEngine.generatePrompts(selectedMood)
                binding.chipGroupSmartPrompts.removeAllViews()
                prompts.forEach { prompt ->
                    val chip = com.google.android.material.chip.Chip(binding.root.context).apply {
                        text = if (prompt.length > 50) prompt.take(47) + "…" else prompt
                        isCheckable = false
                        isClickable = true
                        setOnClickListener {
                            onPromptClicked(prompt)
                        }
                    }
                    binding.chipGroupSmartPrompts.addView(chip)
                }
            }
        }
    }

    private class JournalAdapter(
        private val onNoteSaved: (Int, String) -> Unit,
        private val onDeleteRequested: (MoodEntry) -> Unit
    ) : RecyclerView.Adapter<JournalAdapter.ViewHolder>() {

        private val timeFormatter = java.text.DateFormat.getTimeInstance(
            java.text.DateFormat.SHORT,
            Locale.getDefault()
        )

        private val dateTimeFormatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        private val differ = androidx.recyclerview.widget.AsyncListDiffer(
            this,
            object : DiffUtil.ItemCallback<MoodEntry>() {
                override fun areItemsTheSame(oldItem: MoodEntry, newItem: MoodEntry) =
                    oldItem.id == newItem.id

                override fun areContentsTheSame(oldItem: MoodEntry, newItem: MoodEntry) =
                    oldItem == newItem
            }
        )

        init {
            setHasStableIds(true)
        }

        fun submitList(newEntries: List<MoodEntry>) {
            differ.submitList(newEntries)
        }

        override fun getItemId(position: Int): Long = differ.currentList[position].id.toLong()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemJournalEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding, onNoteSaved, onDeleteRequested)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = differ.currentList[position]
            holder.bind(entry, dateTimeFormatter)
        }

        override fun getItemCount(): Int = differ.currentList.size

        class ViewHolder(
            private val binding: ItemJournalEntryBinding,
            private val onNoteSaved: (Int, String) -> Unit,
            private val onDeleteRequested: (MoodEntry) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            private var currentEntry: MoodEntry? = null

            init {
                binding.etNote.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        currentEntry?.let { entry ->
                            val note = binding.etNote.text?.toString().orEmpty().trim()
                            if (note != (entry.note ?: "")) {
                                onNoteSaved(entry.id, note)
                            }
                        }
                        binding.etNote.clearFocus()
                        true
                    } else {
                        false
                    }
                }

                itemView.setOnLongClickListener {
                    currentEntry?.let { entry ->
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onDeleteRequested(entry)
                        true
                    } ?: false
                }
            }

            fun bind(entry: MoodEntry, dateFormatter: SimpleDateFormat) {
                currentEntry = entry
                binding.tvEmoji.text = MoodUtils.getEmoji(entry.mood)
                binding.tvMood.text = entry.mood
                binding.tvTime.text = dateFormatter.format(Date(entry.timestamp))

                val desiredNote = entry.note.orEmpty()
                if (binding.etNote.text?.toString() != desiredNote) {
                    binding.etNote.setText(desiredNote)
                }

                // Display triggers if present
                if (!entry.triggers.isNullOrBlank()) {
                    binding.tvTriggers.visibility = android.view.View.VISIBLE
                    binding.tvTriggers.text = entry.triggers!!.split(",").joinToString("  ") { tag ->
                        when (tag.trim()) {
                            "Work" -> "💼 Work"
                            "Exercise" -> "🏃 Exercise"
                            "Social" -> "👥 Social"
                            "Sleep" -> "😴 Sleep"
                            "Weather" -> "🌤️ Weather"
                            "Food" -> "🍽️ Food"
                            "Health" -> "💊 Health"
                            "Travel" -> "✈️ Travel"
                            else -> tag.trim()
                        }
                    }
                } else {
                    binding.tvTriggers.visibility = android.view.View.GONE
                }
            }
        }
    }
}
