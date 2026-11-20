package com.hopa.feature.skills

import com.hopa.stores.SkillStore

/** The procedures HOPA may replay, and nothing else. */
interface SkillsDependencies {
    val skills: SkillStore
}
