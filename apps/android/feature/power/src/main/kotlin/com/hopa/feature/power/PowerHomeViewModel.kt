package com.hopa.feature.power

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Power home: what ran recently. */
@HiltViewModel
class PowerHomeViewModel @Inject constructor(
    private val deps: PowerDependencies,
) : ViewModel() {
    private val _state = MutableStateFlow(PowerHomeUiState())
    val state: StateFlow<PowerHomeUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val recent = deps.recorder.all()
            _state.update { it.copy(recent = recent) }
        }
    }
}
