package com.hopa.inference.local

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * The model files on this phone.
 *
 * Everything is written to `<id>.part` first, hashed while it streams, and
 * renamed only when the digest matches, so a half-downloaded or substituted
 * file never becomes a model. The previous file for an id is left alone until
 * the new one is verified; [delete] and [cleanup] are the only things that free
 * space, and both are explicit.
 */
class FileModelStore(private val root: File) {
    fun installed(): List<InstalledModel> = root.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.endsWith(".gguf") }
        .mapNotNull { file ->
            val id = file.name.substringBeforeLast('-', missingDelimiterValue = "")
            if (id.isBlank()) null else InstalledModel(id, file, file.length())
        }
        .sortedBy { it.id }

    fun modelFor(id: String): InstalledModel? = installed().firstOrNull { it.id == id }

    fun totalBytes(): Long = installed().sumOf { it.bytes }

    fun partials(): List<File> = root.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".part") }

    fun import(source: InputStream, spec: ModelSpec, limitBytes: Long = spec.bytes): ImportOutcome {
        root.mkdirs()
        val part = File(root, "${spec.id}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        try {
            source.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (limitBytes > 0 && written > limitBytes) {
                            part.delete()
                            return ImportOutcome.TooLarge
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (error: Exception) {
            part.delete()
            throw error
        }
        if (written == 0L) {
            part.delete()
            return ImportOutcome.Empty
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (spec.sha256.isNotBlank() && !actual.equals(spec.sha256, ignoreCase = true)) {
            part.delete()
            return ImportOutcome.HashMismatch(expected = spec.sha256, actual = actual)
        }
        val target = File(root, "${spec.id}-${actual.take(8)}.gguf")
        val previous = modelFor(spec.id)?.file
        if (!part.renameTo(target)) {
            part.delete()
            throw IllegalStateException("Could not move the model file into place")
        }
        if (previous != null && previous.name != target.name) previous.delete()
        return ImportOutcome.Imported(InstalledModel(spec.id, target, target.length()))
    }

    fun delete(id: String): Boolean {
        var removed = false
        installed().filter { it.id == id }.forEach { removed = it.file.delete() || removed }
        partials().filter { it.name.startsWith("$id") }.forEach { it.delete() }
        return removed
    }

    /**
     * Drops leftovers nobody references: stale `.part` files and model files
     * whose id is not in [keep]. Returns how many files went away.
     */
    fun cleanup(keep: Set<String>, nowMillis: Long = System.currentTimeMillis()): Int {
        var removed = 0
        val staleBefore = nowMillis - STALE_PART_MILLIS
        partials().forEach { part ->
            if (part.lastModified() < staleBefore && part.delete()) removed++
        }
        installed().forEach { model ->
            if (model.id !in keep && model.file.delete()) removed++
        }
        return removed
    }

    private companion object {
        const val DEFAULT_BUFFER = 64 * 1024
        const val STALE_PART_MILLIS = 24L * 60 * 60 * 1000
    }
}
