package com.hopa.feature.memory

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.hopa.feature.memory.MemoryDependencies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val container: MemoryDependencies,
) : ViewModel() {
    private val _state = MutableStateFlow(MemoryUiState())
    val state: StateFlow<MemoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun addFact(text: String) {
        if (text.isBlank()) {
            return
        }
        viewModelScope.launch {
            container.memory.add(text)
            load()
        }
    }

    fun deleteFact(text: String) {
        viewModelScope.launch {
            container.memory.delete(text)
            load()
        }
    }

    fun clearFacts() {
        viewModelScope.launch {
            container.memory.clear()
            load()
        }
    }

    private suspend fun load() {
        _state.update {
            it.copy(
                facts = container.memory.all(),
                busy = false,
            )
        }
    }
}
