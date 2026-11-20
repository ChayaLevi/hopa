package com.hopa.feature.skills

import com.hopa.stores.Skill

data class SkillsUiState(
    val skills: List<Skill> = emptyList(),
    val busy: Boolean = false,
)
