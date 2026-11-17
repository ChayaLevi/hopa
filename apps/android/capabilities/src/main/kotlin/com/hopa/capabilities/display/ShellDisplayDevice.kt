package com.hopa.capabilities.display

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.hopa.capabilities.accessibility.HopaAccessibilityService
import com.hopa.capabilities.accessibility.NodeTreeReader
import com.hopa.capabilities.accessibility.GestureDispatcher
import com.hopa.core.contracts.Observation
import com.hopa.core.contracts.ObservationNode
import com.hopa.core.contracts.ScreenSize
import com.hopa.core.observation.ObservationProducer
import com.hopa.core.ports.DevicePort
import com.hopa.core.ports.InstalledApp
import kotlinx.coroutines.delay

/**
 * A phone the person cannot see: apps run on the helper's virtual display, input
 * goes straight to that display, and the UI tree comes from the accessibility
 * window on the same display. Nothing here touches the visible screen.
 */
class ShellDisplayDevice(
    private val client: ShellClient,
    private val context: Context,
) : DevicePort {
    private val producer = ObservationProducer()
    private var refs: Map<String, AccessibilityNodeInfo> = emptyMap()

    var displayId: Int = -1
        private set
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    /** Create the display; false when the helper refuses, with [error] set. */
    fun start(width: Int, height: Int, dpi: Int): Boolean {
        val (ok, payload) = client.display(width, height, dpi)
        if (!ok) {
            error = payload
            return false
        }
        displayId = payload.toIntOrNull() ?: -1
        this.width = width
        this.height = height
        return displayId > 0
    }

    var error: String = ""
        private set

    private val launched = mutableSetOf<String>()

    /**
     * Leaving apps behind would let the system move them onto the person's screen
     * the moment the display goes away, so the run closes what it opened.
     */
    fun stop() = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        if (displayId > 0 && launched.isNotEmpty()) {
            launched.forEach { runCatching { client.unmute(it) } }
            runCatching { client.finish(launched.toList()) }
        }
        runCatching { client.release() }
        launched.clear()
        displayId = -1
    }

    override suspend fun observe(): Observation {
        val service = HopaAccessibilityService.instance
            ?: throw IllegalStateException(
                "screen access is off, so the hidden screen cannot be read",
            )
        if (displayId <= 0) {
            throw IllegalStateException(
                error.ifBlank { "the hidden screen is not running" },
            )
        }
        // A freshly launched activity needs a moment before its window has a
        // root; polling here is cheaper than a turn that sees an empty screen.
        val deadline = System.currentTimeMillis() + OBSERVE_WAIT_MS
        var window = windowRoot(service)
        while (window == null && System.currentTimeMillis() < deadline) {
            delay(200)
            window = windowRoot(service)
        }
        if (window == null) {
            if (launched.isEmpty()) {
                // Nothing has been opened here yet. That is the normal start of a
                // hidden run, not a broken surface; the model opens an app next.
                return emptyScreen()
            }
            val inventory = service.windows.joinToString { item ->
                "display=${item.displayId} pkg=${item.root?.packageName ?: "no-root"}"
            }
            val message = "no window for ${launched.lastOrNull()} on the hidden display " +
                "(display $displayId; windows: ${inventory.ifBlank { "none" }})"
            // The fallback path hides this from the person, so it must be in the log.
            android.util.Log.w("HopaDisplay", message)
            throw IllegalStateException(message)
        }
        val reader = NodeTreeReader(service, displayId = displayId, rootOverride = window)
        val raw = reader.read()
        refs = reader.refs
        return producer.produce(raw)
    }

    override suspend fun tap(ref: String?, x: Int?, y: Int?) {
        launched.lastOrNull()?.let { HopaAccessibilityService.forget(it) }
        if (ref != null) {
            val node = refs[ref] ?: throw IllegalArgumentException("stale ref $ref")
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val (ok, payload) = onIo { client.tap(bounds.centerX(), bounds.centerY()) }
            delay(TAP_SETTLE_MS)
            if (!ok) {
                throw IllegalStateException(payload)
            }
            return
        }
        if (x == null || y == null) {
            throw IllegalArgumentException("tap needs a ref or coordinates")
        }
        val (ok, payload) = onIo { client.tap(x, y) }
        delay(TAP_SETTLE_MS)
        if (!ok) {
            throw IllegalStateException(payload)
        }
    }

    override suspend fun doubleTap(ref: String?, x: Int?, y: Int?) {
        tap(ref, x, y)
        delay(80)
        tap(ref, x, y)
    }

    override suspend fun longPress(ref: String?, x: Int?, y: Int?) {
        val (px, py) = pointFor(ref, x, y)
        val (ok, payload) = onIo { client.swipe(px, py, px, py, 700) }
        delay(250)
        if (!ok) {
            throw IllegalStateException(payload)
        }
    }

    override suspend fun type(text: String, ref: String?) {
        val service = HopaAccessibilityService.instance
            ?: throw IllegalStateException("accessibility is not connected")
        val node = ref?.let { refs[it] }
            ?: service.windows.firstOrNull { it.displayId == displayId }?.root
                ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: throw IllegalStateException("no text field is focused on the hidden screen")
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text,
            )
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            throw IllegalStateException("the field refused the text")
        }
        delay(200)
    }

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, velocity: Int) {
        val millis = (400 - velocity).coerceIn(120, 900)
        val (ok, payload) = onIo { client.swipe(fromX, fromY, toX, toY, millis) }
        delay(250)
        if (!ok) {
            throw IllegalStateException(payload)
        }
    }

    override suspend fun pressBack() {
        val (ok, payload) = onIo { client.key(KEYCODE_BACK) }
        delay(250)
        if (!ok) {
            throw IllegalStateException(payload)
        }
    }

    override suspend fun pressHome() {
        // HOME on the hidden display reaches its own launcher, so the app is put
        // away without touching the person's screen.
        val (ok, payload) = onIo { client.key(KEYCODE_HOME) }
        delay(250)
        if (!ok) {
            throw IllegalStateException(payload)
        }
    }

    override suspend fun launch(packageName: String) {
        // Reading the launcher's tree after the tap would describe the old screen.
        HopaAccessibilityService.forget(packageName)
        val (ok, payload) = onIo { client.launch(packageName) }
        delay(1_500)
        if (!ok) {
            throw IllegalStateException(payload)
        }
        launched += packageName
        // The app is on a hidden display, so its sound would come from nowhere.
        runCatching { onIo { client.mute(packageName) } }
    }

    override suspend fun stop(packageName: String) {
        // The helper owns the display, so it is also the only thing that can
        // close an app on it. Forgetting the package keeps the end-of-run sweep
        // from closing it twice.
        val (ok, payload) = onIo { client.finish(listOf(packageName)) }
        if (!ok) {
            throw IllegalStateException(payload)
        }
        launched -= packageName
        runCatching { onIo { client.unmute(packageName) } }
    }

    override suspend fun foreground(): String = onIo { client.top() }

    override suspend fun screenshot(): ByteArray? = onIo { client.shot() }

    override suspend fun appFunction(
        packageName: String,
        function: String,
        parameters: String,
    ): String {
        val (ok, payload) =
            onIo { client.appFunctionCall(packageName, function, parameters) }
        if (!ok) {
            throw IllegalStateException(payload)
        }
        return payload
    }

    override suspend fun apps(): List<InstalledApp> =
        com.hopa.capabilities.system.AndroidPackageCatalog(context).installed()

    /** The client is blocking socket I/O, and StrictMode kills that on main. */
    private suspend fun <T> onIo(block: () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }

    private fun pointFor(ref: String?, x: Int?, y: Int?): Pair<Int, Int> {
        if (ref != null) {
            val node = refs[ref] ?: throw IllegalArgumentException("stale ref $ref")
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            return bounds.centerX() to bounds.centerY()
        }
        if (x == null || y == null) {
            throw IllegalArgumentException("a gesture needs a ref or coordinates")
        }
        return x to y
    }


    /**
     * The window on this display, or — when the system does not report a display
     * id for a virtual display — the window of the app this run launched.
     */
    private fun windowRoot(service: HopaAccessibilityService): AccessibilityNodeInfo? {
        service.windows
            .filter { it.displayId == displayId }
            .firstNotNullOfOrNull { window -> window.root }
            ?.let { return it }
        val expected = launched.lastOrNull() ?: return null
        val fallback = service.windows.firstNotNullOfOrNull { window ->
            window.root?.takeIf { it.packageName?.toString() == expected }
        }
        if (fallback != null) {
            android.util.Log.w(
                "HopaDisplay",
                "window for $expected did not report display $displayId; used the package match",
            )
            return fallback
        }
        val event = HopaAccessibilityService.sourceFor(expected)
        if (event != null) {
            android.util.Log.w("HopaDisplay", "read $expected from its window event")
            return event
        }
        return null
    }

    /** The hidden display exists and is empty; the run is expected to open something. */
    private fun emptyScreen(): Observation = Observation(
        id = "obs-hidden-empty",
        timestamp = System.currentTimeMillis(),
        screenSize = ScreenSize(width, height),
        text = "The hidden screen is empty. Open an app with ui.launch, then read it.",
        tree = ObservationNode(type = "root"),
    )

    private companion object {
        const val OBSERVE_WAIT_MS = 6_000L
        const val TAP_SETTLE_MS = 700L
        const val KEYCODE_BACK = 4
        const val KEYCODE_HOME = 3
    }
}
