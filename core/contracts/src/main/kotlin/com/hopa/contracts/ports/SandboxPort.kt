package com.hopa.contracts.ports

interface SandboxPort {
    suspend fun run(script: String): String
}
