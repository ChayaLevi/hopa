package com.hopa.family.board

import com.hopa.contracts.json.CanonicalJson
import com.hopa.family.model.BoardState
import com.hopa.family.model.Partition
import com.hopa.family.model.Scope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import java.security.MessageDigest

/**
 * The state hash every client computes the same way (`spec/fixtures/family/README.md`): lowercase
 * hex SHA-256 of the UTF-8 canonical JSON (sorted keys, no whitespace, JSON.stringify escapes).
 * Two devices with the same hash hold the same state.
 */
object BoardHash {
    fun of(state: BoardState): String = of(BoardJson.toElement(state))

    /** The hash of one scope's partition; `null` (absent) hashes as JSON null. */
    fun of(state: BoardState, scope: Scope): String = of(partitionElement(state.partition(scope)))

    fun of(element: JsonElement): String {
        val bytes = MessageDigest.getInstance(
            "SHA-256",
        ).digest(CanonicalJson.encode(element).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun partitionElement(partition: Partition?): JsonElement =
        partition?.let { BoardJson.json.encodeToJsonElement(Partition.serializer(), it) } ?: JsonNull
}
