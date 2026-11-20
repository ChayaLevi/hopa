package com.hopa.feature.capabilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopa.capabilities.accessibility.state.AccessibilityState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The counts on the capabilities page and the two switches they depend on. */
@HiltViewModel
class CapabilitiesViewModel @Inject constructor(
    private val deps: CapabilitiesDependencies,
) : ViewModel() {
    private val _state = MutableStateFlow(CapabilitiesUiState())
    val state: StateFlow<CapabilitiesUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val records = deps.recorder.all().size
            val facts = deps.memory.all().size
            val skills = deps.skills.all().size
            val allowed = deps.settings.settings.first().accessibilityAllowed
            _state.update {
                it.copy(records = records, facts = facts, skills = skills, allowed = allowed)
            }
            AccessibilityState.refresh(deps.context)
        }
    }

    /** Android settings send this app no event; ask again when the tab comes back. */
    fun refreshAccessibility() {
        viewModelScope.launch { AccessibilityState.refresh(deps.context) }
    }
}
