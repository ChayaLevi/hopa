package com.hopa.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopa.catalog.routing.CatalogRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The saved repetitions and the names the catalogue gives their targets. */
@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val deps: RecipesDependencies,
) : ViewModel() {
    private val _state = MutableStateFlow(RecipesUiState())
    val state: StateFlow<RecipesUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun delete(goal: String) {
        viewModelScope.launch {
            deps.recipes.delete(goal)
            load()
        }
    }

    private suspend fun load() {
        val recipes = deps.recipes.all()
        val names = runCatching {
            val installed = deps.packages.installed()
            CatalogRoutes.targets(installed, "android", deps.knowledge)
                .filter { it.bundle.isNotBlank() }
                .associate { it.bundle.lowercase() to it.display }
        }.getOrDefault(emptyMap())
        _state.update { it.copy(recipes = recipes, names = names) }
    }
}
