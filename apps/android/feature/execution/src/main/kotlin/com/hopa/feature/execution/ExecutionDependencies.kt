package com.hopa.feature.execution

import com.hopa.feature.settings.PairingDependencies
import com.hopa.feature.settings.SettingsDependencies
import com.hopa.feature.settings.SurfaceDependencies
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The Act screens need the settings surface they edit, plus the last
 * wireless-pairing answer they show.
 */
interface ExecutionDependencies :
    SettingsDependencies,
    PairingDependencies,
    SurfaceDependencies {
    val pairingStatus: MutableStateFlow<String?>

    fun bringToFront()
}
