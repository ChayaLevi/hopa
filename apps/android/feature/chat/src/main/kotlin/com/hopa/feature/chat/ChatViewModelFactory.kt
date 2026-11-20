package com.hopa.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hopa.feature.chat.ChatDependencies

class ChatViewModelFactory(
    private val container: ChatDependencies,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(container) as T
}
