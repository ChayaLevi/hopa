package com.hopa.turn.recipe

import com.hopa.contracts.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecipeStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(): RecipeStore = RecipeStore(File(folder.root, "recipes.txt"))

    private val launch = Action(kind = "ui.launch", app = "com.android.settings")
    private val tap = Action(kind = "ui.tap", ref = "e21")
    private val anchor = "TextView|text=connections|id=android:id/title"

    @Test
    fun a_recipe_keeps_its_steps_anchors_and_hashes() {
        val store = store()
        store.save(
            goal = "Open Settings",
            steps = listOf(launch, tap),
            anchors = listOf("", anchor),
            screenHash = "sha256:start",
            endHash = "sha256:end",
        )

        val recipe = store.find("open settings")
        assertEquals("sha256:start", recipe?.screenHash)
        assertEquals("sha256:end", recipe?.endHash)
        assertEquals(listOf("ui.launch", "ui.tap"), recipe?.steps?.map { it.kind })
        assertEquals("e21", recipe?.steps?.get(1)?.ref)
        assertEquals(anchor, recipe?.anchorAt(1))
        assertEquals("", recipe?.anchorAt(0))
    }

    @Test
    fun a_recipe_without_anchors_still_reads() {
        // The format before anchors: at, goal, two hashes, then ten fields a step.
        val step = listOf(
            "ui.tap", "", "e21", "", "", "", "", "", "", "3000",
        ).joinToString("\u001f")
        val line = listOf("1700000000000", "Tap Connections", "sha256:a", "sha256:b", step)
            .joinToString("\u001f")
        File(folder.root, "recipes.txt").writeText(line + "\n")

        val recipe = store().find("Tap Connections")
        assertEquals(1, recipe?.steps?.size)
        assertEquals("", recipe?.anchorAt(0))
        assertFalse(recipe!!.replayable)
    }

    @Test
    fun the_oldest_format_still_reads() {
        val step = listOf(
            "ui.launch", "com.android.settings", "", "", "", "", "", "", "", "3000",
        ).joinToString("\u001f")
        val line = listOf("1700000000000", "Open Settings", step).joinToString("\u001f")
        File(folder.root, "recipes.txt").writeText(line + "\n")

        val recipe = store().find("Open Settings")
        assertEquals(1, recipe?.steps?.size)
        assertEquals("", recipe?.screenHash)
        assertTrue(recipe!!.replayable)
    }

    @Test
    fun a_ref_step_without_an_anchor_makes_the_recipe_unreplayable() {
        val withAnchor = store()
        withAnchor.save("tap", listOf(tap), anchors = listOf(anchor))
        assertTrue(withAnchor.find("tap")!!.replayable)

        val withoutAnchor = store()
        withoutAnchor.save("tap2", listOf(tap))
        assertFalse(withoutAnchor.find("tap2")!!.replayable)

        val launchOnly = store()
        launchOnly.save("launch", listOf(launch))
        assertTrue(launchOnly.find("launch")!!.replayable)
    }

    @Test
    fun saving_the_same_goal_replaces_the_old_recipe() {
        val store = store()
        store.save("Open Settings", listOf(launch))
        store.save("Open Settings", listOf(launch, tap), anchors = listOf("", anchor))

        assertEquals(1, store.all().size)
        assertEquals(2, store.find("Open Settings")?.steps?.size)
        assertEquals(anchor, store.find("Open Settings")?.anchorAt(1))
    }

    @Test
    fun deleting_leaves_the_file_empty_for_the_next_recipe() {
        val store = store()
        store.save("Open Settings", listOf(launch))
        store.delete("Open Settings")

        assertNull(store.find("Open Settings"))
        assertTrue(store.all().isEmpty())
    }
}
