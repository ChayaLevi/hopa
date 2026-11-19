package com.hopa.app.feature.memory

import androidx.lifecycle.ViewModelProvider
import com.hopa.app.di.MemoryDependencies

class MemoryViewModelFactory(
    private val container: MemoryDependencies,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        MemoryViewModel(container) as T
}
