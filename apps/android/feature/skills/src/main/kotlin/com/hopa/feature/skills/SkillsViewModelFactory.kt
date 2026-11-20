package com.hopa.feature.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hopa.feature.skills.SkillsDependencies

class SkillsViewModelFactory(
    private val container: SkillsDependencies,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SkillsViewModel(container) as T
}
