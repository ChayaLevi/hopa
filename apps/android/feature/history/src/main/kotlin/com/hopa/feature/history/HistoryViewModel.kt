package com.hopa.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The run history: the filter and the rows it selects. */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val deps: HistoryDependencies,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch { load(_state.value.filter) }
    }

    fun setFilter(filter: String) {
        _state.update { it.copy(filter = filter) }
        viewModelScope.launch { load(filter) }
    }

    fun clearAll() {
        viewModelScope.launch {
            deps.runHistory.clear()
            _state.update { it.copy(rows = emptyList()) }
        }
    }

    private suspend fun load(filter: String) {
        val rows = deps.runHistory.latest(filter.ifBlank { null })
        _state.update { it.copy(rows = rows) }
    }
}
