package com.hopa.capabilities.accessibility.tree

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import com.hopa.capabilities.accessibility.state.AccessibilityState
import com.hopa.capabilities.accessibility.state.HopaAccessibilityService
import com.hopa.capabilities.display.client.MediaProjectionCapture
import com.hopa.contracts.ports.DevicePort
import com.hopa.contracts.ports.InstalledApp
import com.hopa.contracts.wire.Observation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class AccessibilityDevice(
    private val context: Context,
) : DevicePort {
    private var refs: Map<String, AccessibilityNodeInfo> = emptyMap()
    private val producer = com.hopa.observation.produce.ObservationProducer()
    private var lastShot = 0L

    override suspend fun observe(): Observation {
        // A rebind after an update can leave a short window where access is on
        // but the new instance has not connected yet. Waiting a moment is
        // honest; failing the turn there is not.
        val service = awaitService()
        val reader = NodeTreeReader(service)
        val raw = reader.read()
        refs = reader.refs
        return producer.produce(raw)
    }

    override suspend fun tap(ref: String?, x: Int?, y: Int?) {
        val service = awaitService(ACTION_WAIT_MS)
        val dispatcher = GestureDispatcher(service)
        if (ref != null) {
            val node = refs[ref] ?: throw IllegalArgumentException("stale ref $ref")
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                delay(250)
                return
            }
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            dispatcher.tap(bounds.centerX(), bounds.centerY())
            delay(250)
            return
        }
        if (x == null || y == null) {
            throw IllegalArgumentException("tap needs a ref or coordinates")
        }
        dispatcher.tap(x, y)
        delay(250)
    }

    override suspend fun doubleTap(ref: String?, x: Int?, y: Int?) {
        val point = pointFor(ref, x, y)
        GestureDispatcher(awaitService(ACTION_WAIT_MS)).doubleTap(point.first, point.second)
        delay(250)
    }

    override suspend fun longPress(ref: String?, x: Int?, y: Int?) {
        val point = pointFor(ref, x, y)
        GestureDispatcher(awaitService(ACTION_WAIT_MS)).longPress(point.first, point.second)
        delay(250)
    }

    override suspend fun type(text: String, ref: String?) {
        // A search box that was just tapped usually owns the input focus, and the
        // model often sends the text without a ref. Use the focused field, then
        // the first editable node, instead of failing and letting the loop repeat.
        val target = typingTarget(ref)
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text,
            )
        }
        if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            delay(200)
            return
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("HOPA", text))
        if (target.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            delay(200)
            return
        }
        throw IllegalStateException("the field refused the text")
    }

    private suspend fun typingTarget(ref: String?): AccessibilityNodeInfo {
        if (ref != null) {
            refs[ref]?.let { return it }
        }
        val root = awaitService(ACTION_WAIT_MS).activeRoot() ?: throw IllegalStateException("no window to type into")
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { return it }
        firstEditable(root)?.let { return it }
        throw IllegalStateException("no text field is focused")
    }

    private fun firstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) {
            return node
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            firstEditable(child)?.let { return it }
        }
        return null
    }

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, velocity: Int) {
        GestureDispatcher(awaitService(ACTION_WAIT_MS)).swipe(fromX, fromY, toX, toY)
        delay(350)
    }

    override suspend fun pressBack() {
        awaitService(ACTION_WAIT_MS).performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        delay(250)
    }

    override suspend fun pressHome() {
        awaitService(ACTION_WAIT_MS).performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        delay(250)
    }

    override suspend fun launch(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalArgumentException("no launch intent for $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        delay(1_500)
    }

    override suspend fun stop(packageName: String) {
        throw UnsupportedOperationException("Android does not allow an app to force-stop another app")
    }

    override suspend fun foreground(): String =
        HopaAccessibilityService.instance?.activeRoot()?.packageName?.toString().orEmpty()

    override suspend fun screenshot(): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // takeScreenshot() is API 30+. On Android 10 use MediaProjection when
            // the user has granted screen capture; otherwise the agent works from
            // the accessibility tree and no image is attached.
            return MediaProjectionCapture.capture()
        }
        val now = System.currentTimeMillis()
        if (now - lastShot < 1_000) {
            return null
        }
        val service = runCatching { awaitService(ACTION_WAIT_MS) }.getOrNull() ?: return null
        lastShot = now
        return try {
            suspendCancellableCoroutine { continuation ->
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    context.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                result.hardwareBuffer,
                                result.colorSpace,
                            )
                            val stream = ByteArrayOutputStream()
                            bitmap?.compress(Bitmap.CompressFormat.JPEG, 88, stream)
                            result.hardwareBuffer.close()
                            continuation.resume(stream.toByteArray())
                        }

                        override fun onFailure(errorCode: Int) {
                            // FLAG_SECURE lands here; say nothing rather than
                            // capture something the app asked to hide.
                            continuation.resume(null)
                        }
                    },
                )
            }
        } catch (error: Exception) {
            null
        }
    }

    override suspend fun apps(): List<InstalledApp> =
        com.hopa.capabilities.system.AndroidPackageCatalog(context).installed()

    /** The connected service, waiting up to [waitMillis] for a rebind to finish. */
    private suspend fun awaitService(waitMillis: Long = READ_WAIT_MS): HopaAccessibilityService {
        if (!AccessibilityState.isEnabled(context)) {
            throw IllegalStateException("accessibility is disabled in Android settings")
        }
        HopaAccessibilityService.instance?.let { return it }
        if (AccessibilityState.awaitConnected(waitMillis)) {
            HopaAccessibilityService.instance?.let { return it }
        }
        throw IllegalStateException("accessibility is enabled but not connected yet")
    }

    private fun pointFor(ref: String?, x: Int?, y: Int?): Pair<Int, Int> {
        if (ref != null) {
            val node = refs[ref] ?: throw IllegalArgumentException("stale ref $ref")
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            return bounds.centerX() to bounds.centerY()
        }
        if (x == null || y == null) {
            throw IllegalArgumentException("a gesture needs a ref or coordinates")
        }
        return x to y
    }

    private companion object {
        const val READ_WAIT_MS = 2_500L
        const val ACTION_WAIT_MS = 1_000L
    }
}
