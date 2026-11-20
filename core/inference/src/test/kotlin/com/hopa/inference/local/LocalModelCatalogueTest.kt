package com.hopa.inference.local

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalModelCatalogueTest {
    private val models = LocalModelCatalogue.models

    @Test
    fun every_entry_is_unique() {
        val ids = models.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun every_entry_explains_itself_before_a_download() {
        models.forEach { model ->
            assertTrue(model.strengths.isNotEmpty(), "${model.id} has no strengths")
            assertTrue(model.weaknesses.isNotEmpty(), "${model.id} has no weaknesses")
            assertTrue(model.notes.isNotEmpty(), "${model.id} has no notes")
            assertTrue(model.license.isNotBlank(), "${model.id} has no licence")
            assertTrue(model.bytes > 0, "${model.id} has no size")
            assertTrue(model.minRamGb > 0, "${model.id} has no RAM floor")
            assertTrue(model.contextTokens >= 4_000, "${model.id} has an unusable context")
        }
    }

    @Test
    fun reasoning_models_are_never_the_offer() {
        assertFalse(models.any { it.modelClass == ModelClass.REASONING && it.preferred })
    }

    @Test
    fun a_downloadable_entry_carries_a_full_hash_and_an_https_url() {
        models.filter { it.downloadable }.forEach { model ->
            assertEquals(64, model.sha256.length, "${model.id} needs a sha256")
            assertTrue(model.url.startsWith("https://"), "${model.id} must use https")
        }
    }

    @Test
    fun every_class_on_offer_has_at_least_one_candidate() {
        ModelClass.entries
            .filter { it != ModelClass.REASONING }
            .forEach { modelClass ->
                assertTrue(
                    models.any { it.modelClass == modelClass },
                    "no candidate in $modelClass",
                )
            }
    }
}
