package com.hopa.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The profiles screen: which shell HOPA uses. */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val deps: ProfilesDependencies,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfilesUiState())
    val state: StateFlow<ProfilesUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val profile = deps.settings.settings.first().profile
            _state.update { it.copy(selected = profile) }
        }
    }

    fun select(profile: String) {
        _state.update { it.copy(selected = profile) }
    }

    suspend fun save(profile: String) {
        deps.settings.update { it.copy(profile = profile) }
    }
}
