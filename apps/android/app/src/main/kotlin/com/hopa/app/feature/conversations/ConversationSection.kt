package com.hopa.app.feature.conversations

import com.hopa.contracts.chat.Conversation

/** One dated group in the chats sheet. */
data class ConversationSection(
    val title: String,
    val items: List<Conversation>,
)
