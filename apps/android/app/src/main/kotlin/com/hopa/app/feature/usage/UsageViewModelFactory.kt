package com.hopa.app.feature.usage

import androidx.lifecycle.ViewModelProvider
import com.hopa.app.di.UsageDependencies

class UsageViewModelFactory(
    private val container: UsageDependencies,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        UsageViewModel(container) as T
}
