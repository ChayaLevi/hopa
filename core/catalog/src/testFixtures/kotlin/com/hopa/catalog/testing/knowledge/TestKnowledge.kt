package com.hopa.catalog.testing.knowledge

import com.hopa.catalog.data.ApprovalPolicyData
import com.hopa.catalog.knowledge.Knowledge
import com.hopa.catalog.knowledge.KnowledgeCodec

/** The reviewed bootstrap bundle, for tests that need the real catalog. */
object TestKnowledge {
    val value: Knowledge = run {
        val text = checkNotNull(TestKnowledge::class.java.getResourceAsStream("/knowledge.v1.json"))
            .bufferedReader()
            .use { it.readText() }
        KnowledgeCodec.decode(text).toKnowledge(ApprovalPolicyData)
    }
}
