package com.hopa.app.di

import com.hopa.app.link.sync.ChatSync
import com.hopa.stores.ConversationStore
import com.hopa.stores.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** What the chats sheet and its ViewModel read and write. */
interface ConversationsDependencies {
    val conversations: ConversationStore
    val settings: SettingsRepository
    val selectedConversation: MutableStateFlow<String?>

    suspend fun chatSync(): ChatSync?
}
