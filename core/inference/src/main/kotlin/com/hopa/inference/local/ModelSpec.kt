package com.hopa.inference.local

import kotlinx.serialization.Serializable

/**
 * One model the phone can run.
 *
 * [strengths], [weaknesses] and [notes] are shown before a download: a person
 * choosing a 2.5 GB file deserves to know what it is good at, what it is bad
 * at, and the one detail that changes the choice.
 *
 * [url] and [sha256] stay empty until the gateway serves the file; an entry
 * without both can be imported but not downloaded, which is exactly the state
 * of the catalogue before `/v1/models` exists.
 */
@Serializable
data class ModelSpec(
    val id: String,
    val name: String,
    val modelClass: ModelClass,
    val file: String = "",
    val parameters: String,
    val quantization: String = "Q4_K_M",
    val bytes: Long,
    val minRamGb: Int,
    val contextTokens: Int,
    val speedClass: String,
    val supportsTools: Boolean = false,
    val thinking: Boolean = false,
    val license: String,
    val licenseUrl: String = "",
    val url: String = "",
    val sha256: String = "",
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    /** The one the picker offers first in its class when it fits the phone. */
    val preferred: Boolean = false,
) {
    val downloadable: Boolean get() = url.isNotBlank() && sha256.isNotBlank()
}
