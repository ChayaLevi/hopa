package com.hopa.capabilities.display.client

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HelperSupervisorTest {
    @Test
    fun an_answering_helper_is_running_without_a_start() = runBlocking {
        val helper = FakeHelper(answering = true)

        val status = helper.supervisor.ensure()

        assertEquals(HelperStatus.Running, status)
        assertEquals(0, helper.starts)
    }

    @Test
    fun a_dead_helper_is_started_and_reported_running() = runBlocking {
        val helper = FakeHelper(answering = false)

        val status = helper.supervisor.ensure()

        assertEquals(HelperStatus.Running, status)
        assertEquals(1, helper.starts)
        assertEquals(HelperStatus.Running, helper.supervisor.status.value)
    }

    @Test
    fun a_start_that_fails_is_named() = runBlocking {
        val helper = FakeHelper(answering = false, startSucceeds = false)

        val status = helper.supervisor.ensure()

        assertTrue(status is HelperStatus.Failed)
        assertEquals(1, helper.starts)
        assertTrue((status as HelperStatus.Failed).reason.contains("did not start"))
    }

    @Test
    fun a_start_that_never_answers_gives_up_after_the_attempts() = runBlocking {
        val helper = FakeHelper(answering = false, startBinds = false)

        val status = helper.supervisor.ensure()

        assertTrue(status is HelperStatus.Failed)
        assertTrue((status as HelperStatus.Failed).reason.contains("did not answer"))
        assertEquals(1, helper.starts)
        assertEquals(3, helper.pauses)
    }

    @Test
    fun health_reports_a_helper_that_died_after_it_ran() = runBlocking {
        val helper = FakeHelper(answering = true)
        assertEquals(HelperStatus.Running, helper.supervisor.health())

        helper.answering = false
        val status = helper.supervisor.health()

        assertTrue(status is HelperStatus.Failed)
        assertEquals(0, helper.starts)
    }

    @Test
    fun release_stops_the_helper_and_says_so() = runBlocking {
        val helper = FakeHelper(answering = true)
        helper.supervisor.ensure()

        helper.supervisor.release()

        assertEquals(HelperStatus.Stopped, helper.supervisor.status.value)
        assertEquals(1, helper.stops)
    }

    private class FakeHelper(
        var answering: Boolean,
        private val startSucceeds: Boolean = true,
        private val startBinds: Boolean = true,
    ) {
        var starts = 0
        var stops = 0
        var pauses = 0
        val supervisor = HelperSupervisor(
            ping = { answering },
            start = {
                starts += 1
                if (startSucceeds && startBinds) {
                    answering = true
                }
                startSucceeds
            },
            stop = {
                stops += 1
                answering = false
            },
            pause = { pauses += 1 },
            attempts = 3,
        )
    }
}
