package com.hopa.knowledge

/** The generated bootstrap bundle, for tests that need the real catalog. */
internal object TestBundles {
    val bundled: Knowledge by lazy {
        val text = checkNotNull(TestBundles::class.java.getResourceAsStream("/knowledge.v1.json"))
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        KnowledgeCodec.decode(text).toKnowledge(ApprovalPolicyData)
    }
}
