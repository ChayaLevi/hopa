package com.hopa.capabilities.accessibility.tree

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.hopa.capabilities.accessibility.state.HopaAccessibilityService

/**
 * Reading and pressing another app through accessibility. The wireless pairing
 * dialog lives in Settings and its code is only good while that dialog holds the
 * focus, so the app has to read it there instead of asking a person to retype it
 * in another window.
 */
class ScreenTree(
    private val service: HopaAccessibilityService,
) {
    private val gestures = GestureDispatcher(service)
    private val verbose =
        (service.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** The window of [packageName] when it is on screen, else the focused one. */
    fun root(packageName: String = SETTINGS): AccessibilityNodeInfo? {
        val active = runCatching { service.activeRoot() }
            .onFailure { android.util.Log.w("HopaScreen", "active root failed", it) }
            .getOrNull()
        // The focused window is the dialog when one is open; the list behind it
        // does not carry the code.
        if (active?.packageName?.toString() == packageName) {
            return active
        }
        val fromWindow = runCatching {
            service.windows.firstNotNullOfOrNull { window ->
                window.root?.takeIf { it.packageName?.toString() == packageName }
            }
        }.getOrNull()
        return fromWindow ?: active
    }

    /**
     * Every root of [packageName] on screen, focused window first.
     *
     * A dialog is its own window, and `rootInActiveWindow` sometimes still
     * answers with the page behind it - the pairing dialog is exactly that case.
     * Reading one window missed its code and port.
     */
    private fun roots(packageName: String = SETTINGS): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        val active = runCatching { service.activeRoot() }
            .onFailure { android.util.Log.w("HopaScreen", "active root failed", it) }
            .getOrNull()
        if (active?.packageName?.toString() == packageName) {
            out.add(active)
        }
        runCatching {
            service.windows.forEach { window ->
                val root = window.root ?: return@forEach
                if (root.packageName?.toString() != packageName) return@forEach
                if (out.none { it === root }) {
                    out.add(root)
                }
            }
        }
        return out
    }

    fun texts(packageName: String = SETTINGS): List<String> {
        val nodes = nodes(packageName)
        return nodes.mapNotNull { it.text?.toString()?.takeIf(String::isNotBlank) }
    }

    fun hasText(part: String, packageName: String = SETTINGS): Boolean =
        nodes(packageName).any { it.text?.toString()?.contains(part, ignoreCase = true) == true }

    /**
     * Click the row that carries [part]. Settings rows are often not clickable in
     * the tree even though a tap works, so the last resort is a real tap - at the
     * centre of the nearest ancestor that has bounds, because a text node inside
     * a row can report an empty rectangle.
     */
    fun clickText(part: String, packageName: String = SETTINGS): Boolean {
        val target = nodes(packageName)
            .firstOrNull { it.text?.toString()?.contains(part, ignoreCase = true) == true }
            ?: return false
        var node: AccessibilityNodeInfo? = target
        var bounds: Rect? = null
        var depth = 0
        while (node != null && depth < ANCESTORS) {
            val current = boundsOf(node)
            if (bounds == null && !current.isEmpty) {
                bounds = current
            }
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            node = runCatching { node.parent }.getOrNull()
            depth += 1
        }
        val tapped = bounds ?: return false
        // A row half under the toolbar or the navigation bar is not tappable.
        val window = boundsOf(root(packageName) ?: return false)
        if (tapped.centerY() < window.top + TOP_MARGIN ||
            tapped.centerY() > window.bottom - BOTTOM_MARGIN
        ) {
            if (verbose) {
                android.util.Log.d(
                    "HopaScreen",
                    "\"$part\" sits at ${tapped.centerY()}, window ${window.top}..${window.bottom}",
                )
            }
            return false
        }
        gestures.tap(tapped.centerX(), tapped.centerY())
        return true
    }

    /**
     * One page of scroll. The list's own action reports success but does not
     * move this Settings list, so the page is moved by a real swipe.
     */
    fun scrollForward(packageName: String = SETTINGS): Boolean = scroll(packageName, forward = true)

    /** The other direction; Settings restores the position the person left. */
    fun scrollBackward(packageName: String = SETTINGS): Boolean = scroll(packageName, forward = false)

    private fun scroll(packageName: String, forward: Boolean): Boolean {
        val all = nodes(packageName)
        val scrollable = all.filter { it.isScrollable }
            .maxByOrNull { boundsOf(it).let { rect -> rect.width() * rect.height() } }
        val bounds = boundsOf(scrollable ?: root(packageName) ?: return false)
        if (bounds.isEmpty) {
            return false
        }
        val x = bounds.centerX()
        val from = if (forward) 0.6f else 0.4f
        val to = if (forward) 0.4f else 0.6f
        val swiped = gestures.swipe(
            x,
            bounds.top + (bounds.height() * from).toInt(),
            x,
            bounds.top + (bounds.height() * to).toInt(),
            900,
        )
        if (verbose) {
            android.util.Log.d("HopaScreen", "scroll ${if (forward) "down" else "up"}: swipe=$swiped on $bounds")
        }
        return swiped
    }

    private fun boundsOf(node: AccessibilityNodeInfo): Rect = Rect().also { node.getBoundsInScreen(it) }

    private fun nodes(packageName: String): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        roots(packageName).forEach { collect(it, out) }
        return out
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null) {
            return
        }
        out.add(node)
        for (index in 0 until node.childCount) {
            collect(runCatching { node.getChild(index) }.getOrNull(), out)
        }
    }

    private companion object {
        const val SETTINGS = "com.android.settings"
        const val TOP_MARGIN = 450
        const val BOTTOM_MARGIN = 250
        /** A Settings row's text is one or two levels below its pressable ancestor. */
        const val ANCESTORS = 6
    }
}
