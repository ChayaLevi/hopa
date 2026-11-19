package com.hopa.app.feature.conversations

import com.hopa.contracts.chat.Conversation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One dated group in the chats sheet. */
data class ConversationSection(
    val title: String,
    val items: List<Conversation>,
)

/**
 * Group conversations the way a person scans them: today, yesterday, the last
 * week, then everything older. Pure on purpose - the clock and the zone are
 * parameters, so the grouping is tested without a device or a coroutine.
 */
fun conversationSections(
    conversations: List<Conversation>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<ConversationSection> {
    val groups = linkedMapOf(
        "Today" to mutableListOf<Conversation>(),
        "Yesterday" to mutableListOf(),
        "Previous 7 days" to mutableListOf(),
        "Older" to mutableListOf(),
    )
    for (conversation in conversations) {
        val date = Instant.ofEpochMilli(conversation.updatedAt)
            .atZone(zone)
            .toLocalDate()
        val days = ChronoUnit.DAYS.between(date, today)
        val key = when {
            days <= 0 -> "Today"
            days == 1L -> "Yesterday"
            days <= 7 -> "Previous 7 days"
            else -> "Older"
        }
        groups.getValue(key) += conversation
    }
    return groups.mapNotNull { (title, items) ->
        if (items.isEmpty()) null else ConversationSection(title, items)
    }
}
