package com.mirrormood.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.data.repository.MoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repository: MoodRepository
) : ViewModel() {

    /** Raw stream of all entries, newest first. */
    private val _allEntries = MutableStateFlow<List<MoodEntry>>(emptyList())

    /** Active mood filter — null means "show all". */
    private val _moodFilter = MutableStateFlow<String?>(null)
    val moodFilter: StateFlow<String?> = _moodFilter.asStateFlow()

    /** Free-text search query. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Filtered entries exposed to the UI. */
    val entries: StateFlow<List<MoodEntry>> = combine(
        _allEntries,
        _moodFilter,
        _searchQuery
    ) { all, filter, query ->
        var result = all

        // Apply mood filter
        if (filter != null) {
            result = result.filter { it.mood == filter }
        }

        // Apply text search (case-insensitive, matches note content)
        if (query.isNotBlank()) {
            val lowerQuery = query.lowercase()
            result = result.filter { entry ->
                entry.note?.lowercase()?.contains(lowerQuery) == true ||
                entry.mood.lowercase().contains(lowerQuery)
            }
        }

        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        loadAllEntries()
    }

    private fun loadAllEntries() {
        viewModelScope.launch {
            repository.getAllMoods().collect { moodList ->
                _allEntries.value = moodList
            }
        }
    }

    fun setMoodFilter(mood: String?) {
        _moodFilter.value = mood
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateNote(entryId: Int, note: String) {
        viewModelScope.launch {
            repository.updateNote(entryId, note)
        }
    }

    fun saveEntry(mood: String, note: String, triggers: String? = null) {
        viewModelScope.launch {
            repository.saveMood(
                MoodEntry(
                    mood = mood,
                    smileScore = 0f,
                    eyeOpenScore = 0f,
                    note = note,
                    triggers = triggers
                )
            )
        }
    }

    fun deleteEntry(entryId: Int) {
        viewModelScope.launch {
            repository.deleteMood(entryId)
        }
    }
}
