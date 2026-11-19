package com.hopa.catalog.store

import kotlinx.serialization.json.Json

/**
 * The bundle's wire format: what a backend writes and what the app stores.
 *
 * Unknown fields are ignored so an older app can read a newer bundle that only
 * added fields; the schema version is the hard gate. An empty catalog is
 * rejected: a bundle that would leave the app with nothing is a broken update,
 * not an update.
 */
object CatalogCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(bundle: CatalogBundle): String =
        json.encodeToString(CatalogBundle.serializer(), bundle)

    fun decode(text: String): CatalogBundle {
        val bundle = json.decodeFromString(CatalogBundle.serializer(), text)
        require(bundle.schemaVersion == CatalogBundle.SCHEMA_VERSION) {
            "knowledge schema ${bundle.schemaVersion} is not supported"
        }
        require(bundle.catalog.isNotEmpty()) { "knowledge bundle has no catalog" }
        return bundle
    }
}
