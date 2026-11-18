package com.hopa.stores

import com.hopa.contracts.chat.ChatMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryConversationStoreTest {
    private fun message(id: String, role: String, text: String, at: Long) =
        ChatMessage(id = id, role = role, text = text, timestamp = at)

    @Test
    fun a_new_chat_takes_the_first_user_line_as_its_title() = runBlocking {
        val store = InMemoryConversationStore()
        val conversation = store.create()

        store.append(conversation.id, message("m1", "user", "book a table for two", 10))

        val stored = store.all().single()
        assertEquals("book a table for two", stored.title)
        assertEquals(1, stored.messages.size)
    }

    @Test
    fun the_same_message_twice_is_stored_once() = runBlocking {
        val store = InMemoryConversationStore()
        val conversation = store.create()
        val message = message("m1", "user", "hello", 10)

        store.append(conversation.id, message)
        store.append(conversation.id, message)

        assertEquals(1, store.all().single().messages.size)
    }

    @Test
    fun dropping_the_last_exchange_leaves_the_earlier_turns() = runBlocking {
        val store = InMemoryConversationStore()
        val conversation = store.create()
        store.append(conversation.id, message("m1", "user", "first", 10))
        store.append(conversation.id, message("m2", "assistant", "answer", 20))
        store.append(conversation.id, message("m3", "user", "second", 30))
        store.append(conversation.id, message("m4", "assistant", "answer two", 40))

        store.dropLastExchange(conversation.id)

        val left = store.all().single().messages.map { it.id }
        assertEquals(listOf("m1", "m2"), left)
    }

    @Test
    fun search_looks_at_titles_and_messages() = runBlocking {
        val store = InMemoryConversationStore()
        val first = store.create()
        store.append(first.id, message("m1", "user", "order a pizza", 10))
        val second = store.create()
        store.append(second.id, message("m2", "user", "book a taxi", 20))

        assertEquals(1, store.search("pizza").size)
        assertEquals(2, store.search("").size)
    }

    @Test
    fun a_deleted_chat_leaves_the_list_and_comes_back_on_restore() = runBlocking {
        val store = InMemoryConversationStore()
        val conversation = store.create()

        store.delete(conversation.id)
        assertTrue(store.all().isEmpty())
        assertEquals(1, store.allWithDeleted().size)

        store.restore(conversation.id)
        assertEquals(1, store.all().size)
    }

    @Test
    fun pinned_chats_come_first() = runBlocking {
        val store = InMemoryConversationStore()
        val older = store.create()
        store.append(older.id, message("m1", "user", "older", 10))
        val newer = store.create()
        store.append(newer.id, message("m2", "user", "newer", 20))

        store.setPinned(older.id, true)

        assertEquals(listOf(older.id, newer.id), store.all().map { it.id })
    }
}
