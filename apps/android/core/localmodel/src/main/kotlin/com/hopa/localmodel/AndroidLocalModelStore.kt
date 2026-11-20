package com.hopa.localmodel

import android.content.Context
import android.net.Uri
import com.hopa.inference.local.FileModelStore
import com.hopa.inference.local.ImportOutcome
import com.hopa.inference.local.InstalledModel
import com.hopa.inference.local.ModelSpec
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The store over `filesDir/models`.
 *
 * Both paths end in the same place: [FileModelStore] writes `.part`, hashes
 * while streaming and renames only on a matching digest, so a half-downloaded
 * or substituted file never becomes a model. Import reads a document the
 * person picked; download reads the URL the catalogue pins, and reports bytes
 * as they arrive so the page can show progress.
 */
class AndroidLocalModelStore(context: Context) : LocalModelStore {
    private val context = context.applicationContext
    private val store = FileModelStore(File(context.filesDir, DIRECTORY))
    private val client = OkHttpClient()

    override suspend fun installed(): List<InstalledModel> =
        withContext(Dispatchers.IO) { store.installed() }

    override suspend fun totalBytes(): Long =
        withContext(Dispatchers.IO) { store.totalBytes() }

    override suspend fun import(uri: Uri, spec: ModelSpec): ImportOutcome =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportOutcome.Empty
            input.use { store.import(it, spec) }
        }

    override suspend fun download(
        spec: ModelSpec,
        onProgress: (Long, Long) -> Unit,
    ): ImportOutcome = withContext(Dispatchers.IO) {
        if (spec.url.isBlank()) return@withContext ImportOutcome.Empty
        val request = Request.Builder().url(spec.url).get().build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) return@use ImportOutcome.Empty
                val total = body.contentLength()
                body.byteStream().use { input ->
                    store.import(ProgressStream(input, total, onProgress), spec)
                }
            }
        }.getOrElse { ImportOutcome.Empty }
    }

    override suspend fun delete(id: String): Boolean =
        withContext(Dispatchers.IO) { store.delete(id) }

    override suspend fun cleanup(keep: Set<String>): Int =
        withContext(Dispatchers.IO) { store.cleanup(keep) }

    /** Counts bytes on the way past, so the page can show "43%". */
    private class ProgressStream(
        private val inner: InputStream,
        private val total: Long,
        private val onProgress: (Long, Long) -> Unit,
    ) : InputStream() {
        private var read: Long = 0

        override fun read(): Int = inner.read().also { if (it >= 0) report(1) }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            inner.read(buffer, offset, length).also { if (it > 0) report(it.toLong()) }

        override fun close() = inner.close()

        private fun report(count: Long) {
            read += count
            onProgress(read, total)
        }
    }

    private companion object {
        const val DIRECTORY = "models"
    }
}
