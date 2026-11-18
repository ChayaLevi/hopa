package com.hopa.turn.goal

import com.hopa.contracts.wire.Observation
import com.hopa.contracts.wire.ObservationNode
import com.hopa.contracts.wire.ScreenSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoalVerifierTest {
    private val verifier = GoalVerifier()

    private fun screen(vararg nodes: ObservationNode): Observation = Observation(
        id = "obs",
        timestamp = 1L,
        screenSize = ScreenSize(width = 1080, height = 2400),
        tree = ObservationNode(type = "root", children = nodes.toList()),
    )

    private fun node(text: String, visible: Boolean = true): ObservationNode = ObservationNode(
        ref = "e1",
        type = "TextView",
        text = text,
        id = "android:id/title",
        visible = visible,
    )

    @Test
    fun a_visible_label_meets_its_checkpoint() {
        val result = verifier.verify(screen(node("Wi-Fi")), listOf(Checkpoint("Wi-Fi")))

        assertTrue(result.complete)
        assertEquals(1, result.met.size)
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun a_hidden_label_does_not_meet_its_checkpoint() {
        val result = verifier.verify(screen(node("Wi-Fi", visible = false)), listOf(Checkpoint("Wi-Fi")))

        assertFalse(result.complete)
        assertEquals(listOf(Checkpoint("Wi-Fi")), result.missing)
    }

    @Test
    fun a_label_must_match_exactly_not_as_a_substring() {
        // "Wi-Fi calling" is a different row; it must not satisfy "Wi-Fi".
        val result = verifier.verify(screen(node("Wi-Fi calling")), listOf(Checkpoint("Wi-Fi")))

        assertFalse(result.complete)
    }

    @Test
    fun a_node_anchor_key_matches_through_the_whole_tree() {
        val deep = ObservationNode(
            type = "root",
            children = listOf(
                ObservationNode(
                    type = "ListView",
                    children = listOf(node("Wi-Fi")),
                ),
            ),
        )
        val result = verifier.verify(
            screen().copy(tree = deep),
            listOf(Checkpoint("TextView|text=wi-fi|id=android:id/title")),
        )

        assertTrue(result.complete)
    }

    @Test
    fun an_empty_checkpoint_list_is_complete_and_a_missing_screen_is_not() {
        assertTrue(verifier.verify(screen(node("Wi-Fi")), emptyList()).complete)
        assertFalse(verifier.verify(null, listOf(Checkpoint("Wi-Fi"))).complete)
    }
}
