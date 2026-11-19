package com.hopa.contracts

import com.hopa.codec.CanonicalJson
import com.hopa.contracts.wire.Observation
import com.hopa.contracts.wire.ObservationNode
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest

object ObservationHasher {
    /**
     * The producer's hash when it is there, and a fresh one when it is not.
     * `ObservationProducer` seals every observation it returns, so a record
     * reuses that digest instead of walking the tree again; a hand-built
     * observation (tests, an empty fallback) has no hash yet, so it gets one.
     */
    fun hashOf(observation: Observation): String = observation.hash.ifBlank { hash(observation) }

    fun hash(observation: Observation): String {
        val payload = buildJsonObject {
            put("displayId", observation.displayId)
            put("windowId", observation.windowId)
            put("rotation", observation.rotation)
            put("width", observation.screenSize.width)
            put("height", observation.screenSize.height)
            put("focus", observation.focus)
            put("text", observation.text)
            put("tree", toJson(observation.tree))
        }
        return "sha256:" + sha256(CanonicalJson.encode(payload))
    }

    private fun toJson(value: ObservationNode): JsonObject = buildJsonObject {
        put("ref", value.ref)
        put("type", value.type)
        put("text", value.text)
        put("description", value.description)
        put("id", value.id)
        put(
            "bounds",
            value.bounds?.let { bounds ->
                buildJsonArray { bounds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            } ?: JsonNull,
        )
        put("clickable", value.clickable)
        put("enabled", value.enabled)
        put("checked", value.checked)
        put("scrollable", value.scrollable)
        put("visible", value.visible)
        put("focused", value.focused)
        put("children", buildJsonArray { value.children.forEach { add(toJson(it)) } })
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
