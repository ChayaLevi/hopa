package com.hopa.core.loop

import com.hopa.core.model.ModelMessage
import com.hopa.core.ports.InstalledApp

data class TurnContext(
    val goal: String,
    val history: List<ModelMessage> = emptyList(),
    val memory: String = "",
    val skills: String = "",
    /** What this phone can open right now; the model launches by these packages. */
    val installedApps: List<InstalledApp> = emptyList(),
)
