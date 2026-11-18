package com.hopa.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeCodecTest {
    @Test
    fun the_bundled_snapshot_round_trips_through_the_wire_format() {
        val bundle = KnowledgeBundle.from(BUNDLED_KNOWLEDGE)

        val decoded = KnowledgeCodec.decode(KnowledgeCodec.encode(bundle))

        assertEquals(BUNDLED_KNOWLEDGE.catalog, decoded.catalog)
        assertEquals(BUNDLED_KNOWLEDGE.sites, decoded.sites)
        assertEquals(BUNDLED_KNOWLEDGE.promptRules, decoded.promptRules)
        assertEquals(
            BUNDLED_KNOWLEDGE,
            decoded.toKnowledge(BUNDLED_KNOWLEDGE.approval),
        )
    }

    @Test
    fun the_generated_bundle_is_readable_by_the_app() {
        val text = checkNotNull(javaClass.getResource("/knowledge.v1.json")).readText()

        val bundle = KnowledgeCodec.decode(text)

        assertTrue(bundle.version.startsWith("sha256:"))
        assertTrue(bundle.catalog.any { it.id == "alipay" })
        assertTrue(bundle.sites.isNotEmpty())
        assertTrue(bundle.promptRules.isNotEmpty())
    }

    @Test
    fun an_unknown_schema_is_refused() {
        val bundle = KnowledgeBundle.from(BUNDLED_KNOWLEDGE)
        val text = KnowledgeCodec.encode(bundle).replace("\"schemaVersion\":1", "\"schemaVersion\":99")

        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeCodec.decode(text)
        }
    }

    @Test
    fun a_bundle_without_a_catalog_is_refused() {
        val text = KnowledgeCodec.encode(KnowledgeBundle.from(BUNDLED_KNOWLEDGE))
            .replaceFirst(Regex("\"catalog\":\\[.*?],\"sites\""), "\"catalog\":[],\"sites\"")

        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeCodec.decode(text)
        }
    }
}
