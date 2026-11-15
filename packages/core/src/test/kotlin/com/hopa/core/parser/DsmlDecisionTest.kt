package com.hopa.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DsmlDecisionTest {
    private val payload = """
        <||DSML||calls>
        <||DSML||invoke name="ui.launch">
        <||DSML||parameter name="app" string="true">com.android.settings</||DSML||parameter>
        <||DSML||parameter name="timeoutMs" string="false">8000</||DSML||parameter>
        </||DSML||invoke>
        </||DSML||calls>
    """.trimIndent()

    @Test
    fun a_tool_call_in_dsml_becomes_the_same_action() {
        val outcome = DecisionParser().parse(payload)
        assertNull(outcome.text)
        val action = outcome.decision?.action
        assertEquals("ui.launch", action?.kind)
        assertEquals("com.android.settings", action?.app)
        assertEquals(8000, action?.timeoutMs)
    }

    @Test
    fun prose_around_the_call_does_not_hide_it() {
        val outcome = DecisionParser().parse("Opening Settings now.\n\n$payload")
        assertEquals("ui.launch", outcome.decision?.action?.kind)
    }

    @Test
    fun an_unknown_kind_in_dsml_is_a_schema_error() {
        val raw = """<||DSML||invoke name="do.magic"></||DSML||invoke>"""
        val outcome = DecisionParser().parse(raw)
        assertEquals("E_SCHEMA", outcome.error?.code)
    }
}
