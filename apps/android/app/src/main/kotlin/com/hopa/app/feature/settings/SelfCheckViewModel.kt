package com.hopa.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopa.app.di.AppContainer
import com.hopa.bridge.session.BridgeClient
import com.hopa.capabilities.accessibility.state.AccessibilityState
import com.hopa.capabilities.accessibility.state.HopaAccessibilityService
import com.hopa.feature.settings.SelfCheckUiState
import com.hopa.inference.choice.ProviderState
import com.hopa.inference.choice.ProviderStateResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the self check reads, gathered once per visit. */
@HiltViewModel
class SelfCheckViewModel @Inject constructor(
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(SelfCheckUiState())
    val state: StateFlow<SelfCheckUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _state.value = collect() }
    }

    fun retry() {
        _state.update { it.copy(checking = true) }
        viewModelScope.launch { _state.value = collect() }
    }

    private suspend fun collect(): SelfCheckUiState {
        val settings = container.settings.settings.first()
        val key = container.settings.modelKey()
        val token = container.settings.bridgeToken()
        val provider = ProviderStateResolver().resolve(settings.provider, settings.endpoint, key)
        val bridgeOk = if (settings.bridgeUrl.isNotBlank() && token.isNotBlank()) {
            runCatching { BridgeClient(settings.bridgeUrl, token).health() }.isSuccess
        } else {
            null
        }
        return SelfCheckUiState(
            provider = settings.provider,
            model = provider.label(),
            bridgeUrl = settings.bridgeUrl,
            bridgeOk = bridgeOk,
            accessibilityEnabled = AccessibilityState.isEnabled(container.context),
            accessibilityLive = HopaAccessibilityService.instance != null,
            sessions = runCatching { container.conversations.all().size }.getOrDefault(0),
            records = runCatching { container.recorder.all().size }.getOrDefault(0),
            facts = runCatching { container.memory.all().size }.getOrDefault(0),
            skills = runCatching { container.skills.all().size }.getOrDefault(0),
            checking = false,
        )
    }
}

private fun ProviderState.label(): String = when (this) {
    ProviderState.CLOUD -> "Cloud model"
    ProviderState.LAN -> "LAN model"
    ProviderState.ON_DEVICE -> "Model on this phone"
    ProviderState.DEMO -> "Demo model (no key)"
    ProviderState.NO_MODEL -> "No model"
}
