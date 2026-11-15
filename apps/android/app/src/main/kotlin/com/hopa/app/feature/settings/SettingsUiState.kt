package com.hopa.app.feature.settings

import com.hopa.capabilities.llm.ProviderState
import com.hopa.data.settings.AppSettings

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
    /** Lines from the screen-access self-check, one per step. */
    val screenCheck: List<String> = emptyList(),
    /** True when the accessibility service is enabled and bound now. */
    val phoneAccess: Boolean = false,
)
