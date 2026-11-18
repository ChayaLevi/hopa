package com.hopa.app.feature.launchpad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.hopa.app.di.AppContainer
import com.hopa.capabilities.accessibility.AccessibilityState
import com.hopa.engine.router.CatalogRoutes
import com.hopa.engine.router.RouteTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One catalog entry for this phone: status, surface, rules, and two actions. */
@Composable
fun AppCardRoute(
    container: AppContainer,
    name: String,
    onBack: () -> Unit,
    onOpenScreenAccess: () -> Unit,
) {
    var target by remember { mutableStateOf<RouteTarget?>(null) }
    var surface by remember { mutableStateOf("Auto") }
    var screenAccess by remember { mutableStateOf(false) }
    var trusted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var functions by remember { mutableStateOf(emptyList<String>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(name) {
        val installed = withContext(Dispatchers.IO) {
            runCatching { container.packages.installed() }.getOrDefault(emptyList())
        }
        target = CatalogRoutes.targets(installed, "android", container.knowledge)
            .firstOrNull { it.kind == "app" && it.name == name }
        val settings = runCatching { container.settings.settings.first() }.getOrNull()
        surface = surfaceLabel(settings?.screenPath.orEmpty())
        screenAccess = AccessibilityState.isEnabled(container.context)
        trusted = settings?.trustedApps?.contains(name) == true
        // Functions live behind the helper; without it the card simply has none.
        functions = withContext(Dispatchers.IO) {
            runCatching {
                val packageName = target?.target.orEmpty()
                if (packageName.isBlank() || !container.shell.connect()) {
                    emptyList()
                } else {
                    val (ok, json) = container.shell.appFunctions(packageName)
                    if (ok) com.hopa.capabilities.display.AppFunctions.names(json) else emptyList()
                }
            }.getOrDefault(emptyList())
        }
    }

    AppCardScreen(
        target = target,
        surface = surface,
        screenAccess = screenAccess,
        trusted = trusted,
        status = status,
        functions = functions,
        onBack = onBack,
        onOpen = {
            target?.let { current ->
                scope.launch {
                    status = runCatching {
                        container.packages.launch(current.bundle)
                        "Opened ${current.display}."
                    }.getOrElse {
                        "Couldn't open ${current.display}: ${it.message ?: "no launch intent"}"
                    }
                }
            }
        },
        onInstall = {
            target?.let { current ->
                scope.launch {
                    val packageName = current.target
                    if (packageName.isBlank()) {
                        status = "No Play page is known for ${current.display}."
                    } else {
                        status = "Looking for ${current.display} on Play\u2026"
                        status = if (PlayStorePage.exists(packageName) == false) {
                            "${current.display} is not on Play Store."
                        } else {
                            openStore(container, current)
                        }
                    }
                }
            }
        },
        onOpenScreenAccess = onOpenScreenAccess,
        onTrust = { value ->
            trusted = value
            scope.launch {
                container.settings.update { current ->
                    current.copy(
                        trustedApps = if (value) {
                            current.trustedApps + name
                        } else {
                            current.trustedApps - name
                        },
                    )
                }
            }
        },
    )
}

private fun surfaceLabel(path: String): String = when (path) {
    "background" -> "the hidden screen"
    "onscreen" -> "your screen"
    "bridge" -> "your computer to send the taps"
    else -> "the hidden screen or your screen"
}

/** Open the store page, and say what happened either way. */
private fun openStore(
    container: AppContainer,
    target: RouteTarget,
): String {
    val link = PlayStorePage.url(target.target)
        ?: return "No Play page is known for ${target.display}."
    val opened = runCatching {
        container.context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    return if (opened.isSuccess) {
        "Opening ${target.display} on Play Store\u2026"
    } else {
        "Couldn't open Play Store: ${opened.exceptionOrNull()?.message ?: "no browser"}"
    }
}
