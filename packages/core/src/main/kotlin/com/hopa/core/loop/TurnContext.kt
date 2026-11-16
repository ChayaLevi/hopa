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
    /** Which client is running: android, windows, harmonyos. */
    val platform: String = "",
    /** Send a screenshot with each model call; the model must accept images. */
    val vision: Boolean = false,

    /** Typed functions the foreground app publishes, already formatted. */
    val appFunctions: String = "",

    /** True when the client can actually see and act on this device right now. */
    val phoneAccess: Boolean? = null,
)
