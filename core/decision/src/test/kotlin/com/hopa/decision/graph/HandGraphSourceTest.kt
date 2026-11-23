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
        assertEquals(500, step?.action?.x)
        assertEquals(230, step?.action?.y)
        assertEquals(false, step?.done)
    }

    @Test
    fun `in Connections the graph taps Wi-Fi and finishes`() {
        val connections = screen(
            "com.android.settings",
            "Flight mode",
            ObservationNode(ref = "e5", type = "TextView", text = "Wi-Fi", clickable = true),
        )

        val step = source.next(connections, "open wifi settings")

        assertEquals(500, step?.action?.x)
        assertEquals(230, step?.action?.y)
        assertEquals(true, step?.done)
        assertEquals("Opened Wi-Fi settings.", step?.answer)
    }

    @Test
    fun `a Wi-Fi row on Connections does not make it the Wi-Fi screen`() {
        val page = screen(
            "com.android.settings",
            "Connections",
            ObservationNode(ref = "w", type = "TextView", text = "Wi-Fi", clickable = true),
            ObservationNode(ref = "f", type = "TextView", text = "Flight mode", clickable = true),
        )

        val step = source.next(page, "open wifi settings")

        assertEquals("ui.tap", step?.action?.kind)
        assertEquals(true, step?.done)
        assertEquals("Opened Wi-Fi settings.", step?.answer)
    }

    @Test
    fun `a group heading is not a control`() {
        // Samsung's Settings root labels a group "Connections" without making it
        // tappable; a tap there changes nothing, so the edge must be a miss.
        val root = screen(
            "com.android.settings",
            "Settings",
            ObservationNode(ref = "h1", type = "TextView", text = "Connections"),
        )

        assertNull(source.next(root, "open wifi settings"))
    }

    @Test
    fun `a label inside a clickable row taps the row`() {
        val row = ObservationNode(
            ref = "r1",
            type = "View",
            clickable = true,
            bounds = listOf(0, 1000, 1080, 1200),
            children = listOf(ObservationNode(ref = "t1", type = "TextView", text = "Connections")),
        )
        val settings = screen("com.android.settings", "Settings", row)

        val step = source.next(settings, "open wifi settings")

        assertEquals("ui.tap", step?.action?.kind)
        assertEquals(540, step?.action?.x)
        assertEquals(1100, step?.action?.y)
    }

    @Test
    fun `a Connections page scrolled away from Wi-Fi scrolls up instead of guessing`() {
        // The Connections page has no "Connections" heading in the tree; its
        // items identify it (Flight mode, Mobile networks, UWB).
        val scrolled = screen(
            "com.android.settings",
            "Flight mode",
            ObservationNode(ref = "e9", type = "TextView", text = "Mobile networks", clickable = true),
        )

        val step = source.next(scrolled, "open wifi settings")

        assertEquals("ui.scroll", step?.action?.kind)
        assertEquals("up", step?.action?.direction)
    }

    @Test
    fun `a goal with a finishing edge is marked as completable`() {
        assertEquals(true, source.target("open wifi settings")?.completes)
        assertEquals("Settings", source.target("open wifi settings")?.name)
    }

    @Test
    fun `a goal that only opens an app is not completable`() {
        assertEquals(false, source.target("open settings")?.completes)
    }

    @Test
    fun `words in another app do not choose a node`() {
        // The chat history mentions Settings; that must not make HOPA look like
        // the Settings app or send the graph to a tap that is not on screen.
        val hopa = screen(
            "com.hopa.android",
            "Settings is open, Michael. Let me know which section you'd like me to open next.",
        )

        val step = source.next(hopa, "open wifi settings")

        assertEquals("ui.launch", step?.action?.kind)
        assertEquals("com.android.settings", step?.action?.app)
    }

    @Test
    fun `an unknown goal is left to the model`() {
        assertNull(source.next(screen("com.android.settings", "Settings"), "order a pizza"))
    }

    @Test
    fun `the Samsung root is recognised by its category rows`() {
        // The root exposes no "Settings" title on One UI; its rows are the screen.
        // The label is not clickable, the row around it is.
        val row = ObservationNode(
            ref = "row",
            type = "LinearLayout",
            clickable = true,
            bounds = listOf(28, 1287, 1052, 1437),
            children = listOf(ObservationNode(ref = "label", type = "TextView", text = "Connections")),
        )
        val root = screen(
            "com.android.settings",
            "",
            row,
            ObservationNode(ref = "other", type = "TextView", text = "Notifications"),
        )

        val step = source.next(root, "open wifi settings")

        assertEquals("ui.tap", step?.action?.kind)
        assertEquals(540, step?.action?.x)
        assertEquals(1362, step?.action?.y)
    }

    @Test
    fun `a deep Settings page that still says settings steps back`() {
        // "More connection settings" matches the root node by name; only a step
        // back can tell the graph it is not at the root.
        val deep = screen("com.android.settings", "More connection settings")

        val step = source.next(deep, "open wifi settings")

        assertEquals("ui.back", step?.action?.kind)
    }

    @Test
    fun `an unknown Settings screen steps back instead of guessing`() {
        val odd = screen("com.android.settings", "Some new page")

        val step = source.next(odd, "open wifi settings")

        // Settings reopens on its last page; a back edge is how the graph
        // returns to a screen it knows.
        assertEquals("ui.back", step?.action?.kind)
    }

    @Test
    fun `an unknown screen in an app without a back edge is left to the model`() {
        val odd = screen("com.sec.android.app.clockpackage", "Some new page")

        assertNull(source.next(odd, "open alarm"))
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
        tree = ObservationNode(
            type = "root",
            text = text,
            children = nodes.mapIndexed { index, node ->
                if (node.bounds != null) {
                    node
                } else {
                    node.copy(bounds = listOf(100, 200 + index * 100, 900, 260 + index * 100))
                }
            },
        ),
    )
}
