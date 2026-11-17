package com.hopa.app.feature.settings

import com.hopa.net.llm.ProviderState
import com.hopa.state.settings.AppSettings

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val key: String = "",
    /** True when a key is on disk, even if it cannot be decrypted. */
    val keySaved: Boolean = false,
    val bridgeKey: String = "",
    val providerState: ProviderState = ProviderState.DEMO,
    val busy: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val bridgeStatus: String? = null,
    val notice: String? = null,
    /** The last "Test the model" outcome, shown next to the button. */
    val modelTest: String? = null,
    /** Results of the screen-access self-check, each with its next action. */
    val screenCheck: List<ScreenCheck> = emptyList(),
    /** True when the accessibility service is enabled and bound now. */
    val phoneAccess: Boolean = false,
    /** True when the hidden-display helper answers on loopback now. */
    val backgroundRunning: Boolean = false,
)
