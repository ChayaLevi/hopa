package com.hopa.engine.observation

import com.hopa.contracts.ObservationNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * An anchor names a control so a replay can find it again after the tree has
 * been redrawn; a ref only names a position.
 */
class NodeAnchorTest {
    private fun tree(): ObservationNode = ObservationNode(
        ref = "e1",
        type = "root",
        children = listOf(
            ObservationNode(
                ref = "e2",
                type = "TextView",
                text = "Connections\n",
                id = "android:id/title",
            ),
            ObservationNode(
                ref = "e3",
                type = "ImageView",
                description = "Back",
            ),
            ObservationNode(ref = "e4", type = "View", clickable = true),
        ),
    )

    @Test
    fun a_key_describes_what_the_node_is() {
        val text = NodeAnchor.find(tree(), "e2")!!
        assertEquals("TextView|text=connections|id=android:id/title", NodeAnchor.key(text))
        val icon = NodeAnchor.find(tree(), "e3")!!
        assertEquals("ImageView|desc=back", NodeAnchor.key(icon))
    }

    @Test
    fun a_node_with_no_text_description_or_id_has_no_anchor() {
        assertNull(NodeAnchor.find(tree(), "e4")?.let(NodeAnchor::key))
    }

    @Test
    fun the_same_control_is_found_after_the_tree_moves() {
        val moved = ObservationNode(
            ref = "e1",
            type = "root",
            children = listOf(
                ObservationNode(ref = "e9", type = "ImageView", description = "Back"),
                ObservationNode(
                    ref = "e21",
                    type = "TextView",
                    text = "Connections",
                    id = "android:id/title",
                ),
            ),
        )
        val key = "TextView|text=connections|id=android:id/title"
        assertEquals("e21", NodeAnchor.refOf(moved, key))
    }

    @Test
    fun a_control_that_is_gone_is_not_guessed() {
        val other = ObservationNode(
            ref = "e1",
            type = "root",
            children = listOf(
                ObservationNode(ref = "e21", type = "TextView", text = "Wi-Fi"),
            ),
        )
        assertNull(NodeAnchor.refOf(other, "TextView|text=connections|id=android:id/title"))
    }

    @Test
    fun a_visible_enabled_match_wins_over_a_hidden_one() {
        val key = "TextView|text=connections|id=android:id/title"
        val tree = ObservationNode(
            ref = "e1",
            type = "root",
            children = listOf(
                ObservationNode(
                    ref = "e5",
                    type = "TextView",
                    text = "Connections",
                    id = "android:id/title",
                    visible = false,
                ),
                ObservationNode(
                    ref = "e6",
                    type = "TextView",
                    text = "Connections",
                    id = "android:id/title",
                ),
            ),
        )
        assertEquals("e6", NodeAnchor.refOf(tree, key))
    }
}
