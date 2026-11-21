package com.hopa.feature.settings

import android.content.Context
import com.hopa.backend.link.BackendState
import com.hopa.backend.link.BackendTaskState
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
    val backendState: StateFlow<BackendState>
    val backendTask: StateFlow<BackendTaskState>
    val backendNotice: MutableStateFlow<String?>
    val helperStatus: StateFlow<HelperStatus>
    val shell: ShellClient

    /** What the Decisions page shows; metadata only, no layer is called. */
    val decisionLayers: List<com.hopa.decision.LayerMetadata>

    /** The layer that would decide this class now, or `model` when none does. */
    fun activeDecision(actionClass: com.hopa.decision.ActionClass): String

    suspend fun connectShell(): Boolean

    fun notificationsAllowed(): Boolean

    /** Sends the test notification; false when Android refused to show it. */
    fun postTestNotification(): Boolean
}
