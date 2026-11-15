package com.hopa.app.feature.settings

import com.hopa.capabilities.llm.ProviderState

/** One word for the model row in the hub. */
fun providerSummary(state: SettingsUiState): String = when (state.providerState) {
    ProviderState.CLOUD -> "Cloud"
    ProviderState.LAN -> "LAN"
    ProviderState.DEMO -> "Demo"
    ProviderState.NO_MODEL -> "No key"
}
