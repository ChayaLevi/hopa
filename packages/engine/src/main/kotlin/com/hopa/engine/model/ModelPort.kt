package com.hopa.engine.model

interface ModelPort {
    suspend fun complete(request: ModelRequest): ModelReply
}
