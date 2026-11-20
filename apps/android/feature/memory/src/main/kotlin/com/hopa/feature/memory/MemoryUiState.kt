package com.hopa.feature.memory

data class MemoryUiState(
    val facts: List<String> = emptyList(),
    val busy: Boolean = false,
)
