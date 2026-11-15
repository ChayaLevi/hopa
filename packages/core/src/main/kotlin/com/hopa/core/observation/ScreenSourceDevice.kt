package com.hopa.core.observation

import com.hopa.core.contracts.Observation
import com.hopa.core.ports.DevicePort
import kotlinx.coroutines.withTimeoutOrNull

class ScreenSourceDevice(
    private val sources: List<ScreenSource>,
    private val fallback: DevicePort,
) : DevicePort {
    private var chosen: DevicePort? = null

    /** Which source answered, for the run record. Empty until the first observe. */
    var sourceName: String = ""
        private set

    override suspend fun observe(): Observation {
        chosen?.let { return it.observe() }

        for (source in sources) {
            val observation = withTimeoutOrNull(source.budgetMs) {
                runCatching { source.device.observe() }.getOrNull()
            }
            if (observation != null) {
                chosen = source.device
                sourceName = source.name
                return observation
            }
        }

        chosen = fallback
        sourceName = "fallback"
        return fallback.observe()
    }

    private fun device(): DevicePort = chosen ?: fallback

    override suspend fun tap(ref: String?, x: Int?, y: Int?) = device().tap(ref, x, y)

    override suspend fun doubleTap(ref: String?, x: Int?, y: Int?) = device().doubleTap(ref, x, y)

    override suspend fun longPress(ref: String?, x: Int?, y: Int?) = device().longPress(ref, x, y)

    override suspend fun type(text: String, ref: String?) = device().type(text, ref)

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, velocity: Int) =
        device().swipe(fromX, fromY, toX, toY, velocity)

    override suspend fun pressBack() = device().pressBack()

    override suspend fun pressHome() = device().pressHome()

    override suspend fun launch(packageName: String) = device().launch(packageName)

    override suspend fun stop(packageName: String) = device().stop(packageName)

    override suspend fun foreground(): String = device().foreground()

    override suspend fun screenshot(): ByteArray? = device().screenshot()

    override suspend fun apps() = device().apps()
}
