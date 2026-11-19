package com.hopa.execution

import com.hopa.contracts.ErrorCodes
import com.hopa.contracts.wire.Action
import com.hopa.contracts.wire.Decision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The rules that run before the gate: a step the device cannot carry out is a
 * schema failure, not a gesture at a guess.
 */
class DecisionRulesTest {
    private fun step(action: Action): Decision = Decision(action = action, say = "", done = false, needUser = false)

    private fun error(action: Action): String? = DecisionRules.validate(step(action)).errorCode

    @Test
    fun a_tap_needs_a_ref_or_a_point() {
        assertEquals(ErrorCodes.SCHEMA, error(Action(kind = "ui.tap")))
        assertNull(error(Action(kind = "ui.tap", ref = "e3")))
        assertNull(error(Action(kind = "ui.tap", x = 20, y = 40)))
        // Half a point is not a point.
        assertEquals(ErrorCodes.SCHEMA, error(Action(kind = "ui.tap", x = 20)))
    }

    @Test
    fun typing_may_clear_a_field_but_must_carry_text() {
        assertNull(error(Action(kind = "ui.type", ref = "e1", text = "")))
        assertNull(error(Action(kind = "ui.type", ref = "e1", text = "hello")))
        assertEquals(ErrorCodes.SCHEMA, error(Action(kind = "ui.type", ref = "e1")))
    }

    @Test
    fun a_swipe_needs_the_whole_gesture() {
        assertEquals(
            ErrorCodes.SCHEMA,
            error(Action(kind = "ui.swipe", x = 10, y = 10, toX = 10)),
        )
        assertNull(error(Action(kind = "ui.swipe", x = 10, y = 900, toX = 10, toY = 100)))
    }

    @Test
    fun a_scroll_takes_a_direction_or_a_point() {
        assertNull(error(Action(kind = "ui.scroll", direction = "down")))
        assertNull(error(Action(kind = "ui.scroll", x = 500, y = 1200)))
        assertEquals(ErrorCodes.SCHEMA, error(Action(kind = "ui.scroll")))
    }

    @Test
    fun launch_and_stop_name_an_app() {
        assertEquals(ErrorCodes.SCHEMA, error(Action(kind = "ui.launch")))
        assertNull(error(Action(kind = "ui.launch", app = "com.android.settings")))
        assertEquals(ErrorCodes.SCHEMA, error(Action(kind = "ui.stop", app = "")))
    }

    @Test
    fun a_typed_function_needs_its_app_and_its_name() {
        assertEquals(
            ErrorCodes.SCHEMA,
            error(Action(kind = "app.function", app = "com.example.app")),
        )
        assertNull(
            error(Action(kind = "app.function", app = "com.example.app", ref = "sendMessage")),
        )
    }

    @Test
    fun a_link_is_a_url_or_text() {
        assertEquals(ErrorCodes.SCHEMA, error(Action(kind = "web.navigate")))
        assertNull(error(Action(kind = "web.navigate", url = "https://example.com")))
        assertNull(error(Action(kind = "system.openLink", text = "https://example.com")))
    }

    @Test
    fun gestures_without_a_target_are_still_allowed() {
        assertNull(error(Action(kind = "ui.back")))
        assertNull(error(Action(kind = "ui.home")))
        assertNull(error(Action(kind = "ui.observe")))
        assertNull(error(Action(kind = "web.read")))
    }

    @Test
    fun an_unknown_kind_is_named_in_the_failure() {
        val result = DecisionRules.validate(step(Action(kind = "ui.teleport")))
        assertEquals(ErrorCodes.SCHEMA, result.errorCode)
    }

    @Test
    fun a_plain_answer_needs_no_action() {
        val result = DecisionRules.validate(
            Decision(action = null, say = "Done.", done = true, needUser = false),
        )
        assertTrue(result.valid)
    }
}
