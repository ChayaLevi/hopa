package com.hopa.capabilities.accessibility.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen access as events, not as a timer.
 *
 * The service reports when Android binds and unbinds it; the app reads this
 * flow instead of asking Android every couple of seconds. A disconnect is held
 * for [graceMs] first: the system rebinds the service for a second or two after
 * a run closes its display, and a banner that flickers was the reason the poll
 * existed in the first place.
 *
 * Not a singleton, and not Android-aware: the service owns one through
 * [AccessibilityState], and unit tests drive this one with their own scope.
 */
class AccessibilityMonitor(
    private val scope: CoroutineScope,
    private val graceMs: Long = REBIND_GRACE_MS,
) {
    private val lock = Any()
    private var pending: Job? = null
    private val _state = MutableStateFlow(AccessibilityBinding())
    val state: StateFlow<AccessibilityBinding> = _state.asStateFlow()

    /** Android bound the service: access works now, and the grace is over. */
    fun connected() = synchronized(lock) {
        pending?.cancel()
        pending = null
        _state.value = AccessibilityBinding(enabled = true, connected = true)
    }

    /** The service went away, but a rebind may be on its way. */
    fun disconnected() = refresh(enabled = _state.value.enabled, connected = false)

    /**
     * A snapshot from Android: app start, or the app coming back to the front.
     * A "not connected" answer does not cut a live binding short, because that
     * is exactly the rebind window.
     */
    fun refresh(enabled: Boolean, connected: Boolean) {
        if (connected) {
            connected()
            return
        }
        synchronized(lock) {
            if (!_state.value.connected) {
                pending?.cancel()
                pending = null
                _state.value = AccessibilityBinding(enabled = enabled, connected = false)
                return
            }
            _state.value = _state.value.copy(enabled = enabled)
            if (pending != null) {
                return
            }
            pending = scope.launch {
                delay(graceMs)
                synchronized(lock) {
                    pending = null
                    _state.update { it.copy(connected = false) }
                }
            }
        }
    }

    companion object {
        /**
         * Longer than the "second or two" a Samsung rebind takes, and shorter
         * than the four seconds the old two-check poll needed to admit a real
         * outage.
         */
        const val REBIND_GRACE_MS = 3_000L
    }
}
