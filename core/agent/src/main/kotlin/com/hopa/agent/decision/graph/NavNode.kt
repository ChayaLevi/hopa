package com.hopa.agent.decision.graph

data class NavNode(
    val key: ScreenKey,
    val label: String = "",
)

/**
 * One thing that can be done on [from], and where it led. [guard] is required for
 * safety: an edge only applies to a screen that still looks like the one it was
 * learned on.
 */
