package com.hopa.feature.chat

import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.contracts.chat.Attachment
import com.hopa.contracts.chat.Conversation

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val current: Conversation? = null,
    val input: String = "",
    val busy: Boolean = false,
    /** On-device text as it is produced, shown while the turn runs. */
    val liveText: String = "",
    /** True when this turn runs a model on this phone, so the first token is slow. */
    val onDevice: Boolean = false,
    /** What runs this turn while it is still going, e.g. "Graph · Settings". */
    val liveLabel: String = "",
    /** Provider and model for the receipt, so 'it answered worse' is explainable. */
    val receiptModel: String = "",
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
