package com.hopa.app.feature.execution

import com.hopa.app.feature.settings.SettingsUiState

/**
 * The saved path, read the way the Act screens talk about it: which machine
 * sends the taps, and on this phone whether the work is visible. The computer
 * is its own driver and never becomes a silent fallback for the phone.
 */
data class ActMode(
    val onComputer: Boolean,
    val hidden: Boolean,
    val fallback: Boolean,
    val hiddenHelper: Boolean,
    val phoneAccess: Boolean,
    val pausedByYou: Boolean,
    val computerPaired: Boolean,
    val computerEnabled: Boolean,
) {
    val hiddenReady: Boolean get() = hiddenHelper && phoneAccess

    val screenReady: Boolean get() = phoneAccess

    val computerReady: Boolean get() = computerPaired && computerEnabled

    companion object {
        fun of(state: SettingsUiState): ActMode {
            val path = state.settings.screenPath
            val onComputer = path == "bridge"
            return ActMode(
                onComputer = onComputer,
                hidden = !onComputer && path != "onscreen",
                fallback = path == "auto",
                hiddenHelper = state.backgroundRunning,
                phoneAccess = state.phoneAccess,
                pausedByYou = !state.settings.accessibilityAllowed,
                computerPaired = state.settings.bridgeUrl.isNotBlank() &&
                    state.bridgeKey.isNotBlank(),
                computerEnabled = state.settings.useBridge,
            )
        }
    }
}
