package com.hopa.bridge.chat

import com.hopa.bridge.session.BridgeClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

class BridgeChatTransport(
    private val client: BridgeClient,
) : ChatTransport {
    override suspend fun push(sessions: JsonArray): JsonObject = client.postChats(sessions)

    override suspend fun pull(): JsonObject = client.chats()
}
