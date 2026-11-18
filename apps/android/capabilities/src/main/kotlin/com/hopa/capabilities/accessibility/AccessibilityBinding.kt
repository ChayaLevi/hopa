package com.hopa.capabilities.accessibility

/**
 * What screen access looks like right now.
 *
 * [enabled] is Android's own list, which lags a toggle by a moment and cannot
 * see a rebind; [connected] is the bound service, which is the honest answer to
 * "can HOPA read the screen this second".
 */
data class AccessibilityBinding(
    val enabled: Boolean = false,
    val connected: Boolean = false,
) {
    val ready: Boolean get() = enabled && connected
}
