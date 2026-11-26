package com.hopa.family.engine

import com.hopa.family.board.BoardJson
import com.hopa.family.model.BoardState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads and writes [EngineSnapshot] as JSON, with migrations. A stored state carries its
 * `engineVersion`; an older one is migrated step by step to [CURRENT] before it is decoded, a newer
 * one is refused ([NewerEngineVersion]) so an older app never overwrites what it cannot read.
 * A new version adds one entry to [migrations] (from `n` to `n + 1`) and a test for it.
 */
class EngineStore(
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = MIGRATIONS,
    private val current: Int = CURRENT,
) {
    fun encode(snapshot: EngineSnapshot): String {
        require(snapshot.engineVersion == current) { "write the current version only" }
        return BoardJson.json.encodeToString(EngineSnapshot.serializer(), snapshot)
    }

    fun decode(text: String): EngineSnapshot {
        var json = BoardJson.json.parseToJsonElement(text).jsonObject
        var version = json["engineVersion"]?.jsonPrimitive?.intOrNull ?: error("no engineVersion")
        if (version > current) throw NewerEngineVersion(version)
        while (version < current) {
            val step = migrations[version] ?: error("no migration from engine state v$version")
            json = JsonObject(step(json) + ("engineVersion" to JsonPrimitive(version + 1)))
            version += 1
        }
        return BoardJson.json.decodeFromJsonElement(EngineSnapshot.serializer(), json)
    }

    companion object {
        const val CURRENT = 2

        val MIGRATIONS: Map<Int, (JsonObject) -> JsonObject> = mapOf(1 to ::fromV1)

        /**
         * v1 stopped everything at the first op it could not read and never moved its cursor past
         * it. v2 stops per capability. Dropping the v1 stop and its held entries is enough: the
         * cursor is still before them, so the next pull brings them again and v2 sorts them out.
         * The board state itself is unchanged; it is marked state schema 2.
         */
        private fun fromV1(v1: JsonObject): JsonObject {
            val confirmed = v1.getValue("confirmed").jsonObject
            val marked = JsonObject(confirmed + ("schemaVersion" to JsonPrimitive(BoardState.SCHEMA_VERSION)))
            return JsonObject(v1 - "stoppedAt" - "held" + ("confirmed" to marked))
        }
    }
}
