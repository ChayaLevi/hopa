package com.hopa.bridge.chat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** The conversation half of the bridge: one merged list for every device. */
interface ChatTransport {
    suspend fun push(sessions: JsonArray): JsonObject

    suspend fun pull(): JsonObject
}
