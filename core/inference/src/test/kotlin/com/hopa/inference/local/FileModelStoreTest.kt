package com.hopa.inference.local

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

class FileModelStoreTest {
    @TempDir
    lateinit var dir: File

    private val payload = "GGUF test model bytes".toByteArray()

    private fun spec(sha: String = sha256(payload)) = ModelSpec(
        id = "test-model",
        name = "Test model",
        modelClass = ModelClass.FAST,
        parameters = "0B",
        bytes = payload.size * 4L,
        minRamGb = 4,
        contextTokens = 8_000,
        speedClass = "fast",
        license = "Apache-2.0",
        sha256 = sha,
    )

    @Test
    fun an_import_lands_as_a_model_and_no_part_file_stays() {
        val store = FileModelStore(dir)

        val outcome = store.import(ByteArrayInputStream(payload), spec())

        assertTrue(outcome is ImportOutcome.Imported)
        assertEquals(1, store.installed().size)
        assertEquals("test-model", store.installed().first().id)
        assertEquals(payload.size.toLong(), store.totalBytes())
        assertTrue(store.partials().isEmpty())
    }

    @Test
    fun a_wrong_digest_leaves_nothing_behind() {
        val store = FileModelStore(dir)

        val outcome = store.import(ByteArrayInputStream(payload), spec(sha = "0".repeat(64)))

        assertTrue(outcome is ImportOutcome.HashMismatch)
        assertTrue(store.installed().isEmpty())
        assertTrue(store.partials().isEmpty())
    }

    @Test
    fun bytes_over_the_declared_limit_are_refused() {
        val store = FileModelStore(dir)

        val outcome = store.import(
            ByteArrayInputStream(payload),
            spec(),
            limitBytes = payload.size - 1L,
        )

        assertEquals(ImportOutcome.TooLarge, outcome)
        assertTrue(store.installed().isEmpty())
    }

    @Test
    fun an_empty_stream_is_not_a_model() {
        val store = FileModelStore(dir)

        val outcome = store.import(ByteArrayInputStream(ByteArray(0)), spec())

        assertEquals(ImportOutcome.Empty, outcome)
        assertTrue(store.partials().isEmpty())
    }

    @Test
    fun delete_frees_the_file_and_its_parts() {
        val store = FileModelStore(dir)
        store.import(ByteArrayInputStream(payload), spec())
        File(dir, "test-model.part").writeBytes(ByteArray(8))

        assertTrue(store.delete("test-model"))

        assertNull(store.modelFor("test-model"))
        assertTrue(store.partials().isEmpty())
        assertEquals(0L, store.totalBytes())
    }

    @Test
    fun cleanup_keeps_what_is_referenced_and_drops_what_is_not() {
        val store = FileModelStore(dir)
        store.import(ByteArrayInputStream(payload), spec())
        val orphan = File(dir, "orphan-1234.gguf").apply { writeBytes(ByteArray(16)) }
        val oldPart = File(dir, "gone.part").apply { writeBytes(ByteArray(4)) }
        oldPart.setLastModified(System.currentTimeMillis() - 48L * 60 * 60 * 1000)

        val removed = store.cleanup(keep = setOf("test-model"))

        assertEquals(2, removed)
        assertFalse(orphan.exists())
        assertFalse(oldPart.exists())
        assertNotNull(store.modelFor("test-model"))
    }

    @Test
    fun a_fresh_part_file_survives_cleanup() {
        val store = FileModelStore(dir)
        val fresh = File(dir, "downloading.part").apply { writeBytes(ByteArray(4)) }

        store.cleanup(keep = emptySet())

        assertTrue(fresh.exists())
    }

    @Test
    fun a_finished_download_is_adopted_without_a_copy() {
        val source = File(dir, "download.part").apply { writeBytes(payload) }
        val store = FileModelStore(File(dir, "models"))

        val outcome = store.adopt(source, spec())

        assertTrue(outcome is ImportOutcome.Imported)
        assertFalse(source.exists())
        assertEquals("test-model", store.installed().single().id)
        assertEquals(payload.size.toLong(), store.totalBytes())
    }

    @Test
    fun an_adopted_file_with_the_wrong_digest_is_removed() {
        val source = File(dir, "download.part").apply { writeBytes(payload) }
        val store = FileModelStore(File(dir, "models"))

        val outcome = store.adopt(source, spec(sha = "0".repeat(64)))

        assertTrue(outcome is ImportOutcome.HashMismatch)
        assertFalse(source.exists())
        assertTrue(store.installed().isEmpty())
    }

    @Test
    fun an_adopted_file_larger_than_declared_is_refused() {
        val source = File(dir, "download.part").apply { writeBytes(payload) }
        val store = FileModelStore(File(dir, "models"))

        val outcome = store.adopt(source, spec().copy(bytes = payload.size - 1L))

        assertEquals(ImportOutcome.TooLarge, outcome)
        assertFalse(source.exists())
        assertTrue(store.installed().isEmpty())
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
