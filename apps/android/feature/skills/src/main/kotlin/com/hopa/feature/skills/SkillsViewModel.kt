package com.hopa.feature.skills

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.hopa.feature.skills.SkillsDependencies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Skills only: adding one no longer reloads the facts on the memory page. */
@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val container: SkillsDependencies,
) : ViewModel() {
    private val _state = MutableStateFlow(SkillsUiState())
    val state: StateFlow<SkillsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun add(trigger: String, action: String) {
        if (trigger.isBlank() || action.isBlank()) {
            return
        }
        viewModelScope.launch {
            container.skills.add(trigger, action)
            load()
        }
    }

    fun delete(trigger: String) {
        viewModelScope.launch {
            container.skills.delete(trigger)
            load()
        }
    }

    fun clear() {
        viewModelScope.launch {
            container.skills.clear()
            load()
        }
    }

    private suspend fun load() {
        _state.update {
            it.copy(skills = container.skills.all(), busy = false)
        }
    }
}
