package com.hopa.feature.launchpad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopa.capabilities.accessibility.state.AccessibilityState
import com.hopa.catalog.routing.CatalogRoutes
import com.hopa.catalog.routing.RouteTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The app card: the catalog row, how it opens, and the two actions on it. */
@HiltViewModel
class AppCardViewModel @Inject constructor(
    private val deps: LaunchpadDependencies,
) : ViewModel() {
    private val _state = MutableStateFlow(AppCardUiState())
    val state: StateFlow<AppCardUiState> = _state.asStateFlow()

    private var name: String = ""

    fun refresh(name: String) {
        this.name = name
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) {
                runCatching { deps.packages.installed() }.getOrDefault(emptyList())
            }
            val target = CatalogRoutes.targets(installed, "android", deps.knowledge)
                .firstOrNull { it.kind == "app" && it.name == name }
            val settings = runCatching { deps.settings.settings.first() }.getOrNull()
            val screenAccess = AccessibilityState.isEnabled(deps.context)
            // Functions live behind the helper; without it the card simply has none.
            val functions = withContext(Dispatchers.IO) {
                runCatching {
                    val packageName = target?.target.orEmpty()
                    if (packageName.isBlank() || !deps.shell.connect()) {
                        emptyList()
                    } else {
                        val (ok, json) = deps.shell.appFunctions(packageName)
                        if (ok) {
                            com.hopa.capabilities.display.client.AppFunctions.names(json)
                        } else {
                            emptyList()
                        }
                    }
                }.getOrDefault(emptyList())
            }
            _state.update {
                it.copy(
                    target = target,
                    surface = surfaceLabel(settings?.screenPath.orEmpty()),
                    screenAccess = screenAccess,
                    trusted = settings?.trustedApps?.contains(name) == true,
                    functions = functions,
                )
            }
        }
    }

    fun open() {
        val current = _state.value.target ?: return
        viewModelScope.launch {
            val status = runCatching {
                deps.packages.launch(current.bundle)
                "Opened ${current.display}."
            }.getOrElse {
                "Couldn't open ${current.display}: ${it.message ?: "no launch intent"}"
            }
            _state.update { it.copy(status = status) }
        }
    }

    fun install() {
        val current = _state.value.target ?: return
        viewModelScope.launch {
            val packageName = current.target
            val status = if (packageName.isBlank()) {
                "No Play page is known for ${current.display}."
            } else {
                _state.update { it.copy(status = "Looking for ${current.display} on Play\u2026") }
                if (PlayStorePage.exists(packageName) == false) {
                    "${current.display} is not on Play Store."
                } else {
                    openStore(current)
                }
            }
            _state.update { it.copy(status = status) }
        }
    }

    fun setTrusted(value: Boolean) {
        _state.update { it.copy(trusted = value) }
        viewModelScope.launch {
            deps.settings.update { current ->
                current.copy(
                    trustedApps = if (value) {
                        current.trustedApps + name
                    } else {
                        current.trustedApps - name
                    },
                )
            }
        }
    }

    /** Open the store page, and say what happened either way. */
    private fun openStore(target: RouteTarget): String {
        val link = PlayStorePage.url(target.target)
            ?: return "No Play page is known for ${target.display}."
        val opened = runCatching {
            deps.context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(link),
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        return if (opened.isSuccess) {
            "Opening ${target.display} on Play Store\u2026"
        } else {
            "Couldn't open Play Store: ${opened.exceptionOrNull()?.message ?: "no browser"}"
        }
    }
}

private fun surfaceLabel(path: String): String = when (path) {
    "background" -> "the hidden screen"
    "onscreen" -> "your screen"
    "bridge" -> "your computer to send the taps"
    else -> "the hidden screen or your screen"
}
