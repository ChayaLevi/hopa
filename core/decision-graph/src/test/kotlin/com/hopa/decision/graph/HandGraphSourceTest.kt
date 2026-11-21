package com.hopa.decision.graph

import com.hopa.contracts.wire.Observation
import com.hopa.contracts.wire.ObservationNode
import com.hopa.contracts.wire.ScreenSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HandGraphSourceTest {
    private val graphs = GraphCatalog.load()
    private val source = HandGraphSource(graphs)

    @Test
    fun `the catalogue loads the three known apps`() {
        assertEquals(3, graphs.size)
        assertTrue(graphs.map { it.name }.containsAll(listOf("Settings", "Clock", "Camera")))
    }

    @Test
    fun `a known goal starts by opening the app`() {
        val step = source.next(screen("com.sec.android.app.launcher", "Home"), "open wifi settings")

        assertEquals("ui.launch", step?.action?.kind)
        assertEquals("com.android.settings", step?.action?.app)
        assertEquals(false, step?.done)
    }

    @Test
    fun `in Settings the graph takes Connections when Wi-Fi is not on the page`() {
        val settings = screen(
            "com.android.settings",
            "Settings",
            ObservationNode(ref = "e2", type = "TextView", text = "Connections", clickable = true),
        )

        val step = source.next(settings, "open wifi settings")

        assertEquals("ui.tap", step?.action?.kind)
        assertEquals("e2", step?.action?.ref)
        assertEquals(false, step?.done)
    }

    @Test
    fun `in Connections the graph taps Wi-Fi and finishes`() {
        val connections = screen(
            "com.android.settings",
            "Connections",
            ObservationNode(ref = "e5", type = "TextView", text = "Wi-Fi", clickable = true),
        )

        val step = source.next(connections, "open wifi settings")

        assertEquals("e5", step?.action?.ref)
        assertEquals(true, step?.done)
        assertEquals("Opened Wi-Fi settings.", step?.answer)
    }

    @Test
    fun `an unknown goal is left to the model`() {
        assertNull(source.next(screen("com.android.settings", "Settings"), "order a pizza"))
    }

    @Test
    fun `an unknown screen inside a known app is left to the model`() {
        val odd = screen("com.android.settings", "Some new page")

        assertNull(source.next(odd, "open wifi settings"))
    }

    @Test
    fun `an edge whose control is missing does not fire`() {
        val settings = screen("com.android.settings", "Settings")

        assertNull(source.next(settings, "open wifi settings"))
    }

    @Test
    fun `a single launch goal is done in one step`() {
        val step = source.next(screen("com.sec.android.app.launcher", "Home"), "open the camera")

        assertEquals("ui.launch", step?.action?.kind)
        assertEquals("com.sec.android.app.camera", step?.action?.app)
        assertEquals(true, step?.done)
        assertEquals("Opened Camera.", step?.answer)
    }

    private fun screen(app: String, text: String, vararg nodes: ObservationNode): Observation = Observation(
        id = "obs-$app-$text",
        timestamp = 1L,
        app = app,
        text = text,
        screenSize = ScreenSize(width = 1080, height = 2340),
        tree = ObservationNode(type = "root", text = text, children = nodes.toList()),
    )
}
