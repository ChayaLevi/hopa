package com.hopa.catalog.knowledge
import com.hopa.catalog.data.ApprovalPolicyData
import com.hopa.catalog.data.CatalogApp
import com.hopa.catalog.data.CatalogSite
import com.hopa.catalog.data.PromptRule

/**
 * The tables the engine reads, as one value.
 *
 * Today the client loads this from the bootstrap asset. When
 * the backend serves a newer catalog, a different [KnowledgeSource] will return
 * it instead and the engine will not change: it already reads the value it is
 * given, not the constants.
 *
 * The approval table stays with the bundled snapshot on purpose. A downloaded
 * catalog may say which app to open; it must not be able to make the gate less
 * careful.
 */
data class Knowledge(
    val version: String,
    val catalog: List<CatalogApp>,
    val sites: List<CatalogSite>,
    val approval: ApprovalPolicyData,
    val promptRules: List<PromptRule>,
)
