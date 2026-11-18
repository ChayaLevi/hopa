package com.hopa.contracts.model

data class ModelReply(
    val text: String,
    val usage: ModelUsage = ModelUsage(),
)
