package com.hopa.app.di

import android.content.Context
import com.hopa.app.ui.UiApprovalGateway
import com.hopa.app.runtime.ParallelRunner
import com.hopa.app.runtime.RunCoordinator
import com.hopa.app.runtime.RunGate
import com.hopa.app.share.AttachmentIntake
import com.hopa.capabilities.voice.VoiceRecorder
import com.hopa.contracts.chat.Attachment
import com.hopa.data.store.DbRunHistory
import com.hopa.observation.source.ScreenSourceDevice
import com.hopa.stores.AppSettings
import com.hopa.stores.ConversationStore
import com.hopa.stores.SettingsRepository
import com.hopa.stores.SkillStore
import kotlinx.coroutines.flow.MutableStateFlow

/** Only what the chat screen needs; the rest of the app is out of reach. */
interface ChatDependencies {
    val context: Context
    val conversations: ConversationStore
    val settings: SettingsRepository
    val selectedConversation: MutableStateFlow<String?>
    val pendingAttachment: MutableStateFlow<Attachment?>
    val pendingNotice: MutableStateFlow<String?>
    val intake: AttachmentIntake
    val runHistory: DbRunHistory
    val skills: SkillStore
    val runs: RunCoordinator
    val runner: ParallelRunner
    val runGate: RunGate
    val approval: UiApprovalGateway
    val surfaces: suspend (AppSettings) -> ScreenSourceDevice
    val voice: VoiceRecorder

    suspend fun speak(text: String): Boolean

    suspend fun transcribe(wav: ByteArray): String?

    fun useGate(trustedApps: Collection<String>, autonomous: Boolean = false)

    fun bringToFront()
}
