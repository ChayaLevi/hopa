package com.hopa.catalog.store
import com.hopa.catalog.data.ApprovalPolicyData

/** The generated bootstrap bundle, for tests that need the real catalog. */
internal object TestBundles {
    val bundled: CatalogSnapshot by lazy {
        val text = checkNotNull(TestBundles::class.java.getResourceAsStream("/knowledge.v1.json"))
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        CatalogCodec.decode(text).toCatalogSnapshot(ApprovalPolicyData)
    }
}
