package com.hopa.app.feature.settings

import android.os.Build
import com.hopa.app.di.AppContainer
import com.hopa.app.link.remote.ScreenAccess
import com.hopa.app.service.ScreenCaptureRequester
import com.hopa.app.service.ScreenCaptureService
import com.hopa.bridge.session.BridgeClient
import com.hopa.capabilities.accessibility.state.AccessibilityState
import com.hopa.capabilities.accessibility.state.HopaAccessibilityService
import com.hopa.capabilities.display.client.HelperStatus
import com.hopa.capabilities.display.client.MediaProjectionCapture
import com.hopa.capabilities.display.client.ShellDisplayDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Which surface a run uses, whether HOPA may look at the screen, and the
 * self-check that says what is missing. The screen's state stays in the
 * ViewModel; this class performs the checks and the writes.
 */
internal class SurfaceSettings(
    private val container: AppContainer,
) {
    /** auto, background, onscreen or bridge; the same words the core enum uses. */
    suspend fun setScreenPath(value: String) {
        container.settings.update {
            it.copy(
                screenPath = value,
                // Selecting the computer turns the bridge on. Going back to the
                // phone leaves the bridge configured: chat sync and voice still
                // use it, and only the tap driver changes.
                useBridge = if (value == "bridge") true else it.useBridge,
            )
        }
        // Chosen "Your screen": the helper is no longer wanted, so it goes.
        // A run that owns the phone keeps it until that run ends.
        if (value == "onscreen" && !container.runGate.state.value.busy) {
            withContext(Dispatchers.IO) { container.helper.release() }
        }
    }

    suspend fun setVision(value: Boolean) {
        container.settings.update { it.copy(vision = value) }
        // Below API 30 there is no accessibility screenshot, so turning vision on
        // asks for MediaProjection consent once; API 30+ needs no extra grant.
        if (value &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            !MediaProjectionCapture.isActive
        ) {
            ScreenCaptureRequester.request()
        } else if (!value && MediaProjectionCapture.isActive) {
            ScreenCaptureService.stop(container.context)
        }
    }

    /** True when the accessibility service is enabled and bound now. */
    fun phoneAccess(settings: com.hopa.stores.AppSettings): Boolean = ScreenAccess.available(
        allowed = settings.accessibilityAllowed,
        enabled = AccessibilityState.isEnabled(container.context),
        bound = HopaAccessibilityService.instance != null,
    )

    /** Ask Android once, for this screen opening or the app coming back. */
    fun refreshScreenAccess() {
        AccessibilityState.refresh(container.context)
    }

    /** Only the helper's liveness; cheap enough to run while the page is open. */
    suspend fun refreshBackground(): Boolean = withContext(Dispatchers.IO) {
        container.helper.health() is HelperStatus.Running
    }

    /**
     * Say, step by step, what works right now. The person should never have to
     * guess whether phone access, the helper or the bridge is the missing piece.
     */
    suspend fun runScreenCheck(): List<ScreenCheck> {
        val checks = mutableListOf<ScreenCheck>()
        val current = container.settings.settings.first()
        val path = current.screenPath
        val onBridge = path == "bridge"
        val service = HopaAccessibilityService.instance
        val enabled = AccessibilityState.isEnabled(container.context)

        checks += when {
            onBridge -> ScreenCheck(
                "screen",
                "Screen access is not needed with My computer",
                ok = true,
                info = true,
            )

            !current.accessibilityAllowed -> ScreenCheck(
                "screen",
                "Screen access is paused by you - tap Fix to resume it",
                ok = false,
                action = "Fix",
            )

            enabled && service != null -> ScreenCheck(
                "screen",
                "HOPA can see your screen",
                ok = true,
            )

            enabled -> ScreenCheck(
                "screen",
                "Screen access is on but not answering - tap Fix to open it",
                ok = false,
                action = "Fix",
            )

            else -> ScreenCheck(
                "screen",
                "Screen access is off - tap Fix, then turn on HOPA",
                ok = false,
                action = "Fix",
            )
        }

        val socket = if (onBridge || path == "onscreen") {
            false
        } else {
            withContext(Dispatchers.IO) { container.shell.connect() }
        }
        checks += when {
            onBridge -> ScreenCheck(
                "background",
                "Hidden screen is not used with My computer",
                ok = true,
                info = true,
            )

            path == "onscreen" -> ScreenCheck(
                "background",
                "Hidden screen is not used with Your screen",
                ok = true,
                info = true,
            )

            socket -> ScreenCheck("background", "Hidden screen is ready", ok = true)

            path == "auto" -> ScreenCheck(
                "background",
                "Hidden screen isn't running; your screen will be used",
                ok = true,
                info = true,
            )

            else -> ScreenCheck(
                "background",
                "Hidden screen isn't running - tap Set up, then pair once",
                ok = false,
                action = "Set up",
            )
        }

        if (socket) {
            // Android 16 lets apps publish typed functions; the shell sees them
            // through `cmd app_function`, which is a cleaner door than tapping.
            val listing = withContext(Dispatchers.IO) {
                runCatching { container.shell.appFunctions("com.android.settings") }
                    .getOrNull()
            }
            val appFunctions = when {
                listing == null -> "App functions: could not be checked"
                listing.second.contains("Can't find service") ||
                    listing.second.contains("Unknown command") ->
                    "App functions: this Android does not offer them (needs 16)"
                else -> "App functions: this Android supports them"
            }
            checks += ScreenCheck("appfn", appFunctions, ok = true, info = true)

            val device = runCatching { container.backgroundDevice() }.getOrNull()
            if (device == null) {
                val why = container.lastBackgroundError.ifBlank { "no display" }
                checks += ScreenCheck(
                    "tried",
                    "Hidden screen failed: $why",
                    ok = false,
                )
            } else {
                val opened = withContext(Dispatchers.IO) {
                    runCatching { device.launch("com.android.settings") }
                }
                withContext(Dispatchers.IO) {
                    runCatching { device.observe() }.getOrNull()
                }
                checks += if (opened.isSuccess) {
                    ScreenCheck("tried", "Tested it: Settings opened out of sight", ok = true)
                } else {
                    ScreenCheck(
                        "tried",
                        "Test failed: ${opened.exceptionOrNull()?.message}",
                        ok = false,
                    )
                }
                runCatching {
                    (device as? ShellDisplayDevice)?.stop()
                }
            }
        }

        val token = container.settings.bridgeToken()
        val bridgeConfigured = current.bridgeUrl.isNotBlank() && token.isNotBlank()
        if (bridgeConfigured) {
            val health = withContext(Dispatchers.IO) {
                runCatching {
                    BridgeClient(current.bridgeUrl, token).health()
                    true
                }.getOrDefault(false)
            }
            checks += when {
                health -> ScreenCheck("computer", "HOPA bridge is connected", ok = true)
                onBridge -> ScreenCheck(
                    "computer",
                    "HOPA bridge is not answering - tap Fix",
                    ok = false,
                    action = "Fix",
                )
                else -> ScreenCheck(
                    "computer",
                    "HOPA bridge is not answering (not needed for This phone)",
                    ok = true,
                    info = true,
                )
            }
        } else if (onBridge) {
            checks += ScreenCheck(
                "computer",
                "HOPA bridge isn't set up - tap Fix",
                ok = false,
                action = "Fix",
            )
        } else {
            checks += ScreenCheck(
                "computer",
                "HOPA bridge isn't set up (not needed for This phone)",
                ok = true,
                info = true,
            )
        }

        checks += ScreenCheck(
            "mode",
            "Apps will open: ${pathLabel(path).lowercase()}",
            ok = true,
            info = true,
        )
        return checks
    }
}
