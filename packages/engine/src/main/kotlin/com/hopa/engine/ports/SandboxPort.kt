package com.hopa.engine.ports

interface SandboxPort {
    suspend fun run(script: String): String
}
