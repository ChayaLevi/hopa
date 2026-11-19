package com.hopa.agent.narrate

import com.hopa.contracts.wire.Action
import com.hopa.contracts.wire.RunRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RunNarratorTest {
    private fun action(kind: String, ref: String? = null, app: String? = null) = Action(
        schemaVersion = "1",
        kind = kind,
        ref = ref,
        app = app,
        timeoutMs = 3000,
    )

    private fun record(step: Int, action: Action, executed: Boolean = true, error: String? = null) = RunRecord(
        runId = "run-1",
        step = step,
        traceId = "trace-1",
        action = action,
        classification = "read",
        observationHash = "sha256:0",
        verified = executed && error == null,
        executed = executed,
        error = error,
        prevHash = "sha256:0",
        hash = "sha256:1",
    )

    @Test
    fun `one launch reads as one sentence`() {
        val line = RunNarrator.line(action("ui.launch", app = "com.example.bank"), executed = true)
        assertEquals("Open com.example.bank", line)
    }

    @Test
    fun `a failed step says what failed and why`() {
        val line = RunNarrator.line(action("ui.tap", ref = "e7"), executed = true, error = "no window")
        assertEquals("Tap e7 failed: no window", line)
    }

    @Test
    fun `a step that never ran says so`() {
        val line = RunNarrator.line(action("ui.type", ref = "e3"), executed = false)
        assertEquals("Type into e3 (not run)", line)
    }

    @Test
    fun `consecutive taps fold into one line`() {
        val records = listOf(
            record(1, action("ui.launch", app = "bank")),
            record(2, action("ui.tap", ref = "Menu")),
            record(3, action("ui.tap", ref = "Payments")),
            record(4, action("ui.tap", ref = "Confirm")),
        )
        assertEquals(
            listOf("Open bank", "Tap Menu, Payments and Confirm"),
            RunNarrator.lines(records),
        )
    }

    @Test
    fun `long tap runs stay bounded`() {
        val records = (1..6).map { record(it, action("ui.tap", ref = "item$it")) }
        assertEquals(listOf("Tap item1, item2 and item3, plus 3 more"), RunNarrator.lines(records))
    }

    @Test
    fun `a failure breaks the tap group`() {
        val records = listOf(
            record(1, action("ui.tap", ref = "A")),
            record(2, action("ui.tap", ref = "B"), error = "element gone"),
            record(3, action("ui.tap", ref = "C")),
        )
        assertEquals(
            listOf("Tap A", "Tap B failed: element gone", "Tap C"),
            RunNarrator.lines(records),
        )
    }
}
