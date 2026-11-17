package com.hopa.app.feature.memory

import com.hopa.stores.Skill

data class MemoryUiState(
    val facts: List<String> = emptyList(),
    val skills: List<Skill> = emptyList(),
    val busy: Boolean = false,
)
