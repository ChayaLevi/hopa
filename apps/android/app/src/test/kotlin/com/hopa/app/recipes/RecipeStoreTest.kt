package com.hopa.app.recipes

import com.hopa.contracts.Action
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecipeStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(): RecipeStore = RecipeStore(File(folder.root, "recipes.txt"))

    private val launch = Action(kind = "ui.launch", app = "com.android.settings")
    private val tap = Action(kind = "ui.tap", ref = "e21")

    @Test
    fun a_recipe_keeps_the_screens_it_ran_between() {
        val store = store()
        store.save(
            goal = "Open Settings",
            steps = listOf(launch, tap),
            screenHash = "sha256:start",
            endHash = "sha256:end",
        )

        val recipe = store.find("open settings")
        assertEquals("sha256:start", recipe?.screenHash)
        assertEquals("sha256:end", recipe?.endHash)
        assertEquals(listOf("ui.launch", "ui.tap"), recipe?.steps?.map { it.kind })
        assertEquals("e21", recipe?.steps?.get(1)?.ref)
    }

    @Test
    fun a_recipe_without_hashes_still_reads() {
        // The format before hashes: at, goal, then the steps.
        val line = listOf(
            "1700000000000",
            "Open Settings",
            listOf("ui.launch", "com.android.settings", "", "", "", "", "", "", "", "3000")
                .joinToString("\u001f"),
        ).joinToString("\u001f")
        File(folder.root, "recipes.txt").writeText(line + "\n")

        val recipe = store().find("Open Settings")
        assertEquals(1, recipe?.steps?.size)
        assertEquals("", recipe?.screenHash)
        assertEquals("", recipe?.endHash)
    }

    @Test
    fun a_ref_step_is_never_replayed_until_it_records_its_target() {
        val store = store()
        store.save(goal = "tap", steps = listOf(tap), screenHash = "sha256:start")
        assertTrue(store.find("tap")!!.needsStableScreen)

        val launchOnly = store()
        launchOnly.save(goal = "launch", steps = listOf(launch), screenHash = "sha256:start")
        assertFalse(launchOnly.find("launch")!!.needsStableScreen)
    }

    @Test
    fun saving_the_same_goal_replaces_the_old_recipe() {
        val store = store()
        store.save(goal = "Open Settings", steps = listOf(launch), screenHash = "sha256:one")
        store.save(goal = "Open Settings", steps = listOf(launch, tap), screenHash = "sha256:two")

        assertEquals(1, store.all().size)
        assertEquals("sha256:two", store.find("Open Settings")?.screenHash)
        assertEquals(2, store.find("Open Settings")?.steps?.size)
    }

    @Test
    fun deleting_leaves_the_file_empty_for_the_next_recipe() {
        val store = store()
        store.save(goal = "Open Settings", steps = listOf(launch))
        store.delete("Open Settings")

        assertNull(store.find("Open Settings"))
        assertTrue(store.all().isEmpty())
    }
}
