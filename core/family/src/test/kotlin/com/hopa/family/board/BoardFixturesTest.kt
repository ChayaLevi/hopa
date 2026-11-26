package com.hopa.family.board

import com.hopa.contracts.json.CanonicalJson
import com.hopa.family.model.BoardState
import com.hopa.family.model.LogEntry
import com.hopa.family.model.Scope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File

/**
 * Replays every case of `spec/fixtures/family/board-*.json` (generated from Homemind's core) and
 * checks this reducer reaches the same outcomes, the same final state and the same hashes, on a
 * parent device and on a family-only device.
 */
class BoardFixturesTest {
    private val json = BoardJson.json
    private val dir = File(requireNotNull(javaClass.getResource(DIR)) { "missing $DIR" }.toURI())
    private val seed: BoardState = BoardJson.decodeState(File(dir, "seed.state.json").readText())

    private fun files(): List<File> = dir.listFiles { f -> f.name.startsWith("board-") && f.name.endsWith(".json") }
        .orEmpty()
        .sortedBy { it.name }

    private fun cases(): List<Pair<String, JsonObject>> = files().flatMap { file ->
        json.parseToJsonElement(file.readText()).jsonObject.getValue("cases").jsonArray.map {
            "${file.name}: ${it.jsonObject.getValue("name").jsonPrimitive.content}" to it.jsonObject
        }
    }

    @Test
    fun `the fixtures are there`() {
        assertTrue(files().size >= 9, "board fixture files: ${files().map { it.name }}")
        assertTrue(cases().size >= MIN_CASES, "board cases: ${cases().size}")
    }

    @TestFactory
    fun `every board case replays to the recorded state`(): List<DynamicTest> = cases().map { (name, case) ->
        DynamicTest.dynamicTest(name) { check(case) }
    }

    private fun check(case: JsonObject) {
        val log = json.decodeFromJsonElement(ListSerializer(LogEntry.serializer()), case.getValue("log"))
        val parent = BoardReplay.replay(seed, log, setOf(Scope.FAMILY, Scope.PARENTS))

        assertEquals(
            canonical(case.getValue("results")),
            canonical(json.encodeToJsonElement(ListSerializer(Outcome.serializer()), parent.outcomes)),
            "outcomes",
        )
        assertEquals(case["stoppedAt"]?.jsonPrimitive?.longOrNull, parent.stoppedAt, "stoppedAt")
        assertEquals(canonical(case.getValue("after")), canonical(BoardJson.toElement(parent.state)), "final state")

        val hashes = case.getValue("hashes").jsonObject
        assertEquals(hashes.getValue("state").jsonPrimitive.content, BoardHash.of(parent.state), "state hash")
        assertEquals(
            hashes.getValue("family").jsonPrimitive.content,
            BoardHash.of(parent.state, Scope.FAMILY),
            "family hash",
        )
        assertEquals(
            hashes.getValue("parents").jsonPrimitive.content,
            BoardHash.of(parent.state, Scope.PARENTS),
            "parents hash",
        )

        // A teen device holds only family and never receives parents entries (D11).
        val teen = BoardReplay.replay(seed, log, setOf(Scope.FAMILY))
        assertEquals(null, teen.state.scopes.parents)
        assertEquals(
            hashes.getValue("family").jsonPrimitive.content,
            BoardHash.of(teen.state, Scope.FAMILY),
            "teen family hash",
        )
        val familySeqs = log.filter { it.scope == Scope.FAMILY }.map { it.seq }.toSet()
        assertEquals(parent.outcomes.filter { it.seq in familySeqs }, teen.outcomes, "teen outcomes")
    }

    private fun canonical(element: JsonElement): String = CanonicalJson.encode(element)

    private companion object {
        const val DIR = "/fixtures/family"
        const val MIN_CASES = 25
    }
}
