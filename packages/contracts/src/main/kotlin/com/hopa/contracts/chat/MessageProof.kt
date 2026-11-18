package com.hopa.contracts.chat

import kotlinx.serialization.Serializable

/**
 * What one answer did on the device. It is stored with the message, not in a
 * screen-level slot, so the receipt survives a chat switch and a restart.
 */
@Serializable
data class MessageProof(
    val path: String = "",
    val executed: Int = 0,
    val verified: Int = 0,
    val tokens: Int = 0,
    val millis: Long = 0,
    val steps: List<String> = emptyList(),
    /** True when the phone fell back from the hidden screen to the visible one. */
    val fellBack: Boolean = false,
    /** The knowledge bundle the turn routed with, so two runs are comparable. */
    val knowledgeVersion: String = "",
)
