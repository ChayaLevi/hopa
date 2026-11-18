package com.hopa.contracts.ports

interface BrowserPort {
    suspend fun navigate(url: String): String

    suspend fun read(): String

    suspend fun click(ref: String)

    suspend fun type(text: String, ref: String? = null)
}
