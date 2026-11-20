package com.hopa.inference.local

import kotlinx.serialization.Serializable

/**
 * What the gateway will serve at `GET /v1/models` once that endpoint exists.
 *
 * The shape mirrors the knowledge bundle: a schema version, a digest to compare
 * with `If-None-Match`, and the entries. Today the app reads the same structure
 * from [LocalModelCatalogue]; the codec and the hash check are the seam.
 */
@Serializable
data class ModelManifest(
    val schemaVersion: Int = 1,
    val version: String,
    val models: List<ModelSpec>,
)
