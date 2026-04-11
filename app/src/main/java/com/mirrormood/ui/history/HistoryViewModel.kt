package com.mirrormood.ui.history

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

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: MoodRepository
) : ViewModel() {

    private val _monthData = MutableStateFlow<Map<Int, List<MoodEntry>>>(emptyMap())
    val monthData: StateFlow<Map<Int, List<MoodEntry>>> = _monthData.asStateFlow()

    init {
    }

    fun loadMonthData(startOfMonth: Long, endOfMonth: Long) {
        viewModelScope.launch {
            val entries = repository.getMoodsForRange(startOfMonth, endOfMonth)
            
            // Group entries by day of month
            val grouped = entries.groupBy { entry ->
                java.util.Calendar.getInstance().apply {
                    timeInMillis = entry.timestamp
                }.get(java.util.Calendar.DAY_OF_MONTH)
            }
            _monthData.value = grouped
        }
    }
}
