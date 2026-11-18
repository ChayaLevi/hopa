package com.hopa.capabilities.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

/**
 * One answer to "can HOPA see the screen", shared by the chat banner, the
 * settings pill and the self check.
 *
 * The value comes from two places: the service pushes a change when Android
 * binds or unbinds it, and a screen asks for a [refresh] when it opens or the
 * app comes back to the front. Nothing polls.
 */
object AccessibilityState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val monitor = AccessibilityMonitor(scope)

    /** Live access state; collect it instead of re-reading Android on a timer. */
    val state: StateFlow<AccessibilityBinding> = monitor.state

    fun isEnabled(context: Context): Boolean {
        // A connected instance cannot exist without access being on, and it is
        // the answer that survives a rebind; the manager's list can lag and its
        // entries are not always complete.
        if (HopaAccessibilityService.instance != null) {
            return true
        }
        return runCatching {
            val manager = context.getSystemService(AccessibilityManager::class.java)
            manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        }.getOrDefault(false)
    }

    /** Re-read Android once: on app start, and whenever the app is resumed. */
    fun refresh(context: Context) {
        monitor.refresh(isEnabled(context), HopaAccessibilityService.instance != null)
    }

    /** Called by the service itself; a bound service is access that works. */
    fun connected() {
        monitor.connected()
    }

    /** Called when Android lets the service go; a rebind may follow at once. */
    fun disconnected(context: Context) {
        // Ask Android, do not assume: a toggle there means "off", a rebind means
        // "still on". `instance` is already cleared, so this reads the real list.
        monitor.refresh(isEnabled(context), connected = false)
    }

    fun openSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
