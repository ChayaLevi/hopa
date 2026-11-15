package com.hopa.app.service

/**
 * Whether HOPA's own screen is visible. A run usually happens inside another
 * app, so a finished answer has to come back here rather than wait in silence.
 */
object AppVisibility {
    @Volatile
    var foreground: Boolean = false
}
