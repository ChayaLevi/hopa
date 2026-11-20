package com.hopa.app.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hopa.agent.recipe.Recipe
import com.hopa.app.di.AppContainer
import com.hopa.catalog.routing.CatalogRoutes

/** Paths that worked before, and the goal that brings each one back. */
@Composable
fun RecipesRoute(
    container: AppContainer,
    onBack: () -> Unit,
) {
    var recipes by remember { mutableStateOf(emptyList<Recipe>()) }
    var names by remember { mutableStateOf(emptyMap<String, String>()) }

    LaunchedEffect(Unit) {
        recipes = container.recipes.all()
        names = runCatching {
            val installed = container.packages.installed()
            CatalogRoutes.targets(installed, "android", container.knowledge)
                .filter { it.bundle.isNotBlank() }
                .associate { it.bundle.lowercase() to it.display }
        }.getOrDefault(emptyMap())
    }

    RecipesScreen(
        recipes = recipes,
        names = names,
        onBack = onBack,
        onDelete = { goal ->
            container.recipes.delete(goal)
            recipes = container.recipes.all()
        },
    )
}
