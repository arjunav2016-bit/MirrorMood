package com.mirrormood.ui.correlations

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
class CorrelationsViewModel @Inject constructor(
    private val repository: MoodRepository
) : ViewModel() {

    private val _entries = MutableStateFlow<List<MoodEntry>>(emptyList())
    val entries: StateFlow<List<MoodEntry>> = _entries.asStateFlow()

    init {
        loadRecentEntries()
    }

    private fun loadRecentEntries() {
        viewModelScope.launch {
            val now = Calendar.getInstance()
            val endMs = now.timeInMillis
            now.add(Calendar.DAY_OF_YEAR, -30)
            now.set(Calendar.HOUR_OF_DAY, 0)
            now.set(Calendar.MINUTE, 0)
            now.set(Calendar.SECOND, 0)
            val startMs = now.timeInMillis

            val data = repository.getMoodsForRange(startMs, endMs)
            _entries.value = data
        }
    }
}
