package com.hopa.app.di

import android.content.Context
import com.hopa.app.link.session.BackendSession
import com.hopa.capabilities.display.client.HelperStatus
import com.hopa.capabilities.display.client.ShellClient
import com.hopa.inference.client.ModelFactory
import com.hopa.stores.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the settings screens need: stored settings, the model, the link, the helper. */
interface SettingsDependencies {
    val context: Context
    val settings: SettingsRepository
    val modelFactory: ModelFactory
    val backend: BackendSession
    val backendNotice: MutableStateFlow<String?>
    val helperStatus: StateFlow<HelperStatus>
    val shell: ShellClient
}
