package com.hopa.sidecar

import com.hopa.knowledge.ApprovalPolicyData
import com.hopa.knowledge.Knowledge
import com.hopa.knowledge.KnowledgeCodec

/** The bootstrap bundle packaged with the sidecar; the same document as the app. */
object SidecarKnowledge {
    val value: Knowledge by lazy {
        val text = checkNotNull(SidecarKnowledge::class.java.getResourceAsStream("/knowledge.v1.json")) {
            "knowledge.v1.json is missing from the sidecar resources"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        KnowledgeCodec.decode(text).toKnowledge(ApprovalPolicyData)
    }
}
