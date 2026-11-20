package com.hopa.feature.settings

import android.content.Context
import com.hopa.backend.pairing.PairingLink
import com.hopa.capabilities.display.client.HelperSupervisor
import com.hopa.stores.SettingsRepository

/** What pairing (gateway, bridge, wireless ADB, helper) needs. */
interface PairingDependencies {
    val context: Context
    val settings: SettingsRepository
    val helper: HelperSupervisor

    suspend fun pairWireless(address: String, code: String): String

    suspend fun pairGateway(link: PairingLink): String

    suspend fun disconnectGateway(): String

    fun backendToken(): String?

    fun fcmAvailable(): Boolean

    fun backendName(): String

    fun pairWirelessFromScreen()

    fun pairWirelessWaiting()
}
