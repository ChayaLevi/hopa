package com.hopa.engine.observation

import com.hopa.contracts.Observation
import com.hopa.contracts.ObservationNode
import com.hopa.contracts.ScreenSize
import com.hopa.contracts.ports.DevicePort
import com.hopa.contracts.ports.InstalledApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSourceDeviceTest {
    private fun observation(id: String) = Observation(
        id = id,
        timestamp = 0,
        screenSize = ScreenSize(width = 1, height = 1),
        text = id,
        tree = ObservationNode(type = "root"),
    )

    private open class FakeDevice(
        private val id: String? = null,
        private val delayMs: Long = 0,
        private val fail: Boolean = false,
    ) : DevicePort {
        var acted = mutableListOf<String>()
        var observed = 0

        override suspend fun observe(): Observation {
            observed++
            if (delayMs > 0) delay(delayMs)
            if (fail) error("no tree")
            return Observation(
                id = id ?: "none",
                timestamp = 0,
                screenSize = ScreenSize(width = 1, height = 1),
                tree = ObservationNode(type = "root"),
            )
        }

        override suspend fun tap(ref: String?, x: Int?, y: Int?) {
            acted += "tap"
        }

        override suspend fun type(text: String, ref: String?) {
            acted += "type"
        }

        override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, velocity: Int) = Unit

        override suspend fun pressBack() = Unit

        override suspend fun pressHome() = Unit

        override suspend fun launch(packageName: String) = Unit

        override suspend fun stop(packageName: String) = Unit

        override suspend fun foreground(): String = ""

        override suspend fun screenshot(): ByteArray? = null

        override suspend fun apps(): List<InstalledApp> = emptyList()
    }

    @Test
    fun `the first source that answers wins`() = runBlocking {
        val bridge = FakeDevice("bridge")
        val accessibility = FakeDevice("accessibility")
        val fallback = FakeDevice("fallback")
        val device = ScreenSourceDevice(
            listOf(ScreenSource("bridge", bridge, 1_000), ScreenSource("accessibility", accessibility, 1_000)),
            fallback,
        )

        assertEquals("bridge", device.observe().id)
        assertEquals("bridge", device.sourceName)
        assertEquals(0, accessibility.observed)
    }

    @Test
    fun `a slow source is skipped after its budget`() = runBlocking {
        val bridge = FakeDevice("bridge", delayMs = 500)
        val accessibility = FakeDevice("accessibility")
        val device = ScreenSourceDevice(
            listOf(ScreenSource("bridge", bridge, 50), ScreenSource("accessibility", accessibility, 1_000)),
            FakeDevice(),
        )

        assertEquals("accessibility", device.observe().id)
        assertEquals("accessibility", device.sourceName)
    }

    @Test
    fun `a failing source does not end the turn`() = runBlocking {
        val bridge = FakeDevice(fail = true)
        val accessibility = FakeDevice("accessibility")
        val device = ScreenSourceDevice(
            listOf(ScreenSource("bridge", bridge, 1_000), ScreenSource("accessibility", accessibility, 1_000)),
            FakeDevice(),
        )

        assertEquals("accessibility", device.observe().id)
    }

    @Test
    fun `actions go to the source that observed`() = runBlocking {
        val bridge = FakeDevice("bridge")
        val accessibility = FakeDevice("accessibility")
        val device = ScreenSourceDevice(
            listOf(ScreenSource("bridge", bridge, 1_000), ScreenSource("accessibility", accessibility, 1_000)),
            FakeDevice(),
        )

        device.observe()
        device.tap("e1")
        device.type("hi", "e1")

        assertEquals(listOf("tap", "type"), bridge.acted)
        assertTrue(accessibility.acted.isEmpty())
    }

    @Test
    fun `no source falls back and says so`() = runBlocking {
        val fallback = FakeDevice("fallback")
        val device = ScreenSourceDevice(
            listOf(ScreenSource("bridge", FakeDevice(fail = true), 100)),
            fallback,
        )

        assertEquals("fallback", device.observe().id)
        assertEquals("fallback", device.sourceName)
    }
}
