package com.hopa.app.feature.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopa.app.di.ConversationsDependencies
import com.hopa.contracts.chat.Conversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationsViewModel(
    private val container: ConversationsDependencies,
) : ViewModel() {
    private val _state = MutableStateFlow(ConversationsUiState())
    val state: StateFlow<ConversationsUiState> = _state.asStateFlow()
    private var lastDeleted: String? = null

    init {
        load()
    }

    fun onQuery(value: String) {
        _state.update { it.copy(query = value) }
        load()
    }

    fun newChat(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val conversation = container.conversations.create()
            container.selectedConversation.value = conversation.id
            container.settings.update { it.copy(selectedConversation = conversation.id) }
            onCreated(conversation.id)
        }
    }

    fun select(id: String) {
        container.selectedConversation.value = id
        viewModelScope.launch {
            container.settings.update { it.copy(selectedConversation = id) }
        }
    }

    fun rename(id: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) {
            return
        }
        viewModelScope.launch {
            container.conversations.rename(id, clean)
            load()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            lastDeleted = id
            container.conversations.delete(id)
            load()
        }
    }

    fun undoDelete() {
        val id = lastDeleted ?: return
        viewModelScope.launch {
            container.conversations.restore(id)
            lastDeleted = null
            load()
        }
    }

    fun setPinned(conversation: Conversation, pinned: Boolean) {
        viewModelScope.launch {
            container.conversations.setPinned(conversation.id, pinned)
            load()
        }
    }

    fun archive(id: String) {
        viewModelScope.launch {
            container.conversations.archive(id)
            load()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            container.conversations.deleteAll()
            load()
        }
    }

    /** Pull chats through the bridge; the result is one message for the sheet. */
    fun sync() {
        if (_state.value.syncing) {
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            val message = runCatching {
                val sync = container.chatSync()
                    ?: return@runCatching "Set a bridge first (Settings > Bridge)"
                runCatching { sync.run() }.fold(
                    { report ->
                        val deleted = if (report.tombstoned > 0) {
                            ", ${report.tombstoned} deleted"
                        } else {
                            ""
                        }
                        "Synced: ${report.adopted} chats$deleted"
                    },
                    { error -> "Sync failed: ${error.message}" },
                )
            }.getOrElse { error -> "Sync failed: ${error.message}" }
            load()
            _state.update { it.copy(syncing = false, message = message) }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                container.conversations.search(_state.value.query)
            }.onSuccess { conversations ->
                _state.update {
                    it.copy(conversations = conversations, loading = false)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(loading = false, error = error.message ?: "Could not load chats")
                }
            }
        }
    }
}
