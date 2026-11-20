package com.hopa.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/chat-empty.png")
    }
}
