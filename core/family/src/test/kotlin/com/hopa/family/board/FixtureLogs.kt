package com.hopa.family.board

import com.hopa.family.model.BoardState
import com.hopa.family.model.LogEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File

/** The seed and the logs of `spec/fixtures/family/board-*.json`, for tests that build their own logs. */
object FixtureLogs {
    private const val DIR = "/fixtures/family"
    private val dir = File(requireNotNull(FixtureLogs::class.java.getResource(DIR)) { "missing $DIR" }.toURI())

    val seed: BoardState by lazy { BoardJson.decodeState(File(dir, "seed.state.json").readText()) }

    /** Every case's log, by file name then case order, leaving out the cases that stop (D15). */
    val logs: List<List<LogEntry>> by lazy {
        dir.listFiles { f -> f.name.startsWith("board-") && f.name.endsWith(".json") }.orEmpty()
            .sortedBy { it.name }
            .flatMap { file ->
                BoardJson.json.parseToJsonElement(file.readText()).jsonObject.getValue("cases").jsonArray
                    .map { it.jsonObject }
                    .filter { "stoppedAt" !in it }
                    .map {
                        BoardJson.json.decodeFromJsonElement(
                            ListSerializer(LogEntry.serializer()),
                            it.getValue("log"),
                        )
                    }
            }
    }

    /**
     * One long log made of every case's entries, taken round-robin so the cases and the two scopes
     * interleave, each case keeping its own order, and renumbered from seq 1. Op ids are unique
     * across cases; item ids are not, so some entries meet another case's items and are rejected.
     * Which ones does not matter here: every engine must reach the same result.
     */
    val combined: List<LogEntry> by lazy {
        val out = mutableListOf<LogEntry>()
        val longest = logs.maxOf { it.size }
        for (i in 0 until longest) {
            for (log in logs) log.getOrNull(i)?.let { out += it }
        }
        renumber(out)
    }

    fun renumber(entries: List<LogEntry>): List<LogEntry> = entries.mapIndexed { i, e -> e.copy(seq = i + 1L) }
}
