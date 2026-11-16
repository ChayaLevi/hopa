package com.hopa.capabilities.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class HopaAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // The loop reads the tree on demand, but a virtual display can be missing
        // from AccessibilityService.windows while its events still arrive; keep
        // the newest source so that case can be read.
        val source = event?.source ?: return
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isEmpty()) {
            return
        }
        lastSource = AccessibilityNodeInfo.obtain(source)
        lastSourcePackage = packageName
        lastSourceAt = System.currentTimeMillis()
    }

    override fun onInterrupt() {
        // Nothing to interrupt: actions are short and atomic.
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun activeRoot(): AccessibilityNodeInfo? =
        rootInActiveWindow ?: windows.firstOrNull { it.isActive }?.root

    companion object {
        @Volatile
        var instance: HopaAccessibilityService? = null
            private set

        /** The newest window-event source, used when the window list lacks a display. */
        @Volatile
        var lastSource: AccessibilityNodeInfo? = null
            private set

        @Volatile
        var lastSourcePackage: String = ""
            private set

        @Volatile
        var lastSourceAt: Long = 0
            private set
    }
}
