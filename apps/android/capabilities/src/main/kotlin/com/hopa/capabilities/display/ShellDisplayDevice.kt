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
    fun stop() {
        if (displayId > 0 && launched.isNotEmpty()) {
            launched.forEach { runCatching { client.unmute(it) } }
            runCatching { client.finish(launched.toList()) }
        }
        runCatching { client.release() }
        launched.clear()
        displayId = -1
    }

    override suspend fun observe(): Observation {
        val service = HopaAccessibilityService.instance ?: return offline()
        val reader = NodeTreeReader(service, displayId = displayId)
        val raw = reader.read()
        refs = reader.refs
        return producer.produce(raw)
    }

    override suspend fun tap(ref: String?, x: Int?, y: Int?) {
        if (ref != null) {
            val node = refs[ref] ?: throw IllegalArgumentException("stale ref $ref")
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val (ok, payload) = client.tap(bounds.centerX(), bounds.centerY())
            delay(250)
            if (!ok) {
                throw IllegalStateException(payload)
            }
            return
        }
        if (x == null || y == null) {
            throw IllegalArgumentException("tap needs a ref or coordinates")
        }
        val (ok, payload) = client.tap(x, y)
        delay(250)
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
        val (ok, payload) = client.swipe(px, py, px, py, 700)
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
            ?: throw IllegalStateException("no text field is focused on the background screen")
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
        val (ok, payload) = client.swipe(fromX, fromY, toX, toY, millis)
        delay(250)
        if (!ok) {
            throw IllegalStateException(payload)
        }
    }

    override suspend fun pressBack() {
        val (ok, payload) = client.key(KEYCODE_BACK)
        delay(250)
        if (!ok) {
            throw IllegalStateException(payload)
        }
    }

    override suspend fun pressHome() {
        // Home would leave the background display; the honest "back to the start"
        // is a back press on that display.
        pressBack()
    }

    override suspend fun launch(packageName: String) {
        val (ok, payload) = client.launch(packageName)
        delay(1_500)
        if (!ok) {
            throw IllegalStateException(payload)
        }
        launched += packageName
        // The app is on a hidden display, so its sound would come from nowhere.
        runCatching { client.mute(packageName) }
    }

    override suspend fun stop(packageName: String) {
        throw UnsupportedOperationException("the background display closes with the run")
    }

    override suspend fun foreground(): String = client.top()

    override suspend fun screenshot(): ByteArray? = client.shot()

    override suspend fun apps(): List<InstalledApp> = emptyList()

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

    private fun offline(): Observation {
        val observation = Observation(
            id = "obs-background-offline",
            timestamp = System.currentTimeMillis(),
            screenSize = ScreenSize(0, 0),
            text = "The background screen is not available: ${error.ifBlank { "no display" }}.",
            tree = ObservationNode(type = "root"),
        )
        return observation
    }

    private companion object {
        const val KEYCODE_BACK = 4
    }
}
