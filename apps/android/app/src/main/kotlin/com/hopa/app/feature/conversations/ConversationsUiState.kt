package com.hopa.app.feature.conversations

import com.hopa.contracts.chat.Conversation

data class ConversationsUiState(
    val conversations: List<Conversation> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    /** True while the bridge sync runs; the sheet shows a spinner. */
    val syncing: Boolean = false,
    /** One-shot result of the last sync, shown as a toast and then cleared. */
    val message: String? = null,
)
