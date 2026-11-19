package com.hopa.app.feature.memory

data class MemoryUiState(
    val facts: List<String> = emptyList(),
    val busy: Boolean = false,
)
