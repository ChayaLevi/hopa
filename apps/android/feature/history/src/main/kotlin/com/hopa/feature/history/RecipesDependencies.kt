package com.hopa.feature.history

import com.hopa.agent.recipe.RecipeStore
import com.hopa.capabilities.system.AndroidPackageCatalog
import com.hopa.catalog.store.CatalogSnapshot

/** What the repeats page reads: saved paths and the catalogue to name them. */
interface RecipesDependencies {
    val recipes: RecipeStore
    val packages: AndroidPackageCatalog
    val knowledge: CatalogSnapshot
}
