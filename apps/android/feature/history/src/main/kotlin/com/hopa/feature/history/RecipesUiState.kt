package com.hopa.feature.history

import com.hopa.agent.recipe.Recipe

/** Saved repetitions and the display names of the apps they open. */
data class RecipesUiState(
    val recipes: List<Recipe> = emptyList(),
    val names: Map<String, String> = emptyMap(),
)
