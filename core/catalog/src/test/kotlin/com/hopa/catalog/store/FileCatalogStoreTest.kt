package com.hopa.catalog.store

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import com.hopa.catalog.data.ApprovalPolicyData
import com.hopa.catalog.data.CatalogApp
import com.hopa.catalog.data.CatalogTarget

class FileCatalogStoreTest {
    @TempDir
    lateinit var folder: File

    private val bundled = TestBundles.bundled

    private val empty = CatalogTarget(id = "", detail = "", verified = false)

    private fun tiny(version: String, display: String): CatalogSnapshot = CatalogSnapshot(
        version = version,
        catalog = listOf(
            CatalogApp(
                id = "tiny",
                display = display,
                aliases = emptyList(),
                purpose = "",
                actions = emptyList(),
                harmonyos = empty,
                android = CatalogTarget(id = "com.tiny", detail = "", verified = true),
                windows = empty,
            ),
        ),
        sites = emptyList(),
        approval = ApprovalPolicyData,
        promptRules = emptyList(),
    )

    private fun json(knowledge: CatalogSnapshot): String =
        CatalogCodec.encode(CatalogBundle.from(knowledge))

    private fun store(): FileCatalogStore = FileCatalogStore(folder, bundled)

    @Test
    fun an_empty_directory_uses_the_bundled_snapshot() {
        assertEquals(bundled, store().current())
    }

    @Test
    fun an_accepted_bundle_becomes_current() {
        val store = store()

        assertTrue(store.accept(json(tiny("tiny-1", "Tiny"))))

        assertEquals("tiny-1", store.current().version)
        assertEquals("Tiny", store.current().catalog.single().display)
        assertEquals(ApprovalPolicyData, store.current().approval)
    }

    @Test
    fun a_broken_answer_is_refused_and_changes_nothing() {
        val store = store()
        store.accept(json(tiny("tiny-1", "Tiny")))

        assertFalse(store.accept("not a bundle"))

        assertEquals("tiny-1", store.current().version)
    }

    @Test
    fun an_oversized_answer_is_refused() {
        assertFalse(store().accept("x".repeat(1_048_577)))
    }

    @Test
    fun a_corrupt_current_file_falls_back_to_the_previous_version() {
        val first = store()
        first.accept(json(tiny("tiny-1", "Tiny")))
        first.accept(json(tiny("tiny-2", "Tiny two")))

        File(folder, "knowledge.json").writeText("broken", Charsets.UTF_8)

        assertEquals("tiny-1", store().current().version)
    }

    @Test
    fun clearing_returns_to_the_bundled_snapshot() {
        val store = store()
        store.accept(json(tiny("tiny-1", "Tiny")))

        store.clear()

        assertEquals(bundled, store.current())
    }
}
