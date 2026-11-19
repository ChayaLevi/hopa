package com.hopa.observation

import com.hopa.contracts.json.HopaJson
import com.hopa.contracts.wire.Observation
import com.hopa.contracts.wire.ObservationNode
import com.hopa.contracts.wire.ScreenSize
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObservationProducerTest {
    private val producer = ObservationProducer()

    @Test
    fun refs_are_assigned_in_pre_order() {
        val tree = ObservationNode(
            type = "root",
            children = listOf(
                ObservationNode(type = "text", text = "one"),
                ObservationNode(
                    type = "group",
                    children = listOf(ObservationNode(type = "button", text = "two")),
                ),
            ),
        )
        val produced = producer.produce(observation(tree = tree))
        val refs = mutableListOf<String>()
        collectRefs(produced.tree, refs)
        assertEquals(listOf("e1", "e2", "e3", "e4"), refs)
    }

    @Test
    fun password_fields_and_their_children_are_dropped() {
        val tree = ObservationNode(
            type = "root",
            children = listOf(
                ObservationNode(type = "text", text = "Sign in"),
                ObservationNode(
                    type = "edit",
                    id = "password",
                    text = "hunter2",
                    children = listOf(ObservationNode(type = "text", text = "must go")),
                ),
            ),
        )
        val produced = producer.produce(observation(tree = tree))
        assertEquals(1, produced.tree.children.size)
        assertEquals("Sign in", produced.tree.children.single().text)
        assertTrue(produced.truncated)
    }

    @Test
    fun emails_phones_cards_and_codes_are_redacted() {
        val text = "mail a@b.com, phone +49 151 2345678, card 4111 1111 1111 1111, code 123456"
        val produced = producer.produce(observation(text = text))
        assertTrue(produced.text.contains("[email]"))
        assertTrue(produced.text.contains("[phone]"))
        assertTrue(produced.text.contains("[card]"))
        assertTrue(produced.text.contains("[otp]"))
        assertFalse(produced.text.contains("a@b.com"))
        assertFalse(produced.text.contains("4111"))
    }

    @Test
    fun the_node_cap_keeps_the_focus_path() {
        val children = (0 until 500).map { index ->
            ObservationNode(type = "text", id = "n$index", text = "row $index", focused = index == 450)
        }
        val produced = producer.produce(
            observation(tree = ObservationNode(type = "root", children = children)),
        )
        assertTrue(count(produced.tree) <= 400, "count was ${count(produced.tree)}")
        val ids = mutableListOf<String>()
        collectIds(produced.tree, ids)
        assertTrue(ids.contains("n450"), "the focused row must survive the cap")
    }

    @Test
    fun long_text_is_capped_and_flagged() {
        val produced = producer.produce(observation(text = "a".repeat(7_000)))
        assertEquals(6_000, produced.text.length)
        assertTrue(produced.truncated)
    }

    @Test
    fun the_hash_fixtures_round_trip() {
        val document = HopaJson.element.parseToJsonElement(
            resource("/fixtures/observation-hash.json"),
        ).jsonObject
        for (case in document["cases"]!!.jsonArray) {
            val item = case.jsonObject
            val observation = HopaJson.element.decodeFromJsonElement(
                Observation.serializer(),
                item["observation"]!!,
            )
            assertEquals(
                item["hash"]!!.jsonPrimitive.content,
                producer.produce(observation).hash,
                item["name"]!!.jsonPrimitive.content,
            )
        }
    }

    @Test
    fun the_limits_match_the_contract() {
        val document = HopaJson.element.parseToJsonElement(
            resource("/observation/limits.v1.json"),
        ).jsonObject
        val limits = ObservationLimits()
        assertEquals(limits.maxNodes, document["maxNodes"]!!.jsonPrimitive.int)
        assertEquals(limits.maxTextChars, document["maxTextChars"]!!.jsonPrimitive.int)
        assertEquals(limits.maxElementLines, document["maxElementLines"]!!.jsonPrimitive.int)
        assertEquals(
            limits.maxBrowserTextChars,
            document["maxBrowserTextChars"]!!.jsonPrimitive.int,
        )
    }

    private fun observation(text: String = "", tree: ObservationNode = ObservationNode(type = "root")): Observation =
        Observation(
            id = "obs-test",
            timestamp = 1,
            screenSize = ScreenSize(width = 1080, height = 2400),
            text = text,
            tree = tree,
        )

    private fun collectRefs(node: ObservationNode, into: MutableList<String>) {
        if (node.ref.isNotEmpty()) {
            into += node.ref
        }
        node.children.forEach { collectRefs(it, into) }
    }

    private fun collectIds(node: ObservationNode, into: MutableList<String>) {
        if (node.id.isNotEmpty()) {
            into += node.id
        }
        node.children.forEach { collectIds(it, into) }
    }

    private fun count(node: ObservationNode): Int = 1 + node.children.sumOf { count(it) }

    private fun resource(path: String): String =
        requireNotNull(javaClass.getResource(path)) { "missing $path" }.readText()
}
