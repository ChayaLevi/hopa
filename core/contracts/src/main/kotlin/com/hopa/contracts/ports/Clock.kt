package com.hopa.contracts.ports

interface Clock {
    fun nowMillis(): Long

    suspend fun sleep(millis: Long)
}
