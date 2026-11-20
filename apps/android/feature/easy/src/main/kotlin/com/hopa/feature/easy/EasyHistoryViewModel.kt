package com.hopa.feature.easy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Easy history list. */
@HiltViewModel
class EasyHistoryViewModel @Inject constructor(
    private val deps: EasyDependencies,
) : ViewModel() {
    private val _state = MutableStateFlow(EasyHistoryUiState())
    val state: StateFlow<EasyHistoryUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val conversations = deps.conversations.all()
                .filter { it.messages.isNotEmpty() }
            _state.update { it.copy(conversations = conversations) }
        }
    }
}
