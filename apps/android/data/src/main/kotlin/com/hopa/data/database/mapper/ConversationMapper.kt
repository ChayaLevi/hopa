package com.hopa.data.database.mapper

import com.hopa.data.database.entity.SessionEntity
import com.hopa.data.database.entity.TurnEntity
import com.hopa.state.sessions.Attachment
import com.hopa.state.sessions.ChatMessage
import com.hopa.state.sessions.Conversation
import com.hopa.state.sessions.MessageProof
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ConversationMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun conversation(session: SessionEntity, turns: List<TurnEntity>): Conversation =
        Conversation(
            id = session.id,
            title = session.title,
            messages = turns.map {
                ChatMessage(
                    id = it.id,
                    role = it.role,
                    text = it.text,
                    timestamp = it.timestamp,
                    attachment = it.attachment(),
                    proof = it.proof(),
                )
            },
            updatedAt = session.updatedAt,
            deletedAt = session.deletedAt,
            pinned = session.pinned,
            archivedAt = session.archivedAt,
        )

    fun turn(sessionId: String, message: ChatMessage): TurnEntity =
        TurnEntity(
            id = message.id,
            sessionId = sessionId,
            role = message.role,
            text = message.text,
            timestamp = message.timestamp,
            attachmentName = message.attachment?.name.orEmpty(),
            attachmentMime = message.attachment?.mime.orEmpty(),
            attachmentBytes = message.attachment?.bytes ?: 0,
            attachmentPath = message.attachment?.path.orEmpty(),
            proof = message.proof?.let { json.encodeToString(it) }.orEmpty(),
        )

    private fun TurnEntity.attachment(): Attachment? =
        if (attachmentPath.isBlank() || attachmentName.isBlank()) {
            null
        } else {
            Attachment(
                name = attachmentName,
                mime = attachmentMime,
                bytes = attachmentBytes,
                path = attachmentPath,
            )
        }

    private fun TurnEntity.proof(): MessageProof? =
        if (proof.isBlank()) {
            null
        } else {
            runCatching { json.decodeFromString<MessageProof>(proof) }.getOrNull()
        }
}
