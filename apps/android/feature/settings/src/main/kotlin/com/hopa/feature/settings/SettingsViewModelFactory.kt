package com.hopa.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SettingsViewModelFactory(
    private val settings: SettingsDependencies,
    private val pairing: PairingDependencies,
    private val surface: SurfaceDependencies,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(settings, pairing, surface) as T
}
