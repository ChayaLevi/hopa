package com.hopa.app.feature.settings

import com.hopa.inference.choice.ProviderState
import com.hopa.stores.AppSettings

/** What the Answers page needs to render after a save or a fresh read. */
internal data class ModelSnapshot(
    val settings: AppSettings,
    val key: String,
    val keySaved: Boolean,
    val providerState: ProviderState,
    val error: String?,
)
