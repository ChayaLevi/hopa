package com.hopa.app.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hopa.feature.chat.ChatScreen
import com.hopa.feature.chat.ChatViewModel
import com.hopa.feature.conversations.ChatsSheet
import com.hopa.feature.conversations.ConversationsViewModel

@Composable
fun ChatRoute(
    activeTab: String,
    onSelectTab: (String) -> Unit,
    onOpenActMode: () -> Unit = {},
    initialGoal: String = "",
    easy: Boolean = false,
    onOpenSettings: () -> Unit = {},
) {
    val viewModel: ChatViewModel = hiltViewModel()
    val conversationsViewModel: ConversationsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    var chatsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(initialGoal) {
        if (initialGoal.isNotBlank()) {
            viewModel.prefill(initialGoal)
        }
    }

    ChatScreen(
        state = state,
        activeTab = activeTab,
        onSelectTab = onSelectTab,
        onOpenActMode = onOpenActMode,
        onInput = viewModel::onInput,
        onSend = viewModel::send,
        onOption = viewModel::sendOption,
        onQuick = viewModel::sendQuick,
        onNewChat = viewModel::newChat,
        onOpenConversations = { chatsOpen = true },
        onStartVoice = viewModel::startVoice,
        onStopVoice = viewModel::stopVoice,
        onDismissVoiceError = viewModel::dismissVoiceError,
        onSpeak = viewModel::speak,
        onRetry = viewModel::retryLast,
        onUndo = viewModel::undoLast,
        onDiscardAttachment = viewModel::discardAttachment,
        onDismissAttachmentNotice = viewModel::dismissAttachmentNotice,
        easy = easy,
        onOpenSettings = onOpenSettings,
        accessAllowed = settings.accessibilityAllowed,
        onApprovePlan = viewModel::approvePlan,
        onCancelPlan = viewModel::cancelPlan,
        onSaveSkill = viewModel::saveSkill,
        tasks = tasks,
        onOpenTask = { conversationsViewModel.select(it.conversationId) },
        onDismissTask = viewModel::dismissTask,
    )

    if (chatsOpen) {
        ChatsSheet(
            currentId = state.current?.id,
            onDismiss = { chatsOpen = false },
            onSelect = { id ->
                conversationsViewModel.select(id)
                chatsOpen = false
            },
            onNewChat = { chatsOpen = false },
        )
    }
}
