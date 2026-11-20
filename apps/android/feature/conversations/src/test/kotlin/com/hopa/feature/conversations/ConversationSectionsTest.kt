package com.hopa.feature.conversations

import com.hopa.contracts.chat.Conversation
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConversationSectionsTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 19)

    private fun at(daysAgo: Long): Long =
        today.minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun conversation(id: String, daysAgo: Long) = Conversation(
        id = id,
        title = id,
        updatedAt = at(daysAgo),
    )

    @Test
    fun `groups today, yesterday, the last week and older`() {
        val sections = conversationSections(
            listOf(
                conversation("now", 0),
                conversation("yesterday", 1),
                conversation("week", 7),
                conversation("old", 8),
            ),
            today = today,
            zone = zone,
        )

        assertEquals(listOf("Today", "Yesterday", "Previous 7 days", "Older"), sections.map { it.title })
        assertEquals(listOf("now"), sections[0].items.map { it.id })
        assertEquals(listOf("yesterday"), sections[1].items.map { it.id })
        assertEquals(listOf("week"), sections[2].items.map { it.id })
        assertEquals(listOf("old"), sections[3].items.map { it.id })
    }

    @Test
    fun `drops empty sections and keeps the input order inside one`() {
        val sections = conversationSections(
            listOf(conversation("first", 3), conversation("second", 2)),
            today = today,
            zone = zone,
        )

        assertEquals(1, sections.size)
        assertEquals(listOf("first", "second"), sections.single().items.map { it.id })
    }

    @Test
    fun `a future date counts as today`() {
        val sections = conversationSections(
            listOf(conversation("ahead", -1)),
            today = today,
            zone = zone,
        )

        assertEquals("Today", sections.single().title)
    }
}
