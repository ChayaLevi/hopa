package com.hopa.family.scope

import com.hopa.family.model.Activity
import com.hopa.family.model.Scope

/** An activity record together with the scope of the op that wrote it. */
data class ScopedActivity(
    val scope: Scope,
    val activity: Activity,
)
