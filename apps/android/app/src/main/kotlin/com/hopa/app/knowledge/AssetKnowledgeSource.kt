package com.hopa.app.knowledge

import android.content.Context
import com.hopa.catalog.data.ApprovalPolicyData
import com.hopa.catalog.knowledge.Knowledge
import com.hopa.catalog.knowledge.KnowledgeCodec
import com.hopa.catalog.knowledge.KnowledgeSource

/**
 * The bootstrap bundle packaged with the app.
 *
 * It is the same document the backend serves, so there is one format and one
 * reviewed catalog. If the asset is unreadable the app starts with an empty
 * catalog: the gate still works and the launchpad says it has nothing to show,
 * which is honest, rather than crashing on a missing file.
 */
class AssetKnowledgeSource(
    private val context: Context,
) : KnowledgeSource {
    private val bundled: Knowledge by lazy { load() }

    override fun current(): Knowledge = bundled

    private fun load(): Knowledge = runCatching {
        val text = context.assets.open(FILE)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        KnowledgeCodec.decode(text).toKnowledge(ApprovalPolicyData)
    }.getOrElse {
        Knowledge(
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
