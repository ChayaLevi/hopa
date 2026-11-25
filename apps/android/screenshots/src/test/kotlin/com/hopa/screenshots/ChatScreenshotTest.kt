package com.hopa.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.hopa.feature.chat.ChatScreen
import com.hopa.feature.chat.ChatUiState
import com.hopa.ui.theme.HopaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The conversation screen as it looks before the first message. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun empty() {
        compose.setContent {
            HopaTheme {
                ChatScreen(
                    state = ChatUiState(),
                    activeTab = "chat",
                    onSelectTab = {},
                    onInput = {},
                    onSend = {},
                    onOption = {},
                    onQuick = {},
                    onNewChat = {},
                    onOpenConversations = {},
                    onStartVoice = {},
                    onStopVoice = {},
                    onDismissVoiceError = {},
                    onSpeak = {},
                    onRetry = {},
                    onUndo = {},
                    onDiscardAttachment = {},
                    onDismissAttachmentNotice = {},
                    hourOfDay = 9,
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/chat-empty.png")
    }

    /** The answer chips of a choice; since ADR-0031 phase 2 they come from an A2UI surface. */
    @Test
    fun options() {
        val chosen = mutableListOf<String>()
        compose.setContent {
            HopaTheme {
                ChatScreen(
                    state = ChatUiState(options = listOf("Personal", "Work", "Family")),
                    activeTab = "chat",
                    onSelectTab = {},
                    onInput = {},
                    onSend = {},
                    onOption = { chosen += it },
                    onQuick = {},
                    onNewChat = {},
                    onOpenConversations = {},
                    onStartVoice = {},
                    onStopVoice = {},
                    onDismissVoiceError = {},
                    onSpeak = {},
                    onRetry = {},
                    onUndo = {},
                    onDiscardAttachment = {},
                    onDismissAttachmentNotice = {},
                    hourOfDay = 9,
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/chat-options.png")
        compose.onNodeWithText("Work").performClick()
        org.junit.Assert.assertEquals(listOf("Work"), chosen)
    }

    @Test
    fun graphTurn() {
        val conversation = com.hopa.contracts.chat.Conversation(
            id = "c1",
            title = "Camera",
            messages = listOf(
                com.hopa.contracts.chat.ChatMessage(
                    id = "m1",
                    role = "user",
                    text = "open camera",
                    timestamp = 1L,
                ),
                com.hopa.contracts.chat.ChatMessage(
                    id = "m2",
                    role = "assistant",
                    text = "Opened Camera.",
                    timestamp = 2L,
                    proof = com.hopa.contracts.chat.MessageProof(
                        path = "accessibility",
                        executed = 1,
                        verified = 1,
                        tokens = 0,
                        millis = 3674,
                        steps = listOf("0. ui.launch com.sec.android.app.camera"),
                        knowledgeVersion = "sha256:test",
                        model = "openai/deepseek-v4-flash",
                        source = "graph",
                    ),
                ),
            ),
        )
        compose.setContent {
            HopaTheme {
                ChatScreen(
                    state = ChatUiState(
                        conversations = listOf(conversation),
                        current = conversation,
                    ),
                    activeTab = "chat",
                    onSelectTab = {},
                    onInput = {},
                    onSend = {},
                    onOption = {},
                    onQuick = {},
                    onNewChat = {},
                    onOpenConversations = {},
                    onStartVoice = {},
                    onStopVoice = {},
                    onDismissVoiceError = {},
                    onSpeak = {},
                    onRetry = {},
                    onUndo = {},
                    onDiscardAttachment = {},
                    onDismissAttachmentNotice = {},
                    hourOfDay = 9,
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/chat-graph-turn.png")
    }
}
