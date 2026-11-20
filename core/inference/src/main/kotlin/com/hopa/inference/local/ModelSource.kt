package com.hopa.inference.local

/**
 * Where the list of models comes from.
 *
 * The backend will implement this against `GET /v1/models`; until that endpoint
 * exists [LocalModelCatalogue] answers, so the picker and the storage code have
 * one shape to work with and no placeholder branch.
 */
interface ModelSource {
    suspend fun manifest(knownVersion: String): ModelManifest?
}
