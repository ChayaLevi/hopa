package com.hopa.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopa.capabilities.accessibility.state.AccessibilityState
import com.hopa.feature.settings.SettingsDependencies
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The accessibility switch and the build stamp on that page. */
@HiltViewModel
class AccessibilityViewModel @Inject constructor(
    private val deps: SettingsDependencies,
) : ViewModel() {
    private val _state = MutableStateFlow(AccessibilityUiState())
    val state: StateFlow<AccessibilityUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val allowed = deps.settings.settings.first().accessibilityAllowed
            _state.update { it.copy(allowed = allowed, installedAt = installedAt()) }
            AccessibilityState.refresh(deps.context)
        }
    }

    /** Android settings send this app no event; the page asks when it comes back. */
    fun refreshAccessibility() {
        viewModelScope.launch { AccessibilityState.refresh(deps.context) }
    }

    fun setAllowed(value: Boolean) {
        _state.update { it.copy(allowed = value) }
        viewModelScope.launch {
            deps.settings.update { it.copy(accessibilityAllowed = value) }
        }
    }

    fun openSettings() {
        AccessibilityState.openSettings(deps.context)
    }

    private fun installedAt(): String = runCatching {
        val context = deps.context
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val install = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            java.util.Locale.getDefault(),
        ).format(java.util.Date(info.lastUpdateTime))
        "${info.versionName} (installed $install)"
    }.getOrDefault("")
}
