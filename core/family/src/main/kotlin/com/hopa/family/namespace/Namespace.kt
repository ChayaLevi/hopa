package com.hopa.family.namespace

import kotlinx.serialization.json.JsonObject

/**
 * One capability on the board (shopping, preferences, rules, ...): its own ops (`v: 2` with
 * `ns` = [name]), its own state in each scope, and its own reducer. A namespace writes only its own
 * state; it may read the board and the namespaces it names in [reads], in the same scope. That
 * isolation is what lets a device that does not know a namespace skip it safely (amended D15).
 */
interface Namespace {
    /** `^[a-z][a-z0-9_]{0,31}$`, never `board` (the board's ops are v1). */
    val name: String

    /** The highest `nsv` this build reads. An op with a higher one stops this namespace. */
    val version: Int

    /** Other namespaces this one reads (`board` for the board's items). Fixed per [version]. */
    val reads: Set<String>

    /** Whether this build reads ops of [kind] at version [nsv] (so a kept entry is readable now). */
    fun knows(kind: String, nsv: Int): Boolean

    /**
     * Applies one op to this namespace's state in one scope. Pure: no clock, no ids minted, the
     * actor from [context] (server-attested, D12). An unknown `kind` is [NamespaceStep.Stopped].
     */
    fun apply(context: NamespaceContext, op: JsonObject): NamespaceStep
}
