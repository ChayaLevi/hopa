package com.hopa.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RunGateTest {
    @Test
    fun `one caller claims the phone and a second is refused`() {
        val gate = RunGate()

        assertTrue(gate.claim("open settings", RunSource.CHAT))
        assertFalse(gate.claim("delivered task", RunSource.DELIVERY))
        assertEquals("open settings", gate.state.value.goal)
    }

    @Test
    fun `finish returns the gate to idle`() {
        val gate = RunGate()
        gate.claim("open settings", RunSource.CHAT)

        gate.finish()

        assertFalse(gate.state.value.busy)
        assertTrue(gate.claim("next goal", RunSource.DELIVERY))
    }

    @Test
    fun `cancel is remembered until the run finishes`() {
        val gate = RunGate()
        gate.claim("open settings", RunSource.CHAT)

        gate.cancel()

        assertTrue(gate.isCancelled())
        gate.finish()
        assertFalse(gate.isCancelled())
    }
}
