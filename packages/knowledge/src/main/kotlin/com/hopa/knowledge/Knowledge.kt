package com.hopa.knowledge

/**
 * The tables the engine reads, as one value.
 *
 * Today [BundledKnowledge] returns this snapshot, compiled into the app. When
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
