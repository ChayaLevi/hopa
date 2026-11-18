package com.hopa.capabilities.accessibility.state

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccessibilityMonitorTest {
    @Test
    fun a_bound_service_is_ready() = runBlocking {
        val monitor = AccessibilityMonitor(this, graceMs = 100)

        monitor.refresh(enabled = true, connected = true)

        assertEquals(AccessibilityBinding(enabled = true, connected = true), monitor.state.value)
        assertTrue(monitor.state.value.ready)
    }

    @Test
    fun an_idle_phone_reports_what_android_said() = runBlocking {
        val monitor = AccessibilityMonitor(this, graceMs = 100)

        monitor.refresh(enabled = false, connected = false)
        assertEquals(AccessibilityBinding(enabled = false, connected = false), monitor.state.value)

        monitor.refresh(enabled = true, connected = false)
        assertEquals(AccessibilityBinding(enabled = true, connected = false), monitor.state.value)
        assertFalse(monitor.state.value.ready)
    }

    @Test
    fun a_rebind_inside_the_grace_keeps_access_steady() = runBlocking {
        val monitor = AccessibilityMonitor(this, graceMs = 200)
        monitor.connected()

        monitor.disconnected()
        // The banner must not blink: the old binding is still standing.
        assertTrue(monitor.state.value.connected)
        monitor.connected()

        delay(300)
        assertTrue(monitor.state.value.connected)
    }

    @Test
    fun a_resume_during_the_grace_does_not_cut_it_short() = runBlocking {
        val monitor = AccessibilityMonitor(this, graceMs = 150)
        monitor.connected()
        monitor.disconnected()

        monitor.refresh(enabled = true, connected = false)
        delay(50)

        assertTrue(monitor.state.value.connected)
    }

    @Test
    fun a_disconnect_without_a_rebind_is_published() = runBlocking {
        val monitor = AccessibilityMonitor(this, graceMs = 50)
        monitor.connected()

        monitor.disconnected()
        assertTrue(monitor.state.value.connected)

        withTimeout(2_000) { monitor.state.first { !it.connected } }
        // The service is off, not the permission: the fix is still to turn it on.
        assertTrue(monitor.state.value.enabled)
    }
}
