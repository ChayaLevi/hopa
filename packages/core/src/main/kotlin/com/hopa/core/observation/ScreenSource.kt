package com.hopa.core.observation

import com.hopa.core.contracts.Observation
import com.hopa.core.ports.DevicePort
import kotlinx.coroutines.withTimeoutOrNull

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

/**
 * A DevicePort that picks the first source that can see the screen and then uses
 * that same source for every action of the turn. Observing through one device and
 * acting through another would be a lie about what the person is looking at.
 */
