package com.hopa.engine.ports

interface Clock {
    fun nowMillis(): Long

    suspend fun sleep(millis: Long)
}
