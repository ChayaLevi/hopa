package com.hopa.app.di

import android.content.Context
import com.hopa.runtime.RunGate
import com.hopa.capabilities.display.client.HelperSupervisor
import com.hopa.capabilities.display.client.ShellClient
import com.hopa.contracts.ports.DevicePort
import com.hopa.stores.SettingsRepository

/** What the surface and self-check code needs. */
interface SurfaceDependencies {
    val context: Context
    val settings: SettingsRepository
    val runGate: RunGate
    val helper: HelperSupervisor
    val shell: ShellClient
    val lastBackgroundError: String

    suspend fun backgroundDevice(): DevicePort?
}
