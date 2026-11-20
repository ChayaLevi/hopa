package com.hopa.feature.easy

import com.hopa.contracts.chat.Conversation

/** What the Easy history page shows: conversations that have something in them. */
data class EasyHistoryUiState(
    val conversations: List<Conversation> = emptyList(),
)
