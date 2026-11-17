package com.hopa.sidecar

import com.hopa.engine.ports.Clock

class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override suspend fun sleep(millis: Long) {
        kotlinx.coroutines.delay(millis)
    }
}
