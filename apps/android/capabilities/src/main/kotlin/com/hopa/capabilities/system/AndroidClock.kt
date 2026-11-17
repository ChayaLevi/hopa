package com.hopa.capabilities.system

import com.hopa.engine.ports.Clock
import kotlinx.coroutines.delay

class AndroidClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override suspend fun sleep(millis: Long) {
        delay(millis)
    }
}
