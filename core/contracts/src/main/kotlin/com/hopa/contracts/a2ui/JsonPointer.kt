package com.hopa.contracts.a2ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * RFC 6901 pointers over an A2UI data model. A2UI writes the whole model as
 * `/`, so both `""` and `"/"` mean the root.
 */
internal object JsonPointer {
    fun tokens(pointer: String): List<String> {
        if (pointer.isEmpty() || pointer == "/") return emptyList()
        require(pointer.startsWith("/")) { "not a JSON Pointer: $pointer" }
        return pointer.substring(1).split("/").map { it.replace("~1", "/").replace("~0", "~") }
    }

    fun get(root: JsonElement, pointer: String): JsonElement? =
        tokens(pointer).fold(root as JsonElement?) { node, token ->
            when (node) {
                is JsonObject -> node[token]
                is JsonArray -> token.toIntOrNull()?.takeIf { token == it.toString() }?.let { node.getOrNull(it) }
                else -> null
            }
        }

    /**
     * A copy of [root] with [value] at [pointer], creating objects on the way.
     * [JsonNull] removes the key. Writing inside an array is not supported:
     * HOPA's cards replace a whole list instead.
     */
    fun set(root: JsonObject, pointer: String, value: JsonElement): JsonObject? {
        val tokens = tokens(pointer)
        if (tokens.isEmpty()) return value as? JsonObject ?: if (value == JsonNull) JsonObject(emptyMap()) else null
        return write(root, tokens, value)
    }

    private fun write(node: JsonObject, tokens: List<String>, value: JsonElement): JsonObject? {
        val head = tokens.first()
        val next = LinkedHashMap(node)
        if (tokens.size == 1) {
            if (value == JsonNull) next.remove(head) else next[head] = value
            return JsonObject(next)
        }
        val child = when (val existing = node[head]) {
            null -> JsonObject(emptyMap())
            is JsonObject -> existing
            else -> return null
        }
        next[head] = write(child, tokens.drop(1), value) ?: return null
        return JsonObject(next)
    }
}
