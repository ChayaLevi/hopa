package com.hopa.app.feature.settings

import com.hopa.inference.choice.ProviderState

/** One plain word for the answers tile. */
fun providerSummary(state: SettingsUiState): String = when (state.providerState) {
    ProviderState.CLOUD -> "Cloud"
    ProviderState.LAN -> "Home network"
    ProviderState.DEMO -> "Demo"
    ProviderState.NO_MODEL -> "Needs a key"
}
