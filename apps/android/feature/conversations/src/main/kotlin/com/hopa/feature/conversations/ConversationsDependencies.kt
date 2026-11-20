package com.hopa.feature.conversations

import com.hopa.stores.ConversationStore
import com.hopa.stores.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** What the chats sheet and its ViewModel read and write. */
interface ConversationsDependencies {
    val conversations: ConversationStore
    val settings: SettingsRepository
    val selectedConversation: MutableStateFlow<String?>

    /** Null when no bridge is configured; throws when the sync itself fails. */
    suspend fun syncChats(): ChatSyncOutcome?
}
