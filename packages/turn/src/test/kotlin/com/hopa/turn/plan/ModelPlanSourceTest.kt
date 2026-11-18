package com.hopa.turn.plan

import com.hopa.contracts.model.ModelPort
import com.hopa.contracts.model.ModelReply
import com.hopa.contracts.model.ModelRequest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelPlanSourceTest {
    @Test
    fun the_environment_reaches_the_system_message() = runBlocking {
        val model = CapturingModel("1. Open Settings\n2. Tap Network")
        val plans = ModelPlanSource(model) { "This is an Android phone." }

        val steps = plans.plan("Open Settings")

        assertEquals(listOf("Open Settings", "Tap Network"), steps)
        val system = model.system()
        assertTrue(system.contains("This is an Android phone."), system)
        assertTrue(system.contains("You are planning, not acting."), system)
    }

    @Test
    fun no_environment_leaves_the_prompt_alone() = runBlocking {
        val model = CapturingModel("1. Do it")

        ModelPlanSource(model).plan("goal")

        val system = model.system()
        assertEquals(
            "You are planning, not acting. Reply with a numbered plan of at most six " +
                "short steps, one per line, no other text.",
            system,
        )
    }

    @Test
    fun a_failing_environment_still_plans() = runBlocking {
        val model = CapturingModel("1. Open Calendar")

        val steps = ModelPlanSource(model) { error("no context") }.plan("goal")

        assertEquals(listOf("Open Calendar"), steps)
        assertFalse(model.system().contains("no context"))
    }

    @Test
    fun more_than_six_steps_are_cut() = runBlocking {
        val model = CapturingModel((1..9).joinToString("\n") { "$it. Step $it" })

        val steps = ModelPlanSource(model).plan("goal")

        assertEquals(6, steps.size)
        assertEquals("Step 6", steps.last())
    }

    private class CapturingModel(
        private val reply: String,
    ) : ModelPort {
        private var request: ModelRequest? = null

        override suspend fun complete(request: ModelRequest): ModelReply {
            this.request = request
            return ModelReply(text = reply)
        }

        fun system(): String = request?.messages?.firstOrNull { it.role == "system" }?.content.orEmpty()
    }
}
