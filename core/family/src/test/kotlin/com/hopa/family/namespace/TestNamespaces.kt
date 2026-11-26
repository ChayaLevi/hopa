package com.hopa.family.namespace

import com.hopa.family.board.RejectCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Two small capabilities for tests: `notes` (independent) and `census` (reads the board). */
object TestNamespaces {
    /** `add {id, text}` and `remove {id}`; reads nothing. Version [version]. */
    class Notes(override val version: Int = 1) : Namespace {
        override val name = "notes"
        override val reads = emptySet<String>()

        override fun knows(kind: String, nsv: Int) = kind in setOf("add", "remove") && nsv <= version

        override fun apply(context: NamespaceContext, op: JsonObject): NamespaceStep {
            val notes = context.own?.get("notes")?.jsonArray.orEmpty()
            return when (op["kind"]?.jsonPrimitive?.content) {
                "add" -> {
                    val id = op["id"]?.jsonPrimitive?.content ?: return NamespaceStep.Rejected(RejectCode.INVALID)
                    if (notes.any { it.jsonObject["id"]?.jsonPrimitive?.content == id }) {
                        return NamespaceStep.Rejected(RejectCode.CONFLICT)
                    }
                    val note = buildJsonObject {
                        put("id", id)
                        put("text", op["text"]?.jsonPrimitive?.content.orEmpty())
                        put("by", context.actorId)
                    }
                    NamespaceStep.Applied(JsonObject(mapOf("notes" to JsonArray(notes + note))))
                }
                "remove" -> {
                    val id = op["id"]?.jsonPrimitive?.content
                    val left = notes.filter { it.jsonObject["id"]?.jsonPrimitive?.content != id }
                    if (left.size == notes.size) return NamespaceStep.Rejected(RejectCode.NOT_FOUND)
                    NamespaceStep.Applied(JsonObject(mapOf("notes" to JsonArray(left))))
                }
                else -> NamespaceStep.Stopped
            }
        }
    }

    /** `count {}`: records how many board items there are at that seq; reads the board. */
    class Census : Namespace {
        override val name = "census"
        override val version = 1
        override val reads = setOf(Namespaces.BOARD)

        override fun knows(kind: String, nsv: Int) = kind == "count" && nsv == 1

        override fun apply(context: NamespaceContext, op: JsonObject): NamespaceStep {
            if (op["kind"]?.jsonPrimitive?.content != "count") return NamespaceStep.Stopped
            val counts = context.own?.get("counts")?.jsonArray.orEmpty()
            val now = context.board?.items?.size ?: return NamespaceStep.Rejected(RejectCode.INVALID)
            return NamespaceStep.Applied(JsonObject(mapOf("counts" to JsonArray(counts + JsonPrimitive(now)))))
        }
    }

    fun op(ns: String, kind: String, opId: String, nsv: Int = 1, vararg fields: Pair<String, String>) =
        buildJsonObject {
            put("v", 2)
            put("ns", ns)
            put("nsv", nsv)
            put("kind", kind)
            put("opId", opId)
            put("at", "2026-11-11T12:40:00.000Z")
            for ((key, value) in fields) put(key, value)
        }
}
