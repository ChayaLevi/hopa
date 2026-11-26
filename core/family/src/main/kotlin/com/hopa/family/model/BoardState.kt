package com.hopa.family.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class Scopes(
    val family: Partition,
    val parents: Partition? = null,
)

/** One scope's items, in the order the reducer added them, and one activity per applied op. */
@Serializable
data class Partition(
    val items: List<Item>,
    val activity: List<Activity>,
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
