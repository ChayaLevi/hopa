package com.hopa.catalog.knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnowledgeCodecTest {
    private val bundled = TestBundles.bundled

    @Test
    fun the_bundled_snapshot_round_trips_through_the_wire_format() {
        val bundle = KnowledgeBundle.from(bundled)

        val decoded = KnowledgeCodec.decode(KnowledgeCodec.encode(bundle))

        assertEquals(bundled.catalog, decoded.catalog)
        assertEquals(bundled.sites, decoded.sites)
        assertEquals(bundled.promptRules, decoded.promptRules)
        assertEquals(
            bundled,
            decoded.toKnowledge(bundled.approval),
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
        val bundle = KnowledgeBundle.from(bundled)
        val text = KnowledgeCodec.encode(bundle).replace("\"schemaVersion\":1", "\"schemaVersion\":99")

        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeCodec.decode(text)
        }
    }

    @Test
    fun a_bundle_without_a_catalog_is_refused() {
        val text = KnowledgeCodec.encode(KnowledgeBundle.from(bundled))
            .replaceFirst(Regex("\"catalog\":\\[.*?],\"sites\""), "\"catalog\":[],\"sites\"")

        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeCodec.decode(text)
        }
    }
}
