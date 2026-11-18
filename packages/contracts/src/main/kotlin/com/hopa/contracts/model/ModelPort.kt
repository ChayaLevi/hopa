package com.hopa.contracts.model

interface ModelPort {
    suspend fun complete(request: ModelRequest): ModelReply
}
