package com.hopa.family.board

import com.hopa.contracts.json.CanonicalJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File

/** The model reads and writes a state without losing, adding or reordering anything that hashes. */
class BoardJsonTest {
    private val dir = File(requireNotNull(javaClass.getResource("/fixtures/family")).toURI())

    @Test
    fun `the seed and every final state round-trip exactly`() {
        val states = mutableListOf(BoardJson.json.parseToJsonElement(File(dir, "seed.state.json").readText()))
        dir.listFiles { f -> f.name.startsWith("board-") }.orEmpty().forEach { file ->
            BoardJson.json.parseToJsonElement(file.readText()).jsonObject.getValue("cases").jsonArray
                .forEach { states += it.jsonObject.getValue("after") }
        }
        for (state in states) {
            val decoded = BoardJson.json.decodeFromJsonElement(com.hopa.family.model.BoardState.serializer(), state)
            assertEquals(CanonicalJson.encode(state), CanonicalJson.encode(BoardJson.toElement(decoded)))
        }
    }

    @Test
    fun `an unknown field in a state is refused`() {
        val seed = BoardJson.json.parseToJsonElement(File(dir, "seed.state.json").readText()).jsonObject
        val extra = JsonObject(seed + ("extra" to JsonPrimitive(1)))
        assertThrows(Exception::class.java) { BoardJson.decodeState(extra.toString()) }
    }

    @Test
    fun `the hash is SHA-256 of canonical JSON`() {
        // Same value as the generator's and the Python checker's hash of {}.
        assertEquals(
            "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
            BoardHash.of(JsonObject(emptyMap())),
        )
        val text = JsonObject(mapOf("b" to JsonPrimitive(1), "a" to JsonPrimitive("é\n")))
        assertEquals("{\"a\":\"é\\n\",\"b\":1}", CanonicalJson.encode(text))
    }
}
