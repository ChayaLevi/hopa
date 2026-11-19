package com.hopa.catalog.testing

import com.hopa.catalog.data.ApprovalPolicyData
import com.hopa.catalog.store.CatalogCodec
import com.hopa.catalog.store.CatalogSnapshot

/** The reviewed bootstrap bundle, for tests that need the real catalog. */
object TestCatalog {
    val value: CatalogSnapshot = run {
        val text = checkNotNull(TestCatalog::class.java.getResourceAsStream("/knowledge.v1.json"))
            .bufferedReader()
            .use { it.readText() }
        CatalogCodec.decode(text).toCatalogSnapshot(ApprovalPolicyData)
    }
}
