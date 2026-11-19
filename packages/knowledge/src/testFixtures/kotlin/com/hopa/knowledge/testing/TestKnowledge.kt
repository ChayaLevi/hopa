package com.hopa.knowledge.testing

import com.hopa.knowledge.ApprovalPolicyData
import com.hopa.knowledge.Knowledge
import com.hopa.knowledge.KnowledgeCodec

/** The reviewed bootstrap bundle, for tests that need the real catalog. */
object TestKnowledge {
    val value: Knowledge = run {
        val text = checkNotNull(TestKnowledge::class.java.getResourceAsStream("/knowledge.v1.json"))
            .bufferedReader()
            .use { it.readText() }
        KnowledgeCodec.decode(text).toKnowledge(ApprovalPolicyData)
    }
}
