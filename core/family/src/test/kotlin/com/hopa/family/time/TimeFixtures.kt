package com.hopa.family.time

import com.hopa.family.board.BoardJson
import com.hopa.family.board.BoardReplay
import com.hopa.family.board.FixtureLogs
import com.hopa.family.model.BoardState
import com.hopa.family.model.LogEntry
import com.hopa.family.model.Scope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Reads `spec/fixtures/family/time-*.json` and replays their logs. */
object TimeFixtures {
    private val dir = File(requireNotNull(TimeFixtures::class.java.getResource("/fixtures/family")).toURI())

    fun file(name: String): JsonObject = BoardJson.json.parseToJsonElement(File(dir, name).readText()).jsonObject

    fun cases(name: String): List<JsonObject> = file(name).getValue("cases").jsonArray.map { it.jsonObject }

    fun log(case: JsonObject): List<LogEntry> =
        BoardJson.json.decodeFromJsonElement(ListSerializer(LogEntry.serializer()), case.getValue("log"))

    /** The seed, in the case's time zone when it names one. */
    fun start(case: JsonObject): BoardState {
        val zone = case["timezone"]?.jsonPrimitive?.content ?: return FixtureLogs.seed
        return FixtureLogs.seed.copy(family = FixtureLogs.seed.family.copy(timezone = zone))
    }

    /** The parent-device state after the entries of [log] whose op time is at or before [atMs]. */
    fun stateAt(start: BoardState, log: List<LogEntry>, atMs: Long): BoardState {
        val due = log.filter { FamilyClock.millis(it.op.getValue("at").jsonPrimitive.content) <= atMs }
        val result = BoardReplay.replay(start, due, setOf(Scope.FAMILY, Scope.PARENTS))
        check(result.outcomes.all { it.outcome == "applied" }) { "a fixture op was refused: ${result.outcomes}" }
        return result.state
    }

    fun string(element: JsonElement?, field: String): String = element!!.jsonObject.getValue(
        field,
    ).jsonPrimitive.content
}
