package com.hopa.app.service

/**
 * Bridges a UI toggle to the Activity that must launch the MediaProjection
 * consent dialog (only an Activity can `startActivityForResult`). [MainActivity]
 * installs [launch] while it is alive; the settings screen calls [request] when
 * the user turns on "Look at the screen too" on Android 10.
 */
object ScreenCaptureRequester {
    /** Set by MainActivity: launches the system screen-capture consent dialog. */
    @Volatile
    var launch: (() -> Unit)? = null

    /** Ask for consent if an Activity is available; returns false if none is. */
    fun request(): Boolean {
        val launch = launch ?: return false
        launch()
        return true
    }
}
