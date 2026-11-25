package com.hopa.family.board

import com.hopa.family.model.BoardState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The one JSON configuration for board states and ops. Strict on read (an unknown field is an
 * error, as in the schemas). On write, optional fields that are null are left out, so a state
 * has exactly the fields every other client writes and hashes the same.
 */
object BoardJson {
    val json: Json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = false
        explicitNulls = true
    }

    fun decodeState(text: String): BoardState = json.decodeFromString(BoardState.serializer(), text)

    fun encodeState(state: BoardState): String = json.encodeToString(BoardState.serializer(), state)

    fun toElement(state: BoardState): JsonElement = json.encodeToJsonElement(BoardState.serializer(), state)
}
