package com.hopa.inference.local

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlanShapeTest {
    private val shape = PlanShape()

    @Test
    fun a_well_formed_plan_parses() {
        val answer = shape.parse(
            """{"steps":[{"kind":"ui.open","target":"com.android.calculator","reason":"asked"}]}""",
        )

        assertNotNull(answer)
        assertEquals(1, answer?.steps?.size)
        assertEquals("ui.open", answer?.steps?.first()?.kind)
    }

    @Test
    fun a_reason_may_be_missing() {
        val answer = shape.parse("""{"steps":[{"kind":"ui.open","target":"com.app"}]}""")

        assertNotNull(answer)
        assertEquals("", answer?.steps?.first()?.reason)
    }

    @Test
    fun prose_is_not_a_plan() {
        assertNull(shape.parse("Sure! I will open the calculator for you."))
    }

    @Test
    fun an_empty_plan_is_not_a_plan() {
        assertNull(shape.parse("""{"steps":[]}"""))
    }

    @Test
    fun a_step_without_a_target_is_rejected() {
        assertNull(shape.parse("""{"steps":[{"kind":"ui.open"}]}"""))
    }

    @Test
    fun the_grammar_names_the_fields_the_parser_expects() {
        assertTrue(shape.grammar.contains("steps"))
        assertTrue(shape.grammar.contains("kind"))
        assertTrue(shape.grammar.contains("target"))
        assertTrue(shape.grammar.contains("reason"))
    }
}
