package com.hopa.family.engine

import com.hopa.family.board.BoardJson
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
        const val CURRENT = 1

        /** None yet: v1 is the first stored shape. */
        val MIGRATIONS: Map<Int, (JsonObject) -> JsonObject> = emptyMap()
    }
}
