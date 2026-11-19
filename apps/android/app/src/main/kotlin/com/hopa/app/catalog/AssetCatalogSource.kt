package com.hopa.app.catalog

import android.content.Context
import com.hopa.catalog.data.ApprovalPolicyData
import com.hopa.catalog.store.CatalogSnapshot
import com.hopa.catalog.store.CatalogCodec
import com.hopa.catalog.store.CatalogSource

/**
 * The bootstrap bundle packaged with the app.
 *
 * It is the same document the backend serves, so there is one format and one
 * reviewed catalog. If the asset is unreadable the app starts with an empty
 * catalog: the gate still works and the launchpad says it has nothing to show,
 * which is honest, rather than crashing on a missing file.
 */
class AssetCatalogSource(
    private val context: Context,
) : CatalogSource {
    private val bundled: CatalogSnapshot by lazy { load() }

    override fun current(): CatalogSnapshot = bundled

    private fun load(): CatalogSnapshot = runCatching {
        val text = context.assets.open(FILE)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        CatalogCodec.decode(text).toCatalogSnapshot(ApprovalPolicyData)
    }.getOrElse {
        CatalogSnapshot(
            version = "empty",
            catalog = emptyList(),
            sites = emptyList(),
            approval = ApprovalPolicyData,
            promptRules = emptyList(),
        )
    }

    private companion object {
        const val FILE = "knowledge.v1.json"
    }
}
