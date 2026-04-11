package com.mirrormood.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.data.repository.MoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repository: MoodRepository
) : ViewModel() {
    
    private val _entries = MutableStateFlow<List<MoodEntry>>(emptyList())
    val entries: StateFlow<List<MoodEntry>> = _entries.asStateFlow()

    init {
        loadTodayEntries()
    }

    private fun loadTodayEntries() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000

        viewModelScope.launch {
            repository.getMoodsForDayNewestFirst(startOfDay, endOfDay).collect { moodList ->
                _entries.value = moodList
            }
        }
    }

    fun updateNote(entryId: Int, note: String) {
        viewModelScope.launch {
            repository.updateNote(entryId, note)
        }
    }

    fun saveEntry(mood: String, note: String) {
        viewModelScope.launch {
            repository.saveMood(
                MoodEntry(
                    mood = mood,
                    smileScore = 0f,
                    eyeOpenScore = 0f,
                    note = note
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
