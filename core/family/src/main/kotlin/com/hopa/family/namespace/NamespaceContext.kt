package com.hopa.family.namespace

import com.hopa.family.model.Family
import com.hopa.family.model.Partition
import com.hopa.family.model.Scope
import kotlinx.serialization.json.JsonObject

/**
 * What a namespace sees for one op: the roster, the scope, the actor, its own state (null before
 * its first op in this scope), the board of this scope when it declared `board` among its reads,
 * and the state of the other namespaces it declared. Nothing of the other scope (D11).
 */
data class NamespaceContext(
    val family: Family,
    val scope: Scope,
    val actorId: String,
    val nsv: Int,
    val own: JsonObject?,
    val board: Partition?,
    val reads: Map<String, JsonObject>,
)
