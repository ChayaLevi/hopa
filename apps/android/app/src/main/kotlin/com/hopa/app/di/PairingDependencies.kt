package com.hopa.app.di

import android.content.Context
import com.hopa.app.link.session.BackendSession
import com.hopa.capabilities.display.client.HelperSupervisor
import com.hopa.stores.SettingsRepository

/** What pairing (gateway, bridge, wireless ADB, helper) needs. */
interface PairingDependencies {
    val context: Context
    val settings: SettingsRepository
    val backend: BackendSession
    val helper: HelperSupervisor

    suspend fun pairWireless(address: String, code: String): String
}
