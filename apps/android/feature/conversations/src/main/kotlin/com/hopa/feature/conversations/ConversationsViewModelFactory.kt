package com.hopa.feature.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hopa.feature.conversations.ConversationsDependencies

class ConversationsViewModelFactory(
    private val container: ConversationsDependencies,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ConversationsViewModel(container) as T
}
