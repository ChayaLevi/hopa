package com.hopa.engine.model

data class ModelReply(
    val text: String,
    val usage: ModelUsage = ModelUsage(),
)
