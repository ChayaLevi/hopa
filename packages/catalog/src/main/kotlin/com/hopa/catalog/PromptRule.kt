package com.hopa.catalog

import kotlinx.serialization.Serializable

/** A prompt promise the person is told, kept as data so it can be reviewed. */
@Serializable
data class PromptRule(
    val id: String,
    val phrases: List<String>,
)
