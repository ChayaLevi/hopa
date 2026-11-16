package com.hopa.capabilities.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

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

    fun texts(packageName: String = SETTINGS): List<String> {
        val nodes = nodes(packageName)
        return nodes.mapNotNull { it.text?.toString()?.takeIf(String::isNotBlank) }
    }

    fun hasText(part: String, packageName: String = SETTINGS): Boolean =
        nodes(packageName).any { it.text?.toString()?.contains(part, ignoreCase = true) == true }

    /**
     * Click the row that carries [part]. Settings rows are often not clickable in
     * the tree even though a tap works, so the last resort is a real tap.
     */
    fun clickText(part: String, packageName: String = SETTINGS): Boolean {
        val target = nodes(packageName)
            .firstOrNull { it.text?.toString()?.contains(part, ignoreCase = true) == true }
            ?: return false
        var node: AccessibilityNodeInfo? = target
        while (node != null) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            node = runCatching { node.parent }.getOrNull()
        }
        val bounds = boundsOf(target)
        if (bounds.isEmpty) {
            return false
        }
        // A row half under the toolbar or the navigation bar is not tappable.
        val window = boundsOf(root(packageName) ?: return false)
        if (bounds.centerY() < window.top + TOP_MARGIN || bounds.centerY() > window.bottom - BOTTOM_MARGIN) {
            if (verbose) {
                android.util.Log.d(
                    "HopaScreen",
                    "\"$part\" sits at ${bounds.centerY()}, window ${window.top}..${window.bottom}",
                )
            }
            return false
        }
        gestures.tap(bounds.centerX(), bounds.centerY())
        return true
    }

    /**
     * One page of scroll. The list's own action reports success but does not
     * move this Settings list, so the page is moved by a real swipe.
     */
    fun scrollForward(packageName: String = SETTINGS): Boolean {
        val all = nodes(packageName)
        val scrollable = all.filter { it.isScrollable }
            .maxByOrNull { boundsOf(it).let { rect -> rect.width() * rect.height() } }
        val bounds = boundsOf(scrollable ?: root(packageName) ?: return false)
        if (bounds.isEmpty) {
            return false
        }
        val x = bounds.centerX()
        val swiped = gestures.swipe(
            x,
            bounds.top + (bounds.height() * 0.6f).toInt(),
            x,
            bounds.top + (bounds.height() * 0.4f).toInt(),
            900,
        )
        if (verbose) {
            android.util.Log.d("HopaScreen", "scroll: swipe=$swiped on $bounds")
        }
        return swiped
    }

    private fun boundsOf(node: AccessibilityNodeInfo): Rect = Rect().also { node.getBoundsInScreen(it) }

    private fun nodes(packageName: String): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        collect(root(packageName), out)
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
    }
}
