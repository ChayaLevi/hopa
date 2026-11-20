package com.hopa.feature.runs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The runs screen: the audit trail, chain verification and clearing it. */
@HiltViewModel
class RunsViewModel @Inject constructor(
    private val deps: RunsDependencies,
) : ViewModel() {
    private val _state = MutableStateFlow(RunsUiState())
    val state: StateFlow<RunsUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val records = deps.recorder.all()
            _state.update { it.copy(records = records) }
        }
    }

    suspend fun verify(runId: String): Boolean = deps.recorder.verify(runId)

    suspend fun clearAll() {
        deps.recorder.clear()
        _state.update { it.copy(records = emptyList()) }
    }
}
