package com.mirrormood.ui.timeline

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

data class TimelineUiState(
    val entries: List<MoodEntry> = emptyList(),
    val isEmpty: Boolean = true
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: MoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        loadAllEntries()
    }

    private fun loadAllEntries() {
        viewModelScope.launch {
            repository.getAllMoods().collect { entries ->
                _uiState.value = TimelineUiState(
                    entries = entries,
                    isEmpty = entries.isEmpty()
                )
            }
        }
    }
}
