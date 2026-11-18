package com.hopa.capabilities.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class HopaAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
        AccessibilityState.connected()
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
        source.refresh()
        val children = source.childCount
        val now = System.currentTimeMillis()
        // A window-state event often carries a root with no children; a content
        // event for the same package carries the real subtree, so keep the better.
        val existing = sources[packageName]
        val fresh = existing != null && now - existing.at < SOURCE_TTL_MS
        if (!fresh || children > existing!!.children) {
            sources[packageName] = EventSource(AccessibilityNodeInfo.obtain(source), now, children)
            // Only when the cache changes: a content event fires on every keystroke.
            android.util.Log.d(
                "HopaA11y",
                "$packageName children=$children packages=${sources.size}",
            )
        }
        prune(now)
    }

    override fun onInterrupt() {
        // Nothing to interrupt: actions are short and atomic.
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        // A rebind can connect the new instance before the old one unbinds; only
        // the instance that is still current may clear the handle.
        if (instance === this) {
            instance = null
            AccessibilityState.disconnected(this)
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            AccessibilityState.disconnected(this)
        }
        super.onDestroy()
    }

    fun activeRoot(): AccessibilityNodeInfo? =
        rootInActiveWindow ?: windows.firstOrNull { it.isActive }?.root

    companion object {
        @Volatile
        var instance: HopaAccessibilityService? = null
            private set

        /**
         * The newest useful source per package. A virtual display is missing from
         * AccessibilityService.windows on some builds, but its events arrive; two
         * parallel runs need their own entry, so this is a small map, not one slot.
         */
        private class EventSource(
            val node: AccessibilityNodeInfo,
            val at: Long,
            val children: Int,
        )

        private val sources = java.util.concurrent.ConcurrentHashMap<String, EventSource>()

        /** Drop the cached screen for [packageName]; the next read waits for a new one. */
        fun forget(packageName: String) {
            if (packageName.isNotEmpty()) {
                sources.remove(packageName)
            }
        }

        /** The last source for [packageName], if it is fresh enough to trust. */
        fun sourceFor(packageName: String): AccessibilityNodeInfo? {
            val entry = sources[packageName] ?: return null
            return if (System.currentTimeMillis() - entry.at < SOURCE_TTL_MS) {
                entry.node
            } else {
                null
            }
        }

        private fun prune(now: Long) {
            sources.entries.removeIf { now - it.value.at > SOURCE_TTL_MS }
            if (sources.size > MAX_SOURCES) {
                val oldest = sources.entries.minByOrNull { it.value.at } ?: return
                sources.remove(oldest.key)
            }
        }

        private const val SOURCE_TTL_MS = 15_000L
        private const val MAX_SOURCES = 8
    }
}
