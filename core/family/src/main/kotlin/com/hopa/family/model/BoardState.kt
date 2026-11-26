package com.hopa.family.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The board one device holds after replaying the op log (`spec/schemas/family/state.v1.schema.json`).
 * Stored on the device only. A device without the parents key holds `scopes.family` only, and
 * that part must be identical on every device (D11).
 *
 * Optional fields default to null and are left out when written, so a state written here is
 * byte-for-byte the JSON every other client writes, and hashes the same.
 */
@Serializable
data class BoardState(
    val schemaVersion: Int,
    val family: Family,
    val scopes: Scopes,
) {
    fun partition(scope: Scope): Partition? = when (scope) {
        Scope.FAMILY -> scopes.family
        Scope.PARENTS -> scopes.parents
    }

    fun withPartition(scope: Scope, partition: Partition): BoardState = when (scope) {
        Scope.FAMILY -> copy(scopes = scopes.copy(family = partition))
        Scope.PARENTS -> copy(scopes = scopes.copy(parents = partition))
    }

    /** What a device that holds only [held] keeps of this state. */
    fun viewOf(held: Set<Scope>): BoardState =
        copy(scopes = Scopes(family = scopes.family, parents = scopes.parents.takeIf { Scope.PARENTS in held }))

    companion object {
        /** 2: a partition may hold other capabilities' state (`ns`). v1 states read as they are. */
        const val SCHEMA_VERSION = 2
    }
}

@Serializable
data class Scopes(
    val family: Partition,
    val parents: Partition? = null,
)

/**
 * One scope's board: its items, in the order the reducer added them, and one activity per applied
 * op. [ns] holds the state of every other capability in this scope (shopping, rules, ...), by
 * namespace name, each in the shape its namespace defines; absent until a capability's first op,
 * so a board-only state is byte for byte what v1 wrote.
 */
@Serializable
data class Partition(
    val items: List<Item>,
    val activity: List<Activity>,
    val ns: Map<String, JsonObject>? = null,
)

@Serializable
enum class Scope {
    @SerialName("family")
    FAMILY,

    @SerialName("parents")
    PARENTS,
}

@Serializable
data class Family(
    val id: String,
    val name: String,
    val nameZh: String? = null,
    val timezone: String,
    val members: List<Member>,
)

@Serializable
data class Member(
    val id: String,
    val name: String,
    val nameZh: String? = null,
    val role: FamilyRole,
    val hasAccount: Boolean,
)

@Serializable
enum class FamilyRole {
    @SerialName("parent")
    PARENT,

    @SerialName("teen")
    TEEN,

    @SerialName("child")
    CHILD,
}
