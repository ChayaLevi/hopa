package com.hopa.localmodel

/** Called by the native decoder for every piece it produces. */
fun interface TokenSink {
    fun onToken(text: String)
}
