package com.hopa.localmodel

import android.content.Context
import android.net.Uri
import com.hopa.inference.local.FileModelStore
import com.hopa.inference.local.ImportOutcome
import com.hopa.inference.local.InstalledModel
import com.hopa.inference.local.ModelSpec
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The store over `filesDir/models`.
 *
 * Import reads the document the person picked, hashes it while copying and
 * returns the same [ImportOutcome] the JVM store produces, so the view model
 * has one set of states to render.
 */
class AndroidLocalModelStore(context: Context) : LocalModelStore {
    private val context = context.applicationContext
    private val store = FileModelStore(File(context.filesDir, DIRECTORY))

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

    override suspend fun delete(id: String): Boolean =
        withContext(Dispatchers.IO) { store.delete(id) }

    override suspend fun cleanup(keep: Set<String>): Int =
        withContext(Dispatchers.IO) { store.cleanup(keep) }

    private companion object {
        const val DIRECTORY = "models"
    }
}
