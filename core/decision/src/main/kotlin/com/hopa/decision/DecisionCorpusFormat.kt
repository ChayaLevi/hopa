package com.hopa.decision

import com.hopa.contracts.wire.Action
import kotlinx.serialization.Serializable

/** The on-disk shape of the corpus; codes stay lower-case for readability. */
@Serializable
internal data class CorpusFile(
    val schemaVersion: String,
    val cases: List<CorpusCase>,
)

@Serializable
internal data class CorpusCase(
    val id: String,
    val action: Action? = null,
    val screen: String = "",
    val history: List<String> = emptyList(),
    val approvals: List<String> = emptyList(),
    val attentionLeft: Int = 3,
    val cancelled: Boolean = false,
    val atStepLimit: Boolean = false,
    val expected: String,
    val allowed: List<String>,
    val model: String? = null,
    val notes: String = "",
)
