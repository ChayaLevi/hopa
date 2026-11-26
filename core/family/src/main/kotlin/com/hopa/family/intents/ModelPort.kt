package com.hopa.family.intents

/**
 * The family logic's only way to a model. The app implements it over its own model client (the
 * member's key, on the device, D6); this module never imports `inference`. Blocking: the app calls
 * the jobs from a background thread.
 */
fun interface ModelPort {
    fun complete(request: ModelRequest): ModelResult
}
