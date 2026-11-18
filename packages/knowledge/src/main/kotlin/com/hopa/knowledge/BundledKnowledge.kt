package com.hopa.knowledge

/** The mock: the tables that were generated into this build. */
object BundledKnowledge : KnowledgeSource {
    override fun current(): Knowledge = BUNDLED_KNOWLEDGE
}

val BUNDLED_KNOWLEDGE: Knowledge = Knowledge(
    version = "bundled",
    catalog = APP_CATALOG,
    sites = SITE_CATALOG,
    approval = ApprovalPolicyData,
    promptRules = PROMPT_RULES,
)
