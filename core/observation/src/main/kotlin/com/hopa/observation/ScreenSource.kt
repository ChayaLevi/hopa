package com.hopa.observation

import com.hopa.contracts.ports.DevicePort

/**
 * One place a screen can be read from: the bridge, accessibility, or the
 * browser. The name is what a run records; the budget is how long the client
 * waits before trying the next one.
 */
data class ScreenSource(
    val name: String,
    val device: DevicePort,
    val budgetMs: Long = 4_000,
)
