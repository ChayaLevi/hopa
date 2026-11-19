package com.hopa.catalog.knowledge

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The downloaded snapshot, kept next to the bundled one.
 *
 * The app reads the newest valid file; a missing, oversized, corrupt or
 * unsupported bundle falls back to the previous file and then to the bundled
 * snapshot, so a bad update can never leave the app without a catalog.
 *
 * Writes are a temp file, a sync, then a rename; the old file is copied to
 * `.prev` first. A crash between the two leaves the previous version readable.
 */
class FileKnowledgeStore(
    private val directory: File,
    private val fallback: Knowledge,
) : KnowledgeSource {
    private val currentFile = File(directory, FILE)
    private val previousFile = File(directory, PREVIOUS)
    private val tempFile = File(directory, TEMP)

    private var cached: Knowledge? = null

    @Synchronized
    override fun current(): Knowledge {
        cached?.let { return it }
        val loaded = read(currentFile) ?: read(previousFile) ?: fallback
        cached = loaded
        return loaded
    }

    /**
     * Takes the text the backend returned. False means "keep what you have":
     * the text is too large, does not parse, is a schema this app cannot read,
     * or the write failed.
     */
    @Synchronized
    fun accept(text: String): Boolean {
        if (text.length > MAX_CHARS) {
            return false
        }
        val bundle = runCatching { KnowledgeCodec.decode(text) }.getOrNull() ?: return false
        if (bundle.version.isBlank() || bundle.version == current().version) {
            return bundle.version.isNotBlank()
        }
        return runCatching { write(text) }.isSuccess
    }

    @Synchronized
    fun clear() {
        cached = null
        runCatching { currentFile.delete() }
        runCatching { previousFile.delete() }
        runCatching { tempFile.delete() }
    }

    private fun read(file: File): Knowledge? {
        if (!file.isFile || file.length() > MAX_CHARS) {
            return null
        }
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return runCatching { KnowledgeCodec.decode(text).toKnowledge(fallback.approval) }.getOrNull()
    }

    private fun write(text: String) {
        directory.mkdirs()
        FileOutputStream(tempFile).use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.flush()
            stream.fd.sync()
        }
        if (currentFile.isFile) {
            Files.copy(
                currentFile.toPath(),
                previousFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        try {
            Files.move(
                tempFile.toPath(),
                currentFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: Exception) {
            // Some file systems refuse an atomic replace across handles; a
            // plain replace still leaves the previous copy in place.
            Files.move(
                tempFile.toPath(),
                currentFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        cached = read(currentFile) ?: fallback
    }

    private companion object {
        const val FILE = "knowledge.json"
        const val PREVIOUS = "knowledge.prev.json"
        const val TEMP = "knowledge.tmp"
        /** The catalog is ~60 KB; a megabyte is already a broken answer. */
        const val MAX_CHARS = 1_048_576
    }
}
