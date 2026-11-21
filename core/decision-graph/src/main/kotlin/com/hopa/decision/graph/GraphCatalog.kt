package com.hopa.decision.graph

import kotlinx.serialization.json.Json

/**
 * Reads `graphs.v1.json` from the module resources.
 *
 * A missing or broken document is an empty list, not a crash: the app then uses
 * the model exactly as before. That is the only acceptable failure mode for a
 * hand-written optimisation.
 */
object GraphCatalog {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun load(): List<AppGraph> {
        val text = runCatching {
            GraphCatalog::class.java.getResourceAsStream(RESOURCE)?.use { stream ->
                stream.readBytes().decodeToString()
            }
        }.getOrNull() ?: return emptyList()
        return runCatching {
            val document = json.decodeFromString<GraphDocument>(text)
            if (document.schemaVersion == SUPPORTED) document.graphs else emptyList()
        }.getOrDefault(emptyList())
    }

    private const val RESOURCE = "/graphs.v1.json"
    private const val SUPPORTED = 1
}
