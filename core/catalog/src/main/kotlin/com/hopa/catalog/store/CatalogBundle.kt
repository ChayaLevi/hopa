package com.hopa.catalog.store

import kotlinx.serialization.Serializable
import com.hopa.catalog.data.ApprovalPolicyData
import com.hopa.catalog.data.CatalogApp
import com.hopa.catalog.data.CatalogSite
import com.hopa.catalog.data.PromptRule

/**
 * The downloadable shape of knowledge: one document the backend can serve and
 * the app can store.
 *
 * Approval is not part of it on purpose. The bundle may change which app opens;
 * the gate's floor stays with the app.
 */
@Serializable
data class CatalogBundle(
    val schemaVersion: Int = SCHEMA_VERSION,
    val version: String,
    val catalog: List<CatalogApp>,
    val sites: List<CatalogSite>,
    val promptRules: List<PromptRule>,
) {
    fun toCatalogSnapshot(approval: ApprovalPolicyData): CatalogSnapshot = CatalogSnapshot(
        version = version,
        catalog = catalog,
        sites = sites,
        approval = approval,
        promptRules = promptRules,
    )

    companion object {
        const val SCHEMA_VERSION: Int = 1

        fun from(knowledge: CatalogSnapshot): CatalogBundle = CatalogBundle(
            version = knowledge.version,
            catalog = knowledge.catalog,
            sites = knowledge.sites,
            promptRules = knowledge.promptRules,
        )
    }
}
