package com.hopa.decision

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads the versioned comparison corpus from `spec/decisions/`.
 *
 * The corpus is data, not code: adding a scenario is one JSON object, and the
 * same file is read by a unit test in CI and by the report task on a laptop.
 */
object DecisionCorpus {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun read(text: String): List<DecisionCase> {
        val file = json.decodeFromString<CorpusFile>(text)
        require(file.schemaVersion == SUPPORTED) {
            "unsupported decision corpus schema: ${file.schemaVersion}"
        }
        return file.cases.map { case ->
            require(case.allowed.isNotEmpty()) { "case ${case.id} allows no verb" }
            val expected = verb(case.id, case.expected)
            val allowed = case.allowed.map { verb(case.id, it) }
            require(expected in allowed) {
                "case ${case.id} expects ${expected.name.lowercase()} outside its allowed set"
            }
            DecisionCase(
                id = case.id,
                action = case.action,
                screen = case.screen,
                history = case.history,
                approvals = case.approvals,
                attentionLeft = case.attentionLeft,
                cancelled = case.cancelled,
                atStepLimit = case.atStepLimit,
                expected = expected,
                allowed = allowed,
                model = case.model?.let { verb(case.id, it) },
                notes = case.notes,
            )
        }
    }

    fun readFrom(path: Path): List<DecisionCase> = read(Files.readString(path))

    private fun verb(caseId: String, code: String): Verb =
        Verb.entries.firstOrNull { it.name.equals(code.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException("case $caseId names an unknown verb: $code")

    const val SUPPORTED: String = "1"
}
