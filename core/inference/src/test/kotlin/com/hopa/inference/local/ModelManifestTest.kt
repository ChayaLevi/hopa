package com.hopa.inference.local

import com.hopa.contracts.json.HopaJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModelManifestTest {
    private val json = HopaJson.element

    @Test
    fun a_manifest_survives_a_round_trip() {
        val model = LocalModelCatalogue.models.first()
        val manifest = ModelManifest(version = "sha256:abc", models = listOf(model))

        val text = json.encodeToString(ModelManifest.serializer(), manifest)
        val decoded = json.decodeFromString(ModelManifest.serializer(), text)

        assertEquals(manifest, decoded)
    }

    @Test
    fun the_catalogue_can_be_served_as_a_manifest() {
        val manifest = ModelManifest(
            version = LocalModelCatalogue.VERSION,
            models = LocalModelCatalogue.models,
        )

        val text = json.encodeToString(ModelManifest.serializer(), manifest)
        val decoded = json.decodeFromString(ModelManifest.serializer(), text)

        assertEquals(LocalModelCatalogue.models.size, decoded.models.size)
        assertEquals(LocalModelCatalogue.VERSION, decoded.version)
    }
}
