package com.hopa.engine.parser

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

    @Test
    fun a_different_pipe_style_is_still_the_same_action() {
        val raw = "<|DSML|invoke name='ui.tap'>" +
            "<|DSML|parameter name='ref' string=\"true\">e4</|DSML|parameter>" +
            "</|DSML|invoke>"
        val action = DecisionParser().parse(raw).decision?.action
        assertEquals("ui.tap", action?.kind)
        assertEquals("e4", action?.ref)
    }

    @Test
    fun markup_that_cannot_be_decoded_is_an_error_not_chat_text() {
        val raw = "<||DSML||calls>\n<||DSML||something else>\n</||DSML||calls>"
        val outcome = DecisionParser().parse(raw)
        assertNull(outcome.text)
        assertEquals("E_SCHEMA", outcome.error?.code)
    }
}
