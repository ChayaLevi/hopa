package com.hopa.engine.model

data class ModelRequest(
    val messages: List<ModelMessage>,
    val maxTokens: Int = 2048,
    val reasoningEffort: String = "",
    /** Base64 JPEG frames of the screen, when the model can see them. */
    val images: List<String> = emptyList(),
)
