package com.hopa.app.feature.chat

import com.hopa.engine.gate.ApprovalRequest
import com.hopa.state.sessions.Attachment
import com.hopa.state.sessions.Conversation

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val current: Conversation? = null,
    val input: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val options: List<String> = emptyList(),
    val pendingApproval: ApprovalRequest? = null,
    val recording: Boolean = false,
    val transcribing: Boolean = false,
    val voiceError: String? = null,
    val attachment: Attachment? = null,
    val attachmentNotice: String? = null,
    /** A plan waiting for one approval before it runs. */
    val pendingPlan: List<String>? = null,
    /** Goals whose proof was saved as a skill in this session. */
    val savedGoals: Set<String> = emptySet(),
)
