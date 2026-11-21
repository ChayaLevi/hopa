package com.hopa.capabilities.accessibility.state

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
        val listed = runCatching {
            val manager = context.getSystemService(AccessibilityManager::class.java)
            manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        }.getOrDefault(false)
        if (listed) {
            return true
        }
        // Some OEM builds return an incomplete list right after a toggle. The
        // secure setting is the source Android itself reads, so use it as the
        // fallback instead of telling the person access is off when it is on.
        val component = "${context.packageName}/" +
            "com.hopa.capabilities.accessibility.state.HopaAccessibilityService"
        val stored = runCatching {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        }.getOrNull().orEmpty()
        return stored.split(':').any { it.equals(component, ignoreCase = true) } ||
            stored.contains(context.packageName, ignoreCase = true)
    }

    /** Re-read Android once: on app start, and whenever the app is resumed. */
    fun refresh(context: Context) {
        val enabled = isEnabled(context)
        val connected = HopaAccessibilityService.instance != null
        android.util.Log.i("HopaA11yState", "refresh enabled=$enabled connected=$connected")
        monitor.refresh(enabled, connected)
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

    /**
     * True once the service is connected, waiting briefly for a rebind.
     *
     * After an update, a restart or a toggle Android disconnects and reconnects
     * the service; a turn that reads the screen in that window used to fail with
     * "no screen answered". Waiting a couple of seconds is honest.
     */
    suspend fun awaitConnected(waitMillis: Long = CONNECT_WAIT_MS): Boolean {
        var waited = 0L
        while (true) {
            if (HopaAccessibilityService.instance != null) {
                return true
            }
            if (waited >= waitMillis) {
                return false
            }
            kotlinx.coroutines.delay(CONNECT_STEP_MS)
            waited += CONNECT_STEP_MS
        }
    }

    private const val CONNECT_WAIT_MS = 2_500L
    private const val CONNECT_STEP_MS = 100L
}
