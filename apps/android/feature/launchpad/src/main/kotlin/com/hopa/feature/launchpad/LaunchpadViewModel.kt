package com.hopa.feature.launchpad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopa.catalog.routing.CatalogRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The catalog, resolved for this phone. */
@HiltViewModel
class LaunchpadViewModel @Inject constructor(
    private val deps: LaunchpadDependencies,
) : ViewModel() {
    private val _state = MutableStateFlow(LaunchpadUiState())
    val state: StateFlow<LaunchpadUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) {
                runCatching { deps.packages.installed() }.getOrDefault(emptyList())
            }
            val resolved = runCatching {
                CatalogRoutes.targets(installed, "android", deps.knowledge)
            }
            resolved.fold(
                onSuccess = { raw ->
                    // Only what this phone can attempt: an app whose catalog entry
                    // has no Android target is a row that can never open.
                    val targets = raw.filter { target ->
                        target.kind == "site" || target.status != "none"
                    }
                    _state.update { it.copy(targets = targets, status = "") }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            targets = emptyList(),
                            status = "Could not read the catalog: " +
                                (error.message ?: "unknown error"),
                        )
                    }
                },
            )
        }
    }
}
